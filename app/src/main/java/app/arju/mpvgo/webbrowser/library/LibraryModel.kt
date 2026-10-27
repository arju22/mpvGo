package app.arju.mpvgo.webbrowser.library

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** History list sections: Today, Yesterday, Last 7 days, Last 30 days, Older. */
object HistoryGroups {
  enum class Section(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("Last 7 days"),
    MONTH("Last 30 days"),
    OLDER("Older"),
  }

  fun section(
    time: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
  ): Section {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
      days <= 0 -> Section.TODAY
      days == 1L -> Section.YESTERDAY
      days < 7 -> Section.WEEK
      days < 30 -> Section.MONTH
      else -> Section.OLDER
    }
  }

  /** [items] must be newest first; the order inside each section is kept. */
  fun <T> group(
    items: List<T>,
    time: (T) -> Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
  ): List<Pair<Section, List<T>>> = items.groupBy { section(time(it), now, zone) }.toList().sortedBy { it.first.ordinal }

  /** "11:32" for today and yesterday, "Mon" within a week, "12 Mar" or "12 Mar 2024" otherwise. */
  fun label(
    time: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    is24h: Boolean = true,
  ): String {
    val at = Instant.ofEpochMilli(time).atZone(zone)
    val pattern =
      when (section(time, now, zone)) {
        Section.TODAY, Section.YESTERDAY -> if (is24h) "HH:mm" else "h:mm a"
        Section.WEEK -> "EEE"
        else -> if (at.year == Instant.ofEpochMilli(now).atZone(zone).year) "d MMM" else "d MMM yyyy"
      }
    return at.format(DateTimeFormatter.ofPattern(pattern, locale))
  }
}

/** Time ranges of the Clear browsing data dialog. */
enum class ClearRange(val label: String) {
  LAST_HOUR("Last hour"),
  TODAY("Today"),
  LAST_7_DAYS("Last 7 days"),
  ALL_TIME("All time"),
  ;

  /** Start of the range; everything at or after it is cleared. */
  fun since(
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
  ): Long =
    when (this) {
      LAST_HOUR -> now - 3_600_000L
      TODAY -> LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).atStartOfDay(zone).toInstant().toEpochMilli()
      LAST_7_DAYS -> now - 7 * 86_400_000L
      ALL_TIME -> 0L
    }
}

/** Folder structure helpers over the flat bookmarks table. */
object BookmarkTree {
  /** [ids] plus everything inside them, at any depth. */
  fun withDescendants(
    ids: Collection<Long>,
    all: List<BookmarkEntity>,
  ): Set<Long> {
    val byParent = all.groupBy { it.parentId }
    val out = LinkedHashSet<Long>()
    val queue = ArrayDeque(ids)
    while (queue.isNotEmpty()) {
      val id = queue.removeFirst()
      if (!out.add(id)) continue
      byParent[id]?.forEach { queue.addLast(it.id) }
    }
    return out
  }

  /** Folders in tree order with their depth, the root first, for folder pickers. */
  fun folders(
    all: List<BookmarkEntity>,
    exclude: Set<Long> = emptySet(),
  ): List<Pair<BookmarkEntity?, Int>> {
    val byParent = all.filter { it.isFolder }.groupBy { it.parentId }
    val out = mutableListOf<Pair<BookmarkEntity?, Int>>(null to 0)

    fun walk(
      parent: Long,
      depth: Int,
    ) {
      byParent[parent].orEmpty().sortedBy { it.title.lowercase() }.forEach {
        if (it.id in exclude) return@forEach
        out += it to depth
        walk(it.id, depth + 1)
      }
    }
    walk(BookmarkEntity.ROOT, 1)
    return out
  }

  fun toNodes(
    all: List<BookmarkEntity>,
    parent: Long = BookmarkEntity.ROOT,
  ): List<NetscapeBookmarks.Node> {
    val byParent = all.groupBy { it.parentId }

    fun build(p: Long): List<NetscapeBookmarks.Node> =
      byParent[p].orEmpty().sortedWith(compareByDescending<BookmarkEntity> { it.isFolder }.thenBy { it.createdAt }).map {
        if (it.isFolder) NetscapeBookmarks.Folder(it.title, build(it.id)) else NetscapeBookmarks.Link(it.title, it.url, it.createdAt)
      }
    return build(parent)
  }

  /** Path from the root to [id], e.g. [Movie sites, Trailers]. */
  fun path(
    id: Long,
    all: List<BookmarkEntity>,
  ): List<BookmarkEntity> {
    val byId = all.associateBy { it.id }
    val out = mutableListOf<BookmarkEntity>()
    var cur = byId[id]
    while (cur != null && out.size < 64) {
      out.add(0, cur)
      cur = byId[cur.parentId]
    }
    return out
  }
}
