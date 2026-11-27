package com.imzz.galaxyassistant.mascot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * SKYLEN — kucing putih virtual. Digambar penuh dengan Canvas (tanpa aset gambar),
 * jadi ringan dan tajam di semua layar.
 *
 * Bisa: berkedip, bernapas, goyang ekor, mata mengikuti jari, bicara (mulut
 * sinkron dengan TTS), balon ucapan, 9 ekspresi, 6 aksi (lompat, melambai,
 * stretching, miring kepala, jalan, joget) dan 8 gaya/outfit.
 */
class CatMascotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mood { IDLE, HAPPY, LOVE, THINKING, ALERT, SURPRISED, SLEEPY, SAD, ANGRY }

    enum class Outfit(val label: String) {
        NONE("Polos"),
        BOW("Pita"),
        WIZARD("Topi Penyihir"),
        GLASSES("Kacamata Cyber"),
        HEADPHONES("Headphone"),
        SCARF("Syal"),
        CROWN("Mahkota"),
        HELMET("Helm Astronot")
    }

    enum class Action(val durationMs: Long) {
        HOP(750), WAVE(1900), STRETCH(1700), HEAD_TILT(1500), WALK(4300), DANCE(3400)
    }

    /** Dipanggil saat kepala diusap. Parameter = total usapan. */
    var onPet: ((Int) -> Unit)? = null

    /** Dipanggil saat badan diketuk. */
    var onBodyTap: (() -> Unit)? = null

    var outfit: Outfit = Outfit.NONE
        private set

    val isSpeaking: Boolean get() = speaking

    // ---------- state ----------
    private var baseMood = Mood.IDLE
    private var tempMood: Mood? = null
    private var tempUntil = 0L
    private var autoSleep = false
    private var speaking = false
    private var lastInteraction = SystemClock.uptimeMillis()

    private var action: Action? = null
    private var actionStart = 0L
    private var tiltSign = 1f

    private var nextBlinkAt = SystemClock.uptimeMillis() + 2000L
    private var blinkStart = -1L
    private var nextIdleAt = SystemClock.uptimeMillis() + 6500L
    private var nextGlanceAt = 0L

    private var lookX = 0f
    private var lookY = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var touching = false
    private var downAt = 0L
    private var moved = false
    private var petCount = 0
    private var lastPetAt = 0L

    private var bubbleText = ""
    private var bubbleStart = 0L
    private var bubbleUntil = 0L
    private var fullLayout: StaticLayout? = null
    private var partialLayout: StaticLayout? = null
    private var lastShown = -1
    private var layoutWidth = 0

    // ---------- pose (dihitung tiap frame) ----------
    private var pDx = 0f
    private var pDy = 0f
    private var pSx = 1f
    private var pSy = 1f
    private var pHeadRot = 0f
    private var pBodyRot = 0f
    private var pTailAmp = 10f
    private var pTailSpeed = 1.8f
    private var pLeg = 0f
    private var pWave = false
    private var pEarL = 0f
    private var pEarR = 0f
    private var pBlink = 1f

    // ---------- geometri ----------
    private var scaleU = 0f
    private var originX = 0f
    private var originY = 0f
    private var glow: RadialGradient? = null

    // ---------- warna ----------
    private val outlineColor = Color.rgb(176, 188, 222)
    private val pinkColor = Color.rgb(255, 143, 176)
    private val darkColor = Color.rgb(35, 43, 74)
    private val irisColor = Color.rgb(124, 156, 255)
    private val mouthColor = Color.rgb(138, 43, 79)

    // ---------- paint & objek yang dipakai ulang ----------
    private val fur = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val furBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(0f, 108f, 0f, 191f, Color.WHITE, Color.rgb(224, 230, 247), Shader.TileMode.CLAMP)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineColor
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fxText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.CENTER
    }
    private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 34, 56)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13.5f, resources.displayMetrics)
    }
    private val rect = RectF()
    private val rect2 = RectF()
    private val path = Path()
    private val path2 = Path()

    // =====================================================================
    // API publik
    // =====================================================================

    /** [durationMs] > 0 = ekspresi sementara; 0 = ekspresi dasar (mis. sesuai status Autopilot). */
    fun setMood(mood: Mood, durationMs: Long = 0L) {
        wake()
        if (durationMs > 0L) {
            tempMood = mood
            tempUntil = SystemClock.uptimeMillis() + durationMs
        } else {
            baseMood = mood
            tempMood = null
        }
        postInvalidateOnAnimation()
    }

    fun setSpeaking(on: Boolean) {
        speaking = on
        if (on) wake()
        postInvalidateOnAnimation()
    }

    fun setOutfit(value: Outfit) {
        outfit = value
        postInvalidateOnAnimation()
    }

    fun cycleOutfit(): Outfit {
        val all = Outfit.values()
        setOutfit(all[(outfit.ordinal + 1) % all.size])
        playAction(Action.HOP)
        return outfit
    }

    fun playAction(value: Action) {
        wake()
        startAction(value)
    }

    private fun startAction(value: Action) {
        action = value
        actionStart = SystemClock.uptimeMillis()
        tiltSign = if (Random.nextBoolean()) 1f else -1f
        postInvalidateOnAnimation()
    }

    fun playRandomAction(): Action {
        val pick = Action.values().filter { it != action }.random()
        playAction(pick)
        return pick
    }

    fun say(text: String, durationMs: Long = 0L) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        bubbleText = clean
        bubbleStart = now
        bubbleUntil = now + if (durationMs > 0L) durationMs else (2800L + clean.length * 65L).coerceAtMost(15000L)
        fullLayout = null
        partialLayout = null
        lastShown = -1
        postInvalidateOnAnimation()
    }

    // =====================================================================
    // Lifecycle & ukuran
    // =====================================================================

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val areaH = max(1f, h - dp(112f))
        scaleU = min(w.toFloat(), areaH) / 200f
        originX = (w - 200f * scaleU) / 2f
        originY = h - 200f * scaleU
        glow = RadialGradient(
            w / 2f, originY + 115f * scaleU, max(1f, 125f * scaleU),
            Color.argb(255, 141, 121, 255), Color.argb(0, 141, 121, 255), Shader.TileMode.CLAMP
        )
        layoutWidth = 0
        fullLayout = null
    }

    // =====================================================================
    // Sentuhan
    // =====================================================================

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (scaleU <= 0f) return false
        val ux = (e.x - originX) / scaleU
        val uy = (e.y - originY) / scaleU
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                moved = false
                downAt = now
                wake()
                look(ux, uy)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                look(ux, uy)
                moved = true
                if (isOnHead(ux, uy) && now - lastPetAt > 900L && now - downAt > 150L) pet(now)
                return true
            }
            MotionEvent.ACTION_UP -> {
                touching = false
                if (!moved && now - downAt < 300L) {
                    if (isOnHead(ux, uy)) pet(now)
                    else if (uy > 105f) {
                        playAction(Action.HOP)
                        onBodyTap?.invoke()
                    }
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touching = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun isOnHead(ux: Float, uy: Float): Boolean {
        val dx = ux - 100f
        val dy = uy - 88f
        return dx * dx + dy * dy < 62f * 62f
    }

    private fun look(ux: Float, uy: Float) {
        targetX = ((ux - 100f) / 70f).coerceIn(-1f, 1f)
        targetY = ((uy - 92f) / 70f).coerceIn(-1f, 1f)
    }

    private fun pet(now: Long) {
        lastPetAt = now
        petCount++
        setMood(Mood.LOVE, 2600L)
        onPet?.invoke(petCount)
    }

    private fun wake() {
        lastInteraction = SystemClock.uptimeMillis()
        autoSleep = false
    }

    // =====================================================================
    // Update state per frame
    // =====================================================================

    private fun effectiveMood(now: Long): Mood {
        val temp = tempMood
        if (temp != null && now < tempUntil) return temp
        if (temp != null) tempMood = null
        if (autoSleep && baseMood == Mood.IDLE) return Mood.SLEEPY
        return baseMood
    }

    private fun updateState(now: Long, mood: Mood) {
        // tidur otomatis kalau lama tidak disentuh
        if (!autoSleep && baseMood == Mood.IDLE && tempMood == null && !speaking && now - lastInteraction > 120_000L) {
            autoSleep = true
        }

        // kedip
        if (blinkStart < 0L && now >= nextBlinkAt) blinkStart = now
        if (blinkStart >= 0L) {
            val q = (now - blinkStart) / 170f
            if (q >= 1f) {
                blinkStart = -1L
                nextBlinkAt = now + Random.nextLong(2200L, 5200L)
                pBlink = 1f
            } else {
                pBlink = 1f - sin(PIF * q)
            }
        } else pBlink = 1f

        // aksi selesai?
        val a = action
        if (a != null && now - actionStart >= a.durationMs) action = null

        // aksi spontan
        if (action == null && mood == Mood.IDLE && !speaking && !touching && now >= nextIdleAt) {
            startAction(listOf(Action.WAVE, Action.STRETCH, Action.HEAD_TILT, Action.HOP, Action.WALK).random())
            nextIdleAt = now + Random.nextLong(7000L, 14000L)
        }

        // arah pandang
        if (!touching && now >= nextGlanceAt) {
            targetX = Random.nextFloat() * 1.6f - 0.8f
            targetY = Random.nextFloat() * 1.0f - 0.5f
            nextGlanceAt = now + Random.nextLong(1800L, 4500L)
        }
        if (mood == Mood.THINKING && !touching) {
            targetX = 0.75f
            targetY = -0.8f
        }
        if (mood == Mood.SLEEPY) {
            targetX = 0f
            targetY = 0.4f
        }
        lookX += (targetX - lookX) * 0.12f
        lookY += (targetY - lookY) * 0.12f
    }

    private fun computePose(now: Long, t: Float, mood: Mood) {
        pDx = 0f; pDy = 0f; pSx = 1f; pSy = 1f
        pHeadRot = 0f; pBodyRot = 0f
        pTailAmp = 10f; pTailSpeed = 1.8f
        pLeg = 0f; pWave = false
        pEarL = 0f; pEarR = 0f

        when (mood) {
            Mood.ANGRY -> { pEarL = -16f; pEarR = 16f; pTailAmp = 26f; pTailSpeed = 9f }
            Mood.SAD -> { pEarL = -14f; pEarR = 14f; pTailAmp = 3f; pTailSpeed = 1f; pHeadRot = -3f }
            Mood.SLEEPY -> { pTailAmp = 3f; pTailSpeed = 0.9f; pHeadRot = 5f + 3f * sin(t * 0.8f) }
            Mood.HAPPY, Mood.LOVE -> { pTailAmp = 22f; pTailSpeed = 6f }
            Mood.ALERT -> { pTailAmp = 6f; pTailSpeed = 3f }
            Mood.THINKING -> { pHeadRot = 8f }
            Mood.SURPRISED -> { pDy = -3f }
            Mood.IDLE -> Unit
        }

        // kuping gerak-gerak sesekali
        pEarL += max(0f, sin(t * 1.7f + 1.3f) - 0.9f) * 120f
        pEarR -= max(0f, sin(t * 1.3f + 0.2f) - 0.92f) * 100f

        if (speaking) {
            pHeadRot += sin(t * 8.5f) * 2.5f
            pDy += -abs(sin(t * 8.5f)) * 1.5f
        }

        val a = action
        if (a != null) {
            val p = ((now - actionStart) / a.durationMs.toFloat()).coerceIn(0f, 1f)
            val q = sin(PIF * p)
            when (a) {
                Action.HOP -> {
                    pDy += -26f * q
                    val k = 1f - q
                    pSx = 1f + 0.08f * k
                    pSy = 1f - 0.08f * k
                    pTailAmp = 22f; pTailSpeed = 8f
                }
                Action.WAVE -> {
                    pWave = true
                    pHeadRot += 7f * q
                    pTailAmp = 18f; pTailSpeed = 5f
                }
                Action.STRETCH -> {
                    pSx = 1f + 0.14f * q
                    pSy = 1f - 0.12f * q
                    pHeadRot += -5f * q
                    pTailAmp = 4f
                }
                Action.HEAD_TILT -> {
                    pHeadRot += tiltSign * 15f * q
                }
                Action.WALK -> {
                    val w = p * 2f * PIF * 6f
                    pDx = 40f * sin(2f * PIF * p)
                    pDy += -3f * abs(sin(w))
                    pLeg = w
                    pHeadRot += 3f * sin(w)
                    pTailAmp = 16f; pTailSpeed = 5f
                }
                Action.DANCE -> {
                    pDy += -9f * abs(sin(t * 9f))
                    pBodyRot = 7f * sin(t * 4.5f)
                    pHeadRot += -9f * sin(t * 4.5f)
                    pTailAmp = 26f; pTailSpeed = 9f
                    pWave = sin(t * 4.5f) > 0f
                }
            }
        }
    }

    // =====================================================================
    // Gambar
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scaleU <= 0f) return
        val now = SystemClock.uptimeMillis()
        val t = now / 1000f
        val mood = effectiveMood(now)
        updateState(now, mood)
        computePose(now, t, mood)

        // aura
        glow?.let {
            glowPaint.shader = it
            glowPaint.alpha = (60 + 25 * sin(t * 1.5f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(width / 2f, originY + 115f * scaleU, 125f * scaleU, glowPaint)
        }

        canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(scaleU, scaleU)
        drawCat(canvas, t, mood)
        canvas.restore()

        drawBubble(canvas, now)

        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawCat(c: Canvas, t: Float, mood: Mood) {
        // bayangan
        val k = (1f + pDy / 160f).coerceIn(0.5f, 1.1f)
        rect.set(100f + pDx - 54f * k, 185f, 100f + pDx + 54f * k, 197f)
        c.drawOval(rect, fillColor(Color.argb(70, 141, 121, 255)))

        c.save()
        c.translate(pDx, pDy)
        c.rotate(pBodyRot, 100f, 190f)
        c.scale(pSx, pSy, 100f, 190f)

        drawTail(c, t)

        // paha belakang
        rect.set(38f, 140f, 84f, 191f); c.drawOval(rect, furBody); c.drawOval(rect, line)
        rect.set(116f, 140f, 162f, 191f); c.drawOval(rect, furBody); c.drawOval(rect, line)

        // badan (napas)
        val breathe = sin(t * 2.3f)
        rect.set(46f, 108f - breathe * 1.8f, 154f, 191f)
        c.drawOval(rect, furBody); c.drawOval(rect, line)

        // kaki depan
        val legL = -max(0f, sin(pLeg)) * 7f
        val legR = -max(0f, -sin(pLeg)) * 7f
        drawLeg(c, 78f, legL)
        drawLeg(c, 104f, legR)

        if (outfit == Outfit.SCARF) drawScarf(c, t)

        // kepala
        c.save()
        c.rotate(pHeadRot, 100f, 130f)
        drawHead(c, t, mood)
        c.restore()

        if (pWave) drawWaveArm(c, t)
        c.restore()

        // efek di luar transformasi badan
        c.save()
        c.translate(pDx, pDy)
        drawEffects(c, t, mood)
        c.restore()
    }

    private fun drawTail(c: Canvas, t: Float) {
        val sway = sin(t * pTailSpeed) * pTailAmp
        path.reset()
        path.moveTo(138f, 172f)
        path.cubicTo(184f, 178f, 188f + sway * 0.4f, 138f, 170f + sway, 96f)
        c.drawPath(path, strokeColor(outlineColor, 17f))
        c.drawPath(path, strokeColor(Color.WHITE, 13.5f))
    }

    private fun drawLeg(c: Canvas, x: Float, off: Float) {
        rect.set(x, 150f + off, x + 18f, 188f)
        c.drawRoundRect(rect, 9f, 9f, fur)
        c.drawRoundRect(rect, 9f, 9f, line)
        rect.set(x - 4f, 178f + off, x + 22f, 192f + off * 0.2f)
        c.drawOval(rect, fur)
        c.drawOval(rect, line)
        val lw = strokeColor(outlineColor, 1.6f)
        c.drawLine(x + 6f, 185f + off, x + 6f, 190f + off * 0.2f, lw)
        c.drawLine(x + 12f, 185f + off, x + 12f, 190f + off * 0.2f, lw)
    }

    private fun drawWaveArm(c: Canvas, t: Float) {
        val tipX = 144f
        val tipY = 110f + 9f * sin(t * 17f)
        path.reset()
        path.moveTo(118f, 150f)
        path.quadTo(138f, 142f, tipX, tipY)
        c.drawPath(path, strokeColor(outlineColor, 22f))
        c.drawPath(path, strokeColor(Color.WHITE, 18.5f))
        c.drawCircle(tipX, tipY - 1f, 4.5f, fillColor(Color.argb(170, 255, 143, 176)))
    }

    private fun drawScarf(c: Canvas, t: Float) {
        val sc = Color.rgb(255, 107, 138)
        rect.set(62f, 126f, 138f, 146f)
        c.drawRoundRect(rect, 10f, 10f, fillColor(sc))
        c.drawRoundRect(rect, 10f, 10f, strokeColor(Color.rgb(200, 70, 100), 2f))
        val stripe = strokeColor(Color.argb(200, 255, 255, 255), 3f)
        c.drawLine(80f, 128f, 78f, 144f, stripe)
        c.drawLine(98f, 128f, 96f, 144f, stripe)
        c.drawLine(116f, 128f, 114f, 144f, stripe)
        // ujung syal yang berayun
        c.save()
        c.rotate(sin(t * 2.5f) * 4f, 118f, 140f)
        rect.set(110f, 138f, 128f, 172f)
        c.drawRoundRect(rect, 7f, 7f, fillColor(sc))
        c.drawRoundRect(rect, 7f, 7f, strokeColor(Color.rgb(200, 70, 100), 2f))
        c.drawLine(113f, 160f, 125f, 160f, strokeColor(Color.argb(200, 255, 255, 255), 3f))
        c.restore()
    }

    // ---------------------------------------------------------------- kepala

    private fun drawHead(c: Canvas, t: Float, mood: Mood) {
        drawEar(c, true)
        drawEar(c, false)

        // kepala
        rect.set(50f, 48f, 150f, 136f)
        c.drawOval(rect, fur)
        c.drawOval(rect, line)

        // pipi merona
        val blush = when (mood) {
            Mood.LOVE -> 170
            Mood.HAPPY -> 140
            Mood.SAD, Mood.ANGRY -> 40
            else -> 90
        }
        rect.set(55f, 104.5f, 73f, 115.5f); c.drawOval(rect, fillColor(Color.argb(blush, 255, 143, 176)))
        rect.set(127f, 104.5f, 145f, 115.5f); c.drawOval(rect, fillColor(Color.argb(blush, 255, 143, 176)))

        drawEyes(c, mood, t)
        drawNoseAndMouth(c, mood, t)
        drawWhiskers(c, t)
        drawHeadwear(c, t)
    }

    private fun drawEar(c: Canvas, left: Boolean) {
        fun mx(x: Float) = if (left) x else 200f - x
        c.save()
        c.rotate(if (left) pEarL else pEarR, mx(70f), 70f)
        path.reset()
        path.moveTo(mx(56f), 80f)
        path.lineTo(mx(52f), 24f)
        path.lineTo(mx(98f), 50f)
        path.close()
        c.drawPath(path, fur)
        c.drawPath(path, line)
        path.reset()
        path.moveTo(mx(64f), 72f)
        path.lineTo(mx(61f), 40f)
        path.lineTo(mx(88f), 56f)
        path.close()
        c.drawPath(path, fillColor(Color.rgb(255, 182, 204)))
        c.restore()
    }

    private fun drawEyes(c: Canvas, mood: Mood, t: Float) {
        for (side in 0..1) {
            val cx = if (side == 0) 77f else 123f
            val cy = 96f
            when (mood) {
                Mood.HAPPY -> {
                    path.reset()
                    path.moveTo(cx - 10f, cy + 4f)
                    path.quadTo(cx, cy - 11f, cx + 10f, cy + 4f)
                    c.drawPath(path, strokeColor(darkColor, 3.6f))
                }
                Mood.SLEEPY -> {
                    path.reset()
                    path.moveTo(cx - 10f, cy)
                    path.quadTo(cx, cy + 8f, cx + 10f, cy)
                    c.drawPath(path, strokeColor(darkColor, 3.2f))
                }
                Mood.LOVE -> {
                    val pulse = 1f + 0.12f * sin(t * 8f)
                    drawHeart(c, cx, cy + 1f, 8.5f * pulse, fillColor(Color.rgb(255, 92, 150)))
                }
                else -> drawOpenEye(c, cx, cy, mood)
            }
        }
        // alis
        when (mood) {
            Mood.ANGRY -> {
                val p = strokeColor(darkColor, 3.4f)
                c.drawLine(62f, 75f, 90f, 84f, p)
                c.drawLine(138f, 75f, 110f, 84f, p)
            }
            Mood.SAD -> {
                val p = strokeColor(darkColor, 3f)
                c.drawLine(64f, 84f, 90f, 76f, p)
                c.drawLine(136f, 84f, 110f, 76f, p)
            }
            else -> Unit
        }
    }

    private fun drawOpenEye(c: Canvas, cx: Float, cy: Float, mood: Mood) {
        val big = if (mood == Mood.SURPRISED) 1.2f else 1f
        val open = when (mood) {
            Mood.SAD -> 0.8f * pBlink
            Mood.ANGRY -> 0.72f * pBlink
            else -> pBlink
        }
        val rx = 11f * big
        val ry = 13.5f * big
        c.save()
        c.scale(1f, max(open, 0.06f), cx, cy)
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(rect, fillColor(irisColor))
        c.drawOval(rect, strokeColor(darkColor, 2f))

        val pw = when (mood) { Mood.ALERT -> 3.2f; Mood.SURPRISED -> 7f; else -> 5.6f }
        val ph = when (mood) { Mood.SURPRISED -> 10f; else -> 8.8f }
        val px = cx + lookX * 4.5f
        val py = cy + lookY * 4.5f + (if (mood == Mood.SAD) 2f else 0f)
        rect.set(px - pw, py - ph, px + pw, py + ph)
        c.drawOval(rect, fillColor(darkColor))
        c.drawCircle(px - 3f, py - 4.5f, 3f, fillColor(Color.WHITE))
        c.drawCircle(px + 3f, py + 3.5f, 1.5f, fillColor(Color.argb(210, 255, 255, 255)))
        c.restore()
    }

    private fun drawNoseAndMouth(c: Canvas, mood: Mood, t: Float) {
        // hidung
        path.reset()
        path.moveTo(94.5f, 105f)
        path.lineTo(105.5f, 105f)
        path.lineTo(100f, 111f)
        path.close()
        c.drawPath(path, fillColor(Color.rgb(255, 143, 176)))
        c.drawPath(path, strokeColor(Color.rgb(230, 110, 145), 1.2f))

        val mouthLine = strokeColor(mouthColor, 2.1f)
        val open = when {
            speaking -> 3f + 5.5f * abs(sin(t * 16f)) * (0.6f + 0.4f * sin(t * 5f))
            mood == Mood.SURPRISED -> 6f
            else -> 0f
        }
        when {
            open > 0f -> {
                c.drawLine(100f, 111f, 100f, 113f, mouthLine)
                val h = open * 1.5f + 2f
                rect.set(93.5f, 113f, 106.5f, 113f + h)
                c.drawOval(rect, fillColor(mouthColor))
                if (h > 8f) {
                    rect2.set(96f, 113f + h - 5f, 104f, 113f + h)
                    c.drawOval(rect2, fillColor(Color.rgb(255, 143, 176)))
                }
            }
            mood == Mood.HAPPY || mood == Mood.LOVE -> {
                c.drawLine(100f, 111f, 100f, 113f, mouthLine)
                path.reset()
                path.moveTo(90f, 113f)
                path.quadTo(100f, 129f, 110f, 113f)
                path.close()
                c.drawPath(path, fillColor(mouthColor))
                rect2.set(95f, 119f, 105f, 124.5f)
                c.drawOval(rect2, fillColor(Color.rgb(255, 143, 176)))
            }
            mood == Mood.SAD -> {
                c.drawLine(100f, 111f, 100f, 114f, mouthLine)
                path.reset()
                path.moveTo(91f, 121f)
                path.quadTo(100f, 113f, 109f, 121f)
                c.drawPath(path, mouthLine)
            }
            else -> {
                c.drawLine(100f, 111f, 100f, 114f, mouthLine)
                path.reset()
                path.moveTo(100f, 114f)
                path.quadTo(94.5f, 121f, 88f, 114.5f)
                path.moveTo(100f, 114f)
                path.quadTo(105.5f, 121f, 112f, 114.5f)
                c.drawPath(path, mouthLine)
            }
        }
    }

    private fun drawWhiskers(c: Canvas, t: Float) {
        val w = strokeColor(outlineColor, 1.8f)
        val sw = sin(t * 1.7f) * 1.5f
        c.drawLine(70f, 108f, 32f, 101f + sw, w)
        c.drawLine(70f, 112f, 30f, 113f, w)
        c.drawLine(70f, 116f, 34f, 126f - sw, w)
        c.drawLine(130f, 108f, 168f, 101f + sw, w)
        c.drawLine(130f, 112f, 170f, 113f, w)
        c.drawLine(130f, 116f, 166f, 126f - sw, w)
    }

    // ---------------------------------------------------------------- outfit

    private fun drawHeadwear(c: Canvas, t: Float) {
        when (outfit) {
            Outfit.NONE, Outfit.SCARF -> Unit

            Outfit.BOW -> {
                val pink = Color.rgb(255, 111, 163)
                val edge = Color.rgb(214, 70, 120)
                path.reset()
                path.moveTo(100f, 136f); path.lineTo(76f, 124f); path.lineTo(76f, 148f); path.close()
                path.moveTo(100f, 136f); path.lineTo(124f, 124f); path.lineTo(124f, 148f); path.close()
                c.drawPath(path, fillColor(pink))
                c.drawPath(path, strokeColor(edge, 2f))
                c.drawCircle(100f, 136f, 6f, fillColor(Color.rgb(255, 140, 185)))
                c.drawCircle(100f, 136f, 6f, strokeColor(edge, 2f))
            }

            Outfit.WIZARD -> {
                val body = Color.rgb(91, 75, 196)
                val edge = Color.rgb(60, 47, 150)
                path.reset()
                path.moveTo(70f, 60f); path.lineTo(130f, 60f); path.lineTo(106f, 4f); path.close()
                c.drawPath(path, fillColor(body))
                c.drawPath(path, strokeColor(edge, 2f))
                rect.set(60f, 54f, 140f, 68f)
                c.drawOval(rect, fillColor(Color.rgb(109, 92, 224)))
                c.drawOval(rect, strokeColor(edge, 2f))
                val gold = Color.rgb(255, 214, 90)
                val tw = 0.6f + 0.4f * sin(t * 4f)
                drawSparkle(c, 98f, 40f, 4.5f * tw, fillColor(gold))
                drawSparkle(c, 110f, 24f, 3.5f, fillColor(gold))
                drawSparkle(c, 90f, 52f, 2.8f, fillColor(gold))
            }

            Outfit.CROWN -> {
                val gold = Color.rgb(255, 201, 60)
                val edge = Color.rgb(215, 154, 16)
                path.reset()
                path.moveTo(74f, 62f); path.lineTo(74f, 32f); path.lineTo(88f, 46f)
                path.lineTo(100f, 24f); path.lineTo(112f, 46f); path.lineTo(126f, 32f)
                path.lineTo(126f, 62f); path.close()
                c.drawPath(path, fillColor(gold))
                c.drawPath(path, strokeColor(edge, 2f))
                c.drawCircle(100f, 52f, 3.6f, fillColor(Color.rgb(255, 92, 150)))
                c.drawCircle(86f, 55f, 2.6f, fillColor(Color.rgb(90, 200, 255)))
                c.drawCircle(114f, 55f, 2.6f, fillColor(Color.rgb(90, 200, 255)))
            }

            Outfit.HEADPHONES -> {
                val band = strokeColor(Color.rgb(47, 53, 86), 7f)
                rect.set(46f, 38f, 154f, 140f)
                c.drawArc(rect, 190f, 160f, false, band)
                val cup = Color.rgb(141, 121, 255)
                val edge = Color.rgb(80, 66, 190)
                rect.set(38f, 82f, 58f, 114f)
                c.drawRoundRect(rect, 8f, 8f, fillColor(cup))
                c.drawRoundRect(rect, 8f, 8f, strokeColor(edge, 2f))
                rect.set(142f, 82f, 162f, 114f)
                c.drawRoundRect(rect, 8f, 8f, fillColor(cup))
                c.drawRoundRect(rect, 8f, 8f, strokeColor(edge, 2f))
            }

            Outfit.GLASSES -> {
                val rim = Color.rgb(141, 121, 255)
                for (cx in floatArrayOf(77f, 123f)) {
                    c.drawCircle(cx, 96f, 17f, fillColor(Color.argb(55, 120, 200, 255)))
                    c.drawCircle(cx, 96f, 17f, strokeColor(rim, 3.5f))
                    rect.set(cx - 12f, 83f, cx + 12f, 109f)
                    c.drawArc(rect, 200f, 45f, false, strokeColor(Color.argb(190, 255, 255, 255), 2.2f))
                }
                val rp = strokeColor(rim, 3f)
                c.drawLine(94f, 94f, 106f, 94f, rp)
                c.drawLine(60f, 92f, 51f, 89f, rp)
                c.drawLine(140f, 92f, 149f, 89f, rp)
            }

            Outfit.HELMET -> {
                rect.set(62f, 128f, 138f, 144f)
                c.drawRoundRect(rect, 7f, 7f, fillColor(Color.rgb(205, 214, 235)))
                c.drawRoundRect(rect, 7f, 7f, strokeColor(Color.rgb(150, 162, 195), 2f))
                c.drawCircle(100f, 90f, 66f, fillColor(Color.argb(38, 150, 200, 255)))
                c.drawCircle(100f, 90f, 66f, strokeColor(Color.argb(210, 210, 230, 255), 3f))
                rect.set(48f, 38f, 152f, 142f)
                c.drawArc(rect, 205f, 38f, false, strokeColor(Color.argb(200, 255, 255, 255), 4f))
            }
        }
    }

    // ---------------------------------------------------------------- efek

    private fun drawEffects(c: Canvas, t: Float, mood: Mood) {
        when (mood) {
            Mood.LOVE -> {
                for (i in 0..3) {
                    val ph = (t * 0.6f + i * 0.25f) % 1f
                    val x = 100f + (i - 1.5f) * 26f + sin((t + i) * 3f) * 6f
                    val y = 64f - ph * 64f
                    drawHeart(c, x, y, 6f, fillColor(Color.argb(((1f - ph) * 255).toInt(), 255, 92, 150)))
                }
            }
            Mood.HAPPY -> {
                val gold = Color.rgb(255, 232, 140)
                drawSparkle(c, 40f, 56f, 6f * (0.6f + 0.4f * sin(t * 5f)), fillColor(gold))
                drawSparkle(c, 162f, 66f, 5f * (0.6f + 0.4f * sin(t * 6f + 1f)), fillColor(gold))
                drawSparkle(c, 150f, 30f, 4f * (0.6f + 0.4f * sin(t * 4f + 2f)), fillColor(Color.WHITE))
            }
            Mood.SLEEPY -> {
                fxText.color = Color.argb(230, 190, 200, 255)
                for (i in 0..2) {
                    val ph = (t * 0.35f + i / 3f) % 1f
                    fxText.textSize = 10f + i * 4f
                    fxText.alpha = ((1f - ph) * 230).toInt().coerceIn(0, 255)
                    c.drawText("z", 138f + ph * 26f + i * 4f, 60f - ph * 40f, fxText)
                }
            }
            Mood.THINKING -> {
                val fill = fillColor(Color.argb(235, 250, 251, 255))
                c.drawCircle(146f, 50f, 3f, fill)
                c.drawCircle(154f, 40f, 4.5f, fill)
                rect.set(150f, 6f, 196f, 34f)
                c.drawRoundRect(rect, 14f, 14f, fill)
                for (i in 0..2) {
                    val a = (0.35f + 0.65f * max(0f, sin(t * 5f - i * 1.1f)))
                    c.drawCircle(162f + i * 12f, 20f, 3.2f, fillColor(Color.argb((a * 255).toInt(), 90, 80, 160)))
                }
            }
            Mood.ALERT -> {
                val p = (t * 0.9f) % 1f
                c.drawCircle(100f, 94f, 62f + 26f * p, strokeColor(Color.argb(((1f - p) * 150).toInt(), 141, 121, 255), 2.5f))
            }
            Mood.SURPRISED -> {
                fxText.color = Color.rgb(255, 214, 90)
                fxText.alpha = 255
                fxText.textSize = 26f
                c.drawText("!", 100f, 30f + sin(t * 14f) * 1.5f, fxText)
            }
            Mood.SAD -> {
                val ph = (t * 0.7f) % 1f
                c.drawOval(
                    rect.apply { set(66f, 108f + ph * 18f, 72f, 118f + ph * 18f) },
                    fillColor(Color.argb(((1f - ph * 0.6f) * 220).toInt(), 120, 190, 255))
                )
            }
            Mood.ANGRY -> {
                val p = strokeColor(Color.rgb(255, 70, 70), 3f)
                val s = 1f + 0.15f * sin(t * 12f)
                c.drawLine(146f - 7f * s, 44f - 7f * s, 146f + 7f * s, 44f + 7f * s, p)
                c.drawLine(146f - 7f * s, 44f + 7f * s, 146f + 7f * s, 44f - 7f * s, p)
            }
            Mood.IDLE -> Unit
        }
    }

    // ---------------------------------------------------------------- balon ucapan

    private fun drawBubble(c: Canvas, now: Long) {
        if (bubbleText.isEmpty() || now > bubbleUntil) return
        val maxW = (width - dp(36f)).toInt()
        if (maxW < dp(80f)) return

        var full = fullLayout
        if (full == null || layoutWidth != maxW) {
            full = buildLayout(bubbleText, maxW)
            fullLayout = full
            layoutWidth = maxW
            lastShown = -1
        }
        var textW = 0f
        for (i in 0 until full.lineCount) textW = max(textW, full.getLineWidth(i))
        val tw = ceil(textW).toInt() + 2

        val shown = min(bubbleText.length, ((now - bubbleStart) / 26L).toInt() + 1)
        if (shown != lastShown || partialLayout == null) {
            partialLayout = buildLayout(bubbleText.substring(0, shown), tw)
            lastShown = shown
        }
        val part = partialLayout ?: return

        val fade = ((bubbleUntil - now) / 400f).coerceIn(0f, 1f)
        val a = (255 * fade).toInt()
        val pad = dp(10f)
        val bw = tw + pad * 2f
        val bh = full.height + pad * 2f
        val cx = (width / 2f + pDx * scaleU).coerceIn(bw / 2f + dp(6f), width - bw / 2f - dp(6f))
        val bottom = originY - dp(12f)
        val top = bottom - bh
        val left = cx - bw / 2f

        fillP.shader = null
        fillP.style = Paint.Style.FILL
        fillP.color = Color.argb(a, 250, 251, 255)
        rect.set(left, top, left + bw, bottom)
        c.drawRoundRect(rect, dp(14f), dp(14f), fillP)

        val tail = Path().apply {
            moveTo(cx - dp(7f), bottom - 1f)
            lineTo(cx + dp(7f), bottom - 1f)
            lineTo(cx, bottom + dp(10f))
            close()
        }
        c.drawPath(tail, fillP)

        strokeP.shader = null
        strokeP.color = Color.argb(a, 141, 121, 255)
        strokeP.strokeWidth = dp(1.6f)
        c.drawRoundRect(rect, dp(14f), dp(14f), strokeP)
        // tutup garis di pangkal ekor balon
        strokeP.color = Color.argb(a, 250, 251, 255)
        strokeP.strokeWidth = dp(2.4f)
        c.drawLine(cx - dp(6f), bottom, cx + dp(6f), bottom, strokeP)
        strokeP.color = Color.argb(a, 141, 121, 255)
        strokeP.strokeWidth = dp(1.6f)
        c.drawLine(cx - dp(7f), bottom, cx, bottom + dp(10f), strokeP)
        c.drawLine(cx + dp(7f), bottom, cx, bottom + dp(10f), strokeP)

        bubbleTextPaint.alpha = a
        c.save()
        c.translate(left + pad, top + pad)
        part.draw(c)
        c.restore()
    }

    private fun buildLayout(text: CharSequence, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, bubbleTextPaint, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    // ---------------------------------------------------------------- helper gambar

    private fun drawHeart(c: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        path2.reset()
        path2.moveTo(cx, cy + size * 0.9f)
        path2.cubicTo(cx - size * 1.6f, cy - size * 0.2f, cx - size * 0.6f, cy - size * 1.3f, cx, cy - size * 0.4f)
        path2.cubicTo(cx + size * 0.6f, cy - size * 1.3f, cx + size * 1.6f, cy - size * 0.2f, cx, cy + size * 0.9f)
        path2.close()
        c.drawPath(path2, paint)
    }

    private fun drawSparkle(c: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        path2.reset()
        path2.moveTo(x, y - r)
        path2.quadTo(x, y, x + r, y)
        path2.quadTo(x, y, x, y + r)
        path2.quadTo(x, y, x - r, y)
        path2.quadTo(x, y, x, y - r)
        path2.close()
        c.drawPath(path2, paint)
    }

    private fun fillColor(color: Int): Paint {
        fillP.style = Paint.Style.FILL
        fillP.shader = null
        fillP.color = color
        return fillP
    }

    private fun strokeColor(color: Int, width: Float): Paint {
        strokeP.shader = null
        strokeP.color = color
        strokeP.strokeWidth = width
        return strokeP
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private companion object {
        const val PIF = 3.14159265f
    }
}
