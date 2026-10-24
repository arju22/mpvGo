package app.arju.mpvgo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile (startup + library browsing).
 * Run: ./gradlew :app:generateStandardReleaseBaselineProfile with an API 33+ device or emulator connected.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule
  val rule = BaselineProfileRule()

  private val packageName: String =
    InstrumentationRegistry.getArguments().getString("targetAppId") ?: "io.github.arju22.mpvgo"

  @Test
  fun generate() =
    rule.collect(packageName = packageName, includeInStartupProfile = true) {
      device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
      device.executeShellCommand("pm grant $packageName android.permission.READ_MEDIA_VIDEO")
      device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")

      pressHome()
      startActivityAndWait()
      device.waitForIdle()

      device.findObject(By.scrollable(true))?.let { list ->
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
      }
    }
}
