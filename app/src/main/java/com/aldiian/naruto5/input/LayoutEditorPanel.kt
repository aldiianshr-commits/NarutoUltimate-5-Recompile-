package com.aldiian.naruto5.input

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Floating toolbar for editing the on-screen controller: drag controls on the canvas, then use
 * the sliders for the selected control's size, global size and opacity. Used both by the
 * stand-alone editor and inside the in-game pause menu.
 */
class LayoutEditorPanel(
    context: Context,
    private val view: TouchControllerView,
    private val onDone: (TouchLayout) -> Unit,
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val selLabel = TextView(context)
    private val sizeBar = SeekBar(context)
    private val visible = CheckBox(context)

    init {
        orientation = VERTICAL
        setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
        background = GradientDrawable().apply { setColor(Color.argb(225, 16, 20, 34)); cornerRadius = 16 * dp }
        elevation = 8 * dp

        addView(text("Edit tata letak — seret tombol untuk memindahkan, ketuk untuk memilih", 13f, bold = true))
        selLabel.setTextColor(Color.rgb(255, 170, 90)); selLabel.textSize = 13f
        addView(selLabel)

        addView(row("Ukuran tombol terpilih", sizeBar, 50, 200) { v ->
            view.selected?.let { id -> view.layout = view.layout.resized(id, v / 100f) }
        })
        visible.text = "Tampilkan tombol ini"; visible.setTextColor(Color.WHITE)
        visible.setOnCheckedChangeListener { _, checked ->
            view.selected?.let { id -> if (view.layout.placement(id).visible != checked) view.layout = view.layout.toggled(id, checked) }
        }
        addView(visible)
        val global = SeekBar(context)
        addView(row("Ukuran semua tombol", global, 50, 200, (view.layout.globalScale * 100).toInt()) { v ->
            view.layout = view.layout.copy(globalScale = v / 100f)
        })
        val opacity = SeekBar(context)
        addView(row("Transparansi", opacity, 10, 100, (view.layout.opacity * 100).toInt()) { v ->
            view.layout = view.layout.copy(opacity = v / 100f)
        })
        val haptic = CheckBox(context).apply {
            text = "Getar saat tombol ditekan"; setTextColor(Color.WHITE); isChecked = view.layout.hapticFeedback
            setOnCheckedChangeListener { _, c -> view.layout = view.layout.copy(hapticFeedback = c) }
        }
        addView(haptic)

        val buttons = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.END }
        buttons.addView(Button(context).apply {
            text = "Reset"
            setOnClickListener {
                view.layout = TouchLayout(opacity = view.layout.opacity, enabled = view.layout.enabled)
                global.progress = 100; opacity.progress = (view.layout.opacity * 100).toInt(); refresh(null)
            }
        })
        buttons.addView(Button(context).apply { text = "Selesai"; setOnClickListener { onDone(view.layout) } })
        addView(buttons)

        view.onSelect = { refresh(it) }
        refresh(null)
    }

    private fun refresh(id: ControlId?) {
        if (id == null) {
            selLabel.text = "Belum ada tombol dipilih"
            sizeBar.isEnabled = false; visible.isEnabled = false
        } else {
            val p = view.layout.placement(id)
            selLabel.text = "Dipilih: ${id.key?.label ?: id.name}"
            sizeBar.isEnabled = true; visible.isEnabled = true
            sizeBar.progress = (p.scale * 100).toInt() - 50
            visible.isChecked = p.visible
        }
    }

    private fun text(s: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun row(label: String, bar: SeekBar, min: Int, max: Int, initial: Int = 100, onChange: (Int) -> Unit): View {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(label, 12f), LayoutParams((150 * dp).toInt(), LayoutParams.WRAP_CONTENT))
        bar.max = max - min
        bar.progress = (initial - min).coerceIn(0, max - min)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p + min) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        row.addView(bar, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}
