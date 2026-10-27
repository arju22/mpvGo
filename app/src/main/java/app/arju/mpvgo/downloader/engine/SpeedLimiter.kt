package app.arju.mpvgo.downloader.engine

import kotlinx.coroutines.delay

/** Global token bucket shared by every connection. A limit of 0 means unlimited. */
class SpeedLimiter(private val bytesPerSecond: () -> Long) {
  private var available = 0L
  private var last = System.nanoTime()

  suspend fun acquire(bytes: Int) {
    while (true) {
      val limit = bytesPerSecond()
      if (limit <= 0) return
      val wait: Long
      synchronized(this) {
        val now = System.nanoTime()
        available = (available + (now - last) * limit / 1_000_000_000L).coerceAtMost(limit)
        last = now
        if (available >= bytes || available >= limit) {
          available -= bytes
          return
        }
        wait = ((bytes - available) * 1000L / limit).coerceIn(5, 1000)
      }
      delay(wait)
    }
  }
}

/** Moving average over the last few seconds, for speed and ETA. */
class SpeedMeter(private val windowMs: Long = 3000) {
  private val samples = ArrayDeque<Pair<Long, Long>>()

  @Synchronized
  fun sample(totalBytes: Long, now: Long = System.currentTimeMillis()): Long {
    samples.addLast(now to totalBytes)
    while (samples.size > 2 && now - samples.first().first > windowMs) samples.removeFirst()
    val (t0, b0) = samples.first()
    val dt = now - t0
    return if (dt <= 0) 0 else ((totalBytes - b0) * 1000 / dt).coerceAtLeast(0)
  }

  @Synchronized
  fun reset() = samples.clear()
}
