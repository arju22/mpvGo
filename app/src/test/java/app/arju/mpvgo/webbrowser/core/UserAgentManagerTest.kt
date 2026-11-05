package app.arju.mpvgo.webbrowser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentManagerTest {

  private val sampleUa =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.144 Mobile Safari/537.36"

  @Test
  fun testCleanPhone() {
    val phone = UserAgentManager.cleanPhone(sampleUa)
    assertFalse(phone.contains("; wv"))
    assertFalse(phone.contains("Version/4.0"))
    assertTrue(phone.contains("Chrome/120.0.6099.144"))
    assertTrue(phone.contains("Mobile Safari/537.36"))
  }

  @Test
  fun testTablet() {
    val tablet = UserAgentManager.tablet(sampleUa)
    assertFalse(tablet.contains("Mobile Safari/"))
    assertTrue(tablet.contains("Safari/537.36"))
    assertTrue(tablet.contains("Android 14"))
  }

  @Test
  fun testDesktop() {
    val desktop = UserAgentManager.desktop(sampleUa)
    assertTrue(desktop.contains("Windows NT 10.0; Win64; x64"))
    assertTrue(desktop.contains("Chrome/120.0.6099.144"))
    assertFalse(desktop.contains("Android"))
  }

  @Test
  fun testResolve() {
    val phone = UserAgentManager.resolve(UserAgentPreset.PHONE, "", sampleUa)
    assertTrue(phone.contains("Mobile Safari/"))

    val tablet = UserAgentManager.resolve(UserAgentPreset.TABLET, "", sampleUa)
    assertFalse(tablet.contains("Mobile Safari/"))

    val desktop = UserAgentManager.resolve(UserAgentPreset.DESKTOP, "", sampleUa)
    assertTrue(desktop.contains("Windows NT 10.0"))

    val custom = UserAgentManager.resolve(UserAgentPreset.CUSTOM, "CustomUA/1.0", sampleUa)
    assertEquals("CustomUA/1.0", custom)
  }

  @Test
  fun testHostOverrides() {
    var entries = emptySet<String>()
    assertNull(UserAgentManager.sitePresetFor("example.com", entries))

    entries = UserAgentManager.withSitePreset(entries, "example.com", UserAgentPreset.DESKTOP)
    assertEquals(UserAgentPreset.DESKTOP, UserAgentManager.sitePresetFor("example.com", entries))
    assertNull(UserAgentManager.sitePresetFor("other.org", entries))

    entries = UserAgentManager.withSitePreset(entries, "sub.example.com", UserAgentPreset.TABLET)
    assertEquals(UserAgentPreset.TABLET, UserAgentManager.sitePresetFor("sub.example.com", entries))
    assertEquals(UserAgentPreset.DESKTOP, UserAgentManager.sitePresetFor("example.com", entries))

    // Remove override
    entries = UserAgentManager.withSitePreset(entries, "example.com", null)
    assertNull(UserAgentManager.sitePresetFor("example.com", entries))
    assertEquals(UserAgentPreset.TABLET, UserAgentManager.sitePresetFor("sub.example.com", entries))
  }
}
