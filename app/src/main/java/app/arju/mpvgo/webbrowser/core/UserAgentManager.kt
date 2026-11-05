package app.arju.mpvgo.webbrowser.core

import android.net.Uri
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

enum class UserAgentPreset(val displayName: String) {
  PHONE("Phone"),
  TABLET("Tablet"),
  DESKTOP("Desktop"),
  CUSTOM("Custom"),
  ;

  companion object {
    fun fromName(name: String?): UserAgentPreset =
      entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PHONE
  }
}

object UserAgentManager {

  private val CHROME_VERSION_REGEX = Regex("Chrome/([0-9.]+)")
  private val WV_REGEX = Regex(";\\s*wv\\b")
  private val VERSION_REGEX = Regex("Version/[0-9.]+\\s*")

  fun cleanPhone(defaultUa: String): String =
    defaultUa
      .replace(WV_REGEX, "")
      .replace(VERSION_REGEX, "")
      .trim()

  fun tablet(defaultUa: String): String =
    cleanPhone(defaultUa)
      .replace(" Mobile Safari/", " Safari/")

  fun extractChromeVersion(defaultUa: String): String =
    CHROME_VERSION_REGEX.find(defaultUa)?.groupValues?.get(1) ?: "130.0.0.0"

  fun desktop(defaultUa: String): String {
    val version = extractChromeVersion(defaultUa)
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
  }

  fun resolve(
    preset: UserAgentPreset,
    customUa: String,
    defaultUa: String,
  ): String =
    when (preset) {
      UserAgentPreset.PHONE -> cleanPhone(defaultUa)
      UserAgentPreset.TABLET -> tablet(defaultUa)
      UserAgentPreset.DESKTOP -> desktop(defaultUa)
      UserAgentPreset.CUSTOM -> customUa.ifBlank { cleanPhone(defaultUa) }
    }

  fun hostOf(url: String): String? =
    runCatching {
      val uri = Uri.parse(url)
      uri.host?.lowercase()?.takeIf { it.isNotBlank() }
    }.getOrNull()

  fun sitePresetFor(
    host: String,
    entries: Set<String>,
  ): UserAgentPreset? {
    val prefix = "${host.lowercase()}="
    val entry = entries.firstOrNull { it.startsWith(prefix) } ?: return null
    val raw = entry.substringAfter('=').substringBefore(':')
    return runCatching { UserAgentPreset.valueOf(raw) }.getOrNull()
  }

  fun siteCustomUaFor(
    host: String,
    entries: Set<String>,
  ): String? {
    val prefix = "${host.lowercase()}="
    val entry = entries.firstOrNull { it.startsWith(prefix) } ?: return null
    val value = entry.substringAfter('=')
    return if (value.startsWith("CUSTOM:")) value.substringAfter("CUSTOM:") else null
  }

  fun withSitePreset(
    entries: Set<String>,
    host: String,
    preset: UserAgentPreset?,
    customUa: String? = null,
  ): Set<String> {
    val h = host.lowercase()
    val rest = entries.filterNot { it.startsWith("$h=") }.toSet()
    if (preset == null) return rest
    val value = if (preset == UserAgentPreset.CUSTOM && !customUa.isNullOrBlank()) {
      "CUSTOM:$customUa"
    } else {
      preset.name
    }
    return rest + "$h=$value"
  }

  fun applyClientHints(
    settings: WebSettings,
    preset: UserAgentPreset,
    defaultUa: String,
  ) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
    runCatching {
      val builder = UserAgentMetadata.Builder()
      when (preset) {
        UserAgentPreset.PHONE -> {
          builder.setMobile(true)
          builder.setPlatform("Android")
        }
        UserAgentPreset.TABLET -> {
          builder.setMobile(false)
          builder.setPlatform("Android")
        }
        UserAgentPreset.DESKTOP -> {
          builder.setMobile(false)
          builder.setPlatform("Windows")
          builder.setPlatformVersion("10.0.0")
          builder.setArchitecture("x86")
          builder.setBitness(64)
        }
        UserAgentPreset.CUSTOM -> {
          // Keep defaults for custom
          return
        }
      }
      WebSettingsCompat.setUserAgentMetadata(settings, builder.build())
    }
  }
}
