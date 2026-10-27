package app.arju.mpvgo.webbrowser.adblock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Owns the native adblock-rust engine.
 *
 * Nothing is loaded at app start. [warmUp] (called when the browser opens) loads the compiled
 * binary snapshot (fast), or compiles it from cached lists, or downloads the lists first.
 * Request checks are lock-cheap and safe from any WebView network thread.
 */
class AdBlocker(
  private val context: Context,
  private val prefs: AdBlockPreferences,
) {
  sealed interface State {
    data object Idle : State

    data object Loading : State

    data class Ready(val lists: Int) : State

    /** Lists aren't downloaded yet and the download failed (e.g. offline). */
    data object NoLists : State

    data object Unavailable : State
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mutex = Mutex()
  private val lock = ReentrantReadWriteLock()
  private var handle = 0L
  private val dir = File(context.filesDir, "adblock")
  private val listDir = File(dir, "lists")
  private val snapshot = File(dir, "adblock_engine.dat")
  private val client by lazy {
    OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
  }

  @Volatile private var enabled = prefs.enabled.get()

  @Volatile private var allowedSites: Set<String> = prefs.allowedSites.get()

  private val _state = MutableStateFlow<State>(State.Idle)
  val state: StateFlow<State> = _state.asStateFlow()

  private val blocked = AtomicLong()
  private val _blockedTotal = MutableStateFlow(0L)

  /** Requests blocked since the app started. */
  val blockedTotal: StateFlow<Long> = _blockedTotal.asStateFlow()

  private var started = false

  /** Loads the engine in the background if it isn't loaded yet. Cheap to call repeatedly. */
  fun warmUp() {
    synchronized(this) {
      if (started) return
      started = true
    }
    prefs.enabled.changes().onEach { enabled = it }.launchIn(scope)
    prefs.allowedSites.changes().onEach { allowedSites = it }.launchIn(scope)
    AdBlockUpdateWorker.schedule(context, prefs.autoUpdateWifiOnly.get())
    scope.launch { load() }
  }

  val isEnabled: Boolean get() = enabled

  fun isAllowedSite(pageUrl: String): Boolean = SiteMatcher.isAllowed(pageUrl, allowedSites)

  /**
   * True if the request should be blocked. [pageUrl] is the top-level page (used for the
   * allowed-sites list), [sourceUrl] the frame that made the request.
   */
  fun shouldBlock(
    url: String,
    sourceUrl: String,
    requestType: String,
    pageUrl: String = sourceUrl,
  ): Boolean {
    if (!enabled || url.startsWith("data:") || url.startsWith("blob:")) return false
    if (isAllowedSite(pageUrl)) return false
    val result =
      lock.read {
        if (handle == 0L) return false
        AdBlockBridge.nativeShouldBlock(handle, url, sourceUrl, requestType) == 1
      }
    if (result) _blockedTotal.value = blocked.incrementAndGet()
    return result
  }

  /** Page-level cosmetic CSS rules for [url], or null when there's nothing to apply. */
  fun cosmetic(url: String): Cosmetic? {
    if (!enabled || !prefs.cosmetic.get() || isAllowedSite(url)) return null
    val parts =
      lock.read {
        if (handle == 0L) return null
        AdBlockBridge.nativeCosmetic(handle, url)
      } ?: return null
    if (parts.size < 4) return null
    return Cosmetic(
      genericHide = parts[0] == "1",
      hideSelectors = parts[1],
      exceptions = parts[2],
      injectedScript = parts[3],
    )
  }

  /** Generic hide selectors for classes/ids found on the page, joined by ",". */
  fun classIdSelectors(
    classes: List<String>,
    ids: List<String>,
    exceptions: String,
  ): String {
    if (!enabled || (classes.isEmpty() && ids.isEmpty())) return ""
    return lock.read {
      if (handle == 0L) return ""
      AdBlockBridge.nativeClassIdSelectors(handle, classes.toTypedArray(), ids.toTypedArray(), exceptions)
    }.orEmpty()
  }

  private fun activeLists(): List<FilterList> {
    val ids = prefs.enabledLists.get()
    return FilterLists.BUILT_IN.filter { it.id in ids } + FilterLists.custom(prefs.customListUrls.get())
  }

  private fun listFile(list: FilterList) = File(listDir, "${list.id}.txt")

  private suspend fun load() =
    mutex.withLock {
      if (!AdBlockBridge.isAvailable) {
        _state.value = State.Unavailable
        return@withLock
      }
      _state.value = State.Loading
      if (snapshot.exists() && swapIn(snapshot.readBytes())) return@withLock
      if (activeLists().none { listFile(it).exists() }) downloadLists(force = false)
      compileAndLoad()
    }

  /**
   * Downloads the enabled lists ([force] re-downloads existing ones) and rebuilds the engine.
   * Returns true if the engine is ready afterwards.
   */
  suspend fun update(force: Boolean = true): Boolean =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        if (!AdBlockBridge.isAvailable) return@withLock false
        if (_state.value !is State.Ready) _state.value = State.Loading
        val downloaded = downloadLists(force)
        if (downloaded > 0 || _state.value !is State.Ready) compileAndLoad() else true
      }
    }

  /** Rebuilds the engine from the cached lists, e.g. after My filters or the list choice changed. */
  suspend fun rebuild(): Boolean =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        if (!AdBlockBridge.isAvailable) return@withLock false
        if (activeLists().any { !listFile(it).exists() }) downloadLists(force = false)
        compileAndLoad()
      }
    }

  /** Returns how many lists were downloaded. Failures keep the old copy. */
  private fun downloadLists(force: Boolean): Int {
    listDir.mkdirs()
    var count = 0
    for (list in activeLists()) {
      val file = listFile(list)
      if (!force && file.exists()) continue
      runCatching {
        val request = Request.Builder().url(list.url).header("User-Agent", "mpvGo").build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) error("HTTP ${response.code}")
          val tmp = File(listDir, "${list.id}.tmp")
          response.body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
          if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
          }
        }
        count++
      }.onFailure { Log.w(TAG, "Couldn't download ${list.name}: ${it.message}") }
    }
    if (count > 0) prefs.lastUpdated.set(System.currentTimeMillis())
    return count
  }

  private fun compileAndLoad(): Boolean {
    val lists = activeLists().map(::listFile).filter { it.exists() }
    val texts = lists.map { it.readText() }.toMutableList()
    prefs.myFilters.get().takeIf { it.isNotBlank() }?.let(texts::add)
    if (texts.isEmpty()) {
      snapshot.delete()
      replace(0L)
      _state.value = State.NoLists
      return false
    }
    val start = System.nanoTime()
    val bytes = AdBlockBridge.nativeCompile(texts.toTypedArray())
    if (bytes == null) {
      _state.value = State.NoLists
      return false
    }
    Log.i(TAG, "Compiled ${lists.size} lists in ${(System.nanoTime() - start) / 1_000_000} ms, ${bytes.size} bytes")
    dir.mkdirs()
    val tmp = File(dir, "adblock_engine.tmp")
    tmp.writeBytes(bytes)
    snapshot.delete()
    tmp.renameTo(snapshot)
    return swapIn(bytes, lists.size)
  }

  private fun swapIn(
    bytes: ByteArray,
    listCount: Int = activeLists().count { listFile(it).exists() },
  ): Boolean {
    val start = System.nanoTime()
    val newHandle = AdBlockBridge.nativeLoad(bytes)
    if (newHandle == 0L) return false
    Log.i(TAG, "Loaded snapshot in ${(System.nanoTime() - start) / 1_000_000} ms")
    replace(newHandle)
    _state.value = State.Ready(listCount)
    return true
  }

  private fun replace(newHandle: Long) {
    val old =
      lock.write {
        val old = handle
        handle = newHandle
        old
      }
    if (old != 0L) AdBlockBridge.nativeFree(old)
  }

  data class Cosmetic(
    val genericHide: Boolean,
    val hideSelectors: String,
    val exceptions: String,
    val injectedScript: String,
  )

  companion object {
    private const val TAG = "AdBlocker"
  }
}
