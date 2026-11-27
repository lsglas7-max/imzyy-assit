package com.imzz.galaxyassistant.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.imzz.galaxyassistant.mascot.CatBrain
import com.imzz.galaxyassistant.mascot.CatMascotView
import com.imzz.galaxyassistant.overlay.OverlayCatService
import com.imzz.galaxyassistant.voice.VoiceFeedback
import com.imzz.galaxyassistant.voice.VoiceListener

/** Small assistant surface launched by the power-button assistant gesture or floating cat. */
class AssistantEntryActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var voice: VoiceListener? = null
    private lateinit var cat: CatMascotView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var micButton: Button
    private var chatBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.62f }
        VoiceFeedback.initialize(this)
        buildUi()
        if (intent.getStringExtra(EXTRA_SOURCE) == "power_assistant" && Settings.canDrawOverlays(this)) {
            OverlayCatService.start(this)
        }
        VoiceFeedback.speakingListener = { speaking -> runOnUiThread { cat.setSpeaking(speaking) } }
        VoiceFeedback.textListener = { line -> runOnUiThread { cat.say(line) } }
        if (shouldAutoListen(intent)) {
            handler.postDelayed({ startVoice() }, 450L)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldAutoListen(intent)) handler.postDelayed({ startVoice() }, 250L)
    }

    private fun shouldAutoListen(source: android.content.Intent): Boolean =
        source.getBooleanExtra(EXTRA_AUTO_LISTEN, false) || source.action == android.content.Intent.ACTION_ASSIST

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(Color.TRANSPARENT)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.rgb(17, 27, 46), Color.rgb(117, 103, 204), 24)
            elevation = dp(14).toFloat()
        }
        val heading = TextView(this).apply {
            text = "SKYLEN  /  ASISTEN GALAXY"
            textSize = 12f
            setTextColor(Color.rgb(174, 162, 255))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        card.addView(heading)
        cat = CatMascotView(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        card.addView(cat, LinearLayout.LayoutParams(-1, dp(205)))

        status = TextView(this).apply {
            text = "Gue siap, bro. Ketuk mikrofon atau ketik perintah."
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        card.addView(status)
        answer = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(204, 214, 232))
            gravity = Gravity.CENTER
            maxLines = 4
        }
        card.addView(answer)
        input = EditText(this).apply {
            hint = "Contoh: buka WhatsApp / download CapCut"
            setHintTextColor(Color.rgb(134, 146, 170))
            setTextColor(Color.WHITE)
            textSize = 14f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(28, 39, 60), Color.rgb(69, 83, 119), 14)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { submit(); true } else false
            }
        }
        card.addView(input, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        micButton = actionButton("🎙  DENGARKAN") { startVoice() }
        controls.addView(micButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(5) })
        controls.addView(actionButton("KIRIM") { submit() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })
        card.addView(controls, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        card.addView(actionButton("TUTUP") { finish() }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(7) })
        outer.addView(card, LinearLayout.LayoutParams(-1, -2))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(outer) })
    }

    private fun ensureVoice(): VoiceListener {
        voice?.let { return it }
        return VoiceListener(
            context = this,
            onText = { spoken -> runOnUiThread { input.setText(spoken); submit() } },
            onPartial = { partial -> runOnUiThread { status.text = "GUE DENGER: $partial" } },
            onListening = { listening -> runOnUiThread { micButton.text = if (listening) "● MENDENGARKAN" else "🎙 DENGARKAN" } },
            onStatus = { message -> runOnUiThread { status.text = message } },
            onNeedPermission = { runOnUiThread { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC) } },
            onFinished = { canRetry -> if (!canRetry) runOnUiThread { micButton.text = "🎙 DENGARKAN" } }
        ).also { voice = it }
    }

    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            status.text = "Izinkan mikrofon agar Skylen bisa mendengar perintah."
            return
        }
        if (VoiceFeedback.isSpeaking) VoiceFeedback.stopSpeaking()
        val listener = ensureVoice()
        if (listener.isAvailable()) listener.start() else status.text = "Speech Recognition tidak tersedia. Coba pasang/aktifkan Google Speech Services."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
            else status.text = "Izin mikrofon ditolak. Lu masih bisa mengetik perintah."
        }
    }

    private fun submit() {
        if (chatBusy) return
        val command = input.text.toString().trim()
        if (command.isBlank()) return
        input.setText("")
        status.text = "Skylen lagi memproses perintah..."
        cat.setMood(CatMascotView.Mood.THINKING, 5000)
        val action = AssistantCommandRouter.execute(this, command, cat)
        if (action != null) {
            present(action.message)
            if (action.closeAssistant) handler.postDelayed({ if (!isFinishing) finish() }, 2600L)
            return
        }

        chatBusy = true
        CatBrain.chat(command, listOf("user" to command)) { reply ->
            runOnUiThread {
                chatBusy = false
                answer.text = reply.fullText.take(1600)
                status.text = if (reply.fromApi) "SKYLEN AI" else "SKYLEN OFFLINE"
                reply.mood?.let { cat.setMood(it, 4000) }
                reply.action?.let { cat.playAction(it) }
                VoiceFeedback.speak(reply.text, decorate = false)
            }
        }
    }

    private fun present(message: String) {
        status.text = message
        answer.text = ""
        cat.say(message)
        cat.setMood(CatBrain.moodForText(message) ?: CatMascotView.Mood.HAPPY, 3000)
        VoiceFeedback.speak(message, decorate = false)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        voice?.destroy()
        voice = null
        VoiceFeedback.speakingListener = null
        VoiceFeedback.textListener = null
        VoiceFeedback.shutdown()
        super.onDestroy()
    }

    private fun actionButton(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        minHeight = dp(44)
        background = rounded(Color.rgb(57, 54, 111), Color.rgb(128, 113, 220), 14)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_AUTO_LISTEN = "extra_auto_listen"
        const val EXTRA_SOURCE = "extra_source"
        private const val REQUEST_MIC = 8131
    }
}
