package app.arju.mpvgo.webbrowser.library

import android.content.Context
import app.arju.mpvgo.webbrowser.core.BrowserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bookmarks and history of the Go browser. Private tabs never reach this class. */
class BrowserLibrary(
  context: Context,
  private val prefs: BrowserPreferences,
) {
  private val db by lazy { BrowserLibraryDatabase.create(context) }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val historyLock = Mutex()
  private var pruned = false

  // ---- Bookmarks ----

  val bookmarks: Flow<List<BookmarkEntity>> get() = db.bookmarks().observeAll()

  fun bookmarkFor(url: String): Flow<BookmarkEntity?> = db.bookmarks().observeByUrl(url)

  suspend fun findBookmark(url: String): BookmarkEntity? = db.bookmarks().byUrl(url)

  suspend fun save(item: BookmarkEntity): Long =
    if (item.id == 0L) {
      db.bookmarks().insert(item)
    } else {
      db.bookmarks().update(item)
      item.id
    }

  suspend fun createFolder(
    parentId: Long,
    title: String,
  ): Long = db.bookmarks().insert(BookmarkEntity(parentId = parentId, isFolder = true, title = title.trim()))

  /** Deletes [ids] and, for folders, everything inside them. */
  suspend fun deleteBookmarks(ids: Collection<Long>) {
    val all = db.bookmarks().all()
    db.bookmarks().delete(BookmarkTree.withDescendants(ids, all).toList())
  }

  /** Moves [ids] into [parentId], skipping folders that would end up inside themselves. */
  suspend fun move(
    ids: Collection<Long>,
    parentId: Long,
  ) {
    val all = db.bookmarks().all()
    val byId = all.associateBy { it.id }
    for (id in ids) {
      val item = byId[id] ?: continue
      if (item.isFolder && parentId in BookmarkTree.withDescendants(listOf(id), all)) continue
      db.bookmarks().update(item.copy(parentId = parentId))
    }
  }

  /** Adds [nodes] under the root. Folders with the same name are merged; duplicate links in a folder are skipped. */
  suspend fun import(nodes: List<NetscapeBookmarks.Node>): Int {
    val existing = db.bookmarks().all().filterNot { it.isFolder }.map { it.parentId to it.url }.toMutableSet()
    var added = 0

    suspend fun add(
      list: List<NetscapeBookmarks.Node>,
      parent: Long,
    ) {
      for (node in list) {
        when (node) {
          is NetscapeBookmarks.Folder -> {
            val title = node.title.ifBlank { "Imported" }
            val folder = db.bookmarks().folder(parent, title)?.id ?: createFolder(parent, title)
            add(node.children, folder)
          }
          is NetscapeBookmarks.Link -> {
            if (existing.add(parent to node.url)) {
              val at = node.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
              db.bookmarks().insert(BookmarkEntity(parentId = parent, title = node.title, url = node.url, createdAt = at))
              added++
            }
          }
        }
      }
    }
    add(nodes, BookmarkEntity.ROOT)
    return added
  }

  suspend fun exportHtml(): String = NetscapeBookmarks.export(BookmarkTree.toNodes(db.bookmarks().all()))

  // ---- History ----

  fun history(limit: Int = 5_000): Flow<List<HistoryEntity>> = db.history().observeRecent(limit)

  /** Records a visit. Visiting the same address again within half an hour only moves it to the top. */
  fun recordVisit(
    url: String,
    title: String,
  ) {
    if (!isRecordable(url)) return
    val now = System.currentTimeMillis()
    scope.launch {
      historyLock.withLock {
        pruneOnce(now)
        val recent = db.history().recentVisit(url, now - REVISIT_WINDOW)
        if (recent != null) {
          db.history().update(recent.copy(visitedAt = now, title = title.ifBlank { recent.title }))
        } else {
          db.history().insert(HistoryEntity(url = url, title = title, visitedAt = now))
        }
      }
    }
  }

  /** The page title arrived after the visit was recorded. */
  fun updateTitle(
    url: String,
    title: String,
  ) {
    if (title.isBlank() || !isRecordable(url)) return
    scope.launch {
      historyLock.withLock {
        val recent = db.history().recentVisit(url, System.currentTimeMillis() - REVISIT_WINDOW) ?: return@withLock
        if (recent.title != title) db.history().update(recent.copy(title = title))
      }
    }
  }

  fun markPlayed(pageUrl: String) {
    if (!isRecordable(pageUrl)) return
    scope.launch { historyLock.withLock { db.history().markPlayed(pageUrl) } }
  }

  suspend fun deleteHistory(ids: Collection<Long>) = db.history().delete(ids.toList())

  suspend fun clearHistory(since: Long) = db.history().deleteSince(since)

  /** Bookmarks and history matching [query], as (title, url) pairs. */
  suspend fun search(query: String): Pair<List<Pair<String, String>>, List<Pair<String, String>>> =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
      val q = query.trim()
      if (q.isEmpty()) {
        emptyList<Pair<String, String>>() to db.history().search("", SUGGEST_LIMIT).map { it.title to it.url }
      } else {
        db.bookmarks().search(q, SUGGEST_LIMIT).map { it.title to it.url } to
          db.history().search(q, SUGGEST_LIMIT).map { it.title to it.url }
      }
    }

  /** Applies a changed "Keep history" setting right away. */
  fun pruneNow() {
    scope.launch {
      historyLock.withLock {
        pruned = false
        pruneOnce(System.currentTimeMillis())
      }
    }
  }

  private suspend fun pruneOnce(now: Long) {
    if (pruned) return
    pruned = true
    val days = prefs.historyDays.get()
    if (days > 0) db.history().deleteBefore(now - days * 86_400_000L)
  }

  companion object {
    private const val REVISIT_WINDOW = 30 * 60_000L
    private const val SUGGEST_LIMIT = 6

    fun isRecordable(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")
  }
}
