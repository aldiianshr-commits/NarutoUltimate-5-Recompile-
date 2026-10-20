package com.aldiian.naruto5.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kr.co.iefriends.pcsx2.NativeApp
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Multi-touch on-screen DualShock 2. In play mode it drives the core via NativeApp.setPadButton;
 * in edit mode controls can be dragged, selected and resized (size/opacity come from the host UI).
 */
class TouchControllerView(context: Context) : View(context) {

    var layout: TouchLayout = TouchLayout()
        set(v) { field = v; invalidate() }

    var editMode = false
        set(v) { field = v; releaseAll(); invalidate() }

    var selected: ControlId? = null
        private set

    var onSelect: ((ControlId?) -> Unit)? = null
    var onLayoutChanged: ((TouchLayout) -> Unit)? = null
    /** Called when a touch lands on empty space in play mode (used to show the menu button). */
    var onEmptyTap: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    // pointerId -> what it is touching
    private sealed class Touch {
        data class Button(val id: ControlId) : Touch()
        data class Dpad(var keys: Set<PadKey>) : Touch()
        data class Stick(val id: ControlId, var dx: Float, var dy: Float) : Touch()
        data class Drag(val id: ControlId, val offX: Float, val offY: Float) : Touch()
    }
    private val touches = HashMap<Int, Touch>()
    private val pressed = HashMap<PadKey, Int>() // key -> refcount

    private fun center(id: ControlId): Pair<Float, Float> {
        val p = layout.placement(id)
        return p.x * width to p.y * height
    }

    private fun radius(id: ControlId): Float =
        id.baseSizeDp * density * layout.placement(id).scale * layout.globalScale / 2f

    private fun visibleControls() = ControlId.entries.filter { layout.placement(it).visible || editMode }

    private fun hit(x: Float, y: Float): ControlId? =
        visibleControls().filter { id ->
            val (cx, cy) = center(id)
            hypot(x - cx, y - cy) <= radius(id) * if (id.kind == ControlKind.BUTTON) 1.25f else 1.1f
        }.minByOrNull { id -> val (cx, cy) = center(id); hypot(x - cx, y - cy) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!layout.enabled && !editMode) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        invalidate()
        return true
    }

    private fun down(pid: Int, x: Float, y: Float) {
        val id = hit(x, y)
        if (editMode) {
            selected = id
            onSelect?.invoke(id)
            if (id != null) { val (cx, cy) = center(id); touches[pid] = Touch.Drag(id, x - cx, y - cy) }
            return
        }
        if (id == null) { onEmptyTap?.invoke(); return }
        when (id.kind) {
            ControlKind.BUTTON -> { touches[pid] = Touch.Button(id); press(id.key!!) }
            ControlKind.DPAD -> { val t = Touch.Dpad(emptySet()); touches[pid] = t; updateDpad(t, x, y) }
            ControlKind.STICK_LEFT, ControlKind.STICK_RIGHT -> { val t = Touch.Stick(id, 0f, 0f); touches[pid] = t; updateStick(t, x, y) }
        }
    }

    private fun move(pid: Int, x: Float, y: Float) {
        when (val t = touches[pid]) {
            is Touch.Drag -> {
                if (width > 0 && height > 0) {
                    layout = layout.moved(t.id, (x - t.offX) / width, (y - t.offY) / height)
                    onLayoutChanged?.invoke(layout)
                }
            }
            is Touch.Dpad -> updateDpad(t, x, y)
            is Touch.Stick -> updateStick(t, x, y)
            is Touch.Button -> {
                // Sliding between face buttons (common in fighting games).
                val now = hit(x, y)
                if (now != null && now != t.id && now.kind == ControlKind.BUTTON) {
                    release(t.id.key!!); touches[pid] = Touch.Button(now); press(now.key!!)
                }
            }
            null -> {}
        }
    }

    private fun up(pid: Int) {
        when (val t = touches.remove(pid)) {
            is Touch.Button -> release(t.id.key!!)
            is Touch.Dpad -> t.keys.forEach { release(it) }
            is Touch.Stick -> sendStick(t.id, 0f, 0f)
            else -> {}
        }
    }

    private fun updateDpad(t: Touch.Dpad, x: Float, y: Float) {
        val (cx, cy) = center(ControlId.DPAD)
        val dx = x - cx; val dy = y - cy
        val r = radius(ControlId.DPAD)
        val keys = if (hypot(dx, dy) < r * 0.2f) emptySet() else {
            val ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).let { if (it < 0) it + 360 else it }
            // 8 sectors of 45°, centred on the axes and diagonals.
            when (((ang + 22.5) / 45).toInt() % 8) {
                0 -> setOf(PadKey.RIGHT); 1 -> setOf(PadKey.RIGHT, PadKey.DOWN); 2 -> setOf(PadKey.DOWN)
                3 -> setOf(PadKey.DOWN, PadKey.LEFT); 4 -> setOf(PadKey.LEFT); 5 -> setOf(PadKey.LEFT, PadKey.UP)
                6 -> setOf(PadKey.UP); else -> setOf(PadKey.UP, PadKey.RIGHT)
            }
        }
        (t.keys - keys).forEach { release(it) }
        (keys - t.keys).forEach { press(it) }
        t.keys = keys
    }

    private fun updateStick(t: Touch.Stick, x: Float, y: Float) {
        val (cx, cy) = center(t.id)
        val r = radius(t.id) * 0.75f
        var dx = (x - cx) / r; var dy = (y - cy) / r
        val m = hypot(dx, dy)
        if (m > 1f) { dx /= m; dy /= m }
        t.dx = dx; t.dy = dy
        sendStick(t.id, dx, dy)
    }

    private fun sendStick(id: ControlId, x: Float, y: Float) {
        val h = StickMath.halfAxes(x, y, deadzone = 0.08f)
        val left = id == ControlId.STICK_L
        fun s(k: PadKey, v: Int) = NativeApp.setPadButton(k.code, v, v > 0)
        if (left) { s(PadKey.LS_UP, h.up); s(PadKey.LS_RIGHT, h.right); s(PadKey.LS_DOWN, h.down); s(PadKey.LS_LEFT, h.left) }
        else { s(PadKey.RS_UP, h.up); s(PadKey.RS_RIGHT, h.right); s(PadKey.RS_DOWN, h.down); s(PadKey.RS_LEFT, h.left) }
    }

    private fun press(k: PadKey) {
        val c = (pressed[k] ?: 0) + 1
        pressed[k] = c
        if (c == 1) {
            NativeApp.setPadButton(k.code, 0, true)
            if (layout.hapticFeedback) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun release(k: PadKey) {
        val c = (pressed[k] ?: 0) - 1
        if (c <= 0) { pressed.remove(k); NativeApp.setPadButton(k.code, 0, false) } else pressed[k] = c
    }

    fun releaseAll() {
        touches.values.forEach { if (it is Touch.Stick) sendStick(it.id, 0f, 0f) }
        touches.clear()
        pressed.keys.toList().forEach { NativeApp.setPadButton(it.code, 0, false) }
        pressed.clear()
        invalidate()
    }

    // ---- drawing --------------------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        if (!layout.enabled && !editMode) return
        val alpha = (layout.opacity * 255).toInt().coerceIn(25, 255)
        visibleControls().forEach { id ->
            val (cx, cy) = center(id)
            val r = radius(id)
            val hidden = !layout.placement(id).visible
            val a = if (hidden) alpha / 3 else alpha
            val isSel = editMode && id == selected
            stroke.color = if (isSel) Color.rgb(255, 122, 26) else Color.WHITE
            stroke.alpha = if (isSel) 255 else a
            when (id.kind) {
                ControlKind.DPAD -> drawDpad(canvas, cx, cy, r, a)
                ControlKind.STICK_LEFT, ControlKind.STICK_RIGHT -> drawStick(canvas, id, cx, cy, r, a)
                ControlKind.BUTTON -> drawButton(canvas, id, cx, cy, r, a)
            }
            if (isSel) canvas.drawCircle(cx, cy, r * 1.15f, stroke)
        }
    }

    private fun drawButton(c: Canvas, id: ControlId, cx: Float, cy: Float, r: Float, a: Int) {
        val down = id.key != null && (pressed[id.key] ?: 0) > 0
        fill.color = if (down) Color.WHITE else Color.BLACK
        fill.alpha = if (down) (a * 0.8f).toInt() else (a * 0.55f).toInt()
        val shoulder = id in setOf(ControlId.L1, ControlId.L2, ControlId.R1, ControlId.R2, ControlId.SELECT, ControlId.START)
        if (shoulder) {
            val rect = RectF(cx - r * 1.25f, cy - r * 0.7f, cx + r * 1.25f, cy + r * 0.7f)
            c.drawRoundRect(rect, r * 0.5f, r * 0.5f, fill)
            c.drawRoundRect(rect, r * 0.5f, r * 0.5f, stroke)
        } else {
            c.drawCircle(cx, cy, r, fill)
            c.drawCircle(cx, cy, r, stroke)
        }
        text.color = when (id) {
            ControlId.TRIANGLE -> Color.rgb(64, 226, 160)
            ControlId.CIRCLE -> Color.rgb(255, 102, 102)
            ControlId.CROSS -> Color.rgb(124, 178, 232)
            ControlId.SQUARE -> Color.rgb(255, 105, 248)
            else -> Color.WHITE
        }
        text.alpha = a.coerceAtLeast(90)
        val label = id.key!!.label
        text.textSize = if (label.length > 2) r * 0.42f else r * 0.9f
        c.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawDpad(c: Canvas, cx: Float, cy: Float, r: Float, a: Int) {
        val arm = r * 0.36f
        val active = pressed.keys
        fun arm(key: PadKey, rect: RectF) {
            fill.color = if (key in active) Color.WHITE else Color.BLACK
            fill.alpha = if (key in active) (a * 0.8f).toInt() else (a * 0.55f).toInt()
            c.drawRoundRect(rect, arm * 0.3f, arm * 0.3f, fill)
            c.drawRoundRect(rect, arm * 0.3f, arm * 0.3f, stroke)
        }
        arm(PadKey.UP, RectF(cx - arm, cy - r, cx + arm, cy - arm * 0.6f))
        arm(PadKey.DOWN, RectF(cx - arm, cy + arm * 0.6f, cx + arm, cy + r))
        arm(PadKey.LEFT, RectF(cx - r, cy - arm, cx - arm * 0.6f, cy + arm))
        arm(PadKey.RIGHT, RectF(cx + arm * 0.6f, cy - arm, cx + r, cy + arm))
    }

    private fun drawStick(c: Canvas, id: ControlId, cx: Float, cy: Float, r: Float, a: Int) {
        fill.color = Color.BLACK; fill.alpha = (a * 0.4f).toInt()
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, stroke)
        val t = touches.values.filterIsInstance<Touch.Stick>().firstOrNull { it.id == id }
        val kx = cx + (t?.dx ?: 0f) * r * 0.75f
        val ky = cy + (t?.dy ?: 0f) * r * 0.75f
        fill.color = Color.WHITE; fill.alpha = (a * 0.7f).toInt()
        c.drawCircle(kx, ky, r * 0.42f, fill)
        text.color = Color.BLACK; text.alpha = 200; text.textSize = r * 0.28f
        c.drawText(if (id == ControlId.STICK_L) "L" else "R", kx, ky - (text.descent() + text.ascent()) / 2, text)
    }
}
