package app.arju.mpvgo.downloader.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Downloads an HLS stream: picks a variant, fetches segments (AES-128 decrypted) into a work folder,
 * then joins them. TS output is remuxed to MP4 when asked; fMP4 is joined as MP4.
 */
class HlsDownloader(private val client: OkHttpClient) {

  data class Result(val file: File, val extension: String)

  interface Listener {
    fun onProgress(segmentsDone: Int, segmentsTotal: Int, bytes: Long) {}

    suspend fun onCheckpoint(segmentsDone: Int, segmentsTotal: Int, bytes: Long) {}
  }

  suspend fun download(
    url: String,
    headers: Map<String, String>,
    workDir: File,
    preferredHeight: Int?,
    connections: Int,
    remuxToMp4: Boolean,
    limiter: SpeedLimiter,
    listener: Listener,
  ): Result = withContext(Dispatchers.IO) {
    val media = resolveMedia(url, headers, preferredHeight)
    if (media.isLive) throw DownloadFailure(FailReason.Live)
    if (media.hasUnsupportedEncryption) throw DownloadFailure(FailReason.Drm)
    if (media.segments.isEmpty()) throw DownloadFailure(FailReason.NotVideo)

    workDir.mkdirs()
    val segments = media.segments
    val total = segments.size
    val doneCount = AtomicInteger(segments.indices.count { segFile(workDir, it).exists() })
    val bytes = AtomicLong(segments.indices.sumOf { segFile(workDir, it).takeIf(File::exists)?.length() ?: 0L })
    listener.onProgress(doneCount.get(), total, bytes.get())

    val keys = ConcurrentHashMap<String, ByteArray>()
    val keyLock = Mutex()
    val next = AtomicInteger(0)
    var lastCheckpoint = 0L
    coroutineScope {
      repeat(connections.coerceIn(1, MAX_PARALLEL_SEGMENTS)) {
        launch(Dispatchers.IO) {
          while (true) {
            val i = next.getAndIncrement()
            if (i >= total) break
            val out = segFile(workDir, i)
            if (out.exists()) continue
            val seg = segments[i]
            val key = seg.key?.takeIf { it.method.equals("AES-128", true) }?.let { k ->
              val uri = k.uri ?: throw DownloadFailure(FailReason.Drm)
              keys[uri] ?: keyLock.withLock { keys.getOrPut(uri) { fetchBytes(uri, headers) } }
            }
            val tmp = File(workDir, "${out.name}.tmp")
            val written = fetchSegment(seg, key, headers, tmp, limiter)
            if (!tmp.renameTo(out)) throw java.io.IOException("Can't save segment")
            bytes.addAndGet(written)
            val d = doneCount.incrementAndGet()
            listener.onProgress(d, total, bytes.get())
            val now = System.currentTimeMillis()
            if (now - lastCheckpoint > 2000) {
              lastCheckpoint = now
              listener.onCheckpoint(d, total, bytes.get())
            }
          }
        }
      }
    }
    withContext(NonCancellable) { listener.onCheckpoint(total, total, bytes.get()) }

    val isFmp4 = media.initUrl != null
    val joined = File(workDir, if (isFmp4) "joined.mp4" else "joined.ts")
    joined.outputStream().buffered(256 * 1024).use { out ->
      media.initUrl?.let { out.write(fetchBytes(it, headers)) }
      for (i in segments.indices) {
        currentCoroutineContext().ensureActive()
        segFile(workDir, i).inputStream().use { it.copyTo(out, 256 * 1024) }
      }
    }
    if (isFmp4) return@withContext Result(joined, "mp4")
    if (remuxToMp4) {
      val mp4 = File(workDir, "joined.mp4")
      if (TsRemuxer.remux(joined, mp4)) {
        joined.delete()
        return@withContext Result(mp4, "mp4")
      }
      mp4.delete()
    }
    Result(joined, "ts")
  }

  /** Variants available for [url], best first; empty when it's already a media playlist. */
  suspend fun variants(url: String, headers: Map<String, String>): List<HlsPlaylist.Variant> = withContext(Dispatchers.IO) {
    when (val p = HlsPlaylist.parse(fetchText(url, headers), url)) {
      is HlsPlaylist.Master -> p.variants
      is HlsPlaylist.MediaPlaylist -> emptyList()
    }
  }

  private suspend fun resolveMedia(url: String, headers: Map<String, String>, preferredHeight: Int?): HlsPlaylist.Media {
    val text = fetchText(url, headers)
    if (!HlsPlaylist.isPlaylist(text)) throw DownloadFailure(FailReason.NotVideo)
    return when (val p = HlsPlaylist.parse(text, url)) {
      is HlsPlaylist.MediaPlaylist -> p.media
      is HlsPlaylist.Master -> {
        val v = HlsPlaylist.choose(p.variants, preferredHeight) ?: throw DownloadFailure(FailReason.NotVideo)
        HlsPlaylist.parseMedia(fetchText(v.url, headers), v.url)
      }
    }
  }

  private suspend fun fetchSegment(
    seg: HlsPlaylist.Segment,
    key: ByteArray?,
    headers: Map<String, String>,
    tmp: File,
    limiter: SpeedLimiter,
  ): Long {
    var attempt = 0
    while (true) {
      try {
        return open(seg.url, headers).use { r ->
          var input: InputStream = r.body.byteStream()
          if (key != null) {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(HlsPlaylist.ivFor(seg)))
            input = CipherInputStream(input, cipher)
          }
          var n = 0L
          tmp.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
              currentCoroutineContext().ensureActive()
              val read = input.read(buf)
              if (read < 0) break
              limiter.acquire(read)
              out.write(buf, 0, read)
              n += read
            }
          }
          n
        }
      } catch (e: java.io.IOException) {
        // A few quick in-place retries per segment before failing the whole download.
        val reason = FailReason.fromThrowable(e)
        if (!reason.retryable || ++attempt >= 3) throw e
        kotlinx.coroutines.delay(RetryPolicy.delayMs(attempt))
      }
    }
  }

  private suspend fun fetchText(url: String, headers: Map<String, String>): String =
    open(url, headers).use { it.body.string() }

  private suspend fun fetchBytes(url: String, headers: Map<String, String>): ByteArray =
    open(url, headers).use { it.body.bytes() }

  private suspend fun open(url: String, headers: Map<String, String>): Response {
    val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
    val call = client.newCall(req)
    val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
    val r = try {
      call.execute()
    } finally {
      handle.dispose()
    }
    if (!r.isSuccessful) {
      r.close()
      throw DownloadHttpException(r.code)
    }
    return r
  }

  private fun segFile(dir: File, i: Int) = File(dir, "%06d.seg".format(i))

  companion object {
    const val MAX_PARALLEL_SEGMENTS = 6
  }
}
