package com.aldiian.naruto5.input

/**
 * DualShock 2 inputs as the ARMSX2 core expects them in NativeApp.setPadButton(index, range, pressed)
 * (see applyPadButton in core/platforms/android/app/src/main/cpp/native-lib.cpp). The indices
 * reuse Android KeyEvent codes for buttons; 110-123 are the core's analog half-axes.
 */
enum class PadKey(val code: Int, val label: String, val analog: Boolean = false) {
    UP(19, "↑"), RIGHT(22, "→"), DOWN(20, "↓"), LEFT(21, "←"),
    TRIANGLE(100, "△"), CIRCLE(97, "○"), CROSS(96, "✕"), SQUARE(99, "□"),
    SELECT(109, "SELECT"), START(108, "START"),
    L1(102, "L1"), L2(104, "L2"), R1(103, "R1"), R2(105, "R2"), L3(106, "L3"), R3(107, "R3"),
    LS_UP(110, "LS↑", true), LS_RIGHT(111, "LS→", true), LS_DOWN(112, "LS↓", true), LS_LEFT(113, "LS←", true),
    RS_UP(120, "RS↑", true), RS_RIGHT(121, "RS→", true), RS_DOWN(122, "RS↓", true), RS_LEFT(123, "RS←", true);

    companion object {
        const val AXIS_MAX = 32767
        /** Buttons a physical gamepad button can be bound to (sticks come from axes). */
        val bindable = listOf(CROSS, CIRCLE, SQUARE, TRIANGLE, L1, R1, L2, R2, L3, R3, START, SELECT, UP, DOWN, LEFT, RIGHT)
        fun byName(n: String?): PadKey? = entries.firstOrNull { it.name == n }
    }
}

/** Converts a stick vector (-1..1, y down) into the core's four half-axis magnitudes. */
object StickMath {
    data class HalfAxes(val up: Int, val right: Int, val down: Int, val left: Int)

    fun halfAxes(x: Float, y: Float, deadzone: Float = 0.12f): HalfAxes {
        val mag = kotlin.math.sqrt(x * x + y * y)
        if (mag < deadzone) return HalfAxes(0, 0, 0, 0)
        // Rescale so output starts at 0 just outside the deadzone and clamps to the unit circle.
        val scale = ((mag - deadzone) / (1f - deadzone)).coerceIn(0f, 1f) / mag
        val sx = x * scale
        val sy = y * scale
        fun v(f: Float) = (f.coerceIn(0f, 1f) * PadKey.AXIS_MAX).toInt()
        return HalfAxes(up = v(-sy), right = v(sx), down = v(sy), left = v(-sx))
    }
}
