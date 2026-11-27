package com.imzz.galaxyassistant

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.imzz.galaxyassistant.accessibility.AccessibilityController
import com.imzz.galaxyassistant.accessibility.GalaxyAccessibilityService
import com.imzz.galaxyassistant.assistant.AssistantCommandRouter
import com.imzz.galaxyassistant.assistant.AssistantEntryActivity
import com.imzz.galaxyassistant.mascot.CatBrain
import com.imzz.galaxyassistant.mascot.CatMascotView
import com.imzz.galaxyassistant.overlay.OverlayCatService
import com.imzz.galaxyassistant.voice.VoiceFeedback
import com.imzz.galaxyassistant.voice.VoiceListener
import java.util.Locale

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var voice: VoiceListener? = null
    private lateinit var cat: CatMascotView
    private lateinit var status: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var appList: LinearLayout
    private lateinit var commandInput: EditText
    private lateinit var micButton: Button
    private var chatBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 13, 24)
        window.navigationBarColor = Color.rgb(8, 13, 24)
        VoiceFeedback.initialize(this)
        buildUi()
        bindVoiceFeedback()
        refreshStatus()
        scanApps()
        requestNotificationPermission()
        if (intent?.action == Intent.ACTION_ASSIST) openAssistant(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_ASSIST) openAssistant(true)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(8, 13, 24)); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(30))
        }
        content.addView(text("GALAXY  /  IMZZ SYSTEM", 12, Color.rgb(154, 143, 255), true))
        content.addView(text("ASISTEN GALAXY IMZZ", 27, Color.WHITE, true).apply { setPadding(0, dp(7), 0, dp(3)) })
        content.addView(text("AI GAME & APP AUTOPILOT", 14, Color.rgb(155, 169, 194), false))

        val mascotCard = card().apply { setPadding(dp(10), dp(12), dp(10), dp(12)) }
        cat = CatMascotView(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        mascotCard.addView(cat, LinearLayout.LayoutParams(-1, dp(305)))
        mascotCard.addView(text("Skylen adalah kucing jantan virtual. Ketuk mikrofon untuk bicara, atau ketik perintah di bawah.", 11, Color.rgb(142, 157, 185), false))
        content.addView(mascotCard, margin(top = 16))

        val statusCard = card()
        statusCard.addView(text("AUTOPILOT STATUS", 11, Color.rgb(164, 173, 198), true))
        status = text("VOICE  •  Siap menerima perintah, bro.", 14, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) }
        statusCard.addView(status)
        permissionStatus = text("Memeriksa izin Android...", 11, Color.rgb(156, 169, 197), false).apply { setPadding(0, dp(8), 0, 0) }
        statusCard.addView(permissionStatus)
        content.addView(statusCard, margin(top = 16, bottom = 12))

        content.addView(section("VOICE & CHAT"), margin(bottom = 8))
        val chatCard = card()
        commandInput = EditText(this).apply {
            hint = "Contoh: buka WhatsApp / download CapCut"
            setHintTextColor(Color.rgb(118, 132, 159))
            setTextColor(Color.WHITE)
            textSize = 13f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(25, 35, 55), Color.rgb(47, 62, 91), 12)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendCommand(); true } else false
            }
        }
        chatCard.addView(commandInput, LinearLayout.LayoutParams(-1, dp(48)))
        val voiceRow = row()
        micButton = actionButton("🎙  VOICE COMMAND") { startVoice() }
        voiceRow.addView(micButton, weight())
        voiceRow.addView(actionButton("KIRIM") { sendCommand() }, weight(left = 8))
        chatCard.addView(voiceRow, margin(top = 8))
        content.addView(chatCard)

        content.addView(section("QUICK ACTIONS"), margin(top = 20, bottom = 8))
        val first = row()
        first.addView(actionButton("🐾 TAMPILKAN KUCING") { enableOverlay() }, weight())
        first.addView(actionButton("HILANGKAN KUCING") { OverlayCatService.stop(this); status.text = "Overlay dimatikan." }, weight(left = 8))
        content.addView(first)
        val second = row()
        second.addView(actionButton("⚡ TOMBOL POWER") { requestAssistantRole() }, weight())
        second.addView(actionButton("SCAN APPS") { scanApps() }, weight(left = 8))
        content.addView(second, margin(top = 8))

        content.addView(section("GAME CONTROL / PERMISSION"), margin(top = 20, bottom = 8))
        val controls = card()
        controls.addView(text("Gesture helper hanya mengirim tap/swipe yang lu sebutkan secara manual. Skylen tidak membaca layar atau menjalankan bot game otomatis.", 11, Color.rgb(175, 188, 212), false))
        controls.addView(actionButton("AKTIFKAN MANUAL GESTURE HELPER") { showAccessibilityDisclosure() }, margin(top = 8))
        controls.addView(actionButton("BANTUAN TOMBOL POWER & OVERLAY") { showSetupHelp() }, margin(top = 8))
        content.addView(controls)

        content.addView(section("APLIKASI TERDETEKSI"), margin(top = 20, bottom = 8))
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(appList)
        content.addView(text("Unduh aplikasi dibuka melalui Google Play. Pemasangan tetap harus disetujui pengguna oleh Android/Play Store.", 10, Color.rgb(116, 132, 159), false).apply { setPadding(dp(3), dp(16), dp(3), 0) })
        scroll.addView(content)
        setContentView(scroll)
    }

    private fun sendCommand() {
        if (chatBusy) return
        val command = commandInput.text.toString().trim()
        if (command.isBlank()) return
        commandInput.setText("")
        status.text = "Skylen memproses: $command"
        cat.setMood(CatMascotView.Mood.THINKING, 5000)
        val action = AssistantCommandRouter.execute(this, command, cat)
        if (action != null) {
            showReply(action.message)
            return
        }
        chatBusy = true
        CatBrain.chat(command, emptyList()) { reply ->
            runOnUiThread {
                chatBusy = false
                status.text = if (reply.fromApi) "SKYLEN AI  •  ${reply.text}" else "SKYLEN OFFLINE  •  ${reply.text}"
                reply.mood?.let { cat.setMood(it, 4000) }
                reply.action?.let { cat.playAction(it) }
                cat.say(reply.text)
                VoiceFeedback.speak(reply.text, decorate = false)
                if (reply.fullText.length > reply.text.length + 50) {
                    Toast.makeText(this, reply.fullText.take(220), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showReply(message: String) {
        status.text = message
        cat.say(message)
        cat.setMood(CatBrain.moodForText(message) ?: CatMascotView.Mood.HAPPY, 3200)
        VoiceFeedback.speak(message, decorate = false)
    }

    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            status.text = "Izinkan mikrofon untuk memakai voice command."
            return
        }
        if (VoiceFeedback.isSpeaking) VoiceFeedback.stopSpeaking()
        val listener = voice ?: VoiceListener(
            context = this,
            onText = { words -> runOnUiThread { commandInput.setText(words); sendCommand() } },
            onPartial = { words -> runOnUiThread { status.text = "VOICE  •  $words" } },
            onListening = { listening -> runOnUiThread { micButton.text = if (listening) "● MENDENGARKAN" else "🎙 VOICE COMMAND" } },
            onStatus = { message -> runOnUiThread { status.text = "VOICE  •  $message" } },
            onNeedPermission = { runOnUiThread { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC) } },
            onFinished = { retry -> if (!retry) runOnUiThread { micButton.text = "🎙 VOICE COMMAND" } }
        ).also { voice = it }
        if (listener.isAvailable()) listener.start() else status.text = "Speech Recognition tidak tersedia. Aktifkan Google Speech Services."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    private fun enableOverlay() {
        if (Settings.canDrawOverlays(this)) {
            OverlayCatService.start(this)
            showReply("Skylen muncul, bro. Tahan dan geser kucing ke posisi mana pun. Ketuk kucing untuk ngobrol.")
        } else {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), REQUEST_OVERLAY)
        }
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT < 29) {
            showSetupHelp()
            return
        }
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            Toast.makeText(this, "Pilih Digital assistant app, lalu pilih Skylen jika tersedia.", Toast.LENGTH_LONG).show()
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            showSetupHelp()
            return
        }
        runCatching { startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT_ROLE) }
            .onFailure { showSetupHelp() }
    }

    private fun showAccessibilityDisclosure() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Manual Gesture Helper")
            .setMessage("Layanan ini dapat mengirim ketukan dan geseran pada layar hanya setelah lu memberi perintah koordinat secara langsung, misalnya ‘tap 400 900’. Layanan tidak mengambil isi layar dan tidak menjalankan permainan otomatis. Jangan aktifkan bila lu tidak memerlukan fungsi ini.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Buka Accessibility") { _, _ -> AccessibilityController.openSettings(this) }
            .show()
    }

    private fun showSetupHelp() {
        val roleHeld = if (Build.VERSION.SDK_INT >= 29) runCatching {
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        }.getOrDefault(false) else false
        val message = buildString {
            append("Digital assistant role: ").append(if (roleHeld) "Skylen dipilih" else "belum dipilih").append("\n\n")
            append("1. Tekan tombol ⚡ TOMBOL POWER dan pilih Skylen sebagai Digital Assistant.\n")
            append("2. Pada Samsung, cek Pengaturan > Fitur lanjutan > Tombol samping (Side button). Perilaku tekan lama bergantung versi One UI dan opsi sistem.\n")
            append("3. Aktifkan Tampil di atas aplikasi untuk kucing mengambang.\n\n")
            append("Android tidak mengizinkan aplikasi biasa merebut tombol power secara paksa. Power gesture hanya bekerja saat sistem/OEM mengarahkannya ke asisten default.")
        }
        android.app.AlertDialog.Builder(this).setTitle("Pengaturan Skylen")
            .setMessage(message).setPositiveButton("OK", null)
            .setNeutralButton("Pengaturan default") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            }.show()
    }

    private fun scanApps() {
        if (!::appList.isInitialized) return
        appList.removeAllViews()
        status.text = "SCAN APPS  •  Memindai launcher yang terlihat oleh Android..."
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val results = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.ROOT) }
        results.take(24).forEach { info ->
            val label = info.loadLabel(packageManager).toString()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Color.rgb(17, 26, 43), Color.rgb(37, 50, 75), 13)
            }
            val name = text(label, 13, Color.WHITE, true)
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val open = actionButton("BUKA") {
                runCatching { startActivity(packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)) }
                    .onFailure { status.text = "Aplikasi tidak dapat dibuka oleh Android." }
            }
            row.addView(open, LinearLayout.LayoutParams(dp(76), dp(42)))
            appList.addView(row, margin(bottom = 6))
        }
        status.text = "SCAN APPS  •  ${results.size} aplikasi launcher terdeteksi."
    }

    private fun openAssistant(autoListen: Boolean) {
        startActivity(Intent(this, AssistantEntryActivity::class.java).putExtra(AssistantEntryActivity.EXTRA_AUTO_LISTEN, autoListen))
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val role = if (Build.VERSION.SDK_INT >= 29) runCatching {
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        }.getOrDefault(false) else false
        permissionStatus.text = "Overlay: ${if (overlay) "aktif" else "belum aktif"}  •  Assistant Role: ${if (role) "Skylen" else "belum dipilih"}  •  Gesture: ${if (GalaxyAccessibilityService.isEnabled(this)) "aktif" else "mati"}"
        permissionStatus.setTextColor(if (overlay && role) Color.rgb(112, 230, 177) else Color.rgb(156, 169, 197))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        VoiceFeedback.initialize(this)
        bindVoiceFeedback()
        refreshStatus()
    }

    private fun bindVoiceFeedback() {
        VoiceFeedback.speakingListener = { speaking -> runOnUiThread { if (::cat.isInitialized) cat.setSpeaking(speaking) } }
        VoiceFeedback.textListener = { line -> runOnUiThread { if (::cat.isInitialized) cat.say(line) } }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) enableOverlay()
                else Toast.makeText(this, "Izin overlay belum diberikan.", Toast.LENGTH_LONG).show()
            }
            REQUEST_ASSISTANT_ROLE -> {
                refreshStatus()
                if (Build.VERSION.SDK_INT >= 29 && getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
                    showReply("Mantap bro. Skylen sudah dipilih sebagai Digital Assistant. Coba tekan lama tombol power; bila Samsung masih membuka menu daya, cek pengaturan Tombol Samping.")
                } else showSetupHelp()
            }
        }
    }

    override fun onDestroy() {
        voice?.destroy()
        voice = null
        handler.removeCallbacksAndMessages(null)
        VoiceFeedback.speakingListener = null
        VoiceFeedback.textListener = null
        VoiceFeedback.shutdown()
        super.onDestroy()
    }

    private fun section(value: String) = text(value, 12, Color.rgb(155, 147, 255), true)
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(Color.rgb(18, 28, 47), Color.rgb(39, 53, 80), 18)
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); minHeight = dp(44); minimumHeight = dp(44)
        background = rounded(Color.rgb(57, 54, 111), Color.rgb(117, 103, 204), 13)
        setOnClickListener { action() }
    }
    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radius).toFloat()
    }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun weight(left: Int = 0) = LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(left) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_MIC = 5101
        private const val REQUEST_OVERLAY = 5102
        private const val REQUEST_ASSISTANT_ROLE = 5103
        private const val REQUEST_NOTIFICATIONS = 5104
    }
}
