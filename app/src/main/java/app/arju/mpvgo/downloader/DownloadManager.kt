package app.arju.mpvgo.downloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import app.arju.mpvgo.downloader.data.DownloadDao
import app.arju.mpvgo.downloader.data.DownloadEntity
import app.arju.mpvgo.downloader.data.DownloadPreferences
import app.arju.mpvgo.downloader.data.DownloadStatus
import app.arju.mpvgo.downloader.engine.DownloadFinalizer
import app.arju.mpvgo.downloader.engine.DownloadNaming
import app.arju.mpvgo.downloader.engine.FailReason
import app.arju.mpvgo.downloader.engine.HlsDownloader
import app.arju.mpvgo.downloader.engine.HttpDownloader
import app.arju.mpvgo.downloader.engine.RangePlan
import app.arju.mpvgo.downloader.engine.RetryPolicy
import app.arju.mpvgo.downloader.engine.SpeedLimiter
import app.arju.mpvgo.downloader.engine.SpeedMeter
import app.arju.mpvgo.downloader.engine.SwitchToHls
import app.arju.mpvgo.downloader.engine.TransferListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Live, in-memory transfer stats (not persisted; the DB is checkpointed every couple of seconds). */
data class LiveProgress(
  val downloaded: Long = 0,
  val total: Long = -1,
  val speedBps: Long = 0,
  val connections: Int = 0,
  val segmentsDone: Int = 0,
  val segmentsTotal: Int = 0,
) {
  val etaSeconds: Long get() = if (speedBps > 0 && total > 0) (total - downloaded).coerceAtLeast(0) / speedBps else -1
}

data class NewDownload(
  val url: String,
  val fileName: String,
  val folder: String? = null,
  val title: String? = null,
  val mimeType: String? = null,
  val isHls: Boolean = false,
  val hlsQuality: Int = 0,
  val connections: Int? = null,
  val wifiOnly: Boolean? = null,
  val userAgent: String? = null,
  val referer: String? = null,
  val cookies: String? = null,
  val sourcePageUrl: String? = null,
  val isPrivate: Boolean = false,
  val nameEdited: Boolean = false,
  /** False = "Add to queue" (runs after what's already queued). */
  val startNow: Boolean = true,
)

/** Owns the download queue. The service and worker only keep the process in the foreground while it's busy. */
class DownloadManager(
  private val context: Context,
  private val dao: DownloadDao,
  private val prefs: DownloadPreferences,
  private val finalizer: DownloadFinalizer,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .retryOnConnectionFailure(true)
    .build()
  private val http = HttpDownloader(client)
  private val hls = HlsDownloader(client)
  private val limiter = SpeedLimiter { prefs.speedLimitKbps.get().toLong() * 1024 }
  private val scheduleLock = Mutex()
  private val jobs = ConcurrentHashMap<Long, Job>()
  private val plans = ConcurrentHashMap<Long, RangePlan>()

  private val _live = MutableStateFlow<Map<Long, LiveProgress>>(emptyMap())
  val live: StateFlow<Map<Long, LiveProgress>> = _live.asStateFlow()

  private val _running = MutableStateFlow<Set<Long>>(emptySet())
  val running: StateFlow<Set<Long>> = _running.asStateFlow()

  /** Set when a queued Wi-Fi-only item is waiting for an unmetered network. */
  private val _waitingForWifi = MutableStateFlow(false)
  val waitingForWifi: StateFlow<Boolean> = _waitingForWifi.asStateFlow()

  /** Finished/failed events for notifications. */
  var onFinished: ((DownloadEntity) -> Unit)? = null
  var onFailed: ((DownloadEntity) -> Unit)? = null

  val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

  init {
    runCatching {
      context.getSystemService(ConnectivityManager::class.java)
        .registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) = schedule()

          override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = schedule()
        })
    }
    scope.launch { dao.observeActiveCount().collect { DownloadCounts.setActive(it) } }
  }

  // ---- Public API -----------------------------------------------------------------------------

  suspend fun enqueue(request: NewDownload): Long {
    val entity = DownloadEntity(
      url = request.url,
      fileName = request.fileName,
      folder = request.folder ?: prefs.folder.get(),
      title = request.title,
      mimeType = request.mimeType,
      isHls = request.isHls || DownloadNaming.isHlsUrl(request.url, request.mimeType),
      hlsQuality = request.hlsQuality,
      connections = (request.connections ?: prefs.connectionsPerFile.get()).coerceIn(1, DownloadPreferences.MAX_CONNECTIONS),
      wifiOnly = request.wifiOnly ?: prefs.wifiOnly.get(),
      userAgent = request.userAgent,
      referer = request.referer,
      cookies = request.cookies,
      sourcePageUrl = request.sourcePageUrl,
      isPrivate = request.isPrivate,
      nameEdited = request.nameEdited,
      priority = if (request.startNow) 1 else 0,
    )
    val id = dao.insert(entity)
    schedule()
    return id
  }

  fun pause(ids: Collection<Long>) = scope.launch {
    ids.forEach { id ->
      dao.setStatus(id, DownloadStatus.Paused)
      jobs[id]?.cancel(CancellationException("paused"))
    }
  }

  fun pauseAll() = scope.launch { pause(dao.pending().map { it.id }) }

  fun resume(ids: Collection<Long>) = scope.launch {
    dao.getAll(ids).filter { it.status == DownloadStatus.Paused || it.status == DownloadStatus.Failed }.forEach {
      dao.update(it.copy(status = DownloadStatus.Queued, failReason = null, retries = 0))
    }
    schedule()
  }

  fun retry(ids: Collection<Long>) = resume(ids)

  fun delete(ids: Collection<Long>, deleteFiles: Boolean = prefs.deleteFileOnRemove.get()) = scope.launch {
    val items = dao.getAll(ids)
    items.forEach { jobs[it.id]?.cancel(CancellationException("removed")) }
    items.forEach { jobs[it.id]?.join() }
    dao.delete(ids)
    items.forEach { e ->
      cleanupWork(e.id)
      if (deleteFiles && e.status == DownloadStatus.Completed) finalizer.delete(e.finalPath, e.finalUri)
    }
    schedule()
  }

  fun cancelAll() = scope.launch { delete(dao.pending().map { it.id }, deleteFiles = false) }

  fun rename(id: Long, newName: String) = scope.launch {
    val e = dao.get(id) ?: return@launch
    val clean = DownloadNaming.sanitize(newName).ifBlank { return@launch }
    val name = if (DownloadNaming.extensionOf(clean) == null) clean + "." + e.fileName.substringAfterLast('.', "mp4") else clean
    if (e.status == DownloadStatus.Completed && e.finalPath != null) {
      val f = File(e.finalPath)
      val target = File(f.parentFile, name)
      if (!target.exists() && f.renameTo(target)) {
        android.media.MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath, target.absolutePath), null, null)
        dao.update(e.copy(fileName = name, nameEdited = true, finalPath = target.absolutePath))
        app.arju.mpvgo.core.utils.media.MediaLibraryEvents.notifyChanged()
      }
    } else {
      dao.update(e.copy(fileName = name, nameEdited = true))
    }
  }

  /** "Edit link" / "Refresh link": swap in a fresh URL and keep the bytes already on disk when possible. */
  fun updateLink(id: Long, url: String) = scope.launch {
    val e = dao.get(id) ?: return@launch
    jobs[id]?.cancel(CancellationException("relink"))
    jobs[id]?.join()
    dao.update(e.copy(url = url, status = DownloadStatus.Queued, failReason = null, retries = 0))
    schedule()
  }

  fun plan(id: Long): RangePlan? = plans[id]

  suspend fun hlsVariants(url: String, headers: Map<String, String>) = hls.variants(url, headers)

  suspend fun get(id: Long): DownloadEntity? = dao.get(id)

  /** Final location of a download finished in this process (keeps play-while-downloading going). */
  fun finishedFile(id: Long): File? = finished[id]?.let(::File)

  private val finished = ConcurrentHashMap<Long, String>()

  fun partFile(id: Long): File = File(workRoot(), "$id.part")

  /** Requeues work interrupted by a killed process and starts it. Safe to call repeatedly. */
  fun restore() = scope.launch {
    dao.requeueInterrupted()
    schedule()
  }

  /** Called when Android stops the foreground service (dataSync time limit). */
  fun pauseForSystemLimit() = scope.launch {
    val active = jobs.keys.toList()
    active.forEach { dao.setStatus(it, DownloadStatus.Paused) }
    active.forEach { jobs[it]?.cancel(CancellationException("system limit")) }
  }

  suspend fun hasPending(): Boolean = dao.pending().isNotEmpty()

  // ---- Scheduling -----------------------------------------------------------------------------

  fun schedule() {
    scope.launch {
      scheduleLock.withLock {
        val pending = dao.pending()
        val limit = prefs.concurrentDownloads.get().coerceIn(1, 10)
        val unmetered = isUnmetered()
        var waiting = false
        for (e in pending) {
          if (jobs.containsKey(e.id)) continue
          if (jobs.size >= limit) break
          if (e.wifiOnly && !unmetered) {
            waiting = true
            continue
          }
          start(e)
        }
        _waitingForWifi.value = waiting
      }
    }
  }

  private fun start(entity: DownloadEntity) {
    val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { run(entity.id) }
    jobs[entity.id] = job
    _running.update { it + entity.id }
    job.invokeOnCompletion {
      jobs.remove(entity.id)
      plans.remove(entity.id)
      _running.update { it - entity.id }
      _live.update { it - entity.id }
      schedule()
    }
    DownloadService.start(context)
    job.start()
  }

  private suspend fun run(id: Long) {
    var entity = dao.get(id) ?: return
    dao.setStatus(id, DownloadStatus.Running)
    var attempt = entity.retries
    while (true) {
      entity = dao.get(id) ?: return
      try {
        val (file, ext) = transfer(entity)
        entity = dao.get(id) ?: return
        val name = DownloadNaming.withExtension(entity.fileName, ext)
        val size = file.length()
        val placed = finalizer.place(file, entity.folder, name)
        cleanupWork(id)
        val done = entity.copy(
          status = DownloadStatus.Completed,
          fileName = placed.path?.let { File(it).name } ?: name,
          finalPath = placed.path,
          finalUri = placed.uri?.toString(),
          durationMs = placed.durationMs,
          width = placed.width,
          height = placed.height,
          downloadedBytes = size,
          totalBytes = size,
          failReason = null,
          completedAt = System.currentTimeMillis(),
          partsState = null,
        )
        dao.update(done)
        placed.path?.let { finished[id] = it }
        onFinished?.invoke(done)
        return
      } catch (c: CancellationException) {
        throw c
      } catch (t: Throwable) {
        val reason = FailReason.fromThrowable(t)
        Log.w(TAG, "Download $id failed (${reason.name}), attempt $attempt", t)
        attempt++
        val current = dao.get(id) ?: return
        if (prefs.autoRetry.get() && RetryPolicy.shouldRetry(reason, attempt)) {
          dao.update(current.copy(retries = attempt, failReason = reason))
          delay(RetryPolicy.delayMs(attempt))
          continue
        }
        val failed = current.copy(status = DownloadStatus.Failed, failReason = reason, retries = attempt)
        dao.update(failed)
        onFailed?.invoke(failed)
        return
      }
    }
  }

  /** Returns the finished file and its real extension. */
  private suspend fun transfer(entity: DownloadEntity): Pair<File, String> {
    val id = entity.id
    if (!entity.isHls) {
      val target = partFile(id)
      val meter = SpeedMeter()
      var lastEmit = 0L
      try {
        http.download(
          url = entity.url,
          headers = entity.headers(),
          target = target,
          connections = entity.connections,
          knownTotal = entity.totalBytes,
          resumeState = entity.partsState,
          limiter = limiter,
          listener = object : TransferListener {
            override fun onPlan(plan: RangePlan?) {
              if (plan != null) plans[id] = plan else plans.remove(id)
            }

            override suspend fun onResolved(info: HttpDownloader.Resolved) {
              val e = dao.get(id) ?: return
              val renamed = if (e.nameEdited) {
                e.fileName
              } else {
                DownloadNaming.suggest(info.finalUrl, e.title, info.contentDisposition, info.mimeType)
              }
              dao.update(e.copy(totalBytes = info.total, supportsResume = info.supportsResume, mimeType = info.mimeType, fileName = renamed))
            }

            override fun onProgress(downloaded: Long, total: Long, activeConnections: Int) {
              val now = System.currentTimeMillis()
              if (now - lastEmit < 500) return
              lastEmit = now
              val speed = meter.sample(downloaded, now)
              _live.update { it + (id to LiveProgress(downloaded, total, speed, activeConnections)) }
            }

            override suspend fun onCheckpoint(downloaded: Long, total: Long, state: String?) {
              withContext(NonCancellable) { dao.updateProgress(id, downloaded, total, state, 0, 0) }
            }
          },
        )
      } catch (_: SwitchToHls) {
        val e = dao.get(id) ?: throw CancellationException("removed")
        dao.update(e.copy(isHls = true, partsState = null, fileName = DownloadNaming.withExtension(e.fileName, "mp4")))
        target.delete()
        return transfer(e.copy(isHls = true))
      }
      return target to (DownloadNaming.extensionOf(dao.get(id)?.fileName ?: entity.fileName) ?: "mp4")
    }

    val meter = SpeedMeter()
    var lastEmit = 0L
    val result = hls.download(
      url = entity.url,
      headers = entity.headers(),
      workDir = File(workRoot(), "$id.hls"),
      preferredHeight = entity.hlsQuality.takeIf { it > 0 },
      connections = entity.connections,
      remuxToMp4 = prefs.hlsToMp4.get(),
      limiter = limiter,
      listener = object : HlsDownloader.Listener {
        override fun onProgress(segmentsDone: Int, segmentsTotal: Int, bytes: Long) {
          val now = System.currentTimeMillis()
          if (now - lastEmit < 500) return
          lastEmit = now
          val speed = meter.sample(bytes, now)
          _live.update { it + (id to LiveProgress(bytes, -1, speed, 1, segmentsDone, segmentsTotal)) }
        }

        override suspend fun onCheckpoint(segmentsDone: Int, segmentsTotal: Int, bytes: Long) {
          withContext(NonCancellable) { dao.updateProgress(id, bytes, -1, null, segmentsDone, segmentsTotal) }
        }
      },
    )
    return result.file to result.extension
  }

  private fun cleanupWork(id: Long) {
    partFile(id).delete()
    File(workRoot(), "$id.hls").deleteRecursively()
  }

  private fun workRoot(): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "parts").apply { mkdirs() }

  private fun isUnmetered(): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
  }

  companion object {
    private const val TAG = "DownloadManager"
  }
}
