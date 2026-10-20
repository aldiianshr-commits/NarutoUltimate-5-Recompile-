package com.aldiian.naruto5.input

/**
 * Physical gamepad button (Android KeyEvent keycode) -> PS2 pad key. Pure Kotlin, persisted as
 * "keycode=PADKEY" lines. Defaults follow the Android/Xbox layout: A=✕, B=○, X=□, Y=△.
 * Sticks and triggers are read from MotionEvent axes and are not part of this table.
 */
data class GamepadMapping(val buttons: Map<Int, PadKey> = DEFAULTS, val analogTriggers: Boolean = true, val rumble: Boolean = true) {

    fun lookup(keyCode: Int): PadKey? = buttons[keyCode]

    /** Binds [keyCode] to [key], removing any other physical button that was bound to [key]. */
    fun bind(keyCode: Int, key: PadKey): GamepadMapping =
        copy(buttons = buttons.filterValues { it != key } + (keyCode to key))

    fun serialize(): String = buildString {
        appendLine("analogTriggers=$analogTriggers")
        appendLine("rumble=$rumble")
        buttons.toSortedMap().forEach { (k, v) -> appendLine("$k=${v.name}") }
    }

    companion object {
        // Android KeyEvent constants (kept numeric so this file has no Android dependency).
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_BUTTON_A = 96
        const val KEYCODE_BUTTON_B = 97
        const val KEYCODE_BUTTON_X = 99
        const val KEYCODE_BUTTON_Y = 100
        const val KEYCODE_BUTTON_L1 = 102
        const val KEYCODE_BUTTON_R1 = 103
        const val KEYCODE_BUTTON_L2 = 104
        const val KEYCODE_BUTTON_R2 = 105
        const val KEYCODE_BUTTON_THUMBL = 106
        const val KEYCODE_BUTTON_THUMBR = 107
        const val KEYCODE_BUTTON_START = 108
        const val KEYCODE_BUTTON_SELECT = 109

        val DEFAULTS: Map<Int, PadKey> = mapOf(
            KEYCODE_BUTTON_A to PadKey.CROSS,
            KEYCODE_BUTTON_B to PadKey.CIRCLE,
            KEYCODE_BUTTON_X to PadKey.SQUARE,
            KEYCODE_BUTTON_Y to PadKey.TRIANGLE,
            KEYCODE_BUTTON_L1 to PadKey.L1,
            KEYCODE_BUTTON_R1 to PadKey.R1,
            KEYCODE_BUTTON_L2 to PadKey.L2,
            KEYCODE_BUTTON_R2 to PadKey.R2,
            KEYCODE_BUTTON_THUMBL to PadKey.L3,
            KEYCODE_BUTTON_THUMBR to PadKey.R3,
            KEYCODE_BUTTON_START to PadKey.START,
            KEYCODE_BUTTON_SELECT to PadKey.SELECT,
            KEYCODE_DPAD_UP to PadKey.UP,
            KEYCODE_DPAD_DOWN to PadKey.DOWN,
            KEYCODE_DPAD_LEFT to PadKey.LEFT,
            KEYCODE_DPAD_RIGHT to PadKey.RIGHT,
        )

        fun deserialize(text: String?): GamepadMapping {
            if (text.isNullOrBlank()) return GamepadMapping()
            var triggers = true
            var rumble = true
            val map = mutableMapOf<Int, PadKey>()
            text.lines().forEach { l ->
                val i = l.indexOf('=')
                if (i <= 0) return@forEach
                val k = l.substring(0, i).trim()
                val v = l.substring(i + 1).trim()
                when (k) {
                    "analogTriggers" -> triggers = v.toBooleanStrictOrNull() ?: true
                    "rumble" -> rumble = v.toBooleanStrictOrNull() ?: true
                    else -> {
                        val code = k.toIntOrNull()
                        val key = PadKey.byName(v)
                        if (code != null && key != null) map[code] = key
                    }
                }
            }
            return GamepadMapping(if (map.isEmpty()) DEFAULTS else map, triggers, rumble)
        }
    }
}
