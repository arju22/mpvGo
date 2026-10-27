package app.arju.mpvgo.webbrowser.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.arju.mpvgo.core.preferences.preference.Preference
import app.arju.mpvgo.core.preferences.preference.collectAsState
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.settings.PreferenceCard
import app.arju.mpvgo.core.settings.PreferenceDivider
import app.arju.mpvgo.core.settings.PreferenceSectionHeader
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import app.arju.mpvgo.webbrowser.adblock.FilterLists
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject
import me.zhanghai.compose.preference.Preference as PreferenceRow

/** Go Browser > ⋮ > Settings, and Settings > Browser. */
@Serializable
object BrowserSettingsScreen : Screen {
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val adPrefs = koinInject<AdBlockPreferences>()
    val adBlocker = koinInject<AdBlocker>()
    val adOn by adPrefs.enabled.collectAsState()
    val lists by adPrefs.enabledLists.collectAsState()
    val custom by adPrefs.customListUrls.collectAsState()
    val blocked by adBlocker.blockedTotal.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    fun summary(c: BrowserSettingsCategory): String =
      if (c == BrowserSettingsCategory.AD_BLOCK) adBlockSummary(adOn, listCount(lists, custom), blocked) else c.summary

    BrowserSettingsScaffold("Browser") {
      item(key = "search") {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search browser settings") },
          leadingIcon = { Icon(Icons.Filled.Search, null) },
          trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
          },
          singleLine = true,
          shape = RoundedCornerShape(28.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      if (query.isNotBlank()) {
        val hits = BrowserSettingsIndex.search(query)
        section(null) {
          if (hits.isEmpty()) {
            Text("No settings found", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
          }
          hits.forEachIndexed { i, e ->
            if (i > 0) PreferenceDivider()
            LinkRow(e.title, e.category.title) { backstack.add(BrowserSettingsPage(e.category)) }
          }
        }
      } else {
        BrowserSettingsCategory.entries.groupBy { it.group }.forEach { (group, cats) ->
          section(group) {
            cats.forEachIndexed { i, c ->
              if (i > 0) PreferenceDivider()
              LinkRow(c.title, summary(c)) { backstack.add(BrowserSettingsPage(c)) }
            }
          }
        }
      }
    }
  }
}

/** One of the nine category pages. */
@Serializable
data class BrowserSettingsPage(
  val category: BrowserSettingsCategory,
) : Screen {
  @Composable
  override fun Content() {
    when (category) {
      BrowserSettingsCategory.APPEARANCE -> AppearancePage()
      BrowserSettingsCategory.TOP_BAR -> TopBarPage()
      BrowserSettingsCategory.SEARCH -> SearchPage()
      BrowserSettingsCategory.TABS -> TabsPage()
      BrowserSettingsCategory.VIDEO -> VideoPage()
      BrowserSettingsCategory.DOWNLOADS -> DownloadsPage()
      BrowserSettingsCategory.AD_BLOCK -> AdBlockPage()
      BrowserSettingsCategory.PRIVACY -> PrivacyPage()
      BrowserSettingsCategory.ADVANCED -> AdvancedPage()
    }
  }
}

internal fun listCount(
  enabled: Set<String>,
  custom: Set<String>,
): Int = FilterLists.BUILT_IN.count { it.id in enabled } + custom.size

internal fun adBlockSummary(
  on: Boolean,
  lists: Int,
  blocked: Long,
): String =
  if (!on) {
    "Off"
  } else {
    "On · $lists filter ${if (lists == 1) "list" else "lists"} · $blocked blocked"
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserSettingsScaffold(
  title: String,
  content: LazyListScope.() -> Unit,
) {
  val backstack = LocalBackStack.current
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
          )
        },
        navigationIcon = {
          IconButton(onClick = backstack::removeLastOrNull) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.secondary)
          }
        },
      )
    },
  ) { padding ->
    ProvidePreferenceLocals {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        content = content,
      )
    }
  }
}

internal fun LazyListScope.section(
  title: String?,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (title != null) item(key = "header_$title") { PreferenceSectionHeader(title) }
  item(key = "card_${title ?: "results"}") { PreferenceCard(content = content) }
}

internal fun LazyListScope.footnote(text: String) {
  item(key = "note_$text") {
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
    )
  }
}

@Composable
internal fun Summary(text: String) = Text(text, color = MaterialTheme.colorScheme.outline)

@Composable
internal fun SwitchRow(
  pref: Preference<Boolean>,
  title: String,
  summary: String? = null,
  enabled: Boolean = true,
  onChanged: (Boolean) -> Unit = {},
) {
  val value by pref.collectAsState()
  SwitchPreference(
    value = value,
    onValueChange = {
      pref.set(it)
      onChanged(it)
    },
    title = { Text(title) },
    summary = summary?.let { { Summary(it) } },
    enabled = enabled,
  )
}

@Composable
internal fun LinkRow(
  title: String,
  summary: String? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  PreferenceRow(
    title = { Text(title) },
    summary = summary?.let { { Summary(it) } },
    enabled = enabled,
    onClick = onClick,
  )
}

@Composable
internal fun TextInputDialog(
  title: String,
  initial: String,
  label: String,
  onDismiss: () -> Unit,
  singleLine: Boolean = true,
  valid: (String) -> Boolean = { it.isNotBlank() },
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(enabled = valid(text.trim()), onClick = { onConfirm(text.trim()) }) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
internal fun ConfirmDialog(
  title: String,
  text: String,
  confirm: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
