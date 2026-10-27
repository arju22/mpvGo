package app.arju.mpvgo.downloader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.arju.mpvgo.core.preferences.preference.collectAsState
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.settings.PreferenceCard
import app.arju.mpvgo.core.settings.PreferenceDivider
import app.arju.mpvgo.core.settings.PreferenceSectionHeader
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.downloader.data.DownloadPreferences
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object DownloadPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val prefs = koinInject<DownloadPreferences>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              "Downloads",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.secondary)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
          item { PreferenceSectionHeader(title = "Limits & Folder") }
          item {
            PreferenceCard {
              val folder by prefs.folder.collectAsState()
              ListPreference(
                value = folder,
                onValueChange = { prefs.folder.set(it) },
                values = DownloadPreferences.FOLDER_CHOICES,
                valueToText = { AnnotatedString("/$it") },
                title = { Text("Download folder") },
                summary = { Text("/$folder", color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              val concurrent by prefs.concurrentDownloads.collectAsState()
              SliderPreference(
                value = concurrent.toFloat(),
                onValueChange = { prefs.concurrentDownloads.set(it.toInt()) },
                sliderValue = concurrent.toFloat(),
                onSliderValueChange = { prefs.concurrentDownloads.set(it.toInt()) },
                valueRange = 1f..6f,
                valueSteps = 4,
                title = { Text("Downloads at the same time") },
                summary = { Text("$concurrent", color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              val connections by prefs.connectionsPerFile.collectAsState()
              SliderPreference(
                value = connections.toFloat(),
                onValueChange = { prefs.connectionsPerFile.set(it.toInt()) },
                sliderValue = connections.toFloat(),
                onSliderValueChange = { prefs.connectionsPerFile.set(it.toInt()) },
                valueRange = 1f..DownloadPreferences.MAX_CONNECTIONS.toFloat(),
                valueSteps = DownloadPreferences.MAX_CONNECTIONS - 2,
                title = { Text("Connections per file") },
                summary = { Text("$connections (on servers that allow it)", color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              val limit by prefs.speedLimitKbps.collectAsState()
              ListPreference(
                value = limit,
                onValueChange = { prefs.speedLimitKbps.set(it) },
                values = SPEED_LIMITS,
                valueToText = { AnnotatedString(speedLimitLabel(it)) },
                title = { Text("Speed limit") },
                summary = { Text(speedLimitLabel(limit), color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              val wifiOnly by prefs.wifiOnly.collectAsState()
              SwitchPreference(
                value = wifiOnly,
                onValueChange = { prefs.wifiOnly.set(it) },
                title = { Text("Wi-Fi only") },
                summary = { Text("New downloads wait for an unmetered network", color = MaterialTheme.colorScheme.outline) },
              )
            }
          }

          item { PreferenceSectionHeader(title = "Behaviour") }
          item {
            PreferenceCard {
              val ask by prefs.askBeforeEveryDownload.collectAsState()
              SwitchPreference(
                value = ask,
                onValueChange = { prefs.askBeforeEveryDownload.set(it) },
                title = { Text("Ask before every download") },
                summary = { Text("Show name, folder and quality first", color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              val hlsToMp4 by prefs.hlsToMp4.collectAsState()
              SwitchPreference(
                value = hlsToMp4,
                onValueChange = { prefs.hlsToMp4.set(it) },
                title = { Text("Save HLS streams as MP4") },
                summary = { Text("Falls back to .ts when a stream can't be converted", color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              val autoRetry by prefs.autoRetry.collectAsState()
              SwitchPreference(
                value = autoRetry,
                onValueChange = { prefs.autoRetry.set(it) },
                title = { Text("Retry automatically") },
                summary = { Text("Up to 10 times on network errors", color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              val notify by prefs.notifyWhenFinished.collectAsState()
              SwitchPreference(
                value = notify,
                onValueChange = { prefs.notifyWhenFinished.set(it) },
                title = { Text("Notify when finished") },
              )
              PreferenceDivider()
              val deleteFile by prefs.deleteFileOnRemove.collectAsState()
              SwitchPreference(
                value = deleteFile,
                onValueChange = { prefs.deleteFileOnRemove.set(it) },
                title = { Text("Delete the file when removing a download") },
                summary = { Text("Otherwise only the list entry is removed", color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
              LifecycleResumeEffect(Unit) {
                unrestricted = isIgnoringBatteryOptimizations(context)
                onPauseOrDispose {}
              }
              SwitchPreference(
                value = unrestricted,
                onValueChange = { openBatterySettings(context, request = it) },
                title = { Text("Unrestricted background downloads") },
                summary = {
                  Text(
                    if (unrestricted) "Android won't pause downloads to save battery" else "Android may pause long downloads in the background",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }
        }
      }
    }
  }

  private val SPEED_LIMITS = listOf(0, 256, 512, 1024, 2048, 5120, 10240)

  private fun speedLimitLabel(kbps: Int): String = when {
    kbps <= 0 -> "No limit"
    kbps >= 1024 -> "${kbps / 1024} MB/s"
    else -> "$kbps KB/s"
  }
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean =
  context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** [request] asks for the exemption directly; otherwise opens the system list so the user can revoke it. */
fun openBatterySettings(context: Context, request: Boolean) {
  val intent = if (request) {
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
  } else {
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
  }
  runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
