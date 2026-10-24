package app.arju.mpvgo.ui.browser.states

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

/**
 * mpvGo: a pulse that runs a few cycles and then settles, instead of an infinite transition that
 * keeps drawing frames for as long as an idle screen (empty folder, missing permission) is shown.
 */
@Composable
fun rememberSettlingPulse(
  initialValue: Float,
  targetValue: Float,
  durationMillis: Int,
  settleAt: Float,
  cycles: Int = 3,
): State<Float> {
  val anim = remember { Animatable(initialValue) }
  LaunchedEffect(Unit) {
    val spec = tween<Float>(durationMillis, easing = FastOutSlowInEasing)
    repeat(cycles) {
      anim.animateTo(targetValue, spec)
      anim.animateTo(initialValue, spec)
    }
    anim.animateTo(settleAt, spec)
  }
  return anim.asState()
}
