package id.imzyy.xl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kr.co.donghyun.flamelauncher.presentation.MinecraftActivity
import org.json.JSONArray
import org.json.JSONObject

/** One on-screen button. x/y are the button centre as a fraction of the screen. */
data class PadBtn(val id: String, val label: String, val key: String, val mode: String, val x: Float, val y: Float)

object PadKeys {
    class K(val glfw: Int, val droid: Int)

    val map: Map<String, K> = LinkedHashMap<String, K>().apply {
        for (c in 'A'..'Z') put(c.toString(), K(c.code, 29 + (c - 'A')))
        for (d in 1..9) put(d.toString(), K(48 + d, 7 + d))
        put("SPACE", K(32, 62)); put("SHIFT", K(340, 59)); put("CTRL", K(341, 113)); put("ALT", K(342, 57))
        put("TAB", K(258, 61)); put("ESC", K(256, 111)); put("ENTER", K(257, 66))
        put("F1", K(290, 131)); put("F3", K(292, 133)); put("F5", K(294, 135))
    }
    val mouse = listOf("LMB", "RMB", "MMB")
    val order: List<String> = mouse + map.keys.toList()

    fun short(k: String) = when (k) {
        "SPACE" -> "SPC"; "SHIFT" -> "SHF"; "CTRL" -> "CTL"; "ENTER" -> "ENT"
        "LMB" -> "L"; "RMB" -> "R"; "MMB" -> "M"; else -> k
    }
}

/** Shared by the Hub (edits) and the game process (reads) through a JSON file in filesDir. */
object ImzyyPad {
    private const val FILE = "imzyy_pad.json"
    const val HOLD_MS = 350L
    val MODES = listOf("PRESS", "TOGGLE", "AUTO")

    var enabled by mutableStateOf(true)
    var alwaysShow by mutableStateOf(true)
    var size by mutableStateOf(1.0f)
    var opacity by mutableStateOf(0.8f)
    val buttons = mutableStateListOf<PadBtn>()

    fun modeName(m: String) = when (m) {
        "TOGGLE" -> "TAHAN (ketuk kunci)"
        "AUTO" -> "KLIK / TAHAN LAMA"
        else -> "TEKAN"
    }

    private fun b(key: String, mode: String, x: Float, y: Float) =
        PadBtn("d_" + key.lowercase(), PadKeys.short(key), key, mode, x, y)

    fun defaults(): List<PadBtn> = listOf(
        b("W", "PRESS", 0.13f, 0.56f), b("A", "PRESS", 0.07f, 0.74f), b("S", "PRESS", 0.13f, 0.92f), b("D", "PRESS", 0.19f, 0.74f),
        b("LMB", "AUTO", 0.88f, 0.74f), b("RMB", "AUTO", 0.78f, 0.88f),
        b("SPACE", "PRESS", 0.92f, 0.52f), b("SHIFT", "TOGGLE", 0.78f, 0.60f), b("E", "PRESS", 0.62f, 0.90f),
    )

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun load(ctx: Context) {
        try {
            val f = file(ctx)
            if (!f.exists()) { resetList(); return }
            val o = JSONObject(f.readText())
            enabled = o.optBoolean("enabled", true)
            alwaysShow = o.optBoolean("alwaysShow", true)
            size = o.optDouble("size", 1.0).toFloat()
            opacity = o.optDouble("opacity", 0.8).toFloat()
            val arr = o.optJSONArray("buttons") ?: JSONArray()
            val list = ArrayList<PadBtn>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                list.add(PadBtn(j.getString("id"), j.optString("label", j.getString("key")), j.getString("key"),
                    j.optString("mode", "PRESS"), j.optDouble("x", 0.5).toFloat(), j.optDouble("y", 0.5).toFloat()))
            }
            buttons.clear(); buttons.addAll(list)
        } catch (_: Exception) {
            resetList()
        }
    }

    fun save(ctx: Context) {
        try {
            val arr = JSONArray()
            for (b in buttons) arr.put(JSONObject().put("id", b.id).put("label", b.label).put("key", b.key)
                .put("mode", b.mode).put("x", b.x.toDouble()).put("y", b.y.toDouble()))
            file(ctx).writeText(JSONObject().put("enabled", enabled).put("alwaysShow", alwaysShow)
                .put("size", size.toDouble()).put("opacity", opacity.toDouble()).put("buttons", arr).toString())
        } catch (_: Exception) {
        }
    }

    private fun resetList() { buttons.clear(); buttons.addAll(defaults()) }

    fun reset(ctx: Context) { resetList(); enabled = true; alwaysShow = true; size = 1.0f; opacity = 0.8f; save(ctx) }

    fun add(ctx: Context, key: String) {
        val mode = if (key.endsWith("MB")) "AUTO" else "PRESS"
        buttons.add(PadBtn("u" + System.currentTimeMillis(), PadKeys.short(key), key, mode, 0.5f, 0.5f))
        save(ctx)
    }

    fun remove(ctx: Context, id: String) { buttons.removeAll { it.id == id }; save(ctx) }

    fun cycleMode(ctx: Context, id: String) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = buttons[i]
        buttons[i] = cur.copy(mode = MODES[(MODES.indexOf(cur.mode) + 1) % MODES.size])
        save(ctx)
    }

    fun move(ctx: Context, id: String, x: Float, y: Float) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i < 0) return
        buttons[i] = buttons[i].copy(x = x, y = y)
        save(ctx)
    }

    private const val TAG = "imzyy_pad"

    fun attach(a: MinecraftActivity) {
        load(a)
        val root = a.window.decorView as? ViewGroup ?: return
        val existing = root.findViewWithTag<View>(TAG) as? PadView
        if (existing != null) { existing.rebuild(); return }
        val v = PadView(a, a)
        v.tag = TAG
        root.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun releaseAll(a: MinecraftActivity) {
        val root = a.window.decorView as? ViewGroup ?: return
        (root.findViewWithTag<View>(TAG) as? PadView)?.release()
    }
}

@SuppressLint("ViewConstructor")
class PadView(ctx: Context, private val act: MinecraftActivity) : FrameLayout(ctx) {
    private val h = Handler(Looper.getMainLooper())
    private val dp = ctx.resources.displayMetrics.density
    private val btns = ArrayList<PadButtonView>()
    private val editBtn = TextView(ctx)
    var editMode = false

    private val tick = object : Runnable {
        override fun run() {
            val show = ImzyyPad.enabled && (ImzyyPad.alwaysShow || !act.isGrabbing)
            val vis = if (show) View.VISIBLE else View.GONE
            if (visibility != vis) {
                visibility = vis
                if (!show) release()
            }
            h.postDelayed(this, 300)
        }
    }

    init {
        isMotionEventSplittingEnabled = true
        clipChildren = false
        editBtn.text = "PAD"
        editBtn.setTextColor(Color.WHITE)
        editBtn.textSize = 11f
        editBtn.gravity = Gravity.CENTER
        editBtn.setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
        editBtn.background = GradientDrawable().apply {
            setColor(Color.argb(140, 0, 0, 0)); cornerRadius = 40 * dp; setStroke((1 * dp).toInt().coerceAtLeast(1), Color.argb(120, 255, 255, 255))
        }
        editBtn.setOnClickListener { toggleEdit() }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.END
        lp.topMargin = (8 * dp).toInt()
        lp.marginEnd = (84 * dp).toInt()
        addView(editBtn, lp)
        rebuild()
    }

    fun rebuild() {
        for (v in btns) removeView(v)
        btns.clear()
        val px = (56 * dp * ImzyyPad.size).toInt()
        for (m in ImzyyPad.buttons.toList()) {
            val v = PadButtonView(context, act, m, this)
            btns.add(v)
            addView(v, FrameLayout.LayoutParams(px, px))
        }
        requestLayout()
    }

    private fun toggleEdit() {
        release()
        editMode = !editMode
        editBtn.text = if (editMode) "SIMPAN" else "PAD"
        for (v in btns) v.invalidate()
    }

    fun commit(v: PadButtonView) {
        ImzyyPad.move(context, v.model.id, v.fx, v.fy)
    }

    fun release() {
        for (v in btns) v.release()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val w = r - l
        val hh = b - t
        for (v in btns) {
            val s = v.measuredWidth
            val cx = (v.fx * w).toInt()
            val cy = (v.fy * hh).toInt()
            v.layout(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        h.post(tick)
    }

    override fun onDetachedFromWindow() {
        h.removeCallbacks(tick)
        release()
        super.onDetachedFromWindow()
    }
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class PadButtonView(ctx: Context, private val act: MinecraftActivity, val model: PadBtn, private val pad: PadView) : View(ctx) {
    var fx = model.x
    var fy = model.y
    private var held = false
    private var latched = false
    private var ignoreUp = false
    private var downAt = 0L
    private var sx = 0f
    private var sy = 0f
    private var sfx = 0f
    private var sfy = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val box = RectF()

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        val r = s / 2f
        val a = (ImzyyPad.opacity * 255).toInt().coerceIn(40, 255)
        val active = held || latched
        fill.color = if (active) Color.argb(a, 255, 255, 255) else Color.argb((a * 0.5f).toInt(), 16, 16, 16)
        c.drawCircle(r, r, r - 2f, fill)
        line.strokeWidth = if (pad.editMode) 4f else 2f
        line.color = Color.argb(if (pad.editMode) 255 else (a * 0.7f).toInt(), 255, 255, 255)
        c.drawCircle(r, r, r - 2f, line)
        // glossy highlight on the upper edge
        line.strokeWidth = 3f
        line.color = Color.argb((a * 0.55f).toInt(), 255, 255, 255)
        box.set(s * 0.16f, s * 0.12f, s * 0.84f, s * 0.88f)
        c.drawArc(box, 200f, 140f, false, line)
        text.color = if (active) Color.BLACK else Color.WHITE
        text.textSize = if (model.label.length > 2) s * 0.27f else s * 0.36f
        c.drawText(model.label, r, r - (text.descent() + text.ascent()) / 2f, text)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pad.editMode) return drag(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); press() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lift()
        }
        return true
    }

    private fun drag(e: MotionEvent): Boolean {
        val p = parent as? View ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; sfx = fx; sfy = fy }
            MotionEvent.ACTION_MOVE -> {
                fx = (sfx + (e.rawX - sx) / p.width).coerceIn(0.03f, 0.97f)
                fy = (sfy + (e.rawY - sy) / p.height).coerceIn(0.05f, 0.95f)
                p.requestLayout()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pad.commit(this)
        }
        return true
    }

    private fun press() {
        downAt = SystemClock.uptimeMillis()
        when (model.mode) {
            "TOGGLE" -> { latched = !latched; send(latched) }
            "AUTO" -> {
                if (latched) { latched = false; ignoreUp = true; send(false) } else { held = true; send(true) }
            }
            else -> { held = true; send(true) }
        }
        invalidate()
    }

    private fun lift() {
        when (model.mode) {
            "TOGGLE" -> {}
            "AUTO" -> {
                if (ignoreUp) {
                    ignoreUp = false
                } else if (held) {
                    held = false
                    if (SystemClock.uptimeMillis() - downAt >= ImzyyPad.HOLD_MS) latched = true else send(false)
                }
            }
            else -> if (held) { held = false; send(false) }
        }
        invalidate()
    }

    fun release() {
        if (held || latched) {
            held = false
            latched = false
            send(false)
        }
        ignoreUp = false
        invalidate()
    }

    private fun send(down: Boolean) {
        try {
            when (model.key) {
                "LMB" -> ImzyyInput.mouse(act, 0, down)
                "RMB" -> ImzyyInput.mouse(act, 1, down)
                "MMB" -> ImzyyInput.mouse(act, 2, down)
                else -> {
                    val k = PadKeys.map[model.key] ?: return
                    ImzyyInput.key(act, k.glfw, k.droid, down)
                }
            }
        } catch (_: Throwable) {
        }
    }
}
