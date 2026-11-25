package com.imzyy.xllauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/** Single flat palette: graphite neutrals plus one mint accent. No gradients. */
object Ui {
    val BG = 0xFF0B0C0E.toInt()
    val SURFACE = 0xFF141619.toInt()
    val SURFACE2 = 0xFF1B1E22.toInt()
    val LINE = 0xFF272B31.toInt()
    val TEXT = 0xFFEDEEF0.toInt()
    val MUTED = 0xFF98A0AB.toInt()
    val DIM = 0xFF5F6670.toInt()
    val ACCENT = 0xFF6EE7A0.toInt()
    val ACCENT_INK = 0xFF05210F.toInt()
    val WARN = 0xFFF2B35A.toInt()
    val DANGER = 0xFFF07178.toInt()
    val RIPPLE = 0x26FFFFFF
}

/** Rounded progress bar that eases to its target value. */
class Meter(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.ACCENT }
    private val rect = RectF()
    private var shown = 0f
    private var animator: ValueAnimator? = null

    var warn: Boolean = false
        set(value) {
            field = value
            fill.color = if (value) Ui.WARN else Ui.ACCENT
            invalidate()
        }

    fun setProgress(target: Float, animate: Boolean = true) {
        val t = target.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate) {
            shown = t
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(shown, t).apply {
            duration = 650
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, r, r, track)
        val w = width * shown
        if (w > 0f) {
            rect.set(0f, 0f, maxOf(w, h), h)
            canvas.drawRoundRect(rect, r, r, fill)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

/** Status dot. With pulse enabled a soft halo breathes outward. */
class PulseDot(context: Context, dotColor: Int, private val pulse: Boolean) : View(context) {
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
    private var phase = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pulse && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        if (pulse) {
            halo.alpha = ((1f - phase) * 90f).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * (0.45f + 0.55f * phase), halo)
        }
        canvas.drawCircle(cx, cy, r * 0.45f, core)
    }
}

/** Card background with a faint voxel-style dot grid. */
class DotGridDrawable(
    private val fillColor: Int,
    private val strokeColor: Int,
    private val dotColor: Int,
    private val radius: Float,
    private val strokeWidth: Float,
    private val spacing: Float,
    private val dotRadius: Float
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        this.strokeWidth = this@DotGridDrawable.strokeWidth
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
    private val clip = Path()
    private val box = RectF()

    override fun draw(canvas: Canvas) {
        box.set(bounds)
        box.inset(strokeWidth / 2f, strokeWidth / 2f)
        canvas.drawRoundRect(box, radius, radius, fillPaint)
        canvas.save()
        clip.reset()
        clip.addRoundRect(box, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        var y = box.top + spacing / 2f
        var row = 0
        while (y < box.bottom) {
            var x = box.left + spacing / 2f + if (row % 2 == 0) 0f else spacing / 2f
            while (x < box.right) {
                canvas.drawCircle(x, y, dotRadius, dotPaint)
                x += spacing
            }
            y += spacing
            row++
        }
        canvas.restore()
        canvas.drawRoundRect(box, radius, radius, strokePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
