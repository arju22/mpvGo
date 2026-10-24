package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import kotlin.math.hypot

private const val DEFAULT_SUB_MARGIN_Y = 22

// Raw pixels on purpose: must stay below the 20px/30px thresholds of the other drag gestures
// so a subtitle drag is claimed before they start.
private const val CLAIM_DISTANCE_PX = 12f

/** Set while a gesture that started on the subtitle owns the touch; other gestures stand down. */
class SubtitleGestureState {
  @Volatile
  var claimed = false
}

/**
 * MX-style subtitle gestures for touches that start on the visible subtitle:
 * horizontal swipe = previous/next line, vertical drag = sub-pos, pinch = sub-scale.
 * Runs in the Initial pass so it can claim the touch before the other gestures react.
 * Taps are left alone.
 */
fun Modifier.subtitleGestures(
  state: SubtitleGestureState,
  enabled: Boolean,
  subtitlesPreferences: SubtitlesPreferences,
  isBlocked: () -> Boolean,
  onSubSeek: () -> Unit,
): Modifier = pointerInput(state, enabled) {
  state.claimed = false
  if (!enabled) return@pointerInput
  val padding = 16.dp.toPx()
  val seekThreshold = 48.dp.toPx()

  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    state.claimed = false
    if (isBlocked()) return@awaitEachGesture
    if (MPVLib.getPropertyBoolean("sub-visibility") == false) return@awaitEachGesture
    val marginY = MPVLib.getPropertyInt("sub-margin-y") ?: DEFAULT_SUB_MARGIN_Y
    val startPos = MPVLib.getPropertyInt("sub-pos") ?: subtitlesPreferences.subPos.get()
    val startScale = MPVLib.getPropertyDouble("sub-scale")?.toFloat() ?: subtitlesPreferences.subScale.get()
    val band = SubtitleGestures.estimateBand(
      viewWidth = size.width.toFloat(),
      viewHeight = size.height.toFloat(),
      subPos = startPos,
      marginY = marginY,
      fontSize = MPVLib.getPropertyInt("sub-font-size") ?: subtitlesPreferences.fontSize.get(),
      subScale = startScale,
      text = MPVLib.getPropertyString("sub-text"),
      padding = padding,
    ) ?: return@awaitEachGesture
    if (!band.contains(down.position.x, down.position.y)) return@awaitEachGesture

    var mode = SubtitleDragMode.None
    var pinching = false
    var pinchStartDistance = 0f
    var seeked = false
    var newPos = startPos
    var newScale = startScale

    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val pressed = event.changes.filter { it.pressed }
      if (pressed.isEmpty()) break

      if (pressed.size >= 2) {
        if (!pinching && mode == SubtitleDragMode.None && !isBlocked()) {
          pinching = true
          state.claimed = true
          pinchStartDistance = hypot(pressed[1].position.x - pressed[0].position.x, pressed[1].position.y - pressed[0].position.y)
        }
        if (pinching) {
          val distance = hypot(pressed[1].position.x - pressed[0].position.x, pressed[1].position.y - pressed[0].position.y)
          val scale = SubtitleGestures.pinchedScale(startScale, pinchStartDistance, distance)
          if (scale != newScale) {
            newScale = scale
            MPVLib.setPropertyFloat("sub-scale", scale)
          }
        }
        continue
      }
      if (pinching) continue

      val change = pressed.firstOrNull { it.id == down.id } ?: break
      val dx = change.position.x - down.position.x
      val dy = change.position.y - down.position.y
      if (mode == SubtitleDragMode.None) {
        if (isBlocked()) break
        mode = SubtitleGestures.dragMode(dx, dy, CLAIM_DISTANCE_PX)
        if (mode != SubtitleDragMode.None) state.claimed = true
      }
      when (mode) {
        SubtitleDragMode.Seek -> if (!seeked) {
          val step = SubtitleGestures.subSeekStep(dx, seekThreshold)
          if (step != 0) {
            seeked = true
            MPVLib.command("sub-seek", step.toString())
            onSubSeek()
          }
        }
        SubtitleDragMode.Move -> {
          val pos = SubtitleGestures.draggedSubPos(startPos, dy, size.height.toFloat(), marginY)
          if (pos != newPos) {
            newPos = pos
            MPVLib.setPropertyInt("sub-pos", pos)
          }
        }
        SubtitleDragMode.None -> Unit
      }
    }

    if (newPos != startPos) subtitlesPreferences.subPos.set(newPos)
    if (newScale != startScale) subtitlesPreferences.subScale.set(newScale)
  }
}

/** Tracks the top edge of the bottom player controls so subtitles can be lifted above them. */
class SubtitleLiftState {
  private val tops = mutableStateMapOf<String, Float>()
  var viewHeight by mutableStateOf(0f)
    private set
  internal var lifted = false

  val controlsTop: Float?
    get() = tops.values.minOrNull()

  fun report(key: String, top: Float, rootHeight: Float) {
    tops[key] = top
    viewHeight = rootHeight
  }
}

fun Modifier.subtitleLiftAnchor(state: SubtitleLiftState, key: String): Modifier =
  onGloballyPositioned { coordinates ->
    // A hidden AnimatedVisibility collapses to zero height; keep the last real position.
    if (coordinates.size.height > 0) {
      state.report(key, coordinates.boundsInRoot().top, coordinates.findRootCoordinates().size.height.toFloat())
    }
  }

/** While [active] (controls visible), keeps subtitles above the controls without changing the saved position. */
@Composable
fun SubtitleLiftEffect(state: SubtitleLiftState, active: Boolean, subtitlesPreferences: SubtitlesPreferences) {
  val userPos by subtitlesPreferences.subPos.collectAsState()
  val gap = with(LocalDensity.current) { 8.dp.toPx() }
  val controlsTop = state.controlsTop
  val viewHeight = state.viewHeight
  LaunchedEffect(active, userPos, controlsTop, viewHeight) {
    val target =
      if (active && controlsTop != null && viewHeight > 0f) {
        val marginY = MPVLib.getPropertyInt("sub-margin-y") ?: DEFAULT_SUB_MARGIN_Y
        SubtitleGestures.liftedSubPos(userPos, controlsTop, viewHeight, marginY, gap)
      } else {
        userPos
      }
    if (target != userPos) {
      MPVLib.setPropertyInt("sub-pos", target)
      state.lifted = true
    } else if (state.lifted) {
      MPVLib.setPropertyInt("sub-pos", userPos)
      state.lifted = false
    }
  }
}

@Composable
fun rememberSubtitleLiftState(): SubtitleLiftState = remember { SubtitleLiftState() }
