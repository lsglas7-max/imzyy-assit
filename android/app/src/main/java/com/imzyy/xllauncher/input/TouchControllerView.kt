package com.imzyy.xllauncher.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Native touch HUD component. The game session must attach an InputSink backed by
 * the actual renderer/input pipe before these actions can control Minecraft.
 */
class TouchControllerView(context: Context) : View(context) {
    enum class Action { MOVE, JUMP, ATTACK, SNEAK, USE, INVENTORY }
    fun interface InputSink { fun onInput(action: Action, pressed: Boolean, x: Float, y: Float) }

    var inputSink: InputSink? = null
    var opacity: Float = 0.72f
        set(value) { field = value.coerceIn(0.2f, 1f); invalidate() }
    var scale: Float = 1f
        set(value) { field = value.coerceIn(0.65f, 1.4f); invalidate() }
    var compact: Boolean = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activePointers = mutableMapOf<Int, Action>()
    private val actionPressCounts = mutableMapOf<Action, Int>()
    private var stickPointer = -1
    private var stickX = 0f
    private var stickY = 0f
    private var baseX = 0f
    private var baseY = 0f
    private var stickRadius = 0f
    private val centers = mutableMapOf<Action, Pair<Float, Float>>()
    private val actionRadius = mutableMapOf<Action, Float>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = min(width, height).toFloat()
        val s = unit * (if (compact) 0.075f else 0.09f) * scale
        baseX = width * 0.19f
        baseY = height * 0.72f
        stickRadius = s * 1.42f
        drawCircle(canvas, baseX, baseY, stickRadius, "MOVE", false)
        val knobX = if (stickPointer == -1) baseX else stickX
        val knobY = if (stickPointer == -1) baseY else stickY
        drawCircle(canvas, knobX, knobY, s * 0.55f, "", true)

        val factor = if (compact) 0.88f else 1f
        place(Action.ATTACK, width * 0.77f, height * 0.78f, s * factor, "HIT", canvas)
        place(Action.JUMP, width * 0.89f, height * 0.67f, s * factor, "JUMP", canvas)
        place(Action.SNEAK, width * 0.64f, height * 0.86f, s * factor * 0.82f, "SNEAK", canvas)
        place(Action.USE, width * 0.91f, height * 0.86f, s * factor * 0.82f, "USE", canvas)
        place(Action.INVENTORY, width * 0.52f, height * 0.16f, s * factor * 0.72f, "INV", canvas)
    }

    private fun place(action: Action, x: Float, y: Float, r: Float, label: String, canvas: Canvas) {
        centers[action] = x to y
        actionRadius[action] = r
        drawCircle(canvas, x, y, r, label, activePointers.values.contains(action))
    }

    private fun drawCircle(canvas: Canvas, x: Float, y: Float, r: Float, label: String, active: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (active) 0xCCFFFFFF.toInt() else (0xFF252B34.toInt() and 0x00FFFFFF or ((opacity * 180).toInt() shl 24))
        canvas.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (r * 0.07f).coerceAtLeast(2f)
        paint.color = 0xBFFFFFFF.toInt()
        canvas.drawCircle(x, y, r, paint)
        if (label.isNotEmpty()) {
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = (r * 0.28f).coerceAtLeast(9f)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawText(label, x, y + paint.textSize * 0.35f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val x = event.getX(index); val y = event.getY(index)
                if (distance(x, y, baseX, baseY) <= stickRadius * 1.7f && stickPointer == -1) {
                    stickPointer = id; updateStick(x, y); return true
                }
                val hit = centers.entries.firstOrNull { (action, center) ->
                    val r = actionRadius[action] ?: 0f
                    distance(x, y, center.first, center.second) <= r * 1.25f
                }?.key
                if (hit != null) {
                    activePointers[id] = hit
                    val previous = actionPressCounts[hit] ?: 0
                    actionPressCounts[hit] = previous + 1
                    if (previous == 0) inputSink?.onInput(hit, true, 0f, 0f)
                    invalidate(); return true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id == stickPointer) updateStick(event.getX(i), event.getY(i))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    if (stickPointer != -1) inputSink?.onInput(Action.MOVE, false, 0f, 0f)
                    actionPressCounts.keys.toList().forEach { inputSink?.onInput(it, false, 0f, 0f) }
                    actionPressCounts.clear()
                    activePointers.clear()
                    stickPointer = -1; stickX = baseX; stickY = baseY
                    invalidate(); return true
                }
                val id = event.getPointerId(event.actionIndex)
                if (id == stickPointer) {
                    inputSink?.onInput(Action.MOVE, false, 0f, 0f)
                    stickPointer = -1
                    stickX = baseX; stickY = baseY
                }
                val action = activePointers.remove(id)
                if (action != null) {
                    val remaining = ((actionPressCounts[action] ?: 1) - 1).coerceAtLeast(0)
                    if (remaining == 0) {
                        actionPressCounts.remove(action)
                        inputSink?.onInput(action, false, 0f, 0f)
                    } else actionPressCounts[action] = remaining
                }
                invalidate(); return true
            }
        }
        return true
    }

    private fun updateStick(x: Float, y: Float) {
        val dx = x - baseX; val dy = y - baseY
        val length = hypot(dx, dy).coerceAtLeast(1f)
        val clamped = length.coerceAtMost(stickRadius)
        stickX = baseX + dx / length * clamped
        stickY = baseY + dy / length * clamped
        val nx = ((stickX - baseX) / stickRadius).coerceIn(-1f, 1f)
        val ny = ((stickY - baseY) / stickRadius).coerceIn(-1f, 1f)
        inputSink?.onInput(Action.MOVE, true, nx, ny)
        invalidate()
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float) = hypot(x1 - x2, y1 - y2)
}
