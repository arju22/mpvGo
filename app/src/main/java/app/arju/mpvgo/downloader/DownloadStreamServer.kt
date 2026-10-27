package app.arju.mpvgo.downloader

import app.arju.mpvgo.downloader.engine.RangePlan
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Loopback HTTP server that streams a download while it is still being written.
 * Reads block until the requested bytes exist and ask the [RangePlan] to fetch that offset next,
 * so seeking in mpv pulls the right part of the file forward.
 */
class DownloadStreamServer(private val manager: DownloadManager) : NanoHTTPD("127.0.0.1", 0) {

  fun urlFor(id: Long, fileName: String): String {
    ensureStarted()
    val safe = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
    return "http://127.0.0.1:$listeningPort/d/$id/$safe"
  }

  @Synchronized
  private fun ensureStarted() {
    if (!isAlive) start(SOCKET_READ_TIMEOUT, true)
  }

  override fun serve(session: IHTTPSession): Response {
    val id = session.uri.removePrefix("/d/").substringBefore('/').toLongOrNull()
      ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    val partPlan = manager.plan(id)
    val partFile = manager.partFile(id)
    val (file, plan) = if (partPlan != null && partFile.exists()) {
      partFile to partPlan
    } else {
      val done = manager.finishedFile(id)?.takeIf { it.exists() && it.length() > 0 }
        ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not available")
      val len = done.length()
      done to (RangePlan.deserialize(len, "0:$len:$len") ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, ""))
    }
    val total = plan.total
    val mime = "video/mp4"
    val range = session.headers["range"]
    var start = 0L
    var end = total - 1
    if (range != null && range.startsWith("bytes=")) {
      val spec = range.removePrefix("bytes=").substringBefore(',')
      val s = spec.substringBefore('-').trim()
      val e = spec.substringAfter('-').trim()
      if (s.isEmpty()) {
        start = (total - (e.toLongOrNull() ?: 0)).coerceAtLeast(0)
      } else {
        start = s.toLongOrNull() ?: 0
        if (e.isNotEmpty()) end = (e.toLongOrNull() ?: end).coerceAtMost(total - 1)
      }
    }
    if (start >= total) {
      return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
        addHeader("Content-Range", "bytes */$total")
      }
    }
    val length = end - start + 1
    val stream = PlanInputStream(file, plan, start, length)
    val status = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
    return newFixedLengthResponse(status, mime, stream, length).apply {
      addHeader("Accept-Ranges", "bytes")
      if (range != null) addHeader("Content-Range", "bytes $start-$end/$total")
    }
  }

  private class PlanInputStream(file: File, private val plan: RangePlan, start: Long, private var left: Long) : InputStream() {
    private val raf = RandomAccessFile(file, "r")
    private var pos = start

    override fun read(): Int {
      val b = ByteArray(1)
      return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (left <= 0) return -1
      var available = plan.availableFrom(pos)
      var waited = 0L
      if (available <= 0) plan.prioritize(pos)
      while (available <= 0) {
        if (waited > MAX_WAIT_MS) return -1
        Thread.sleep(100)
        waited += 100
        available = plan.availableFrom(pos)
      }
      val n = minOf(len.toLong(), available, left).toInt()
      raf.seek(pos)
      val read = raf.read(b, off, n)
      if (read > 0) {
        pos += read
        left -= read
      }
      return read
    }

    override fun close() {
      runCatching { raf.close() }
    }
  }

  companion object {
    private const val MAX_WAIT_MS = 60_000L
  }
}
