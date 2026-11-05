package app.arju.mpvgo.webbrowser.settings

import app.arju.mpvgo.webbrowser.core.UserAgentPreset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arju.mpvgo.core.preferences.preference.collectAsState
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.settings.PreferenceDivider
import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.AdBlockUpdateWorker
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import app.arju.mpvgo.webbrowser.adblock.FilterLists
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import app.arju.mpvgo.webbrowser.core.BrowserPreferences
import app.arju.mpvgo.webbrowser.core.ExitClear
import app.arju.mpvgo.webbrowser.core.SiteChoice
import app.arju.mpvgo.webbrowser.core.TopBarButton
import app.arju.mpvgo.webbrowser.ui.icon
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

enum class BrowserSettingsSub {
  TOP_BAR_BUTTONS,
  SITE_CHOICES,
  FILTER_LISTS,
  MY_FILTERS,
  ALLOWED_SITES,
  SITE_PERMISSIONS,
}

@Serializable
data class BrowserSettingsSubPage(
  val sub: BrowserSettingsSub,
) : Screen {
  @Composable
  override fun Content() {
    when (sub) {
      BrowserSettingsSub.TOP_BAR_BUTTONS -> TopBarButtonsPage()
      BrowserSettingsSub.SITE_CHOICES -> SiteChoicesPage()
      BrowserSettingsSub.FILTER_LISTS -> FilterListsPage()
      BrowserSettingsSub.MY_FILTERS -> MyFiltersPage()
      BrowserSettingsSub.ALLOWED_SITES -> AllowedSitesPage()
      BrowserSettingsSub.SITE_PERMISSIONS -> SitePermissionsPage()
    }
  }
}

@Composable
private fun TopBarButtonsPage() {
  val prefs = koinInject<BrowserPreferences>()
  val raw by prefs.topBarButtons.collectAsState()
  val shown = TopBarButton.parse(raw)

  fun save(list: List<TopBarButton>) = prefs.topBarButtons.set(TopBarButton.encode(list))

  BrowserSettingsScaffold("Top bar buttons") {
    section("Preview") {
      Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)),
          contentAlignment = Alignment.Center,
        ) { Text("3", style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.weight(1f),
        ) {
          Text("example.com", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), maxLines = 1)
        }
        shown.forEach { Icon(it.icon(), it.label, Modifier.padding(start = 12.dp).size(22.dp)) }
        Icon(Icons.Filled.MoreVert, null, Modifier.padding(start = 8.dp).size(22.dp))
      }
    }
    section("Shown (${shown.size} of ${TopBarButton.MAX})") {
      if (shown.isEmpty()) Text("No buttons; everything stays in the ⋮ menu.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
      shown.forEachIndexed { i, b ->
        if (i > 0) PreferenceDivider()
        ListItem(
          leadingContent = { Icon(b.icon(), null, tint = MaterialTheme.colorScheme.primary) },
          headlineContent = { Text(b.label) },
          supportingContent = { Summary(b.summary) },
          trailingContent = {
            Row {
              IconButton(enabled = i > 0, onClick = { save(TopBarButton.move(shown, i, i - 1)) }) {
                Icon(Icons.Filled.KeyboardArrowUp, "Move left")
              }
              IconButton(enabled = i < shown.lastIndex, onClick = { save(TopBarButton.move(shown, i, i + 1)) }) {
                Icon(Icons.Filled.KeyboardArrowDown, "Move right")
              }
              IconButton(onClick = { save(TopBarButton.toggle(shown, b)) }) {
                Icon(Icons.Filled.RemoveCircleOutline, "Remove")
              }
            }
          },
          colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
      }
    }
    val full = shown.size >= TopBarButton.MAX
    section("More buttons") {
      TopBarButton.entries.filterNot { it in shown }.forEachIndexed { i, b ->
        if (i > 0) PreferenceDivider()
        ListItem(
          leadingContent = { Icon(b.icon(), null) },
          headlineContent = { Text(b.label) },
          supportingContent = { Summary(b.summary) },
          trailingContent = { if (!full) Icon(Icons.Filled.Add, "Add") },
          colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
          modifier =
            Modifier
              .clickable(enabled = !full) { save(TopBarButton.toggle(shown, b)) }
              .then(if (full) Modifier.background(androidx.compose.ui.graphics.Color.Transparent) else Modifier),
        )
      }
    }
    footnote(if (full) "Remove a button to pick another. Everything else stays in the ⋮ menu." else "Tap a button to add it.")
  }
}

@Composable
private fun SiteChoicesPage() {
  val prefs = koinInject<BrowserPreferences>()
  val choices by prefs.siteChoices.collectAsState()
  val sites =
    choices
      .mapNotNull { e ->
        val host = e.substringBefore('=')
        runCatching { SiteChoice.valueOf(e.substringAfter('=')) }.getOrNull()?.let { host to it }
      }.sortedBy { it.first }
  BrowserSettingsScaffold("Site choices") {
    section(null) {
      if (sites.isEmpty()) Text("No sites yet. Your answer to \"Play this video in mpvGo?\" is saved here when you tick Remember.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
      sites.forEachIndexed { i, (host, choice) ->
        if (i > 0) PreferenceDivider()
        RemovableRow(host, if (choice == SiteChoice.MPVGO) "Play in mpvGo" else "Web player") { prefs.rememberSite(host, null) }
      }
    }
    if (sites.isNotEmpty()) {
      section(null) { LinkRow("Forget all sites") { prefs.siteChoices.set(emptySet()) } }
    }
  }
}

@Composable
private fun FilterListsPage() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val adPrefs = koinInject<AdBlockPreferences>()
  val adBlocker = koinInject<AdBlocker>()
  val enabled by adPrefs.enabledLists.collectAsState()
  val custom by adPrefs.customListUrls.collectAsState()
  val updated by adPrefs.lastUpdated.collectAsState()
  val state by adBlocker.state.collectAsState()
  var adding by remember { mutableStateOf(false) }
  var updating by remember { mutableStateOf(false) }

  fun rebuild() = scope.launch { adBlocker.rebuild() }

  BrowserSettingsScaffold("Filter lists") {
    section("Built-in") {
      FilterLists.BUILT_IN.forEachIndexed { i, list ->
        if (i > 0) PreferenceDivider()
        SwitchPreference(
          value = list.id in enabled,
          onValueChange = { on ->
            adPrefs.enabledLists.set(if (on) enabled + list.id else enabled - list.id)
            rebuild()
          },
          title = { Text(list.name) },
          summary = { Summary(list.url.substringAfter("://").substringBefore('/')) },
        )
      }
    }
    section("Your lists") {
      custom.sorted().forEachIndexed { i, url ->
        if (i > 0) PreferenceDivider()
        RemovableRow(url.substringAfterLast('/').ifBlank { url }, url) {
          adPrefs.customListUrls.set(custom - url)
          rebuild()
        }
      }
      if (custom.isNotEmpty()) PreferenceDivider()
      LinkRow("Add a list", "Link to an Adblock Plus filter list") { adding = true }
    }
    section("Updates") {
      val busy = updating || state is AdBlocker.State.Loading
      LinkRow("Update now", if (busy) "Updating…" else updatedLabel(updated).replaceFirstChar { it.uppercase() }, enabled = !busy) {
        updating = true
        scope.launch {
          adBlocker.update(force = true)
          updating = false
        }
      }
      PreferenceDivider()
      SwitchRow(adPrefs.autoUpdateWifiOnly, "Update on Wi-Fi only", "Lists refresh about once a week") {
        AdBlockUpdateWorker.schedule(context, it, replace = true)
      }
    }
  }
  if (adding) {
    TextInputDialog(
      title = "Add a filter list",
      initial = "https://",
      label = "List URL",
      onDismiss = { adding = false },
      valid = { it.startsWith("https://") || it.startsWith("http://") },
    ) {
      adPrefs.customListUrls.set(custom + it)
      adding = false
      scope.launch { adBlocker.update(force = false) }
    }
  }
}

@Composable
private fun MyFiltersPage() {
  val scope = rememberCoroutineScope()
  val adPrefs = koinInject<AdBlockPreferences>()
  val adBlocker = koinInject<AdBlocker>()
  val saved by adPrefs.myFilters.collectAsState()
  var text by rememberSaveable(saved) { mutableStateOf(saved) }
  BrowserSettingsScaffold("My filters") {
    item(key = "editor") {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "One rule per line in Adblock Plus syntax, for example ||ads.example.com^ or example.com##.banner. Lines starting with ! are comments.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
        )
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(enabled = text != saved, onClick = { text = saved }) { Text("Undo changes") }
          Spacer(Modifier.weight(1f))
          Button(enabled = text != saved, onClick = {
            adPrefs.myFilters.set(text.trim())
            scope.launch { adBlocker.rebuild() }
          }) { Text("Save") }
        }
      }
    }
  }
}

@Composable
private fun AllowedSitesPage() {
  val adPrefs = koinInject<AdBlockPreferences>()
  val allowed by adPrefs.allowedSites.collectAsState()
  var adding by remember { mutableStateOf(false) }
  BrowserSettingsScaffold("Allowed sites") {
    section(null) {
      allowed.sorted().forEachIndexed { i, site ->
        if (i > 0) PreferenceDivider()
        RemovableRow(site, "Ads are not blocked here") { adPrefs.allowedSites.set(allowed - site) }
      }
      if (allowed.isNotEmpty()) PreferenceDivider()
      LinkRow("Add a site") { adding = true }
    }
    footnote("You can also turn ad block off for a site from the shield in the browser's ⋮ menu.")
  }
  if (adding) {
    TextInputDialog(
      title = "Allow ads on a site",
      initial = "",
      label = "Site, e.g. example.com",
      onDismiss = { adding = false },
      valid = { SiteMatcher.host(it).isNotEmpty() || '.' in it },
    ) {
      val host = SiteMatcher.host(it).ifEmpty { it.lowercase().removePrefix("www.") }
      adPrefs.allowedSites.set(allowed + host)
      adding = false
    }
  }
}

@Composable
private fun SitePermissionsPage() {
  val prefs = koinInject<BrowserPreferences>()
  val adPrefs = koinInject<AdBlockPreferences>()
  val choices by prefs.siteChoices.collectAsState()
  val allowed by adPrefs.allowedSites.collectAsState()
  val video = choices.associate { it.substringBefore('=') to it.substringAfter('=') }
  val adOff = allowed.map { SiteMatcher.host(it).ifEmpty { it } }.toSet()
  val hosts = (video.keys + adOff).sorted()
  BrowserSettingsScaffold("Site permissions") {
    section("Always blocked") {
      Text(
        "Camera, microphone and location are never given to websites in the mpvGo browser.",
        Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    section("Sites with custom settings") {
      if (hosts.isEmpty()) Text("None", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
      hosts.forEachIndexed { i, host ->
        if (i > 0) PreferenceDivider()
        val parts =
          listOfNotNull(
            video[host]?.let { if (it == SiteChoice.MPVGO.name) "Videos play in mpvGo" else "Videos use the web player" },
            if (host in adOff) "Ad block off" else null,
          )
        RemovableRow(host, parts.joinToString(" · ")) {
          prefs.rememberSite(host, null)
          adPrefs.allowedSites.set(allowed.filterNot { SiteMatcher.host(it).ifEmpty { it } == host }.toSet())
        }
      }
    }
    if (hosts.isNotEmpty()) {
      section(null) {
        LinkRow("Reset all sites") {
          prefs.siteChoices.set(emptySet())
          adPrefs.allowedSites.set(emptySet())
        }
      }
    }
  }
}

@Composable
internal fun RemovableRow(
  title: String,
  summary: String,
  onRemove: () -> Unit,
) {
  ListItem(
    headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supportingContent = { Summary(summary) },
    trailingContent = { IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") } },
    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
  )
}

@Composable
internal fun ExitClearDialog(
  initial: Set<ExitClear>,
  onDismiss: () -> Unit,
  onConfirm: (Set<ExitClear>) -> Unit,
) {
  var picked by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Clear data when I exit") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
          "Runs when you close the browser with Exit, Close browser or Back.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.size(8.dp))
        ExitClear.entries.forEach { e ->
          Row(
            Modifier.fillMaxWidth().clickable { picked = if (e in picked) picked - e else picked + e },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = e in picked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Text(e.label)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
internal fun UserAgentDialog(
  initialPreset: UserAgentPreset,
  initialCustom: String,
  onDismiss: () -> Unit,
  onConfirm: (preset: UserAgentPreset, customString: String) -> Unit,
) {
  var selectedPreset by remember { mutableStateOf(initialPreset) }
  var text by remember { mutableStateOf(initialCustom) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("User agent") },
    text = {
      Column {
        listOf(
          UserAgentPreset.PHONE to "Phone (default)",
          UserAgentPreset.TABLET to "Tablet",
          UserAgentPreset.DESKTOP to "Desktop",
          UserAgentPreset.CUSTOM to "Custom",
        ).forEach { (preset, label) ->
          Row(
            Modifier.fillMaxWidth().clickable { selectedPreset = preset },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = selectedPreset == preset, onClick = null, modifier = Modifier.padding(12.dp))
            Text(label)
          }
        }
        if (selectedPreset == UserAgentPreset.CUSTOM) {
          OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Custom user agent string") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = selectedPreset != UserAgentPreset.CUSTOM || text.isNotBlank(),
        onClick = { onConfirm(selectedPreset, text.trim()) },
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
