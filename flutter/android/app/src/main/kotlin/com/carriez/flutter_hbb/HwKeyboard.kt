package com.carriez.flutter_hbb

import android.app.Activity
import android.content.ComponentName
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference

/**
 * Physical keyboard support while this device controls a remote peer.
 *
 * - Window keyboard capture (Android 16 QPR2+, `CAPTURE_KEYBOARD`): the focused window
 *   receives most system shortcuts (Meta, Alt+Tab, ...) instead of the system.
 * - [KeyCaptureService]: optional accessibility key filter for older systems / OEM builds
 *   that do not honour window capture. It consumes hardware keyboard events and
 *   re-dispatches them into the activity, which skips the system shortcut handling.
 * - Remaps for keys the system still swallows: Right Alt -> Win, Alt+` -> Alt+Tab.
 */
object HwKeyboard {
    private const val TAG = "HwKeyboard"

    private var activityRef: WeakReference<Activity>? = null
    @Volatile private var captureActive = false
    @Volatile private var remapActive = false

    private var rightAltAsWin = false
    private var graveAsTab = false

    private const val SCAN_CODE_TAB = 15
    private const val SCAN_CODE_LEFT_META = 125

    private val passThroughKeyCodes = setOf(
        KeyEvent.KEYCODE_LANGUAGE_SWITCH,
        KeyEvent.KEYCODE_ALL_APPS,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_VOICE_ASSIST,
        KeyEvent.KEYCODE_NOTIFICATION,
        KeyEvent.KEYCODE_SETTINGS,
        KeyEvent.KEYCODE_BRIGHTNESS_UP,
        KeyEvent.KEYCODE_BRIGHTNESS_DOWN,
        KeyEvent.KEYCODE_SLEEP,
        KeyEvent.KEYCODE_WAKEUP,
    )

    /** Returns true if the call was handled. */
    fun handle(activity: Activity, call: MethodCall, result: MethodChannel.Result): Boolean {
        when (call.method) {
            "hw_keyboard_session" -> {
                val active = call.argument<Boolean>("active") == true
                val capture = active && call.argument<Boolean>("capture") == true
                activityRef = if (active) WeakReference(activity) else null
                captureActive = capture
                remapActive = active && call.argument<Boolean>("remap") == true
                HwTrackpad.active = active && call.argument<Boolean>("trackpad") == true
                setWindowKeyboardCapture(activity, capture)
                result.success(true)
            }
            "hw_keyboard_capture_service_enabled" -> {
                result.success(isCaptureServiceEnabled(activity))
            }
            else -> return false
        }
        return true
    }

    private fun setWindowKeyboardCapture(activity: Activity, enabled: Boolean) {
        try {
            val attrs = activity.window.attributes
            // Added in API 36.1; compileSdk does not expose it yet.
            WindowManager.LayoutParams::class.java
                .getMethod("setKeyboardCaptureEnabled", Boolean::class.javaPrimitiveType)
                .invoke(attrs, enabled)
            activity.window.attributes = attrs
        } catch (e: ReflectiveOperationException) {
            Log.d(TAG, "Window keyboard capture unavailable: $e")
        }
    }

    private fun isCaptureServiceEnabled(activity: Activity): Boolean {
        val enabled = Settings.Secure.getString(
            activity.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val self = ComponentName(activity, KeyCaptureService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }

    private fun isHardwareKeyboardEvent(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        return !device.isVirtual &&
            (event.source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
            device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    /** Called by [KeyCaptureService]. Returns true to consume the event. */
    fun onCapturedKeyEvent(event: KeyEvent): Boolean {
        if (!captureActive) return false
        val activity = activityRef?.get() ?: return false
        if (!activity.hasWindowFocus()) return false
        if (!isHardwareKeyboardEvent(event) || event.isSystem ||
            event.keyCode > KeyEvent.getMaxKeyCode() ||
            event.keyCode in passThroughKeyCodes
        ) {
            return false
        }
        // Always consume: returning false would deliver the event a second time.
        activity.dispatchKeyEvent(event)
        return true
    }

    /** Returns a replacement event, or null to keep the original one. */
    fun remap(event: KeyEvent): KeyEvent? {
        if (!remapActive && !rightAltAsWin && !graveAsTab) return null
        if (!isHardwareKeyboardEvent(event)) return null
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        if (!down && !up) return null

        var keyCode = event.keyCode
        var scanCode = event.scanCode
        if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT && (rightAltAsWin || (down && remapActive))) {
            rightAltAsWin = down
            keyCode = KeyEvent.KEYCODE_META_LEFT
            scanCode = SCAN_CODE_LEFT_META
        } else if (keyCode == KeyEvent.KEYCODE_GRAVE && (graveAsTab ||
                (down && remapActive && (event.metaState and KeyEvent.META_ALT_LEFT_ON) != 0))
        ) {
            graveAsTab = down
            keyCode = KeyEvent.KEYCODE_TAB
            scanCode = SCAN_CODE_TAB
        } else if (!rightAltAsWin) {
            return null
        }

        var metaState = event.metaState
        if (rightAltAsWin || keyCode == KeyEvent.KEYCODE_META_LEFT) {
            metaState = metaState and KeyEvent.META_ALT_RIGHT_ON.inv()
            if ((metaState and KeyEvent.META_ALT_LEFT_ON) == 0) {
                metaState = metaState and KeyEvent.META_ALT_ON.inv()
            }
            metaState = if (rightAltAsWin) {
                metaState or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
            } else {
                metaState and (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON).inv()
            }
        }
        return KeyEvent(
            event.downTime, event.eventTime, event.action, keyCode, event.repeatCount,
            metaState, event.deviceId, scanCode, event.flags, event.source
        )
    }
}
