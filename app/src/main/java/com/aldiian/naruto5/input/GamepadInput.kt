package com.aldiian.naruto5.input

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Bluetooth/USB gamepad -> PS2 pad. Buttons go through the user's [GamepadMapping]; sticks,
 * hat and analog triggers are read from MotionEvent axes. All input is pushed to the core with
 * NativeApp.setPadButton (port 1).
 */
class GamepadInput(private var mapping: GamepadMapping) {
    private val held = HashMap<PadKey, Int>()
    private var lastRumbleDevice = -1

    fun setMapping(m: GamepadMapping) { mapping = m }

    fun isGamepad(source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

    /** Returns true when the key was consumed. */
    fun onKey(event: KeyEvent): Boolean {
        if (!isGamepad(event.source) && !KeyEvent.isGamepadButton(event.keyCode)) return false
        val key = mapping.lookup(event.keyCode) ?: return false
        lastRumbleDevice = event.deviceId
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) send(key, PadKey.AXIS_MAX)
            KeyEvent.ACTION_UP -> send(key, 0)
        }
        return true
    }

    fun onMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false
        lastRumbleDevice = event.deviceId
        val dev = event.device
        fun axis(a: Int): Float {
            val v = event.getAxisValue(a)
            val flat = dev?.getMotionRange(a, event.source)?.flat ?: 0f
            return if (kotlin.math.abs(v) <= flat) 0f else v
        }
        stick(axis(MotionEvent.AXIS_X), axis(MotionEvent.AXIS_Y), PadKey.LS_UP, PadKey.LS_RIGHT, PadKey.LS_DOWN, PadKey.LS_LEFT)
        stick(axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RZ), PadKey.RS_UP, PadKey.RS_RIGHT, PadKey.RS_DOWN, PadKey.RS_LEFT)

        // D-pad reported as a hat on many Bluetooth pads.
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        digital(PadKey.LEFT, hx < -0.5f); digital(PadKey.RIGHT, hx > 0.5f)
        digital(PadKey.UP, hy < -0.5f); digital(PadKey.DOWN, hy > 0.5f)

        // Analog L2/R2 (PS2 pressure-sensitive buttons).
        if (mapping.analogTriggers) {
            val l = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
            val r = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            analog(PadKey.L2, l)
            analog(PadKey.R2, r)
        }
        return true
    }

    private fun stick(x: Float, y: Float, up: PadKey, right: PadKey, down: PadKey, left: PadKey) {
        val h = StickMath.halfAxes(x, y)
        send(up, h.up); send(right, h.right); send(down, h.down); send(left, h.left)
    }

    private fun digital(k: PadKey, on: Boolean) = send(k, if (on) PadKey.AXIS_MAX else 0)

    private fun analog(k: PadKey, v: Float) {
        val value = if (v < 0.08f) 0 else (v.coerceIn(0f, 1f) * PadKey.AXIS_MAX).toInt()
        send(k, value)
    }

    /** Sends only on change, so idle axes do not flood the core. */
    private fun send(k: PadKey, value: Int) {
        val prev = held[k] ?: 0
        if (prev == value) return
        held[k] = value
        NativeApp.setPadButton(k.code, if (k.analog || k == PadKey.L2 || k == PadKey.R2) value else 0, value > 0)
    }

    fun releaseAll() {
        held.keys.toList().forEach { send(it, 0) }
    }

    // ---- rumble -----------------------------------------------------------------------------
    /** PS2 motors: large is 0..255 intensity, small is on/off. */
    fun rumble(ctx: Context, large: Int, small: Int) {
        if (!mapping.rumble) return
        val intensity = maxOf(large, if (small > 0) 160 else 0).coerceIn(0, 255)
        val vib = padVibrator() ?: deviceVibrator(ctx) ?: return
        runCatching {
            if (intensity == 0) vib.cancel()
            else vib.vibrate(VibrationEffect.createOneShot(120, if (vib.hasAmplitudeControl()) intensity.coerceAtLeast(1) else VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun padVibrator(): Vibrator? {
        val dev = InputDevice.getDevice(lastRumbleDevice) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dev.vibratorManager.vibratorIds.firstOrNull()?.let { dev.vibratorManager.getVibrator(it) }
        } else {
            @Suppress("DEPRECATION") dev.vibrator.takeIf { it.hasVibrator() }
        }
    }

    private fun deviceVibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }

    companion object {
        fun connectedPads(): List<InputDevice> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual && (it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || it.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) }
    }
}
