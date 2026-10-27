package app.arju.mpvgo.webbrowser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Hosts the tab's WebView and turns a downward drag that starts while the page is scrolled to
 * the top into a pull-to-refresh. Compose draws the indicator from [onPull].
 */
class PullRefreshLayout(context: Context) : FrameLayout(context) {
  var pullEnabled = true

  /** Pull distance as a fraction of the trigger distance; 0 when released. */
  var onPull: (Float) -> Unit = {}
  var onRefresh: () -> Unit = {}

  private val slop = ViewConfiguration.get(context).scaledTouchSlop
  private val trigger = 96f * resources.displayMetrics.density
  private var startX = 0f
  private var startY = 0f
  private var armed = false
  private var dragging = false
  private var fraction = 0f

  private fun atTop(): Boolean = getChildAt(0)?.canScrollVertically(-1) == false

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        startX = ev.x
        startY = ev.y
        dragging = false
        armed = pullEnabled && atTop()
      }
      MotionEvent.ACTION_POINTER_DOWN -> armed = false
      MotionEvent.ACTION_MOVE -> {
        if (armed && !dragging) {
          val dy = ev.y - startY
          val dx = ev.x - startX
          if (dy > slop && dy > abs(dx) * 2 && atTop()) {
            dragging = true
            startY = ev.y
          } else if (dy < -slop || abs(dx) > slop) {
            armed = false
          }
        }
      }
    }
    return dragging
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(ev: MotionEvent): Boolean {
    if (!dragging) return super.onTouchEvent(ev)
    when (ev.actionMasked) {
      MotionEvent.ACTION_MOVE -> {
        fraction = ((ev.y - startY) * 0.5f / trigger).coerceIn(0f, 1.5f)
        onPull(fraction)
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (ev.actionMasked == MotionEvent.ACTION_UP && fraction >= 1f) onRefresh()
        fraction = 0f
        dragging = false
        armed = false
        onPull(0f)
      }
    }
    return true
  }
}
