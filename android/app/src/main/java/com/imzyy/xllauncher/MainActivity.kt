package com.imzyy.xllauncher

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.ColorDrawable
import com.imzyy.xllauncher.ui.DotGridDrawable
import com.imzyy.xllauncher.ui.Meter
import com.imzyy.xllauncher.ui.PulseDot
import com.imzyy.xllauncher.ui.Ui
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.net.URL
import java.net.HttpURLConnection
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean
import android.app.ProgressDialog
import com.imzyy.xllauncher.runtime.MinecraftInstaller
import com.imzyy.xllauncher.engine.AmethystBootstrapper
import com.imzyy.xllauncher.input.TouchControllerView
import androidx.documentfile.provider.DocumentFile
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MATCH = -1
private const val WRAP = -2

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("imzyy-xl", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private var selectedVersion = "latest-release"
    private var sessionUnlocked = false
    private val files = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        window.decorView.systemUiVisibility = 0
        selectedVersion = prefs.getString("version", "latest-release") ?: "latest-release"
        files.addAll(prefs.getStringSet("managed_files", emptySet()) ?: emptySet())
        showAccount()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    // Engine class probing is static for a given APK, so inspect once.
    private val engineChecks: List<AmethystBootstrapper.Check> by lazy { AmethystBootstrapper.inspect(this) }
    private val engineReady: Boolean
        get() = engineChecks.firstOrNull { it.title == "Amethyst version/profile launcher" }?.ready == true

    private val handler = Handler(Looper.getMainLooper())
    private var page = ""
    private var header: LinearLayout? = null
    private var ramMeter: Meter? = null
    private var ramLabel: TextView? = null
    private val ramTick = object : Runnable {
        override fun run() {
            updateRam(true)
            handler.postDelayed(this, 2500)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ramTick)
        if (page == "home") handler.postDelayed(ramTick, 2500)
    }

    override fun onPause() {
        handler.removeCallbacks(ramTick)
        super.onPause()
    }

    override fun onBackPressed() {
        val gate = page == "Local Launcher Account" && !sessionUnlocked
        if (page == "home" || gate) super.onBackPressed() else buildHome()
    }

    private fun alive() = !isFinishing && !isDestroyed

    private fun ripple(fill: Int, stroke: Int, radiusDp: Int, rippleColor: Int = Ui.RIPPLE): RippleDrawable {
        val content = GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != 0) setStroke(dp(1), stroke)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(0xFFFFFFFF.toInt())
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    /** Springy press feedback shared by every tappable surface. */
    private fun View.pressable() {
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().setStartDelay(0).setDuration(90)
                    .setInterpolator(DecelerateInterpolator()).scaleX(0.97f).scaleY(0.97f).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().setStartDelay(0).setDuration(220)
                    .setInterpolator(OvershootInterpolator(2.2f)).scaleX(1f).scaleY(1f).start()
            }
            false
        }
    }

    private fun SeekBar.styled() {
        progressTintList = ColorStateList.valueOf(Ui.ACCENT)
        thumbTintList = ColorStateList.valueOf(Ui.ACCENT)
        progressBackgroundTintList = ColorStateList.valueOf(Ui.LINE)
    }

    private fun CheckBox.styled() {
        buttonTintList = ColorStateList.valueOf(Ui.ACCENT)
        setTextColor(Ui.TEXT)
    }

    private fun EditText.styled() {
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.DIM)
        textSize = 15f
        setPadding(dp(16), dp(13), dp(16), dp(13))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Ui.SURFACE2)
            setStroke(dp(1), Ui.LINE)
        }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(6) }
    }

    private fun text(value: String, size: Float = 14f, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(if (bold) Ui.TEXT else Ui.MUTED)
            if (bold) typeface = medium
            setLineSpacing(dp(2).toFloat(), 1.0f)
            setPadding(dp(1), dp(4), dp(1), dp(4))
        }

    private fun caption(value: String): TextView = text(value.uppercase(), 11f, true).apply {
        setTextColor(Ui.DIM)
        letterSpacing = 0.12f
    }

    /** Buttons are written ALL-CAPS in code; show them in readable sentence case. */
    private fun pretty(raw: String): String {
        if (raw != raw.uppercase() || raw.none { it.isLetter() }) return raw
        val fixes = mapOf(
            "saf" to "SAF", "amethyst" to "Amethyst", "imzyy" to "IMZYY", "minecraft" to "Minecraft",
            "java" to "Java", "apk" to "APK", "ram" to "RAM", "xl" to "XL"
        )
        val lower = raw.lowercase().replaceFirstChar { it.uppercase() }
        return Regex("[A-Za-z]+").replace(lower) { m ->
            val dotted = m.range.first > 0 && lower[m.range.first - 1] == '.'
            if (dotted) m.value else (fixes[m.value.lowercase()] ?: m.value)
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = pretty(label)
        isAllCaps = false
        textSize = 14f
        typeface = medium
        setTextColor(Ui.TEXT)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minHeight = dp(52)
        minimumHeight = dp(52)
        setPadding(dp(18), dp(8), dp(18), dp(8))
        background = ripple(Ui.SURFACE2, Ui.LINE, 14)
        stateListAnimator = null
        elevation = 0f
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        pressable()
        setOnClickListener { action() }
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = pretty(label)
        isAllCaps = false
        textSize = 16f
        typeface = medium
        setTextColor(Ui.ACCENT_INK)
        gravity = Gravity.CENTER
        minHeight = dp(58)
        minimumHeight = dp(58)
        setPadding(dp(18), dp(8), dp(18), dp(8))
        background = ripple(Ui.ACCENT, 0, 16, 0x33000000)
        stateListAnimator = null
        elevation = 0f
        pressable()
        setOnClickListener { action() }
    }

    private fun addAction(container: LinearLayout, label: String, action: () -> Unit, primary: Boolean = false) {
        val view = if (primary) primaryButton(label, action) else button(label, action)
        container.addView(view, LinearLayout.LayoutParams(MATCH, dp(if (primary) 58 else 52)).apply {
            topMargin = dp(if (primary) 16 else 9)
        })
    }

    private fun panel(title: String? = null, subtitle: String? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(18))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Ui.SURFACE)
            setStroke(dp(1), Ui.LINE)
        }
        if (!title.isNullOrBlank()) addView(text(title, 15f, true))
        if (!subtitle.isNullOrBlank()) addView(text(subtitle, 12f))
    }

    private fun addPanel(view: View) {
        root.addView(view, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(14) })
    }

    private fun section(label: String, detail: String? = null) {
        root.addView(caption(label), LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(14)
            bottomMargin = dp(8)
            leftMargin = dp(4)
        })
        if (!detail.isNullOrBlank()) root.addView(text(detail, 12f), LinearLayout.LayoutParams(MATCH, WRAP).apply {
            bottomMargin = dp(8)
            leftMargin = dp(4)
        })
    }

    private fun statTile(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(11), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Ui.SURFACE2)
        }
        addView(caption(label))
        addView(text(value, 15f, true))
    }

    private fun listRow(title: String, sub: String?, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(14), dp(14))
        background = RippleDrawable(ColorStateList.valueOf(Ui.RIPPLE), null, ColorDrawable(0xFFFFFFFF.toInt()))
        isClickable = true
        isFocusable = true
        val copy = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(title, 15f, true).apply { setPadding(0, 0, 0, 0) })
        if (!sub.isNullOrBlank()) copy.addView(text(sub, 12f).apply { setPadding(0, dp(2), 0, 0) })
        addView(copy, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(TextView(this@MainActivity).apply {
            this.text = "›"
            textSize = 22f
            setTextColor(Ui.DIM)
        })
        setOnClickListener { action() }
    }

    private fun infoRow(name: String, value: String, valueColor: Int = Ui.TEXT): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        addView(text(name, 14f), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(text(value, 14f, true).apply { setTextColor(valueColor); gravity = Gravity.END })
    }

    private fun checkRow(title: String, detail: String, ok: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(13), dp(18), dp(13))
        addView(PulseDot(this@MainActivity, if (ok) Ui.ACCENT else Ui.WARN, false), LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(14) })
        val copy = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(title, 14f, true).apply { setPadding(0, 0, 0, 0) })
        copy.addView(text(detail, 12f).apply { setPadding(0, dp(2), 0, 0) })
        addView(copy, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    private fun listCard(rows: List<View>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Ui.SURFACE)
            setStroke(dp(1), Ui.LINE)
        }
        clipToOutline = true
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                addView(View(this@MainActivity).apply { setBackgroundColor(Ui.LINE) },
                    LinearLayout.LayoutParams(MATCH, dp(1)).apply { marginStart = dp(18) })
            }
            addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private fun tile(title: String, sub: String, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = dp(112)
        setPadding(dp(16), dp(16), dp(16), dp(14))
        background = ripple(Ui.SURFACE, Ui.LINE, 18)
        isClickable = true
        isFocusable = true
        addView(View(this@MainActivity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(Ui.ACCENT) }
        }, LinearLayout.LayoutParams(dp(18), dp(3)))
        addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f))
        addView(text(title, 15f, true).apply { setPadding(0, 0, 0, 0) })
        addView(text(sub, 12f).apply { setPadding(0, dp(3), 0, 0) })
        pressable()
        setOnClickListener { action() }
    }

    private fun tileRow(left: View, right: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(5) })
        addView(right, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(5) })
    }

    private fun initPage(title: String, home: Boolean = false) {
        handler.removeCallbacks(ramTick)
        ramMeter = null
        ramLabel = null
        header = null
        page = if (home) "home" else title
        val gate = title == "Local Launcher Account" && !sessionUnlocked

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
            clipToPadding = false
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(root)
            setOnApplyWindowInsetsListener { _, insets ->
                root.setPadding(dp(20), dp(20) + insets.systemWindowInsetTop, dp(20), dp(32) + insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(scroll)
        scroll.requestApplyInsets()

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (home || gate) {
            val mark = TextView(this).apply {
                this.text = "X"
                textSize = 17f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setTextColor(Ui.ACCENT_INK)
                background = GradientDrawable().apply { cornerRadius = dp(11).toFloat(); setColor(Ui.ACCENT) }
            }
            bar.addView(mark, LinearLayout.LayoutParams(dp(38), dp(38)))
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            copy.addView(text("IMZYY XL", 15f, true).apply { setPadding(0, 0, 0, 0); letterSpacing = 0.04f })
            copy.addView(text("Java Edition launcher", 11f).apply { setPadding(0, dp(1), 0, 0); setTextColor(Ui.DIM) })
            bar.addView(copy, LinearLayout.LayoutParams(0, WRAP, 1f))
        } else {
            val back = TextView(this).apply {
                this.text = "‹"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Ui.TEXT)
                background = ripple(Ui.SURFACE2, Ui.LINE, 20)
                isClickable = true
                pressable()
                setOnClickListener { buildHome() }
            }
            bar.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
            val heading = text(title, 18f, true).apply {
                setPadding(dp(14), 0, 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            bar.addView(heading, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        header = bar
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(if (home) 18 else 16) })

        if (gate) {
            root.addView(text("Sign in", 30f, true).apply { setPadding(0, dp(10), 0, dp(2)); letterSpacing = -0.01f })
            root.addView(text("Your launcher account lives on this device.", 13f).apply { setPadding(0, 0, 0, dp(12)) })
        }

        // Entrance: every top-level block rises in with a short stagger.
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                val rise = dp(18).toFloat()
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    child.alpha = 0f
                    child.translationY = rise
                    child.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(minOf(i, 9) * 34L)
                        .setDuration(380)
                        .setInterpolator(DecelerateInterpolator(1.8f))
                        .start()
                }
                return true
            }
        })
    }

    private fun updateRam(animate: Boolean) {
        val meter = ramMeter ?: return
        val label = ramLabel ?: return
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val total = info.totalMem.coerceAtLeast(1L)
        val used = 1f - info.availMem.toFloat() / total.toFloat()
        meter.warn = info.lowMemory || used > 0.88f
        meter.setProgress(used, animate)
        label.text = "${(used * 100).toInt()}% used  ·  ${info.availMem / (1024 * 1024)} MB free"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }

    private fun buildHome() {
        initPage("IMZYY XL", home = true)
        val username = prefs.getString("local_username", "Guest") ?: "Guest"
        val versionLabel = when (selectedVersion) {
            "latest-release" -> "Latest release"
            "latest-snapshot" -> "Latest snapshot"
            else -> selectedVersion
        }

        // Header avatar opens the account screen.
        val avatar = TextView(this).apply {
            this.text = username.take(1).uppercase()
            textSize = 15f
            typeface = medium
            gravity = Gravity.CENTER
            setTextColor(Ui.ACCENT)
            background = ripple(Ui.SURFACE2, Ui.LINE, 20)
            isClickable = true
            pressable()
            setOnClickListener { showAccount() }
        }
        header?.addView(avatar, LinearLayout.LayoutParams(dp(40), dp(40)))

        // Hero
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = DotGridDrawable(
                Ui.SURFACE, Ui.LINE, 0x14FFFFFF,
                dp(22).toFloat(), dp(1).toFloat(), dp(14).toFloat(), dp(1).toFloat() * 0.8f
            )
        }
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusRow.addView(PulseDot(this, if (engineReady) Ui.ACCENT else Ui.WARN, engineReady), LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(8) })
        statusRow.addView(text(if (engineReady) "Engine ready  ·  ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}" else "Engine not built into this APK", 12f))
        hero.addView(statusRow)
        hero.addView(text("Minecraft", 34f, true).apply { setPadding(0, dp(22), 0, 0); letterSpacing = -0.02f })
        hero.addView(text("Java Edition", 34f, false).apply { setPadding(0, 0, 0, dp(2)); setTextColor(Ui.ACCENT); letterSpacing = -0.02f })
        hero.addView(text("$versionLabel  ·  $username", 13f).apply { setPadding(dp(1), dp(8), 0, 0) })
        addAction(hero, "Play Minecraft", { openAmethystEngine() }, primary = true)
        addPanel(hero)

        // Quick actions
        val gameTile = tile("Game folder", "Checking…") { chooseGameFolder() }
        root.addView(tileRow(
            tile("Versions & profiles", "Installer, Java, renderer") { openAmethystEngine() },
            gameTile
        ), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        root.addView(tileRow(
            tile("Mods", "Import .jar files") { pickFiles("application/java-archive", 101) },
            tile("Resource packs", "Import .zip files") { pickFiles("application/zip", 102) }
        ), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) })

        // Live memory card
        section("Device")
        val memoryCard = panel()
        val memoryTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        memoryTop.addView(text("Memory", 15f, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val memoryText = text("", 12f).apply { gravity = Gravity.END }
        memoryTop.addView(memoryText)
        memoryCard.addView(memoryTop)
        val meter = Meter(this)
        memoryCard.addView(meter, LinearLayout.LayoutParams(MATCH, dp(6)).apply { topMargin = dp(12); bottomMargin = dp(10) })
        val cacheText = text("Launcher cache: …", 12f)
        memoryCard.addView(cacheText)
        addPanel(memoryCard)
        ramMeter = meter
        ramLabel = memoryText
        updateRam(false)
        handler.postDelayed(ramTick, 2500)

        // Probe disk-backed state off the main thread so the screen opens instantly.
        thread(name = "IMZYY-home-probe") {
            val linked = AmethystBootstrapper.gameDirectory(this) != null
            val cacheBytes = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            runOnUiThread {
                if (!alive() || page != "home") return@runOnUiThread
                (gameTile.getChildAt(3) as TextView).text = if (linked) "Active profile linked" else "Open the engine once"
                cacheText.text = "Launcher cache: ${formatSize(cacheBytes)}"
            }
        }

        root.addView(listCard(listOf(
            listRow("Performance & cache", "Memory snapshot and safe cleanup") { showPerformance() },
            listRow("Java runtime & renderer", "Engine runtime status") { showRuntime() },
            listRow("Device monitor", "Hardware and system state") { showDevice() }
        )), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) })

        section("Library")
        root.addView(listCard(listOf(
            listRow("Shader packs", "Import .zip into the active profile") { pickFiles("application/zip", 107) },
            listRow("Managed files", if (files.isEmpty()) "Nothing imported yet" else "${files.size} imported") { showFiles() }
        )), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) })

        section("Tools")
        root.addView(listCard(listOf(
            listRow("Touch controller", "Layout preview and profile") { showController() },
            listRow("Engine check", "Verify the embedded Amethyst build") { showEngineReadiness() },
            listRow("Account & backup", "Encrypted .imzyy backup and restore") { showAccount() }
        )), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) })

        root.addView(text("IMZYY XL 6.0  ·  single APK  ·  native UI", 11f).apply {
            setTextColor(Ui.DIM)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        })
    }

    private fun openAmethystEngine() {
        try {
            AmethystBootstrapper.launch(this)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Amethyst engine could not be started.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showVersions() {
        initPage("Minecraft Versions")
        root.addView(text("Fetching official Mojang version manifest…", 13f))
        val status = root.getChildAt(root.childCount - 1) as TextView
        thread {
            try {
                val conn = (URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 12000; requestMethod = "GET"
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()
                val latest = json.getJSONObject("latest")
                if (selectedVersion == "latest-release" || selectedVersion == "latest-snapshot") {
                    selectedVersion = latest.getString("release")
                    prefs.edit().putString("version", selectedVersion).apply()
                }
                val versions = json.getJSONArray("versions")
                runOnUiThread {
                    status.text = "Latest release: ${latest.getString("release")}\nLatest snapshot: ${latest.getString("snapshot")}\n\nSelect a version:"
                    val ids = mutableListOf(latest.getString("release"), latest.getString("snapshot"))
                    for (i in 0 until minOf(versions.length(), 60)) ids.add(versions.getJSONObject(i).getString("id"))
                    ids.distinct().forEach { id ->
                        root.addView(button(if (id == selectedVersion) "✓ $id" else id) {
                            selectedVersion = id
                            prefs.edit().putString("version", id).apply()
                            val historyJson = JSONArray(prefs.getString("version_history_json", "[]") ?: "[]")
                            val history = mutableListOf<String>()
                            for (i in 0 until historyJson.length()) history.add(historyJson.optString(i))
                            val ordered = (listOf(id) + history.filter { it != id }).take(30)
                            val savedHistory = JSONArray(); ordered.forEach { savedHistory.put(it) }
                            prefs.edit().putString("version_history_json", savedHistory.toString()).apply()
                            Toast.makeText(this, "Selected $id", Toast.LENGTH_SHORT).show()
                            showVersions()
                        })
                    }
                    root.addView(text("Selected version: $selectedVersion", 13f, true))
                    root.addView(button("INSTALL SELECTED VERSION TO MANUAL SAF FOLDER") { installSelectedVersion() })
                    root.addView(button("INSTALLATION MANAGER") { showInstall() })
                            }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Could not retrieve Mojang manifest: ${e.message}"
                    root.addView(button("RETRY") { showVersions() })
                            }
            }
        }
    }


    private fun showInstall() {
        initPage("Manual SAF Game Library")
        root.addView(text("This is the optional IMZYY file-library helper. The real Minecraft installation, version selection, Java runtime and game launch are handled by the embedded Amethyst engine.", 12f))
        root.addView(button("OPEN AMETHYST INSTALLER / VERSION MANAGER") { openAmethystEngine() })
        root.addView(button("SELECT VERSION FOR MANUAL SAF LIBRARY") { showVersions() })
        val folder = prefs.getString("game_tree_uri", null)
        val installed = JSONArray(prefs.getString("installed_versions", "[]") ?: "[]")
        val installedNames = mutableListOf<String>()
        for (i in 0 until installed.length()) installedNames.add(installed.optString(i))
        if (!folder.isNullOrBlank()) {
            val rootFolder = runCatching { DocumentFile.fromTreeUri(this, Uri.parse(folder)) }.getOrNull()
            val versionsFolder = rootFolder?.findFile("versions")?.takeIf { it.isDirectory }
            versionsFolder?.listFiles()?.forEach { versionFolder ->
                val id = versionFolder.name ?: return@forEach
                if (id.matches(Regex("[A-Za-z0-9._-]{1,80}")) && versionFolder.findFile("$id.jar") != null && versionFolder.findFile("$id.json") != null && id !in installedNames) installedNames.add(id)
            }
            prefs.edit().putString("installed_versions", JSONArray(installedNames).toString()).apply()
        }
        root.addView(text("Manual SAF version: $selectedVersion", 15f, true))
        root.addView(text("Game directory: ${if (folder.isNullOrBlank()) "Not selected" else "SAF folder linked"}", 12f))
        root.addView(button("CHOOSE / CHANGE GAME DIRECTORY") { chooseGameFolder() })
        root.addView(button("DOWNLOAD + INSTALL $selectedVersion") { installSelectedVersion() })
        root.addView(text("Installed versions", 13f, true))
        if (installedNames.isEmpty()) root.addView(text("No completed installation recorded yet.", 12f))
        installedNames.forEach { root.addView(text("✓ $it", 12f)) }
        root.addView(text("This legacy helper downloads a separate file library and is not the active Amethyst game instance. For the playable game, install the version and its matching runtime from Amethyst's own installer above.", 12f))
    }

    private fun installSelectedVersion() {
        if (selectedVersion == "latest-release" || selectedVersion == "latest-snapshot") {
            Toast.makeText(this, "Pilih versi dari manifest Mojang terlebih dahulu.", Toast.LENGTH_LONG).show()
            showVersions()
            return
        }
        val uriText = prefs.getString("game_tree_uri", null)
        if (uriText.isNullOrBlank()) {
            android.app.AlertDialog.Builder(this).setTitle("Choose game folder")
                .setMessage("Pilih folder kosong atau folder khusus Minecraft. Installer membutuhkan izin baca/tulis ke folder tersebut.")
                .setPositiveButton("CHOOSE FOLDER") { _, _ -> chooseGameFolder() }
                .setNegativeButton("CANCEL", null).show()
            return
        }
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            Toast.makeText(this, "Perangkat ini tidak melaporkan dukungan ARM64.", Toast.LENGTH_LONG).show()
            return
        }
        val cancelled = AtomicBoolean(false)
        val dialog = ProgressDialog(this).apply {
            setTitle("Installing Minecraft $selectedVersion")
            setMessage("Preparing official files…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 1; progress = 0; setCancelable(false)
            setButton(ProgressDialog.BUTTON_NEGATIVE, "CANCEL") { _, _ -> cancelled.set(true); setMessage("Cancelling after current file…") }
            show()
        }
        val version = selectedVersion
        val treeUri = Uri.parse(uriText)
        thread(name = "IMZYY-Minecraft-Installer") {
            try {
                val result = MinecraftInstaller(this, treeUri, { progress ->
                    runOnUiThread {
                        dialog.max = progress.total.coerceAtLeast(1)
                        dialog.progress = progress.completed
                        dialog.setMessage(progress.phase)
                    }
                }) { cancelled.get() }.install(version)
                runOnUiThread {
                    dialog.dismiss()
                    val list = JSONArray(prefs.getString("installed_versions", "[]") ?: "[]")
                    val versions = mutableListOf<String>()
                    for (i in 0 until list.length()) versions.add(list.optString(i))
                    val saved = JSONArray((listOf(result.version) + versions.filter { it != result.version }).take(50))
                    prefs.edit().putString("installed_versions", saved.toString()).apply()
                    Toast.makeText(this, "File Minecraft $version tersimpan di folder SAF manual: ${result.installedFiles} file baru, ${result.skippedFiles} sudah valid. Ini bukan instalasi aktif Amethyst.", Toast.LENGTH_LONG).show()
                    showInstall()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Instalasi gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    showInstall()
                }
            }
        }
    }

    private fun showEngineReadiness() {
        initPage("Amethyst Engine Readiness")
        val ok = engineChecks.count { it.ready }
        root.addView(text("$ok of ${engineChecks.size} engine components found in this APK.", 13f).apply { setPadding(dp(4), 0, 0, dp(10)) })
        root.addView(listCard(engineChecks.map { checkRow(it.title, it.detail, it.ready) }),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        root.addView(text("These checks confirm the Amethyst module, native libraries and JVM bootstrap were compiled into this one APK. Device drivers and the Minecraft or mod version you pick can still affect stability.", 12f).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) })
        addAction(root, "OPEN EMBEDDED AMETHYST ENGINE", { openAmethystEngine() }, primary = true)
    }

    private fun showRuntime() {
        initPage("Java Runtime & Renderer")
        val arm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
        root.addView(listCard(listOf(
            infoRow("CPU ABI", Build.SUPPORTED_ABIS.joinToString()),
            infoRow("ARM64", if (arm64) "Available" else "Not available", if (arm64) Ui.ACCENT else Ui.WARN)
        )), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(14) })
        root.addView(listCard(engineChecks.map { checkRow(it.title, it.detail, it.ready) }),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        root.addView(text("Amethyst owns the Minecraft JVM, Java runtime downloads, renderer compatibility, the OpenGL/LWJGL bridge, audio and native bootstrap. Change runtime and renderer inside its settings so the profile and native runtime stay in sync.", 12f).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) })
        addAction(root, "OPEN AMETHYST RUNTIME + RENDERER MANAGER", { openAmethystEngine() }, primary = true)
    }

    private fun showController() {
        initPage("Touchscreen Controller")
        val scaleValue = prefs.getFloat("controller_scale", 1f)
        val opacityValue = prefs.getFloat("controller_opacity", 0.72f)
        val compactValue = prefs.getBoolean("controller_compact", false)
        root.addView(text("Preview input HUD", 14f, true))
        val output = text("This is an IMZYY layout preview. Actual in-game touch input is handled by Amethyst's native controller system.", 12f)
        root.addView(output)
        val preview = TouchControllerView(this).apply {
            scale = scaleValue; opacity = opacityValue; compact = compactValue
            inputSink = TouchControllerView.InputSink { action, pressed, x, y ->
                runOnUiThread { output.text = "$action ${if (pressed) "pressed" else "released"}  x=${"%.2f".format(x)} y=${"%.2f".format(y)}" }
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280)))
        root.addView(text("Control scale", 12f))
        val scale = SeekBar(this).apply { styled(); max = 100; progress = (((scaleValue - 0.65f) / 0.75f) * 100).toInt().coerceIn(0,100) }
        root.addView(scale)
        root.addView(text("Opacity", 12f))
        val opacity = SeekBar(this).apply { styled(); max = 100; progress = (opacityValue * 100).toInt() }
        root.addView(opacity)
        val compact = CheckBox(this).apply { text = "Compact HUD layout"; isChecked = compactValue; styled() }
        root.addView(compact)
        scale.setOnSeekBarChangeListener(simpleSeek { value -> preview.scale = 0.65f + value / 100f * 0.75f })
        opacity.setOnSeekBarChangeListener(simpleSeek { value -> preview.opacity = 0.2f + value / 100f * 0.8f })
        compact.setOnCheckedChangeListener { _, checked -> preview.compact = checked }
        root.addView(button("SAVE CONTROLLER PROFILE") {
            prefs.edit().putFloat("controller_scale", 0.65f + scale.progress / 100f * 0.75f)
                .putFloat("controller_opacity", 0.2f + opacity.progress / 100f * 0.8f)
                .putBoolean("controller_compact", compact.isChecked).apply()
            Toast.makeText(this, "Controller profile saved and included in account backup.", Toast.LENGTH_SHORT).show()
        })
        root.addView(text("The IMZYY widget above is a layout preview only. The actual game controller is Amethyst's native TouchControllerInputView and its in-game control-layout editor, connected to the native game input pipeline.", 12f))
        root.addView(button("OPEN AMETHYST IN-GAME CONTROLS") { openAmethystEngine() })
    }

    private fun simpleSeek(onValue: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { onValue(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun showAccount() {
        initPage("Local Launcher Account")
        root.addView(text("Account tersimpan di perangkat. Untuk pindah perangkat atau setelah APK di-uninstall, pulihkan dari file backup yang kamu simpan sendiri.", 13f))
        val userInput = EditText(this).apply { hint = "Username (3-20: a-z, 0-9, _)"; setText(prefs.getString("local_username", "")); styled() }
        val passInput = EditText(this).apply { hint = "Password (minimum 8 karakter)"; inputType = 129; styled() }
        root.addView(text("Username", 12f)); root.addView(userInput)
        root.addView(text("Password", 12f)); root.addView(passInput)
        val result = text("", 12f); root.addView(result)
        root.addView(button("REGISTER LOCAL ACCOUNT") {
            val username = userInput.text.toString().trim().lowercase()
            val password = passInput.text.toString()
            if (!username.matches(Regex("[a-z0-9_]{3,20}")) || password.length < 8) {
                result.text = "Username 3-20 karakter (a-z, angka, _) dan password minimal 8 karakter."; return@button
            }
            if (!prefs.getString("local_username", null).isNullOrBlank()) {
                result.text = "Akun lokal sudah ada. Login atau import backup untuk memulihkan akun."; return@button
            }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = deriveKey(password, salt)
            prefs.edit().putString("local_username", username)
                .putString("password_salt", b64(salt)).putString("password_hash", b64(hash)).apply()
            result.text = "Akun lokal dibuat. Segera buat backup terenkripsi agar bisa dipulihkan setelah reinstall."
            sessionUnlocked = true
            buildHome()
        })
        root.addView(button("LOGIN") {
            val username = userInput.text.toString().trim().lowercase()
            val password = passInput.text.toString()
            val savedUser = prefs.getString("local_username", null)
            val saltText = prefs.getString("password_salt", null)
            val hashText = prefs.getString("password_hash", null)
            if (savedUser == null || saltText == null || hashText == null) {
                result.text = "Belum ada akun lokal. Register atau import file backup."; return@button
            }
            try {
                val valid = username == savedUser && MessageDigest.isEqual(deriveKey(password, unb64(saltText)), unb64(hashText))
                if (valid) { sessionUnlocked = true; buildHome() } else result.text = "Username atau password salah."
            } catch (_: Exception) { result.text = "Data akun tidak valid. Import backup yang benar." }
        })
        root.addView(button("RESTORE ACCOUNT BACKUP (.imzyy)") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
            startActivityForResult(intent, 105)
        })
        if (!prefs.getString("local_username", null).isNullOrBlank()) {
            root.addView(button("BACKUP ACCOUNT + SETTINGS + GAME FILES") {
                if (passInput.text.isNullOrBlank()) { result.text = "Masukkan password akun untuk mengenkripsi backup."; return@button }
                if (!verifyPassword(passInput.text.toString())) { result.text = "Password akun salah."; return@button }
                val gameUri = prefs.getString("game_tree_uri", null)
                val gameRoot = gameUri?.let { runCatching { DocumentFile.fromTreeUri(this, Uri.parse(it)) }.getOrNull() }
                val manualFolderReady = gameRoot != null && gameRoot.isDirectory && gameRoot.canRead()
                val amethystFolderReady = AmethystBootstrapper.gameDirectory(this)?.isDirectory == true
                if (!manualFolderReady && !amethystFolderReady) { result.text = "Buka engine Amethyst sekali atau pilih Game Library Folder sebelum membuat backup."; return@button }
                if (!gameUri.isNullOrBlank() && !manualFolderReady) { result.text = "Izin Game Library Folder lama tidak tersedia. Pilih ulang folder, lalu buat backup."; return@button }
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"; putExtra(Intent.EXTRA_TITLE, "IMZYY-XL-${prefs.getString("local_username", "account")}.imzyy")
                }
                startActivityForResult(intent, 104)
            })
            root.addView(button("LOCK ACCOUNT") { sessionUnlocked = false; showAccount() })
            if (sessionUnlocked) root.addView(button("BACK TO LAUNCHER") { buildHome() })
        }
        root.addView(text("Security: password hash memakai PBKDF2-HMAC-SHA256. Backup .imzyy dienkripsi AES-256-GCM dan mencakup pengaturan/riwayat IMZYY, pengaturan launcher Amethyst yang tidak sensitif, folder .minecraft utama, profil game aktif, peta kontrol sentuh dan folder SAF manual bila tersedia. File OAuth, akun Microsoft/Xbox dan token tidak disalin; login Microsoft perlu dilakukan ulang. Simpan backup di luar folder aplikasi dan jangan bagikan password.", 11f))
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 180000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(value: String) = Base64.decode(value, Base64.NO_WRAP)
    private fun verifyPassword(password: String): Boolean = try {
        val salt = unb64(prefs.getString("password_salt", "") ?: "")
        val expected = unb64(prefs.getString("password_hash", "") ?: "")
        MessageDigest.isEqual(deriveKey(password, salt), expected)
    } catch (_: Exception) { false }

    private fun createBackup(password: String): JSONObject {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val payload = JSONObject()
        val all = prefs.all
        for ((name, value) in all) {
            if (name == "auth_token" || name == "api_base") continue
            when (value) {
                is String -> payload.put(name, JSONObject().put("t", "s").put("v", value))
                is Int -> payload.put(name, JSONObject().put("t", "i").put("v", value))
                is Long -> payload.put(name, JSONObject().put("t", "l").put("v", value))
                is Float -> payload.put(name, JSONObject().put("t", "f").put("v", value.toDouble()))
                is Boolean -> payload.put(name, JSONObject().put("t", "b").put("v", value))
                is Set<*> -> payload.put(name, JSONObject().put("t", "ss").put("v", JSONArray(value.toList())))
            }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        return JSONObject().put("format", "IMZYY-XL-BACKUP").put("version", 1)
            .put("kdf", "PBKDF2-HMAC-SHA256-180000").put("salt", b64(salt))
            .put("iv", b64(iv)).put("ciphertext", b64(encrypted))
    }

    private fun restoreBackup(backup: JSONObject, password: String) {
        require(backup.optString("format") == "IMZYY-XL-BACKUP" && backup.optInt("version") == 1) { "Format backup tidak dikenali." }
        val key = deriveKey(password, unb64(backup.getString("salt")))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(backup.getString("iv"))))
        val plain = cipher.doFinal(unb64(backup.getString("ciphertext"))).toString(Charsets.UTF_8)
        applyPrefsPayload(JSONObject(plain))
    }

    private var pendingBackupUri: Uri? = null
    private fun showBackupPasswordConfirm() {
        val input = EditText(this).apply { hint = "Password akun"; inputType = 129 }
        android.app.AlertDialog.Builder(this).setTitle("Encrypt full backup")
            .setMessage("Backup akan mengarsipkan seluruh folder game terpilih beserta pengaturan, lalu mengenkripsinya. File besar bisa membutuhkan ruang penyimpanan yang banyak.")
            .setView(input).setPositiveButton("SAVE BACKUP") { _, _ ->
                val password = input.text.toString()
                if (!verifyPassword(password)) {
                    Toast.makeText(this, "Password akun salah.", Toast.LENGTH_LONG).show()
                    pendingBackupUri = null
                    return@setPositiveButton
                }
                val uri = pendingBackupUri ?: return@setPositiveButton
                val progress = ProgressDialog(this).apply { setTitle("Creating encrypted backup"); setMessage("Archiving game files…"); setProgressStyle(ProgressDialog.STYLE_SPINNER); setCancelable(false); show() }
                thread(name = "IMZYY-Backup-Writer") {
                    try {
                        writeFullBackup(uri, password)
                        runOnUiThread { progress.dismiss(); Toast.makeText(this, "Encrypted .imzyy backup saved, including game folder and settings.", Toast.LENGTH_LONG).show() }
                    } catch (e: Exception) {
                        runOnUiThread { progress.dismiss(); Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show() }
                    } finally { pendingBackupUri = null }
                }
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun prefsPayload(): JSONObject {
        val payload = JSONObject()
        for ((name, value) in prefs.all) {
            if (name == "auth_token" || name == "api_base" || name == "game_tree_uri") continue
            when (value) {
                is String -> payload.put(name, JSONObject().put("t", "s").put("v", value))
                is Int -> payload.put(name, JSONObject().put("t", "i").put("v", value))
                is Long -> payload.put(name, JSONObject().put("t", "l").put("v", value))
                is Float -> payload.put(name, JSONObject().put("t", "f").put("v", value.toDouble()))
                is Boolean -> payload.put(name, JSONObject().put("t", "b").put("v", value))
                is Set<*> -> payload.put(name, JSONObject().put("t", "ss").put("v", JSONArray(value.toList())))
            }
        }
        amethystPreferencesSnapshot()?.let { payload.put("__amethyst_preferences", it) }
        return payload
    }

    /** Capture Amethyst's real SharedPreferences without exporting sign-in secrets. */
    private fun amethystPreferencesSnapshot(): JSONObject? = runCatching {
        val type = classLoader.loadClass("net.kdt.pojavlaunch.prefs.LauncherPreferences")
        val field = type.getDeclaredField("DEFAULT_PREF").apply { isAccessible = true }
        val source = field.get(null) as? SharedPreferences ?: return@runCatching null
        val snapshot = JSONObject()
        for ((key, value) in source.all) {
            if (isSensitiveAmethystKey(key)) continue
            val item = when (value) {
                is String -> JSONObject().put("t", "s").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> null
            }
            if (item != null) snapshot.put(key, item)
        }
        snapshot
    }.getOrNull()

    private fun isSensitiveAmethystKey(key: String): Boolean {
        val normalized = key.lowercase()
        return listOf("token", "oauth", "auth", "account", "microsoft", "xbox", "password", "credential", "secret", "refresh").any { normalized.contains(it) }
    }

    /** Merge only supported non-credential settings; never wipe unknown engine preferences. */
    private fun restoreAmethystPreferences(snapshot: JSONObject) {
        val target = runCatching {
            val type = classLoader.loadClass("net.kdt.pojavlaunch.prefs.LauncherPreferences")
            val field = type.getDeclaredField("DEFAULT_PREF").apply { isAccessible = true }
            field.get(null) as? SharedPreferences
        }.getOrNull() ?: return
        val editor = target.edit()
        val keys = snapshot.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isSensitiveAmethystKey(key)) continue
            val item = snapshot.optJSONObject(key) ?: continue
            val value = item.opt("v")
            when (item.optString("t")) {
                "s" -> if (value is String) editor.putString(key, value)
                "i" -> if (value is Number) editor.putInt(key, value.toInt())
                "l" -> if (value is Number) editor.putLong(key, value.toLong())
                "f" -> if (value is Number) editor.putFloat(key, value.toFloat())
                "b" -> if (value is Boolean) editor.putBoolean(key, value)
                "ss" -> {
                    val array = item.optJSONArray("v") ?: continue
                    val strings = mutableSetOf<String>()
                    for (i in 0 until array.length()) if (array.opt(i) is String) strings.add(array.getString(i))
                    editor.putStringSet(key, strings)
                }
            }
        }
        check(editor.commit()) { "Gagal memulihkan pengaturan Amethyst." }
    }

    private fun refreshAmethystEngineState() {
        runCatching {
            val type = classLoader.loadClass("net.kdt.pojavlaunch.prefs.LauncherPreferences")
            type.getMethod("loadPreferences", android.content.Context::class.java).invoke(null, this)
            val profiles = classLoader.loadClass("net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles")
            profiles.getMethod("load").invoke(null)
            type.getMethod("writeMGRendererSettings").invoke(null)
        }
    }

    private fun writeFullBackup(uri: Uri, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(password, salt), "AES"), GCMParameterSpec(128, iv))
        val raw = BufferedOutputStream(contentResolver.openOutputStream(uri, "w") ?: error("Tidak bisa membuka file backup."))
        raw.write(BACKUP_MAGIC)
        raw.write(salt)
        raw.write(iv)
        val encrypted = CipherOutputStream(raw, cipher)
        ZipOutputStream(BufferedOutputStream(encrypted)).use { zip ->
            zip.setLevel(1)
            zip.putNextEntry(ZipEntry("launcher/prefs.json"))
            zip.write(prefsPayload().toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val treeText = prefs.getString("game_tree_uri", null)
            if (!treeText.isNullOrBlank()) {
                val gameRoot = DocumentFile.fromTreeUri(this, Uri.parse(treeText))
                    ?: error("Game folder tidak dapat dibuka. Pilih ulang folder sebelum backup.")
                require(gameRoot.isDirectory && gameRoot.canRead()) { "Izin baca game folder sudah tidak tersedia." }
                addDirectoryToZip(zip, gameRoot, "game/")
            }
            val amethystRoot = AmethystBootstrapper.defaultGameDirectory(this)
            if (amethystRoot != null && amethystRoot.isDirectory) {
                addFileDirectoryToZip(zip, amethystRoot, "amethyst-root/")
            }
            val activeProfileHome = AmethystBootstrapper.gameDirectory(this)
            if (activeProfileHome != null && activeProfileHome.isDirectory &&
                (amethystRoot == null || activeProfileHome.canonicalFile != amethystRoot.canonicalFile)) {
                addFileDirectoryToZip(zip, activeProfileHome, "amethyst-active-profile/")
            }
            val controlMaps = AmethystBootstrapper.controlMapDirectory(this)
            if (controlMaps != null && controlMaps.isDirectory) {
                addFileDirectoryToZip(zip, controlMaps, "amethyst-controls/")
            }
            zip.putNextEntry(ZipEntry("backup-info.txt"))
            zip.write("IMZYY XL full backup v5.2. Encrypted with AES-256-GCM + PBKDF2-HMAC-SHA256. Includes local account verifier, IMZYY preferences/history, non-sensitive Amethyst launcher preferences, optional manual SAF library, base .minecraft metadata/versions/libraries/assets, active custom profile files, mods, resource packs, shader packs, worlds and touch control maps when present. OAuth, Microsoft and other authentication tokens are excluded; sign in again after restore.".toByteArray())
            zip.closeEntry()
        }
    }

    private fun addDirectoryToZip(zip: ZipOutputStream, directory: DocumentFile, prefix: String) {
        for (child in directory.listFiles()) {
            val name = child.name ?: continue
            if (name == "." || name == ".." || name.contains('/') || name.contains('\\')) continue
            val path = prefix + name
            if (child.isDirectory) {
                zip.putNextEntry(ZipEntry("$path/")); zip.closeEntry()
                addDirectoryToZip(zip, child, "$path/")
            } else if (child.isFile) {
                zip.putNextEntry(ZipEntry(path))
                contentResolver.openInputStream(child.uri)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val n = input.read(buffer); if (n < 0) break; zip.write(buffer, 0, n) }
                } ?: error("Tidak dapat membaca ${child.name} saat backup.")
                zip.closeEntry()
            }
        }
    }

    private fun addFileDirectoryToZip(zip: ZipOutputStream, directory: java.io.File, prefix: String) {
        val rootCanonical = directory.canonicalFile
        directory.walkTopDown().forEach { file ->
            if (file == directory) return@forEach
            val canonical = file.canonicalFile
            require(canonical.path == rootCanonical.path || canonical.path.startsWith(rootCanonical.path + java.io.File.separator)) {
                "Engine game directory contains an unsafe symbolic link."
            }
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            if (relative.isBlank() || relative.contains('\\') || relative.split('/').any { it == ".." || it == "." }) return@forEach
            val lowerName = relative.lowercase()
            if (file.isFile && isSensitiveAmethystPath(lowerName)) return@forEach
            val entryName = prefix + relative + if (file.isDirectory) "/" else ""
            zip.putNextEntry(ZipEntry(entryName))
            if (file.isFile) file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; zip.write(buffer, 0, n) }
            }
            zip.closeEntry()
        }
    }

    private fun isSensitiveAmethystPath(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return listOf("accounts.json", "launcher_accounts.json", "account.json", "token", "oauth", "refresh_token", "access_token", "credentials", "microsoft_auth", "xbox_auth").any { path.contains(it) || name == it }
    }

    private var pendingRestoreUri: Uri? = null

    private fun beginRestore(uri: Uri) {
        val treeText = prefs.getString("game_tree_uri", null)
        val existingTree = treeText?.let { runCatching { DocumentFile.fromTreeUri(this, Uri.parse(it)) }.getOrNull() }
        val engineHomeReady = AmethystBootstrapper.gameDirectory(this, create = true)?.isDirectory == true
        if ((existingTree == null || !existingTree.isDirectory || !existingTree.canWrite()) && !engineHomeReady) {
            pendingRestoreUri = uri
            android.app.AlertDialog.Builder(this).setTitle("Choose restore folder")
                .setMessage("Pilih folder tujuan Minecraft terlebih dahulu agar file game dari backup dapat dipulihkan.")
                .setPositiveButton("CHOOSE FOLDER") { _, _ -> chooseGameFolder() }
                .setNegativeButton("CANCEL", null).show()
            return
        }
        val input = EditText(this).apply { hint = "Password backup"; inputType = 129 }
        android.app.AlertDialog.Builder(this).setTitle("Decrypt full backup")
            .setMessage("Masukkan password yang dipakai saat membuat file .imzyy.")
            .setView(input).setPositiveButton("RESTORE") { _, _ ->
                val password = input.text.toString()
                val progress = ProgressDialog(this).apply { setTitle("Restoring backup"); setMessage("Decrypting and restoring files…"); setProgressStyle(ProgressDialog.STYLE_SPINNER); setCancelable(false); show() }
                thread(name = "IMZYY-Backup-Restorer") {
                    try {
                        restoreBackupFile(uri, password)
                        val restoredVersion = prefs.getString("version", "latest-release") ?: "latest-release"
                        val runtimeRecovery = " Runtime Java app-private akan diperiksa dan diunduh kembali oleh Amethyst saat diperlukan."
                        runOnUiThread {
                            progress.dismiss()
                            selectedVersion = restoredVersion
                            files.clear(); files.addAll(prefs.getStringSet("managed_files", emptySet()) ?: emptySet())
                            sessionUnlocked = true
                            Toast.makeText(this, "Akun, pengaturan, dan data game dipulihkan bila tersedia.$runtimeRecovery Login Microsoft perlu dilakukan ulang.", Toast.LENGTH_LONG).show()
                            buildHome()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { progress.dismiss(); Toast.makeText(this, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun restoreBackupFile(uri: Uri, password: String) {
        val source = BufferedInputStream(contentResolver.openInputStream(uri) ?: error("File backup tidak dapat dibuka."))
        source.mark(4)
        val magic = ByteArray(4)
        val count = source.read(magic)
        source.reset()
        if (count != 4 || !magic.contentEquals(BACKUP_MAGIC)) {
            source.close()
            val legacy = JSONObject(contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            restoreBackup(legacy, password)
            return
        }
        val headerMagic = ByteArray(4); source.readFully(headerMagic)
        val salt = ByteArray(16); source.readFully(salt)
        val iv = ByteArray(12); source.readFully(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(password, salt), "AES"), GCMParameterSpec(128, iv))
        val zip = ZipInputStream(CipherInputStream(source, cipher))
        var prefsJson: JSONObject? = null
        var prefsApplied = false
        var activeProfileTarget: java.io.File? = null
        val tree = prefs.getString("game_tree_uri", null)?.let { runCatching { DocumentFile.fromTreeUri(this, Uri.parse(it)) }.getOrNull() }
        zip.use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val name = entry.name
                if (name.startsWith("/") || name.contains('\\') || name.split('/').any { it == ".." || it == "." }) error("Backup memiliki path yang tidak aman.")
                when {
                    name == "launcher/prefs.json" -> {
                        prefsJson = JSONObject(zin.readBytes().toString(Charsets.UTF_8))
                        applyPrefsPayload(prefsJson!!, refreshEngine = false)
                        prefsApplied = true
                    }
                    name.startsWith("game/") -> {
                        val rel = name.removePrefix("game/").trimEnd('/')
                        if (rel.isNotBlank()) {
                            val targetTree = tree ?: error("Backup berisi folder SAF manual. Pilih Game Library Folder sebelum restore, lalu ulangi.")
                            writeZipEntryToTree(targetTree, rel, entry.isDirectory, zin)
                        }
                    }
                    name.startsWith("amethyst-root/") -> {
                        val rel = name.removePrefix("amethyst-root/").trimEnd('/')
                        val engineRoot = AmethystBootstrapper.defaultGameDirectory(this, create = true)
                            ?: error("Folder .minecraft utama Amethyst belum dapat disiapkan.")
                        if (rel.isNotBlank() && !isSensitiveAmethystPath(rel.lowercase())) writeZipEntryToFile(engineRoot, rel, entry.isDirectory, zin)
                    }
                    name.startsWith("amethyst-active-profile/") -> {
                        val rel = name.removePrefix("amethyst-active-profile/").trimEnd('/')
                        if (activeProfileTarget == null) {
                            runCatching {
                                val profiles = classLoader.loadClass("net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles")
                                profiles.getMethod("load").invoke(null)
                            }
                            activeProfileTarget = AmethystBootstrapper.gameDirectory(this, create = true)
                                ?: error("Folder profil aktif Amethyst belum dapat disiapkan.")
                        }
                        if (rel.isNotBlank() && !isSensitiveAmethystPath(rel.lowercase())) writeZipEntryToFile(activeProfileTarget!!, rel, entry.isDirectory, zin)
                    }
                    // Backward compatibility for encrypted v5.0/v5.1 backups.
                    name.startsWith("amethyst-game/") -> {
                        val rel = name.removePrefix("amethyst-game/").trimEnd('/')
                        val engineHome = AmethystBootstrapper.gameDirectory(this, create = true)
                            ?: error("Folder game Amethyst belum dapat ditemukan. Buka engine sekali, lalu ulangi restore.")
                        if (rel.isNotBlank() && !isSensitiveAmethystPath(rel.lowercase())) writeZipEntryToFile(engineHome, rel, entry.isDirectory, zin)
                    }
                    name.startsWith("amethyst-controls/") -> {
                        val rel = name.removePrefix("amethyst-controls/").trimEnd('/')
                        val controlHome = AmethystBootstrapper.controlMapDirectory(this, create = true)
                            ?: error("Folder kontrol sentuh Amethyst belum dapat disiapkan.")
                        if (rel.isNotBlank()) writeZipEntryToFile(controlHome, rel, entry.isDirectory, zin)
                    }
                }
                zin.closeEntry()
            }
        }
        val payload = prefsJson ?: error("Backup tidak berisi data akun launcher.")
        if (!prefsApplied) applyPrefsPayload(payload, refreshEngine = false)
        refreshAmethystEngineState()
    }

    private fun writeZipEntryToFile(root: java.io.File, relative: String, directory: Boolean, input: ZipInputStream) {
        val parts = relative.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." && !it.contains('\\') }) { "Nama file backup engine tidak valid." }
        val rootCanonical = root.canonicalFile
        val output = java.io.File(root, parts.joinToString(java.io.File.separator)).canonicalFile
        require(output.path.startsWith(rootCanonical.path + java.io.File.separator)) { "Path restore keluar dari folder engine." }
        if (directory) {
            require(output.mkdirs() || output.isDirectory) { "Tidak dapat membuat folder engine ${output.name}." }
            return
        }
        require(output.parentFile?.let { it.mkdirs() || it.isDirectory } == true) { "Tidak dapat membuat direktori restore engine." }
        output.outputStream().buffered().use { destination ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; destination.write(buffer, 0, n) }
        }
    }

    private fun writeZipEntryToTree(root: DocumentFile, relative: String, directory: Boolean, input: ZipInputStream) {
        val parts = relative.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." && !it.contains('\\') }) { "Nama file backup tidak valid." }
        var parent = root
        for (part in parts.dropLast(if (directory) 0 else 1)) {
            parent = parent.findFile(part)?.takeIf { it.isDirectory } ?: parent.createDirectory(part)
                ?: error("Tidak bisa membuat folder restore $part.")
        }
        if (directory) return
        val name = parts.last()
        parent.findFile(name)?.delete()
        val doc = parent.createFile("application/octet-stream", name) ?: error("Tidak bisa membuat file restore $name.")
        contentResolver.openOutputStream(doc.uri, "w")?.use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
        } ?: error("Tidak bisa menulis file restore $name.")
    }

    private fun applyPrefsPayload(payload: JSONObject, refreshEngine: Boolean = true) {
        val preservedTreeUri = prefs.getString("game_tree_uri", null)
        val amethystSnapshot = payload.optJSONObject("__amethyst_preferences")
        val editor = prefs.edit().clear()
        val keys = payload.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            if (name == "game_tree_uri" || name == "__amethyst_preferences") continue
            val item = payload.getJSONObject(name)
            val value = item.get("v")
            when (item.getString("t")) {
                "s" -> editor.putString(name, value.toString())
                "i" -> editor.putInt(name, (value as Number).toInt())
                "l" -> editor.putLong(name, (value as Number).toLong())
                "f" -> editor.putFloat(name, (value as Number).toFloat())
                "b" -> editor.putBoolean(name, value as Boolean)
                "ss" -> { val arr = item.getJSONArray("v"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i)); editor.putStringSet(name, set) }
            }
        }
        if (!preservedTreeUri.isNullOrBlank()) editor.putString("game_tree_uri", preservedTreeUri)
        check(editor.commit()) { "Gagal menyimpan pengaturan backup." }
        if (amethystSnapshot != null) restoreAmethystPreferences(amethystSnapshot)
        if (refreshEngine) refreshAmethystEngineState()
        require(!prefs.getString("local_username", null).isNullOrBlank()) { "Backup tidak mengandung akun lokal." }
    }

    companion object { private val BACKUP_MAGIC = byteArrayOf(73, 77, 90, 50) }

    private fun chooseGameFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, 103)
    }

    private fun pickFiles(mime: String, request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, request)
    }

    @Deprecated("Legacy callback retained for Android API 26 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == 104) {
            val uri = data.data ?: return
            // Password is intentionally never persisted; ask again before export.
            pendingBackupUri = uri
            showBackupPasswordConfirm()
            return
        }
        if (requestCode == 105) {
            val uri = data.data ?: return
            beginRestore(uri)
            return
        }
        if (requestCode == 103) {
            val tree = data.data ?: return
            try { contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            prefs.edit().putString("game_tree_uri", tree.toString()).apply()
            Toast.makeText(this, "Game library folder selected. After reinstall, select this folder again if Android asks for access.", Toast.LENGTH_LONG).show()
            val pending = pendingRestoreUri
            if (pending != null) { pendingRestoreUri = null; beginRestore(pending) } else buildHome()
            return
        }
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
        data.data?.let { if (!uris.contains(it)) uris.add(it) }
        val targetName = when (requestCode) { 101 -> "mods"; 102 -> "resourcepacks"; 107 -> "shaderpacks"; else -> "imports" }
        val engineHome = AmethystBootstrapper.gameDirectory(this, create = true)
        val engineTarget = engineHome?.let { java.io.File(it, targetName) }
        val usableEngineTarget = engineTarget?.takeIf { it.isDirectory || it.mkdirs() }
        val treeText = prefs.getString("game_tree_uri", null)
        val gameRoot = treeText?.let { runCatching { DocumentFile.fromTreeUri(this, Uri.parse(it)) }.getOrNull() }
        val safTarget = if (usableEngineTarget == null && gameRoot != null && gameRoot.isDirectory && gameRoot.canWrite()) {
            gameRoot.findFile(targetName)?.takeIf { it.isDirectory } ?: gameRoot.createDirectory(targetName)
        } else null
        if (usableEngineTarget == null && safTarget == null) {
            Toast.makeText(this, "Amethyst game folder belum siap. Buka engine sekali atau pilih Game Library Folder untuk file manual.", Toast.LENGTH_LONG).show()
            showFiles(); return
        }
        var imported = 0
        val destinationLabel = if (usableEngineTarget != null) "Amethyst .minecraft/$targetName" else "SAF/$targetName"
        uris.forEach { uri ->
            try {
                val name = displayName(uri) ?: "import-${System.currentTimeMillis()}"
                val lower = name.lowercase()
                val valid = when (requestCode) { 101 -> lower.endsWith(".jar"); else -> lower.endsWith(".zip") }
                if (!valid || name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) return@forEach
                if (usableEngineTarget != null) {
                    val outputFile = java.io.File(usableEngineTarget, name).canonicalFile
                    require(outputFile.parentFile == usableEngineTarget.canonicalFile) { "Nama file tujuan tidak valid." }
                    contentResolver.openInputStream(uri)?.use { input -> outputFile.outputStream().buffered().use { output -> input.copyTo(output) } }
                        ?: error("Tidak bisa membaca $name")
                } else {
                    val target = safTarget ?: return@forEach
                    target.findFile(name)?.delete()
                    val destination = target.createFile("application/octet-stream", name) ?: return@forEach
                    contentResolver.openInputStream(uri)?.use { input ->
                        contentResolver.openOutputStream(destination.uri, "w")?.use { output -> input.copyTo(output) }
                            ?: error("Tidak bisa membuka file tujuan $name")
                    } ?: error("Tidak bisa membaca file $name")
                }
                files.add("$targetName/$name")
                imported++
            } catch (_: Exception) {}
        }
        prefs.edit().putStringSet("managed_files", files.toSet()).apply()
        Toast.makeText(this, "Copied $imported file(s) to $destinationLabel.", Toast.LENGTH_LONG).show()
        showFiles()
    }

    private fun displayName(uri: Uri): String? {
        var result: String? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) result = cursor.getString(0)
        }
        return result ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun showFiles() {
        initPage("Managed Files")
        root.addView(text("Imported into the active Amethyst game directory, or the manual SAF folder when the engine has not created one yet.", 13f).apply { setPadding(dp(4), 0, dp(4), dp(10)) })
        if (files.isEmpty()) {
            addPanel(panel("Nothing imported yet", "Use Mods, Resource packs or Shader packs on the home screen."))
        } else {
            root.addView(listCard(files.map { infoRow(it, "") }), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        }
        root.addView(text("Mods go to mods/, resource packs to resourcepacks/ and shader packs to shaderpacks/. Check that each file matches your Minecraft version and mod loader.", 12f).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) })
    }

    private fun showPerformance() {
        initPage("Performance & Optimization")
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val totalMb = memory.totalMem / (1024 * 1024)
        val availableMb = memory.availMem / (1024 * 1024)
        val used = 1f - memory.availMem.toFloat() / memory.totalMem.coerceAtLeast(1L).toFloat()

        val resources = panel("Memory", "Reported by Android when this page opened.")
        val meter = Meter(this).apply { warn = memory.lowMemory || used > 0.88f }
        resources.addView(meter, LinearLayout.LayoutParams(MATCH, dp(6)).apply { topMargin = dp(10); bottomMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(statTile("Total", "$totalMb MB"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(5) })
        row.addView(statTile("Free", "$availableMb MB"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(5) })
        resources.addView(row)
        resources.addView(text(if (memory.lowMemory) "Android reports low memory. Close other apps before playing." else "Memory pressure is normal.", 12f).apply { setPadding(dp(1), dp(10), 0, 0) })
        addPanel(resources)
        meter.post { meter.setProgress(used, true) }

        val maintenance = panel("Launcher cache", "Removes only temporary files owned by IMZYY XL. Worlds, mods, resource packs and Java runtimes are never touched.")
        val cacheText = text("Measuring\u2026", 14f, true)
        maintenance.addView(cacheText)
        addAction(maintenance, "CLEAR LAUNCHER TEMP CACHE", { clearLauncherCache() })
        addPanel(maintenance)
        thread(name = "IMZYY-cache-size") {
            val cacheBytes = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            runOnUiThread { if (alive() && page == "Performance & Optimization") cacheText.text = "Temporary cache: ${formatSize(cacheBytes)}" }
        }

        val tuning = panel("In-game performance", "Engine status: ${if (engineReady) "ready in this APK" else "build integration required"}")
        tuning.addView(text("Renderer, Java runtime, memory allocation, resolution and render distance are applied by the real engine from the active Amethyst profile, not by cosmetic switches here.", 12f))
        addAction(tuning, "OPEN RENDERER & GAME SETTINGS", { openAmethystEngine() }, primary = true)
        addAction(tuning, "DEVICE MONITOR", { showDevice() })
        addPanel(tuning)
    }

    private fun clearLauncherCache() {
        Toast.makeText(this, "Clearing launcher cache\u2026", Toast.LENGTH_SHORT).show()
        thread(name = "IMZYY-cache-clear") {
            val bytesBefore = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val cleared = runCatching {
                val removed = cacheDir.deleteRecursively()
                val recreated = cacheDir.mkdirs() || cacheDir.isDirectory
                removed && recreated
            }.getOrDefault(false)
            runOnUiThread {
                if (!alive()) return@runOnUiThread
                val message = if (cleared) "Launcher cache cleared (${formatSize(bytesBefore)}). Game data was kept." else "Could not clear all temporary launcher files."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                if (page == "Performance & Optimization") showPerformance()
            }
        }
    }

    private fun showDevice() {
        initPage("Device Monitor")
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val mem = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        root.addView(listCard(listOf(
            infoRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}"),
            infoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            infoRow("Architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
            infoRow("Total RAM", "${mem.totalMem / (1024 * 1024)} MB"),
            infoRow("Available RAM", "${mem.availMem / (1024 * 1024)} MB"),
            infoRow("Memory state", if (mem.lowMemory) "Low" else "Normal", if (mem.lowMemory) Ui.WARN else Ui.ACCENT)
        )), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        root.addView(text("Temperature, GPU telemetry and live game FPS are not exposed reliably to a launcher, so they are not shown instead of guessing.", 12f).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) })
    }
}
