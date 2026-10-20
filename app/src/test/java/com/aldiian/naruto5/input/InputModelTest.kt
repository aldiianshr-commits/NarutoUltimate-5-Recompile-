package com.aldiian.naruto5.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModelTest {

    @Test fun padKeyCodesMatchCore() {
        // Values hard-wired in the core's applyPadButton switch.
        assertEquals(96, PadKey.CROSS.code)
        assertEquals(97, PadKey.CIRCLE.code)
        assertEquals(99, PadKey.SQUARE.code)
        assertEquals(100, PadKey.TRIANGLE.code)
        assertEquals(104, PadKey.L2.code)
        assertEquals(105, PadKey.R2.code)
        assertEquals(110, PadKey.LS_UP.code)
        assertEquals(123, PadKey.RS_LEFT.code)
        assertEquals(PadKey.entries.size, PadKey.entries.map { it.code }.toSet().size)
    }

    @Test fun stickDeadzoneAndRange() {
        assertEquals(StickMath.HalfAxes(0, 0, 0, 0), StickMath.halfAxes(0.05f, -0.05f))
        val full = StickMath.halfAxes(1f, 0f)
        assertEquals(PadKey.AXIS_MAX, full.right)
        assertEquals(0, full.left)
        val up = StickMath.halfAxes(0f, -1f)
        assertEquals(PadKey.AXIS_MAX, up.up)
        assertEquals(0, up.down)
        val over = StickMath.halfAxes(3f, 0f)
        assertEquals(PadKey.AXIS_MAX, over.right)
    }

    @Test fun touchLayoutRoundTrip() {
        val l = TouchLayout().moved(ControlId.CROSS, 0.5f, 0.4f).resized(ControlId.DPAD, 1.4f)
            .toggled(ControlId.L3, false).copy(opacity = 0.8f, globalScale = 1.2f, hapticFeedback = false)
        val r = TouchLayout.deserialize(l.serialize())
        assertEquals(l.placement(ControlId.CROSS), r.placement(ControlId.CROSS))
        assertEquals(1.4f, r.placement(ControlId.DPAD).scale, 0.0001f)
        assertFalse(r.placement(ControlId.L3).visible)
        assertEquals(0.8f, r.opacity, 0.0001f)
        assertFalse(r.hapticFeedback)
    }

    @Test fun touchLayoutClamps() {
        val l = TouchLayout().moved(ControlId.START, 5f, -3f).resized(ControlId.START, 10f)
        val p = l.placement(ControlId.START)
        assertTrue(p.x <= 0.98f && p.y >= 0.02f)
        assertEquals(2.0f, p.scale, 0.0001f)
    }

    @Test fun gamepadRemapIsExclusive() {
        val m = GamepadMapping().bind(GamepadMapping.KEYCODE_BUTTON_B, PadKey.CROSS)
        assertEquals(PadKey.CROSS, m.lookup(GamepadMapping.KEYCODE_BUTTON_B))
        assertNull(m.lookup(GamepadMapping.KEYCODE_BUTTON_A)) // old binding for CROSS removed
        val r = GamepadMapping.deserialize(m.copy(rumble = false).serialize())
        assertEquals(PadKey.CROSS, r.lookup(GamepadMapping.KEYCODE_BUTTON_B))
        assertFalse(r.rumble)
    }
}
