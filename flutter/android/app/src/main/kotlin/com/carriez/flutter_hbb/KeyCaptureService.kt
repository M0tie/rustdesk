package com.carriez.flutter_hbb

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional accessibility key filter: lets a remote session receive Win, Alt+Tab and other
 * shortcuts the system would otherwise handle. Inactive unless a remote session has
 * enabled capture, see [HwKeyboard.onCapturedKeyEvent].
 */
class KeyCaptureService : AccessibilityService() {
    override fun onKeyEvent(event: KeyEvent): Boolean = HwKeyboard.onCapturedKeyEvent(event)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
