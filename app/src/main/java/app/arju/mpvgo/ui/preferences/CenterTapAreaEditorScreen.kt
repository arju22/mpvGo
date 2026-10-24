package app.arju.mpvgo.ui.preferences

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.arju.mpvgo.R
import app.arju.mpvgo.preferences.GesturePreferences
import app.arju.mpvgo.preferences.TapZonePreferences
import app.arju.mpvgo.preferences.preference.collectAsState
import app.arju.mpvgo.presentation.Screen
import app.arju.mpvgo.ui.player.controls.SingleTapAction
import app.arju.mpvgo.ui.player.controls.TapRegion
import app.arju.mpvgo.ui.player.controls.TapZoneConfig
import app.arju.mpvgo.ui.player.controls.TapZones
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val SideColor = Color(66, 165, 245)
private val EllipseColor = Color(255, 202, 40)
private val StripColor = Color(102, 187, 106)
private val ClashColor = Color(239, 83, 80)
private val DummyInk = Color(0xFF23262D)

/** Landscape editor for the single-tap center ellipse. Edits are committed only on Save. */
@Serializable
object CenterTapAreaEditorScreen : Screen {
  @Composable
  override fun Content() {
    val tapPrefs = koinInject<TapZonePreferences>()
    val gesturePrefs = koinInject<GesturePreferences>()
    val savedW by tapPrefs.centerAreaWidth.collectAsState()
    val savedH by tapPrefs.centerAreaHeight.collectAsState()
    val savedX by tapPrefs.centerAreaX.collectAsState()
    val savedY by tapPrefs.centerAreaY.collectAsState()
    val sideWidth by gesturePrefs.doubleTapSeekAreaWidth.collectAsState()
    val stripOn by tapPrefs.bottomStripShowsControls.collectAsState()

    var w by rememberSaveable { mutableFloatStateOf(savedW.toFloat()) }
    var h by rememberSaveable { mutableFloatStateOf(savedH.toFloat()) }
    var cx by rememberSaveable { mutableFloatStateOf(savedX.toFloat()) }
    var cy by rememberSaveable { mutableFloatStateOf(savedY.toFloat()) }
    var stage by remember { mutableStateOf(IntSize.Zero) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackKey by remember { mutableStateOf(0) }

    val current = listOf(w, h, cx, cy).map { it.roundToInt() }
    val dirty = current != listOf(savedW, savedH, savedX, savedY)
    val atDefaults = current == listOf(TapZones.DEFAULT_SIZE, TapZones.DEFAULT_SIZE, TapZones.DEFAULT_CENTER, TapZones.DEFAULT_CENTER)
    val config = TapZoneConfig(sideWidth, true, current[0], current[1], current[2], current[3], stripOn)

    LockLandscapeImmersive()

    val tapTexts = mapOf(
      SingleTapAction.CenterAction to stringResource(R.string.tap_area_editor_tap_center),
      SingleTapAction.ShowControls to stringResource(R.string.tap_area_editor_tap_show),
      SingleTapAction.ToggleControls to stringResource(R.string.tap_area_editor_tap_toggle),
    )
    val sideSuffix = stringResource(R.string.tap_area_editor_tap_side_suffix)
    LaunchedEffect(feedbackKey) {
      if (feedback != null) {
        delay(1500)
        feedback = null
      }
    }

    Box(
      Modifier
        .fillMaxSize()
        .background(Color.White)
        .onSizeChanged { stage = it }
        .pointerInput(config) {
          detectTapGestures { p ->
            val t = TapZones.classifyTap(p.x, p.y, size.width.toFloat(), size.height.toFloat(), config)
            feedback = tapTexts.getValue(t.singleTap) + if (t.region != TapRegion.Center) sideSuffix else ""
            feedbackKey++
          }
        },
    ) {
      Canvas(Modifier.fillMaxSize()) { drawZones(config) }
      DummyControls()

      val sw = stage.width.toFloat()
      val sh = stage.height.toFloat()
      if (sw > 0f && sh > 0f) {
        val px = sw * cx / 100f
        val py = sh * cy / 100f
        val rx = sw * w / 200f
        val ry = sh * h / 200f
        Handle(Offset((px + rx).coerceIn(0f, sw), py), 22.dp) { d ->
          w = (w + 200f * d.x / sw).coerceIn(TapZones.MIN_SIZE.toFloat(), TapZones.MAX_SIZE.toFloat())
        }
        Handle(Offset(px, (py + ry).coerceIn(0f, sh)), 22.dp) { d ->
          h = (h + 200f * d.y / sh).coerceIn(TapZones.MIN_SIZE.toFloat(), TapZones.MAX_SIZE.toFloat())
        }
        Handle(Offset(px, py), 34.dp, Icons.Default.OpenWith) { d ->
          cx = (cx + 100f * d.x / sw).coerceIn(0f, 100f)
          cy = (cy + 100f * d.y / sh).coerceIn(0f, 100f)
        }
      }

      Column(
        Modifier
          .align(Alignment.TopCenter)
          .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedButton(
            enabled = !atDefaults,
            onClick = {
              w = TapZones.DEFAULT_SIZE.toFloat()
              h = TapZones.DEFAULT_SIZE.toFloat()
              cx = TapZones.DEFAULT_CENTER.toFloat()
              cy = TapZones.DEFAULT_CENTER.toFloat()
            },
            modifier = Modifier.background(Color.White, CircleShape),
          ) { Text(stringResource(R.string.tap_area_editor_reset)) }
          Button(
            enabled = dirty,
            onClick = {
              tapPrefs.centerAreaWidth.set(current[0])
              tapPrefs.centerAreaHeight.set(current[1])
              tapPrefs.centerAreaX.set(current[2])
              tapPrefs.centerAreaY.set(current[3])
            },
          ) { Text(stringResource(R.string.tap_area_editor_save)) }
        }
        val status = feedback
          ?: if (dirty) stringResource(R.string.tap_area_editor_unsaved) else stringResource(R.string.tap_area_editor_hint)
        Text(
          status,
          fontSize = 12.sp,
          color = if (dirty && feedback == null) Color(0xFFB26A00) else DummyInk,
          modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Text(
          "X ${current[0]}% × Y ${current[1]}% · ${current[2]}%, ${current[3]}%",
          fontSize = 11.sp,
          color = DummyInk.copy(alpha = 0.7f),
        )
      }
    }
  }
}

@Composable
private fun LockLandscapeImmersive() {
  val activity = LocalActivity.current
  DisposableEffect(activity) {
    if (activity == null) return@DisposableEffect onDispose {}
    val previous = activity.requestedOrientation
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    onDispose {
      controller.show(WindowInsetsCompat.Type.systemBars())
      activity.requestedOrientation = previous
    }
  }
}

@Composable
private fun Handle(center: Offset, size: Dp, icon: ImageVector? = null, onDrag: (Offset) -> Unit) {
  val half = with(LocalDensity.current) { (size / 2).roundToPx() }
  val latestOnDrag by rememberUpdatedState(onDrag)
  Box(
    Modifier
      .offset { IntOffset(center.x.roundToInt() - half, center.y.roundToInt() - half) }
      .size(size)
      .clip(CircleShape)
      .background(EllipseColor)
      .border(2.dp, Color.White, CircleShape)
      .pointerInput(Unit) {
        detectDragGestures { change, drag ->
          change.consume()
          latestOnDrag(drag)
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    if (icon != null) Icon(icon, null, tint = Color.Black, modifier = Modifier.size(18.dp))
  }
}

private fun glass(color: Color, top: Float, bottom: Float) = Brush.verticalGradient(
  0f to color.copy(alpha = 0.42f),
  0.5f to color.copy(alpha = 0.26f),
  1f to color.copy(alpha = 0.36f),
  startY = top,
  endY = bottom,
)

private fun DrawScope.sheen(rect: Rect) {
  drawRect(
    Brush.verticalGradient(
      0f to Color.White.copy(alpha = 0.55f),
      1f to Color.Transparent,
      startY = rect.top,
      endY = rect.top + rect.height * 0.45f,
    ),
    topLeft = rect.topLeft,
    size = Size(rect.width, rect.height * 0.45f),
  )
}

private fun DrawScope.drawZones(config: TapZoneConfig) {
  val sw = size.width
  val sh = size.height
  val side = sw * config.sideWidthPercent / 100f
  val stripTop = if (config.bottomStripShowsControls) sh * (1f - TapZones.BOTTOM_STRIP_FRACTION) else sh
  val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
  val oval = Rect(
    sw * config.centerXPercent / 100f - sw * config.ellipseWidthPercent / 200f,
    sh * config.centerYPercent / 100f - sh * config.ellipseHeightPercent / 200f,
    sw * config.centerXPercent / 100f + sw * config.ellipseWidthPercent / 200f,
    sh * config.centerYPercent / 100f + sh * config.ellipseHeightPercent / 200f,
  )
  val ellipse = Path().apply { addOval(oval) }
  val sides = listOf(Rect(0f, 0f, side, stripTop), Rect(sw - side, 0f, sw, stripTop))

  // Double-tap sides, without the ellipse
  clipPath(ellipse, ClipOp.Difference) {
    sides.forEach {
      drawRect(glass(SideColor, 0f, sh), it.topLeft, it.size)
      sheen(it)
    }
  }
  clipPath(ellipse) {
    // Ellipse center band
    clipRect(side, 0f, sw - side, stripTop) { drawRect(glass(EllipseColor, oval.top, oval.bottom)) }
    // Ellipse ∩ sides: blue + amber stripes
    sides.forEach { r ->
      clipRect(r.left, r.top, r.right, r.bottom) {
        drawRect(glass(SideColor, 0f, sh))
        var x = -sh
        while (x < sw) {
          drawLine(EllipseColor.copy(alpha = 0.6f), Offset(x, sh), Offset(x + sh, 0f), strokeWidth = 7f)
          x += 22f
        }
      }
    }
    clipRect(0f, 0f, sw, stripTop) { sheen(oval) }
    // Ellipse ∩ bottom strip: strip wins
    if (config.bottomStripShowsControls) {
      clipRect(0f, stripTop, sw, sh) { drawRect(glass(ClashColor, stripTop, sh)) }
    }
  }
  if (config.bottomStripShowsControls) {
    clipPath(ellipse, ClipOp.Difference) {
      val strip = Rect(0f, stripTop, sw, sh)
      drawRect(glass(StripColor, stripTop, sh), strip.topLeft, strip.size)
      sheen(strip)
    }
    drawLine(StripColor, Offset(0f, stripTop), Offset(sw, stripTop), strokeWidth = 3f, pathEffect = dash)
  }
  drawLine(SideColor, Offset(side, 0f), Offset(side, stripTop), strokeWidth = 3f, pathEffect = dash)
  drawLine(SideColor, Offset(sw - side, 0f), Offset(sw - side, stripTop), strokeWidth = 3f, pathEffect = dash)
  drawPath(ellipse, EllipseColor, style = Stroke(width = 3f, pathEffect = dash))
}

/** Non-interactive frosted copies of the landscape player controls, at their usual positions. */
@Composable
private fun DummyControls() {
  Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Frost(Icons.AutoMirrored.Filled.ArrowBack)
      Text("sample_episode_S01E02.mkv", color = DummyInk, fontSize = 14.sp)
    }
    Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      FrostPill("Ch 2")
      FrostPill("HW+")
      Frost(Icons.Default.Audiotrack)
      Frost(Icons.Default.ClosedCaption)
      Frost(Icons.Default.MoreVert)
    }
    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
      Frost(Icons.Default.SkipPrevious)
      Frost(Icons.Default.Pause, 58.dp)
      Frost(Icons.Default.SkipNext)
    }
    Row(Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Frost(Icons.Default.Lock)
      Frost(Icons.Default.ScreenRotation)
      FrostPill("1.00x")
      Frost(Icons.Default.Refresh)
    }
    Row(Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Frost(Icons.Default.PictureInPicture)
      Frost(Icons.Default.AspectRatio)
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(DummyInk.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(0.34f).height(4.dp).background(Color(0xFF3A6DF0)))
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("07:48", fontSize = 11.sp, color = DummyInk)
        Text("-15:12", fontSize = 11.sp, color = DummyInk)
      }
    }
  }
}

@Composable
private fun Frost(icon: ImageVector, size: Dp = 30.dp) {
  Box(
    Modifier
      .size(size)
      .clip(CircleShape)
      .background(Color.White.copy(alpha = 0.45f))
      .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, null, tint = DummyInk, modifier = Modifier.size(size * 0.55f))
  }
}

@Composable
private fun FrostPill(text: String) {
  val shape = RoundedCornerShape(14.dp)
  Text(
    text,
    fontSize = 12.sp,
    color = DummyInk,
    modifier = Modifier
      .clip(shape)
      .background(Color.White.copy(alpha = 0.45f))
      .border(1.dp, Color.White.copy(alpha = 0.85f), shape)
      .padding(horizontal = 9.dp, vertical = 4.dp),
  )
}
