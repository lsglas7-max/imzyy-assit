package id.imzyy.xl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.io.File
import kr.co.donghyun.flamelauncher.presentation.MinecraftActivity
import org.json.JSONObject

/**
 * IMZYY control tuning shared by the Hub (writes) and the game process (reads).
 * The game runs in its own Android process, so settings travel through a small JSON file in filesDir.
 *
 * Hooks are patched into MinecraftSurface (touch), MinecraftActivity (look sensitivity) and
 * GameControllerView (cursor overlay) by scripts/prepare-imzyy.py.
 */
object ImzyyControls {
    const val DEFAULT_LOOK = 1.5f
    const val DEFAULT_SLOP = 20f
    const val DEFAULT_LONG_PRESS = 500L

    @Volatile var look = DEFAULT_LOOK
    @Volatile var touchpad = false
    @Volatile var cursorSpeed = 1.4f
    @Volatile var cursorSize = 1.0f
    @Volatile var dragSlop = DEFAULT_SLOP
    @Volatile var longPressMs = DEFAULT_LONG_PRESS

    private const val FILE = "imzyy_controls.json"
    private var checkedAt = 0L
    private var loadedMtime = -1L

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    /** Re-reads the file at most every 1.5 s, and only when it actually changed. */
    fun refresh(ctx: Context, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - checkedAt < 1500) return
        checkedAt = now
        val f = file(ctx)
        if (!f.exists()) return
        val m = f.lastModified()
        if (!force && m == loadedMtime) return
        loadedMtime = m
        try {
            val o = JSONObject(f.readText())
            look = o.optDouble("look", DEFAULT_LOOK.toDouble()).toFloat().coerceIn(0.3f, 4f)
            touchpad = o.optBoolean("touchpad", false)
            cursorSpeed = o.optDouble("speed", 1.4).toFloat().coerceIn(0.3f, 4f)
            cursorSize = o.optDouble("size", 1.0).toFloat().coerceIn(0.5f, 3f)
            dragSlop = o.optDouble("slop", DEFAULT_SLOP.toDouble()).toFloat().coerceIn(4f, 60f)
            longPressMs = o.optLong("longPress", DEFAULT_LONG_PRESS).coerceIn(200L, 1200L)
        } catch (_: Exception) {
        }
    }

    fun save(ctx: Context) {
        val o = JSONObject()
            .put("look", look.toDouble())
            .put("touchpad", touchpad)
            .put("speed", cursorSpeed.toDouble())
            .put("size", cursorSize.toDouble())
            .put("slop", dragSlop.toDouble())
            .put("longPress", longPressMs)
        file(ctx).writeText(o.toString())
        loadedMtime = file(ctx).lastModified()
    }

    fun reset(ctx: Context) {
        look = DEFAULT_LOOK; touchpad = false; cursorSpeed = 1.4f; cursorSize = 1.0f
        dragSlop = DEFAULT_SLOP; longPressMs = DEFAULT_LONG_PRESS
        save(ctx)
    }

    // ── hooks called from patched upstream code ───────────────────────────

    /** Replaces the constant MOUSE_SENSITIVITY in MinecraftActivity. */
    fun lookSensitivity(ctx: Context): Float {
        refresh(ctx)
        return look
    }

    // ── virtual mouse (touchpad) for menus / inventory ────────────────────

    private val ui = Handler(Looper.getMainLooper())
    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var moved = false
    private var rightHeld = false
    private var twoFingers = false
    private var longRunnable: Runnable? = null
    private var scaleX = 1f
    private var scaleY = 1f

    private fun resetTouch() {
        longRunnable?.let { ui.removeCallbacks(it) }
        longRunnable = null
        pointerId = -1
        moved = false
        rightHeld = false
        twoFingers = false
    }

    private fun click(a: MinecraftActivity, button: Int) {
        a.sendCursorPos(a.currentCursorX, a.currentCursorY)
        a.sendMouseButton(button, 1)
        ui.postDelayed({ a.sendMouseButton(button, 0) }, 40)
    }

    /**
     * Touchpad mode: dragging moves a visible cursor, tap = left click, long press or two-finger tap
     * = right click. Active only outside gameplay (menus, inventory) and only when enabled.
     * Returns true when the event was consumed.
     */
    fun handleTouchpad(a: MinecraftActivity, v: View, e: MotionEvent, buf: IntArray): Boolean {
        refresh(a)
        if (!touchpad || a.isGrabbing) {
            if (pointerId != -1) resetTouch()
            return false
        }
        scaleX = if (v.width > 0 && buf[0] > 0) buf[0].toFloat() / v.width else 1f
        scaleY = if (v.height > 0 && buf[1] > 0) buf[1].toFloat() / v.height else 1f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetTouch()
                pointerId = e.getPointerId(0)
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                downAt = SystemClock.uptimeMillis()
                val r = Runnable {
                    if (!moved && !twoFingers) {
                        rightHeld = true
                        click(a, 1)
                    }
                }
                longRunnable = r
                ui.postDelayed(r, longPressMs)
            }
            MotionEvent.ACTION_POINTER_DOWN -> twoFingers = true
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i < 0) return true
                val px = e.getX(i)
                val py = e.getY(i)
                if (!moved) {
                    val dx0 = px - downX
                    val dy0 = py - downY
                    val slop = dragSlop * 0.4f
                    if (dx0 * dx0 + dy0 * dy0 > slop * slop) {
                        moved = true
                        longRunnable?.let { ui.removeCallbacks(it) }
                        lastX = px; lastY = py
                    }
                }
                if (moved) {
                    val maxX = if (buf[0] > 0) buf[0].toFloat() else v.width.toFloat()
                    val maxY = if (buf[1] > 0) buf[1].toFloat() else v.height.toFloat()
                    a.currentCursorX = (a.currentCursorX + (px - lastX) * scaleX * cursorSpeed).coerceIn(0f, maxX)
                    a.currentCursorY = (a.currentCursorY + (py - lastY) * scaleY * cursorSpeed).coerceIn(0f, maxY)
                    a.sendCursorPos(a.currentCursorX, a.currentCursorY)
                    a.gameControllerView?.invalidate()
                }
                lastX = px; lastY = py
            }
            MotionEvent.ACTION_UP -> {
                longRunnable?.let { ui.removeCallbacks(it) }
                val quick = SystemClock.uptimeMillis() - downAt < 450
                if (!moved && !rightHeld) {
                    if (twoFingers) click(a, 1) else if (quick) click(a, 0)
                }
                resetTouch()
                a.gameControllerView?.invalidate()
            }
            MotionEvent.ACTION_CANCEL -> resetTouch()
        }
        return true
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val arrow = Path()

    /** Draws the virtual cursor on top of the game (called from GameControllerView.onDraw). */
    fun drawCursor(canvas: Canvas, view: View, a: MinecraftActivity) {
        if (!touchpad || a.isGrabbing) return
        val d = view.resources.displayMetrics.density * cursorSize
        val x = (a.currentCursorX / scaleX).coerceIn(0f, view.width.toFloat())
        val y = (a.currentCursorY / scaleY).coerceIn(0f, view.height.toFloat())
        arrow.reset()
        arrow.moveTo(0f, 0f)
        arrow.lineTo(0f, 17f * d)
        arrow.lineTo(4.2f * d, 13.2f * d)
        arrow.lineTo(7.2f * d, 20f * d)
        arrow.lineTo(10f * d, 18.8f * d)
        arrow.lineTo(7f * d, 12.2f * d)
        arrow.lineTo(12.2f * d, 12.2f * d)
        arrow.close()
        canvas.save()
        canvas.translate(x, y)
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, stroke)
        canvas.restore()
    }
}
