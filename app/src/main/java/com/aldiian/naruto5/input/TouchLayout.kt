package com.aldiian.naruto5.input

/**
 * On-screen controller layout: position (fraction of the view), per-control size, visibility,
 * plus global opacity and scale. Pure Kotlin so it can be unit-tested and persisted as text.
 */
enum class ControlKind { BUTTON, DPAD, STICK_LEFT, STICK_RIGHT }

enum class ControlId(val kind: ControlKind, val key: PadKey?, val baseSizeDp: Float) {
    DPAD(ControlKind.DPAD, null, 150f),
    STICK_L(ControlKind.STICK_LEFT, null, 130f),
    STICK_R(ControlKind.STICK_RIGHT, null, 110f),
    CROSS(ControlKind.BUTTON, PadKey.CROSS, 62f),
    CIRCLE(ControlKind.BUTTON, PadKey.CIRCLE, 62f),
    SQUARE(ControlKind.BUTTON, PadKey.SQUARE, 62f),
    TRIANGLE(ControlKind.BUTTON, PadKey.TRIANGLE, 62f),
    L1(ControlKind.BUTTON, PadKey.L1, 56f),
    L2(ControlKind.BUTTON, PadKey.L2, 56f),
    R1(ControlKind.BUTTON, PadKey.R1, 56f),
    R2(ControlKind.BUTTON, PadKey.R2, 56f),
    L3(ControlKind.BUTTON, PadKey.L3, 44f),
    R3(ControlKind.BUTTON, PadKey.R3, 44f),
    SELECT(ControlKind.BUTTON, PadKey.SELECT, 44f),
    START(ControlKind.BUTTON, PadKey.START, 44f),
}

data class ControlPlacement(val x: Float, val y: Float, val scale: Float = 1f, val visible: Boolean = true)

data class TouchLayout(
    val placements: Map<ControlId, ControlPlacement> = DEFAULT_PLACEMENTS,
    val opacity: Float = 0.55f,
    val globalScale: Float = 1f,
    val enabled: Boolean = true,
    val hapticFeedback: Boolean = true,
) {
    fun placement(id: ControlId): ControlPlacement = placements[id] ?: DEFAULT_PLACEMENTS.getValue(id)

    fun moved(id: ControlId, x: Float, y: Float) =
        copy(placements = placements + (id to placement(id).copy(x = x.coerceIn(0.02f, 0.98f), y = y.coerceIn(0.02f, 0.98f))))

    fun resized(id: ControlId, scale: Float) =
        copy(placements = placements + (id to placement(id).copy(scale = scale.coerceIn(0.5f, 2.0f))))

    fun toggled(id: ControlId, visible: Boolean) =
        copy(placements = placements + (id to placement(id).copy(visible = visible)))

    fun serialize(): String = buildString {
        appendLine("opacity=$opacity")
        appendLine("globalScale=$globalScale")
        appendLine("enabled=$enabled")
        appendLine("haptic=$hapticFeedback")
        ControlId.entries.forEach { id ->
            val p = placement(id)
            appendLine("c.${id.name}=${p.x},${p.y},${p.scale},${p.visible}")
        }
    }

    companion object {
        /** Landscape defaults for a 16:10 tablet (Xiaomi Pad 6). y grows downwards. */
        val DEFAULT_PLACEMENTS: Map<ControlId, ControlPlacement> = mapOf(
            ControlId.DPAD to ControlPlacement(0.11f, 0.70f),
            ControlId.STICK_L to ControlPlacement(0.25f, 0.82f),
            ControlId.STICK_R to ControlPlacement(0.74f, 0.84f),
            ControlId.TRIANGLE to ControlPlacement(0.89f, 0.52f),
            ControlId.SQUARE to ControlPlacement(0.83f, 0.64f),
            ControlId.CIRCLE to ControlPlacement(0.95f, 0.64f),
            ControlId.CROSS to ControlPlacement(0.89f, 0.76f),
            ControlId.L1 to ControlPlacement(0.07f, 0.30f),
            ControlId.L2 to ControlPlacement(0.07f, 0.16f),
            ControlId.R1 to ControlPlacement(0.93f, 0.30f),
            ControlId.R2 to ControlPlacement(0.93f, 0.16f),
            ControlId.L3 to ControlPlacement(0.18f, 0.48f),
            ControlId.R3 to ControlPlacement(0.82f, 0.36f),
            ControlId.SELECT to ControlPlacement(0.42f, 0.93f),
            ControlId.START to ControlPlacement(0.58f, 0.93f),
        )

        fun deserialize(text: String?): TouchLayout {
            if (text.isNullOrBlank()) return TouchLayout()
            val kv = text.lines().mapNotNull { l ->
                val i = l.indexOf('='); if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
            }.toMap()
            val placements = DEFAULT_PLACEMENTS.toMutableMap()
            ControlId.entries.forEach { id ->
                val parts = kv["c.${id.name}"]?.split(',') ?: return@forEach
                if (parts.size == 4) {
                    val x = parts[0].toFloatOrNull(); val y = parts[1].toFloatOrNull()
                    val s = parts[2].toFloatOrNull(); val v = parts[3].toBooleanStrictOrNull()
                    if (x != null && y != null && s != null && v != null) {
                        placements[id] = ControlPlacement(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), s.coerceIn(0.5f, 2f), v)
                    }
                }
            }
            return TouchLayout(
                placements = placements,
                opacity = kv["opacity"]?.toFloatOrNull()?.coerceIn(0.1f, 1f) ?: 0.55f,
                globalScale = kv["globalScale"]?.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1f,
                enabled = kv["enabled"]?.toBooleanStrictOrNull() ?: true,
                hapticFeedback = kv["haptic"]?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}
