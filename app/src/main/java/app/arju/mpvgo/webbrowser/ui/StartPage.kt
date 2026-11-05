package app.arju.mpvgo.webbrowser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import app.arju.mpvgo.webbrowser.core.FaviconStore
import app.arju.mpvgo.webbrowser.core.SearchEngine
import org.koin.compose.koinInject
import app.arju.mpvgo.webbrowser.core.SpeedDial
import app.arju.mpvgo.webbrowser.core.SpeedDials

/** Colours of the private start page; also used to tint the private browser chrome. */
object PrivateColors {
  val background = Color(0xFF1F1A2E)
  val surface = Color(0xFF2C2540)
  val content = Color(0xFFEDE7F6)
  val muted = Color(0xFFB8AED0)
  val accent = Color(0xFFD0BCFF)
}

@Composable
fun StartPage(
  isPrivate: Boolean,
  engine: SearchEngine,
  customSearchUrl: String,
  dials: List<SpeedDial>,
  showDials: Boolean = true,
  onSearch: (String) -> Unit,
  onOpen: (url: String, newTab: Boolean, private: Boolean) -> Unit,
  onDialsChange: (List<SpeedDial>) -> Unit,
  onEngineChange: (SearchEngine, custom: String?) -> Unit,
) {
  val bg = if (isPrivate) PrivateColors.background else MaterialTheme.colorScheme.surface
  val fg = if (isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface
  var editing by remember { mutableStateOf<Pair<Int, SpeedDial>?>(null) }
  var askCustom by remember { mutableStateOf(false) }

  Column(
    Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier.size(64.dp).background(if (isPrivate) PrivateColors.surface else MaterialTheme.colorScheme.primaryContainer, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (isPrivate) Icons.Filled.VisibilityOff else Icons.Filled.Language,
        null,
        Modifier.size(36.dp),
        tint = if (isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Spacer(Modifier.height(12.dp))
    Text(
      if (isPrivate) "Private tab" else "mpvGo",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
      color = fg,
    )
    Spacer(Modifier.height(20.dp))
    SearchBox(isPrivate, engine, onSearch, onPickEngine = { picked ->
      if (picked == SearchEngine.CUSTOM) askCustom = true else onEngineChange(picked, null)
    })
    Spacer(Modifier.height(28.dp))
    if (isPrivate) {
      Text(
        "mpvGo won't save your history, cookies or site data from private tabs. They are deleted when you close " +
          "your last private tab. Videos you download still appear in Downloads.",
        color = PrivateColors.muted,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )
    } else if (showDials) {
      SpeedDialGrid(
        dials = dials,
        onOpen = { onOpen(it.url, false, false) },
        onAdd = { editing = -1 to SpeedDial("", "") },
        onEdit = { index -> editing = index to dials[index] },
        onOpenNew = { dial, private -> onOpen(dial.url, true, private) },
        onRemove = { index -> onDialsChange(dials.toMutableList().apply { removeAt(index) }) },
      )
    }
  }

  editing?.let { (index, dial) ->
    SpeedDialDialog(
      initial = dial,
      onDismiss = { editing = null },
      onSave = { saved ->
        val list = dials.toMutableList()
        if (index < 0) list += saved else list[index] = saved
        onDialsChange(list)
        editing = null
      },
    )
  }
  if (askCustom) {
    var text by remember { mutableStateOf(customSearchUrl.ifBlank { "https://" }) }
    val valid = text.startsWith("http") && text.contains("%s")
    AlertDialog(
      onDismissRequest = { askCustom = false },
      title = { Text("Custom search engine") },
      text = {
        Column {
          Text("Search address with %s where the words go.", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("https://example.com/search?q=%s") })
        }
      },
      confirmButton = {
        TextButton(enabled = valid, onClick = {
          onEngineChange(SearchEngine.CUSTOM, text.trim())
          askCustom = false
        }) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { askCustom = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun SearchBox(
  isPrivate: Boolean,
  engine: SearchEngine,
  onSearch: (String) -> Unit,
  onPickEngine: (SearchEngine) -> Unit,
) {
  var text by remember { mutableStateOf("") }
  var menu by remember { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current
  val fg = if (isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface
  Surface(
    shape = RoundedCornerShape(28.dp),
    color = if (isPrivate) PrivateColors.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().height(56.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
      Box {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { menu = true }.padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
          EngineBadge(engine)
          Icon(Icons.Filled.ArrowDropDown, "Search engine", tint = fg)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          SearchEngine.entries.forEach { e ->
            DropdownMenuItem(
              text = { Text(e.label) },
              leadingIcon = { EngineBadge(e) },
              trailingIcon = { if (e == engine) Icon(Icons.Filled.Check, null) },
              onClick = {
                menu = false
                onPickEngine(e)
              },
            )
          }
        }
      }
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (text.isEmpty()) {
          Text(
            if (isPrivate) "Search privately" else "Search or type a link",
            color = fg.copy(alpha = 0.6f),
            maxLines = 1,
          )
        }
        BasicTextField(
          value = text,
          onValueChange = { text = it },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg),
          cursorBrush = SolidColor(fg),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
          keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onSearch(text) }),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      IconButton(onClick = {
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.isNotEmpty()) text = clip
      }) { Icon(Icons.Filled.ContentPaste, "Paste", tint = fg) }
    }
  }
}

@Composable
fun EngineBadge(engine: SearchEngine) {
  val color =
    when (engine) {
      SearchEngine.GOOGLE -> Color(0xFF4285F4)
      SearchEngine.DUCKDUCKGO -> Color(0xFFDE5833)
      SearchEngine.BRAVE -> Color(0xFFFB542B)
      SearchEngine.BING -> Color(0xFF008373)
      SearchEngine.STARTPAGE -> Color(0xFF6573FF)
      SearchEngine.CUSTOM -> Color(0xFF757575)
    }
  Box(Modifier.size(24.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
    Text(engine.label.take(1), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun SpeedDialGrid(
  dials: List<SpeedDial>,
  onOpen: (SpeedDial) -> Unit,
  onAdd: () -> Unit,
  onEdit: (Int) -> Unit,
  onOpenNew: (SpeedDial, Boolean) -> Unit,
  onRemove: (Int) -> Unit,
) {
  val cells: List<SpeedDial?> = dials + if (dials.size < SpeedDials.MAX) listOf(null) else emptyList()
  cells.chunked(4).forEachIndexed { row, chunk ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      chunk.forEachIndexed { col, dial ->
        val index = row * 4 + col
        if (dial == null) {
          DialTile("Add", null, null, onClick = onAdd)
        } else {
          var menu by remember(dial) { mutableStateOf(false) }
          Box {
            DialTile(
              label = dial.name,
              letter = dial.name.take(1).uppercase(),
              url = dial.url,
              onClick = { onOpen(dial) },
              onLongClick = { menu = true },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
              DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = {
                menu = false
                onEdit(index)
              })
              DropdownMenuItem(text = { Text("Open in new tab") }, leadingIcon = { Icon(Icons.Filled.OpenInNew, null) }, onClick = {
                menu = false
                onOpenNew(dial, false)
              })
              DropdownMenuItem(text = { Text("Open in private tab") }, leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) }, onClick = {
                menu = false
                onOpenNew(dial, true)
              })
              DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                menu = false
                onRemove(index)
              })
            }
          }
        }
      }
      repeat(4 - chunk.size) { Spacer(Modifier.width(72.dp)) }
    }
    Spacer(Modifier.height(16.dp))
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialTile(
  label: String,
  letter: String?,
  url: String? = null,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
) {
  val faviconStore = koinInject<FaviconStore>()
  val host = remember(url) { url?.let { FaviconStore.cleanHost(it) }.orEmpty() }
  val cachedIcon by if (host.isNotEmpty()) {
    faviconStore.flowFor(host).collectAsState()
  } else {
    remember { mutableStateOf(null) }
  }

  LaunchedEffect(url, host) {
    if (url != null && host.isNotEmpty() && cachedIcon == null) {
      faviconStore.fetchIfNeeded(url)
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier
        .width(72.dp)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(vertical = 4.dp),
  ) {
    val tileShape = RoundedCornerShape(16.dp)
    val bgColor = if (cachedIcon != null) {
      MaterialTheme.colorScheme.surfaceContainerHigh
    } else if (letter != null) {
      FaviconStore.hostColor(host)
    } else {
      MaterialTheme.colorScheme.secondaryContainer
    }

    Box(
      Modifier
        .size(52.dp)
        .background(bgColor, tileShape),
      contentAlignment = Alignment.Center,
    ) {
      androidx.compose.animation.AnimatedContent(
        targetState = cachedIcon,
        label = "dialIcon",
      ) { icon ->
        if (icon != null) {
          Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = label,
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit,
          )
        } else if (letter == null) {
          Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
          Text(letter, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun SpeedDialDialog(
  initial: SpeedDial,
  onDismiss: () -> Unit,
  onSave: (SpeedDial) -> Unit,
) {
  var name by remember { mutableStateOf(initial.name) }
  var address by remember { mutableStateOf(initial.url) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial.url.isEmpty()) "Add shortcut" else "Edit shortcut") },
    text = {
      Column {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          address,
          { address = it },
          label = { Text("Address") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
      }
    },
    confirmButton = {
      TextButton(enabled = address.isNotBlank(), onClick = {
        val url = address.trim().let { if (it.contains("://")) it else "https://$it" }
        onSave(SpeedDial(name.trim().ifBlank { SiteMatcher.host(url) }, url))
      }) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

