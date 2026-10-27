package app.arju.mpvgo.downloader.engine

/**
 * Byte-range bookkeeping for a multi-connection download.
 *
 * Each [Part] covers [start, end) and has written bytes [start, pos). Workers [claim] parts; when nothing
 * is unclaimed, the largest running part is split so idle connections keep helping. The first and last
 * [EDGE_BYTES] are claimed first so containers with their index at either end open quickly while the
 * rest downloads. A [prioritize]d offset (a seek during play-while-downloading) jumps the queue.
 * All methods are thread-safe.
 */
class RangePlan private constructor(val total: Long, parts: List<Part>) {

  class Part internal constructor(val start: Long, end: Long, pos: Long) {
    @Volatile var end: Long = end
      internal set

    @Volatile var pos: Long = pos
      internal set

    @Volatile var claimed: Boolean = false
      internal set

    val remaining: Long get() = (end - pos).coerceAtLeast(0)
    val isDone: Boolean get() = pos >= end
  }

  private val parts = parts.sortedBy { it.start }.toMutableList()
  private val priority = ArrayDeque<Long>()

  @Synchronized
  fun snapshot(): List<Part> = parts.toList()

  @Synchronized
  fun downloadedBytes(): Long = parts.sumOf { it.pos - it.start }

  @Synchronized
  fun isComplete(): Boolean = parts.all { it.isDone }

  /** Claims the next part to download, splitting a running part if needed. Null when nothing is left. */
  @Synchronized
  fun claim(minSplit: Long = MIN_SPLIT_BYTES): Part? {
    while (priority.isNotEmpty()) {
      val p = partStartingAt(priority.removeFirst()) ?: continue
      if (!p.claimed && !p.isDone) return p.also { it.claimed = true }
    }
    val free = parts.filter { !it.claimed && !it.isDone }
    val pick = free.firstOrNull { it.start == 0L }
      ?: free.firstOrNull { it.end == total && it.start >= total - EDGE_BYTES }
      ?: free.firstOrNull()
    if (pick != null) return pick.also { it.claimed = true }

    val victim = parts.filter { it.claimed && !it.isDone }.maxByOrNull { it.remaining } ?: return null
    if (victim.remaining < minSplit * 2) return null
    val mid = victim.pos + victim.remaining / 2
    return insertSplit(victim, mid).also { it.claimed = true }
  }

  @Synchronized
  fun release(part: Part) {
    part.claimed = false
  }

  @Synchronized
  fun advance(part: Part, count: Int) {
    part.pos = (part.pos + count).coerceAtMost(part.end)
  }

  /** Asks the next free connection to start at [offset]. */
  @Synchronized
  fun prioritize(offset: Long) {
    if (offset !in 0 until total || isAvailable(offset)) return
    priority.remove(offset)
    priority.addFirst(offset)
  }

  @Synchronized
  fun hasPriorityWork(): Boolean = priority.isNotEmpty()

  @Synchronized
  fun isAvailable(offset: Long): Boolean = parts.any { offset >= it.start && offset < it.pos }

  /** Number of contiguous downloaded bytes starting at [offset]. */
  @Synchronized
  fun availableFrom(offset: Long): Long {
    var cur = offset
    while (true) {
      val p = parts.firstOrNull { cur >= it.start && cur < it.pos } ?: break
      cur = p.pos
      if (p.pos < p.end) break
    }
    return cur - offset
  }

  /** Returns a part beginning exactly at [offset], splitting one if needed; null if it's already covered. */
  private fun partStartingAt(offset: Long): Part? {
    val p = parts.firstOrNull { offset >= it.start && offset < it.end } ?: return null
    if (offset < p.pos) return null
    if (offset == p.pos) return if (p.claimed) null else p
    // A running part reaches this offset soon if it is close; don't open another connection for that.
    if (p.claimed && offset - p.pos < MIN_SPLIT_BYTES / 2) return null
    return insertSplit(p, offset)
  }

  private fun insertSplit(p: Part, at: Long): Part {
    val fresh = Part(at, p.end, at)
    p.end = at
    parts.add(parts.indexOf(p) + 1, fresh)
    return fresh
  }

  /** Compact persistable form: "start:pos:end;..." */
  @Synchronized
  fun serialize(): String = parts.joinToString(";") { "${it.start}:${it.pos}:${it.end}" }

  companion object {
    const val EDGE_BYTES = 2L * 1024 * 1024
    const val MIN_SPLIT_BYTES = 1L * 1024 * 1024

    fun create(total: Long, connections: Int): RangePlan {
      require(total > 0)
      val n = connections.coerceIn(1, 32)
      val parts = mutableListOf<Part>()
      if (n > 1 && total > EDGE_BYTES * 4) {
        parts += Part(0, EDGE_BYTES, 0)
        parts += Part(total - EDGE_BYTES, total, total - EDGE_BYTES)
        val midEnd = total - EDGE_BYTES
        val chunks = (n - 2).coerceAtLeast(1)
        val size = (midEnd - EDGE_BYTES) / chunks
        for (i in 0 until chunks) {
          val s = EDGE_BYTES + i * size
          parts += Part(s, if (i == chunks - 1) midEnd else s + size, s)
        }
      } else {
        val size = total / n
        for (i in 0 until n) {
          val s = i * size
          parts += Part(s, if (i == n - 1) total else s + size, s)
        }
      }
      return RangePlan(total, parts)
    }

    fun deserialize(total: Long, text: String): RangePlan? = runCatching {
      val parts = text.split(';').filter { it.isNotBlank() }.map {
        val (s, p, e) = it.split(':').map(String::toLong)
        Part(s, e, p)
      }
      if (parts.isEmpty() || parts.sumOf { it.end - it.start } != total) null else RangePlan(total, parts)
    }.getOrNull()
  }
}
