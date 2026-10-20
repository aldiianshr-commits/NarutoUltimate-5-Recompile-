package com.aldiian.naruto5.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aldiian.naruto5.input.LayoutEditorPanel
import com.aldiian.naruto5.input.TouchControllerView
import com.aldiian.naruto5.storage.AppPrefs

/** Full-screen editor for the on-screen controller, reachable from the launcher. */
class LayoutEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val prefs = AppPrefs(this)
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(20, 24, 38)) }
        root.addView(TextView(this).apply {
            text = "Pratinjau layar game (16:10)"; setTextColor(Color.argb(90, 255, 255, 255)); textSize = 28f; gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
        val touch = TouchControllerView(this).apply { layout = prefs.touchLayout(); editMode = true }
        root.addView(touch, FrameLayout.LayoutParams(-1, -1))
        val panel = LayoutEditorPanel(this, touch) { l -> prefs.saveTouchLayout(l); finish() }
        root.addView(panel, FrameLayout.LayoutParams((420 * dp).toInt(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (12 * dp).toInt() })
        setContentView(root)
    }
}
