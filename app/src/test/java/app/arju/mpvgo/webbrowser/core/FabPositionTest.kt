package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.core.preferences.preference.PreferenceStore
import app.arju.mpvgo.core.preferences.preference.Preference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FabPositionTest {
  private class MemoryPref<T>(private var value: T) : Preference<T> {
    private val flow = MutableStateFlow(value)
    override fun key(): String = "test"
    override fun get(): T = value
    override fun set(value: T) {
      this.value = value
      flow.value = value
    }
    override fun isSet(): Boolean = true
    override fun delete() {}
    override fun defaultValue(): T = value
    override fun changes(): Flow<T> = flow
    override fun stateIn(scope: kotlinx.coroutines.CoroutineScope): kotlinx.coroutines.flow.StateFlow<T> = flow
  }

  private class MemoryStore : PreferenceStore {
    private val strings = mutableMapOf<String, Preference<String>>()
    private val bools = mutableMapOf<String, Preference<Boolean>>()
    private val ints = mutableMapOf<String, Preference<Int>>()
    private val stringSets = mutableMapOf<String, Preference<Set<String>>>()

    override fun getString(key: String, defaultValue: String): Preference<String> =
      strings.getOrPut(key) { MemoryPref(defaultValue) }

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
      bools.getOrPut(key) { MemoryPref(defaultValue) }

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
      ints.getOrPut(key) { MemoryPref(defaultValue) }

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
      stringSets.getOrPut(key) { MemoryPref(defaultValue) }

    override fun getLong(key: String, defaultValue: Long): Preference<Long> = MemoryPref(defaultValue)
    override fun getFloat(key: String, defaultValue: Float): Preference<Float> = MemoryPref(defaultValue)
    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ): Preference<T> = MemoryPref(defaultValue)

    override fun getAll(): Map<String, *> = emptyMap<String, Any>()
  }

  private lateinit var prefs: BrowserPreferences

  @Before
  fun setUp() {
    prefs = BrowserPreferences(MemoryStore())
  }

  @Test
  fun testDefaultFabPositionIsBottomRight() {
    val (portraitEnd, portraitY) = prefs.getFabPosition(isLandscape = false)
    assertTrue(portraitEnd)
    assertEquals(1.0f, portraitY, 0.001f)

    val (landscapeEnd, landscapeY) = prefs.getFabPosition(isLandscape = true)
    assertTrue(landscapeEnd)
    assertEquals(1.0f, landscapeY, 0.001f)
  }

  @Test
  fun testSetFabPositionSnapping() {
    prefs.setFabPosition(isLandscape = false, isEnd = false, yFraction = 0.45f)
    val (portraitEnd, portraitY) = prefs.getFabPosition(isLandscape = false)
    assertFalse(portraitEnd)
    assertEquals(0.45f, portraitY, 0.001f)

    // Landscape remains unaffected
    val (landscapeEnd, landscapeY) = prefs.getFabPosition(isLandscape = true)
    assertTrue(landscapeEnd)
    assertEquals(1.0f, landscapeY, 0.001f)
  }

  @Test
  fun testResetFabPosition() {
    prefs.setFabPosition(isLandscape = false, isEnd = false, yFraction = 0.2f)
    prefs.setFabPosition(isLandscape = true, isEnd = false, yFraction = 0.3f)

    prefs.resetFabPosition()

    val (portraitEnd, portraitY) = prefs.getFabPosition(isLandscape = false)
    assertTrue(portraitEnd)
    assertEquals(1.0f, portraitY, 0.001f)

    val (landscapeEnd, landscapeY) = prefs.getFabPosition(isLandscape = true)
    assertTrue(landscapeEnd)
    assertEquals(1.0f, landscapeY, 0.001f)
  }
}
