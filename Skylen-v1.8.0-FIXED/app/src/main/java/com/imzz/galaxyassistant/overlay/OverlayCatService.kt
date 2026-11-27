package com.imzz.galaxyassistant.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.imzz.galaxyassistant.MainActivity
import com.imzz.galaxyassistant.assistant.AssistantEntryActivity
import com.imzz.galaxyassistant.mascot.CatMascotView
import kotlin.math.abs

/** User-started draggable mascot overlay. Tap the cat to open the voice/chat surface. */
class OverlayCatService : Service() {
    private lateinit var windowManager: WindowManager
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) showCat()
        return START_STICKY
    }

    private fun showCat() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val size = (116 * density).toInt()
        val maxX = (resources.displayMetrics.widthPixels - size).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - size).coerceAtLeast(0)
        val startX = prefs.getInt(KEY_X, 10).coerceIn(0, maxX)
        val startY = prefs.getInt(KEY_Y, 220).coerceIn(0, maxY)

        val mascot = CatMascotView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setMood(CatMascotView.Mood.IDLE)
            say("Geser gue ke mana aja. Ketuk buat ngobrol.", 3600)
        }
        val wrapper = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(24, 102, 91, 190))
                cornerRadius = 64 * density
                setStroke((1 * density).toInt(), Color.argb(95, 165, 151, 255))
            }
            addView(mascot, FrameLayout.LayoutParams(-1, -1))
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else legacyWindowType()
        val lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
        params = lp
        root = wrapper
        wrapper.setOnTouchListener(DragTouchListener(wrapper, lp, mascot))
        mascot.setOnTouchListener(DragTouchListener(wrapper, lp, mascot))
        runCatching { windowManager.addView(wrapper, lp) }.onFailure { stopSelf() }
    }

    private inner class DragTouchListener(
        private val target: View,
        private val layout: WindowManager.LayoutParams,
        private val mascot: CatMascotView
    ) : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var originX = 0
        private var originY = 0
        private var moved = false
        private val slop = (8 * resources.displayMetrics.density)

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    originX = layout.x
                    originY = layout.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        val metrics = resources.displayMetrics
                        val maxX = (metrics.widthPixels - target.width).coerceAtLeast(0)
                        val maxY = (metrics.heightPixels - target.height).coerceAtLeast(0)
                        layout.x = (originX + dx.toInt()).coerceIn(0, maxX)
                        layout.y = (originY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { windowManager.updateViewLayout(target, layout) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putInt(KEY_X, layout.x).putInt(KEY_Y, layout.y).apply()
                        mascot.setMood(CatMascotView.Mood.HAPPY, 1100)
                    } else {
                        openAssistant()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }
    }

    private fun openAssistant() {
        val intent = Intent(this, AssistantEntryActivity::class.java).apply {
            putExtra(AssistantEntryActivity.EXTRA_AUTO_LISTEN, true)
            putExtra(AssistantEntryActivity.EXTRA_SOURCE, "floating_cat")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Skylen floating assistant", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Status overlay kucing Skylen yang diaktifkan pengguna"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else legacyNotificationBuilder()
        return builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Skylen aktif")
            .setContentText("Kucing mengambang aktif. Ketuk untuk ngobrol, geser untuk memindahkan.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun legacyWindowType(): Int = WindowManager.LayoutParams.TYPE_PHONE

    @Suppress("DEPRECATION")
    private fun legacyNotificationBuilder(): Notification.Builder = Notification.Builder(this)

    override fun onDestroy() {
        root?.let { view -> runCatching { windowManager.removeView(view) } }
        root = null
        params = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.imzz.galaxyassistant.overlay.STOP"
        private const val CHANNEL_ID = "skylen_overlay"
        private const val NOTIFICATION_ID = 1842
        private const val PREFS = "skylen_overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        fun start(context: Context) {
            val intent = Intent(context, OverlayCatService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayCatService::class.java).setAction(ACTION_STOP))
        }
    }
}
