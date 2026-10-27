package app.arju.mpvgo.library

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Number of tabs currently in a mode (selection, search, open FAB menu) that should suppress tab swiping. */
internal object TabSwipeBlock {
  var count by mutableIntStateOf(0)
}

/** Call from a main tab; while [blocked] is true, swiping between tabs is disabled. */
@Composable
fun BlockTabSwipe(blocked: Boolean) {
  DisposableEffect(blocked) {
    if (blocked) TabSwipeBlock.count++
    onDispose { if (blocked) TabSwipeBlock.count-- }
  }
}

/** Which way a finished horizontal swipe should move the selected tab, or 0 for none. */
internal fun tabSwipeDirection(
  totalDx: Float,
  velocityX: Float,
  distanceThreshold: Float,
  velocityThreshold: Float,
): Int {
  val fling = abs(velocityX) >= velocityThreshold && (velocityX > 0) == (totalDx > 0)
  if (abs(totalDx) < distanceThreshold && !fling) return 0
  return if (totalDx < 0) 1 else -1
}

/**
 * Flick-to-switch for the main tabs: a horizontal swipe calls [onSwipe] with +1 (next) or -1
 * (previous) on release. Only the current tab stays composed; the existing tab transition animates.
 */
@Composable
fun Modifier.tabSwipe(enabled: Boolean, onSwipe: (Int) -> Unit): Modifier {
  if (!enabled || TabSwipeBlock.count > 0) return this
  val density = LocalDensity.current
  val distance = with(density) { 64.dp.toPx() }
  val velocity = with(density) { 600.dp.toPx() }
  val callback = rememberUpdatedState(onSwipe)
  return pointerInput(distance, velocity) {
    var total = 0f
    val tracker = VelocityTracker()
    detectHorizontalDragGestures(
      onDragStart = {
        total = 0f
        tracker.resetTracking()
      },
      onDragEnd = {
        val direction = tabSwipeDirection(total, tracker.calculateVelocity().x, distance, velocity)
        if (direction != 0) callback.value(direction)
      },
      onDragCancel = { total = 0f },
    ) { change, dx ->
      change.consume()
      total += dx
      tracker.addPosition(change.uptimeMillis, change.position)
    }
  }
}
