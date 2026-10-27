package app.arju.mpvgo.webbrowser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import app.arju.mpvgo.webbrowser.library.BookmarkEntity
import app.arju.mpvgo.webbrowser.library.BookmarkTree
import app.arju.mpvgo.webbrowser.library.BrowserLibrary
import app.arju.mpvgo.webbrowser.library.ClearRange
import app.arju.mpvgo.webbrowser.library.HistoryEntity
import app.arju.mpvgo.webbrowser.library.HistoryGroups
import app.arju.mpvgo.webbrowser.library.NetscapeBookmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab { BOOKMARKS, HISTORY }

enum class OpenMode { CURRENT, NEW_TAB, PRIVATE_TAB }

/** What the Clear browsing data dialog asked for, besides history (cleared by the page itself). */
data class ClearRequest(
  val cookies: Boolean,
  val cache: Boolean,
  val siteSettings: Boolean,
)

/**
 * Full-screen Bookmarks | History page of the Go browser. Tapping an item opens it in the
 * current tab and returns to the browser.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserLibraryPage(
  initial: LibraryTab,
  library: BrowserLibrary,
  onOpen: (url: String, mode: OpenMode) -> Unit,
  onOpenMany: (List<String>) -> Unit,
  onAddToStartPage: (title: String, url: String) -> Boolean,
  onClearData: (ClearRequest) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val is24h = remember { android.text.format.DateFormat.is24HourFormat(context) }
  val scope = rememberCoroutineScope()
  var tab by rememberSaveable { mutableStateOf(initial) }
  var folderId by rememberSaveable { mutableLongStateOf(BookmarkEntity.ROOT) }
  var query by rememberSaveable { mutableStateOf<String?>(null) }
  var selected by remember { mutableStateOf(emptySet<Long>()) }
  var sortByName by rememberSaveable { mutableStateOf(false) }
  var editing by remember { mutableStateOf<BookmarkEntity?>(null) }
  var naming by remember { mutableStateOf<BookmarkEntity?>(null) }
  var moving by remember { mutableStateOf<Set<Long>?>(null) }
  var confirmDelete by remember { mutableStateOf<Set<Long>?>(null) }
  var clearing by remember { mutableStateOf(false) }

  val bookmarksFlow = remember { library.bookmarks }
  val historyFlow = remember { library.history() }
  val all by bookmarksFlow.collectAsState(initial = emptyList())
  val history by historyFlow.collectAsState(initial = emptyList())
  val selecting = selected.isNotEmpty()

  LaunchedEffect(all, folderId) {
    if (folderId != BookmarkEntity.ROOT && all.isNotEmpty() && all.none { it.id == folderId }) folderId = BookmarkEntity.ROOT
  }

  fun switchTab(to: LibraryTab) {
    tab = to
    selected = emptySet()
  }

  BackHandler {
    when {
      selecting -> selected = emptySet()
      query != null -> query = null
      tab == LibraryTab.BOOKMARKS && folderId != BookmarkEntity.ROOT ->
        folderId = all.firstOrNull { it.id == folderId }?.parentId ?: BookmarkEntity.ROOT
      else -> onClose()
    }
  }

  val importer =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        val added =
          runCatching {
            val html = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
            library.import(NetscapeBookmarks.parse(html.orEmpty()))
          }.getOrNull()
        toast(context, if (added == null) "Couldn't read that file" else "Imported $added bookmarks")
      }
    }
  val exporter =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        val ok =
          runCatching {
            val html = library.exportHtml()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(html.toByteArray()) } }
          }.isSuccess
        toast(context, if (ok) "Bookmarks exported" else "Couldn't save the file")
      }
    }

  val q = query.orEmpty().trim()
  val currentFolder = all.firstOrNull { it.id == folderId }
  val folders: List<BookmarkEntity>
  val links: List<BookmarkEntity>
  if (q.isNotEmpty()) {
    folders = emptyList()
    links = all.filter { !it.isFolder && (it.title.contains(q, true) || it.url.contains(q, true)) }
  } else {
    val children = all.filter { it.parentId == folderId }
    folders = children.filter { it.isFolder }.sortedBy { it.title.lowercase() }
    links =
      children.filterNot { it.isFolder }.let { list ->
        if (sortByName) list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.createdAt }
      }
  }
  val shownHistory =
    if (q.isEmpty()) history else history.filter { it.title.contains(q, true) || it.url.contains(q, true) }
  val visibleIds =
    if (tab == LibraryTab.BOOKMARKS) (folders + links).map { it.id } else shownHistory.map { it.id }

  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      // ---- Top bar ----
      Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
          selecting -> {
            IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel selection") }
            Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { selected = visibleIds.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
          }
          query != null -> {
            IconButton(onClick = { query = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
            val focus = remember { FocusRequester() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              if (query.isNullOrEmpty()) {
                Text(
                  if (tab == LibraryTab.BOOKMARKS) "Search bookmarks" else "Search history",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              BasicTextField(
                value = query.orEmpty(),
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
              )
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            if (!query.isNullOrEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
          }
          else -> {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to browser") }
            Text(
              if (tab == LibraryTab.BOOKMARKS) "Bookmarks" else "History",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Search, "Search") }
            if (tab == LibraryTab.BOOKMARKS) {
              BookmarksMoreMenu(
                sortByName = sortByName,
                onSort = { sortByName = it },
                onImport = { importer.launch(arrayOf("text/html", "text/plain", "application/octet-stream")) },
                onExport = { exporter.launch("mpvGo-bookmarks.html") },
              )
            } else {
              IconButton(onClick = { clearing = true }) { Icon(Icons.Filled.DeleteSweep, "Clear browsing data") }
            }
          }
        }
      }
      // ---- Bookmarks | History switch ----
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SegmentedButton(
          selected = tab == LibraryTab.BOOKMARKS,
          onClick = { switchTab(LibraryTab.BOOKMARKS) },
          shape = SegmentedButtonDefaults.itemShape(0, 2),
          icon = { Icon(Icons.Filled.Bookmarks, null, Modifier.size(18.dp)) },
        ) { Text("Bookmarks") }
        SegmentedButton(
          selected = tab == LibraryTab.HISTORY,
          onClick = { switchTab(LibraryTab.HISTORY) },
          shape = SegmentedButtonDefaults.itemShape(1, 2),
          icon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp)) },
        ) { Text("History") }
      }

      Box(Modifier.fillMaxWidth().weight(1f)) {
        fun toggle(id: Long) {
          selected = if (id in selected) selected - id else selected + id
        }
        if (tab == LibraryTab.BOOKMARKS) {
          LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (q.isEmpty()) {
              item(key = "path") {
                FolderPath(
                  path = if (currentFolder == null) emptyList() else BookmarkTree.path(currentFolder.id, all),
                  onOpen = { folderId = it },
                )
              }
            }
            items(folders, key = { it.id }) { folder ->
              FolderRow(
                folder = folder,
                count = all.count { it.parentId == folder.id },
                selected = folder.id in selected,
                selecting = selecting,
                onClick = { if (selecting) toggle(folder.id) else folderId = folder.id },
                onLongClick = { toggle(folder.id) },
                onRename = { naming = folder },
                onMove = { moving = setOf(folder.id) },
                onDelete = { confirmDelete = setOf(folder.id) },
              )
            }
            items(links, key = { it.id }) { item ->
              BookmarkRow(
                item = item,
                query = q,
                selected = item.id in selected,
                selecting = selecting,
                onClick = { if (selecting) toggle(item.id) else onOpen(item.url, OpenMode.CURRENT) },
                onLongClick = { toggle(item.id) },
                onAction = { action ->
                  when (action) {
                    ItemAction.OPEN -> onOpen(item.url, OpenMode.CURRENT)
                    ItemAction.NEW_TAB -> onOpen(item.url, OpenMode.NEW_TAB)
                    ItemAction.PRIVATE_TAB -> onOpen(item.url, OpenMode.PRIVATE_TAB)
                    ItemAction.EDIT -> editing = item
                    ItemAction.MOVE -> moving = setOf(item.id)
                    ItemAction.START_PAGE ->
                      toast(context, if (onAddToStartPage(item.title, item.url)) "Added to start page" else "Start page is full or already has it")
                    ItemAction.COPY -> copy(context, item.url)
                    ItemAction.SHARE -> share(context, item.url)
                    ItemAction.DELETE -> scope.launch { library.deleteBookmarks(listOf(item.id)) }
                  }
                },
              )
            }
            if (folders.isEmpty() && links.isEmpty()) {
              item(key = "empty") {
                EmptyState(
                  icon = { Icon(Icons.Filled.BookmarkBorder, null, Modifier.size(48.dp)) },
                  title = if (q.isNotEmpty()) "No matches" else if (currentFolder != null) "This folder is empty" else "No bookmarks yet",
                  text = if (q.isEmpty() && currentFolder == null) "Tap the star in the browser menu to save a page." else "",
                )
              }
            }
          }
          if (!selecting && q.isEmpty()) {
            FloatingActionButton(
              onClick = { naming = BookmarkEntity(parentId = folderId, isFolder = true, title = "") },
              modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.CreateNewFolder, "New folder") }
          }
        } else {
          val now = remember(history) { System.currentTimeMillis() }
          val groups = remember(shownHistory, now) { HistoryGroups.group(shownHistory, { it.visitedAt }, now) }
          LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            groups.forEach { (section, entries) ->
              item(key = "h-${section.name}") {
                Text(
                  section.label,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
              }
              items(entries, key = { it.id }) { entry ->
                HistoryRow(
                  entry = entry,
                  time = HistoryGroups.label(entry.visitedAt, now, is24h = is24h),
                  selected = entry.id in selected,
                  selecting = selecting,
                  onClick = { if (selecting) toggle(entry.id) else onOpen(entry.url, OpenMode.CURRENT) },
                  onLongClick = { toggle(entry.id) },
                )
              }
            }
            if (groups.isEmpty()) {
              item(key = "empty") {
                EmptyState(
                  icon = { Icon(Icons.Filled.History, null, Modifier.size(48.dp)) },
                  title = if (q.isNotEmpty()) "No matches" else "No history",
                  text = if (q.isEmpty()) "Pages you visit appear here. Private tabs never add to history." else "",
                )
              }
            }
          }
        }

        if (selecting) {
          SelectionPill(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            onOpenTabs = {
              val urls =
                if (tab == LibraryTab.BOOKMARKS) {
                  all.filter { it.id in selected && !it.isFolder }.map { it.url }
                } else {
                  history.filter { it.id in selected }.map { it.url }.distinct()
                }
              selected = emptySet()
              if (urls.isNotEmpty()) onOpenMany(urls)
            },
            onBookmark =
              if (tab == LibraryTab.HISTORY) {
                {
                  val picked = history.filter { it.id in selected }
                  selected = emptySet()
                  scope.launch {
                    var added = 0
                    picked.distinctBy { it.url }.forEach {
                      if (library.findBookmark(it.url) == null) {
                        library.save(BookmarkEntity(title = it.title.ifBlank { it.url }, url = it.url))
                        added++
                      }
                    }
                    toast(context, "Bookmarked $added pages")
                  }
                }
              } else {
                null
              },
            onMove = if (tab == LibraryTab.BOOKMARKS) ({ moving = selected }) else null,
            onShare = {
              val urls =
                if (tab == LibraryTab.BOOKMARKS) {
                  all.filter { it.id in selected && !it.isFolder }.map { it.url }
                } else {
                  history.filter { it.id in selected }.map { it.url }
                }
              if (urls.isNotEmpty()) share(context, urls.distinct().joinToString("\n"))
            },
            onDelete = {
              if (tab == LibraryTab.BOOKMARKS) {
                if (all.any { it.id in selected && it.isFolder }) {
                  confirmDelete = selected
                } else {
                  val ids = selected
                  scope.launch { library.deleteBookmarks(ids) }
                }
              } else {
                val ids = selected
                scope.launch { library.deleteHistory(ids) }
              }
              selected = emptySet()
            },
          )
        }
      }
    }
  }

  editing?.let { item ->
    BookmarkEditDialog(
      initial = item,
      isNew = item.id == 0L,
      folders = BookmarkTree.folders(all),
      onSave = { saved, toStart ->
        scope.launch { library.save(saved) }
        if (toStart) onAddToStartPage(saved.title, saved.url)
        editing = null
      },
      onDelete = {
        scope.launch { library.deleteBookmarks(listOf(item.id)) }
        editing = null
      },
      onDismiss = { editing = null },
    )
  }
  naming?.let { folder ->
    NameDialog(
      title = if (folder.id == 0L) "New folder" else "Rename folder",
      initial = folder.title,
      onConfirm = { name ->
        scope.launch {
          if (folder.id == 0L) library.createFolder(folder.parentId, name) else library.save(folder.copy(title = name.trim()))
        }
        naming = null
      },
      onDismiss = { naming = null },
    )
  }
  moving?.let { ids ->
    FolderPickerDialog(
      folders = BookmarkTree.folders(all, exclude = ids.filter { id -> all.any { it.id == id && it.isFolder } }.toSet()),
      onPick = { parent ->
        scope.launch { library.move(ids, parent) }
        moving = null
        selected = emptySet()
      },
      onDismiss = { moving = null },
    )
  }
  confirmDelete?.let { ids ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      title = { Text("Delete folder?") },
      text = { Text("The folder and everything in it will be deleted.") },
      confirmButton = {
        TextButton(onClick = {
          scope.launch { library.deleteBookmarks(ids) }
          confirmDelete = null
          selected = emptySet()
        }) { Text("Delete") }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
    )
  }
  if (clearing) {
    ClearDataDialog(
      onClear = { range, historyToo, request ->
        val since = range.since(System.currentTimeMillis())
        if (historyToo) scope.launch { library.clearHistory(since) }
        onClearData(request)
        clearing = false
        toast(context, "Browsing data cleared")
      },
      onDismiss = { clearing = false },
    )
  }
}

private enum class ItemAction { OPEN, NEW_TAB, PRIVATE_TAB, EDIT, MOVE, START_PAGE, COPY, SHARE, DELETE }

@Composable
private fun BookmarksMoreMenu(
  sortByName: Boolean,
  onSort: (Boolean) -> Unit,
  onImport: () -> Unit,
  onExport: () -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text("Import") },
        leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
        onClick = {
          open = false
          onImport()
        },
      )
      DropdownMenuItem(
        text = { Text("Export") },
        leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
        onClick = {
          open = false
          onExport()
        },
      )
      HorizontalDivider()
      DropdownMenuItem(
        text = { Text(if (sortByName) "Sort: Name" else "Sort: Date added") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
        onClick = {
          open = false
          onSort(!sortByName)
        },
      )
    }
  }
}

@Composable
private fun FolderPath(
  path: List<BookmarkEntity>,
  onOpen: (Long) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(6.dp))
    val crumbs = listOf<BookmarkEntity?>(null) + path
    crumbs.forEachIndexed { i, folder ->
      val last = i == crumbs.lastIndex
      Text(
        folder?.title ?: "All bookmarks",
        style = MaterialTheme.typography.labelLarge,
        color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          Modifier
            .weight(1f, fill = false)
            .clickable(enabled = !last) { onOpen(folder?.id ?: BookmarkEntity.ROOT) }
            .padding(vertical = 8.dp, horizontal = 2.dp),
      )
      if (!last) Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp))
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
  folder: BookmarkEntity,
  count: Int,
  selected: Boolean,
  selecting: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onRename: () -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
) {
  ListRow(
    selected = selected,
    onClick = onClick,
    onLongClick = onLongClick,
    leading = {
      Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    },
    title = folder.title,
    subtitle = if (count == 1) "1 item" else "$count items",
    trailing = {
      if (!selecting) {
        var open by remember { mutableStateOf(false) }
        Box {
          IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Folder menu") }
          DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Rename", Icons.Filled.Edit) {
              open = false
              onRename()
            }
            MenuItem("Move to folder", Icons.Filled.DriveFileMove) {
              open = false
              onMove()
            }
            MenuItem("Delete", Icons.Filled.Delete) {
              open = false
              onDelete()
            }
          }
        }
      }
    },
  )
}

@Composable
private fun BookmarkRow(
  item: BookmarkEntity,
  query: String,
  selected: Boolean,
  selecting: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onAction: (ItemAction) -> Unit,
) {
  ListRow(
    selected = selected,
    onClick = onClick,
    onLongClick = onLongClick,
    leading = { LetterIcon(item.title.ifBlank { SiteMatcher.host(item.url) }) },
    title = item.title.ifBlank { item.url },
    subtitle = item.url.removePrefix("https://").removePrefix("http://").removePrefix("www."),
    highlight = query,
    trailing = {
      if (!selecting) {
        var open by remember { mutableStateOf(false) }
        Box {
          IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Bookmark menu") }
          DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(a: ItemAction) {
              open = false
              onAction(a)
            }
            MenuItem("Open", Icons.Filled.Tab) { pick(ItemAction.OPEN) }
            MenuItem("Open in new tab", Icons.AutoMirrored.Filled.OpenInNew) { pick(ItemAction.NEW_TAB) }
            MenuItem("Open in private tab", Icons.Filled.VisibilityOff) { pick(ItemAction.PRIVATE_TAB) }
            HorizontalDivider()
            MenuItem("Edit", Icons.Filled.Edit) { pick(ItemAction.EDIT) }
            MenuItem("Move to folder", Icons.Filled.DriveFileMove) { pick(ItemAction.MOVE) }
            MenuItem("Add to start page", Icons.Filled.AddToHomeScreen) { pick(ItemAction.START_PAGE) }
            MenuItem("Copy link", Icons.Filled.ContentCopy) { pick(ItemAction.COPY) }
            MenuItem("Share", Icons.Filled.Share) { pick(ItemAction.SHARE) }
            MenuItem("Delete", Icons.Filled.Delete) { pick(ItemAction.DELETE) }
          }
        }
      }
    },
  )
}

@Composable
private fun HistoryRow(
  entry: HistoryEntity,
  time: String,
  selected: Boolean,
  selecting: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val host = SiteMatcher.host(entry.url).removePrefix("www.")
  ListRow(
    selected = selected,
    onClick = onClick,
    onLongClick = onLongClick,
    leading = { LetterIcon(host.ifBlank { entry.title }) },
    title = entry.title.ifBlank { entry.url },
    subtitle = host,
    badge = if (entry.played) ({ PlayedTag() }) else null,
    trailing = {
      Text(
        time,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 12.dp),
      )
    },
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  leading: @Composable () -> Unit,
  title: String,
  subtitle: String,
  trailing: @Composable () -> Unit,
  highlight: String = "",
  badge: (@Composable () -> Unit)? = null,
) {
  val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
  Row(
    Modifier
      .fillMaxWidth()
      .background(bg)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .heightIn(min = 64.dp)
      .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (selected) {
      Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.onPrimary) }
    } else {
      leading()
    }
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          highlighted(title, highlight),
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (badge != null) {
          Spacer(Modifier.width(6.dp))
          badge()
        }
      }
      if (subtitle.isNotEmpty()) {
        Text(
          highlighted(subtitle, highlight),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    trailing()
  }
}

@Composable
private fun highlighted(
  text: String,
  query: String,
): androidx.compose.ui.text.AnnotatedString {
  val color = MaterialTheme.colorScheme.tertiaryContainer
  return androidx.compose.ui.text.buildAnnotatedString {
    append(text)
    if (query.isNotEmpty()) {
      var i = text.indexOf(query, ignoreCase = true)
      while (i >= 0) {
        addStyle(androidx.compose.ui.text.SpanStyle(background = color), i, i + query.length)
        i = text.indexOf(query, i + query.length, ignoreCase = true)
      }
    }
  }
}

@Composable
private fun PlayedTag() {
  Row(
    Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.PlayArrow, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    Text("Played", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
  }
}

@Composable
private fun LetterIcon(text: String) {
  Box(
    Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun MenuItem(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
) {
  DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun EmptyState(
  icon: @Composable () -> Unit,
  title: String,
  text: String,
) {
  Column(
    Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (text.isNotEmpty()) {
      Spacer(Modifier.height(4.dp))
      Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun SelectionPill(
  modifier: Modifier,
  onOpenTabs: () -> Unit,
  onBookmark: (() -> Unit)?,
  onMove: (() -> Unit)?,
  onShare: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    shadowElevation = 6.dp,
  ) {
    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      IconButton(onClick = onOpenTabs) { Icon(Icons.Filled.Tab, "Open in tabs") }
      onBookmark?.let { IconButton(onClick = it) { Icon(Icons.Filled.StarBorder, "Bookmark") } }
      onMove?.let { IconButton(onClick = it) { Icon(Icons.Filled.DriveFileMove, "Move to folder") } }
      IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
      IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
    }
  }
}

/** Add or edit a bookmark: name, address, folder and "Also add to start page". */
@Composable
fun BookmarkEditDialog(
  initial: BookmarkEntity,
  isNew: Boolean,
  folders: List<Pair<BookmarkEntity?, Int>>,
  onSave: (BookmarkEntity, addToStartPage: Boolean) -> Unit,
  onDelete: (() -> Unit)?,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initial.title) }
  var address by remember { mutableStateOf(initial.url) }
  var parent by remember { mutableLongStateOf(initial.parentId) }
  var toStart by remember { mutableStateOf(false) }
  var pickFolder by remember { mutableStateOf(false) }
  val folderName = folders.firstOrNull { (it.first?.id ?: BookmarkEntity.ROOT) == parent }?.first?.title ?: "All bookmarks"
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isNew) "Add bookmark" else "Edit bookmark") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true)
        Text("Folder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
          OutlinedButton(onClick = { pickFolder = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Folder, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(folderName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          DropdownMenu(expanded = pickFolder, onDismissRequest = { pickFolder = false }) {
            folders.forEach { (folder, depth) ->
              DropdownMenuItem(
                text = { Text(folder?.title ?: "All bookmarks", Modifier.padding(start = (depth * 12).dp)) },
                onClick = {
                  parent = folder?.id ?: BookmarkEntity.ROOT
                  pickFolder = false
                },
              )
            }
          }
        }
        Row(
          Modifier.fillMaxWidth().selectable(selected = toStart, role = Role.Checkbox, onClick = { toStart = !toStart }),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(checked = toStart, onCheckedChange = null)
          Spacer(Modifier.width(8.dp))
          Text("Also add to start page")
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = address.isNotBlank(),
        onClick = {
          val url = address.trim()
          onSave(initial.copy(title = name.trim().ifEmpty { url }, url = url, parentId = parent), toStart)
        },
      ) { Text("Save") }
    },
    dismissButton = {
      Row {
        if (!isNew && onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}

@Composable
private fun NameDialog(
  title: String,
  initial: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
    confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("OK") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun FolderPickerDialog(
  folders: List<Pair<BookmarkEntity?, Int>>,
  onPick: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Move to folder") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        folders.forEach { (folder, depth) ->
          Row(
            Modifier
              .fillMaxWidth()
              .clickable { onPick(folder?.id ?: BookmarkEntity.ROOT) }
              .padding(start = (depth * 16).dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(folder?.title ?: "All bookmarks")
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
internal fun ClearDataDialog(
  onClear: (ClearRange, history: Boolean, ClearRequest) -> Unit,
  onDismiss: () -> Unit,
) {
  var range by remember { mutableStateOf(ClearRange.LAST_HOUR) }
  var history by remember { mutableStateOf(true) }
  var cookies by remember { mutableStateOf(false) }
  var cache by remember { mutableStateOf(true) }
  var sites by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Clear browsing data") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Time range", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ClearRange.entries.forEach { r ->
          Row(
            Modifier.fillMaxWidth().selectable(selected = range == r, role = Role.RadioButton, onClick = { range = r }),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = range == r, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text(r.label, Modifier.padding(vertical = 8.dp))
          }
        }
        Spacer(Modifier.height(8.dp))
        CheckRow("History", "Pages and searches", history) { history = it }
        CheckRow("Cookies and site data", "Signs you out of most sites (all time)", cookies) { cookies = it }
        CheckRow("Cached images and files", "All time", cache) { cache = it }
        CheckRow("Site settings", "Ad block and video choice per site", sites) { sites = it }
        Spacer(Modifier.height(4.dp))
        Text(
          "Bookmarks are never cleared.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = history || cookies || cache || sites,
        onClick = { onClear(range, history, ClearRequest(cookies, cache, sites)) },
      ) { Text("Clear") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun CheckRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().selectable(selected = checked, role = Role.Checkbox, onClick = { onChange(!checked) }).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    Spacer(Modifier.width(8.dp))
    Column {
      Text(title)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun toast(
  context: Context,
  text: String,
) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun copy(
  context: Context,
  text: String,
) {
  context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Link", text))
}

private fun share(
  context: Context,
  text: String,
) {
  val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
  context.startActivity(Intent.createChooser(send, null))
}
