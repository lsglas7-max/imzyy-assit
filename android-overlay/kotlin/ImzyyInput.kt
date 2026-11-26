package id.imzyy.xl

import android.os.SystemClock
import android.view.KeyEvent
import kr.co.donghyun.flamelauncher.presentation.MinecraftActivity

/**
 * Input injection for the IMZYY pad. This default sends Android key events to the activity;
 * scripts/prepare-imzyy.py rewrites key() to call the upstream key sender when it finds one.
 */
object ImzyyInput {
    fun key(a: MinecraftActivity, glfw: Int, droid: Int, down: Boolean) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, droid, 0))
    }

    fun mouse(a: MinecraftActivity, button: Int, down: Boolean) {
        if (!a.isGrabbing) a.sendCursorPos(a.currentCursorX, a.currentCursorY)
        a.sendMouseButton(button, if (down) 1 else 0)
    }
}
