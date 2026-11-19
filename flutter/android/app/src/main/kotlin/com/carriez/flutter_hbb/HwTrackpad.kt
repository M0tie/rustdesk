package com.carriez.flutter_hbb

import android.content.res.Resources
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sign

/**
 * Makes a built-in / cover touchpad act as a mouse while controlling a remote peer.
 *
 * Android reports touchpad pointer events with TOOL_TYPE_FINGER, which Flutter maps to
 * touch, so RustDesk never sees a mouse. Here they are re-tagged as TOOL_TYPE_MOUSE, and
 * two-finger swipes are turned into scroll-wheel events.
 */
object HwTrackpad {
    @Volatile var active = false

    // Two-finger swipe distance per wheel notch.
    private const val SCROLL_STEP_DP = 18f
    private const val CLASSIFICATION_TWO_FINGER_SWIPE = 3
    private const val CLASSIFICATION_MULTI_FINGER_SWIPE = 4
    private const val CLASSIFICATION_PINCH = 5

    private var lastX = 0f
    private var lastY = 0f
    private var accX = 0f
    private var accY = 0f

    fun dispatch(
        event: MotionEvent,
        dispatchSame: (MotionEvent) -> Boolean,
        dispatchGeneric: (MotionEvent) -> Boolean
    ): Boolean {
        if (!active || !isTrackpadFinger(event)) return dispatchSame(event)
        when (classification(event)) {
            CLASSIFICATION_TWO_FINGER_SWIPE -> {
                swipeToScroll(event, dispatchGeneric)
                return true
            }
            CLASSIFICATION_PINCH, CLASSIFICATION_MULTI_FINGER_SWIPE -> return dispatchSame(event)
        }
        val mouse = asMouse(event)
        return try {
            dispatchSame(mouse)
        } finally {
            mouse.recycle()
        }
    }

    private fun isTrackpadFinger(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_MOUSE) &&
            event.pointerCount > 0 &&
            event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER

    private fun classification(event: MotionEvent): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.classification else 0

    private fun asMouse(event: MotionEvent): MotionEvent {
        val n = event.pointerCount
        val props = Array(n) { i ->
            MotionEvent.PointerProperties().also {
                event.getPointerProperties(i, it)
                it.toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        }
        val coords = Array(n) { i -> MotionEvent.PointerCoords().also { event.getPointerCoords(i, it) } }
        var buttons = event.buttonState
        val action = event.actionMasked
        // Flutter treats a button-less mouse down as a Chromebook-style trackpad pan.
        if (buttons == 0 && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
            buttons = MotionEvent.BUTTON_PRIMARY
        }
        return MotionEvent.obtain(
            event.downTime, event.eventTime, event.action, n, props, coords,
            event.metaState, buttons, event.xPrecision, event.yPrecision,
            event.deviceId, event.edgeFlags, event.source, event.flags
        )
    }

    private fun swipeToScroll(event: MotionEvent, dispatchGeneric: (MotionEvent) -> Boolean) {
        val x = event.x
        val y = event.y
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            accX = 0f
            accY = 0f
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            accX += x - lastX
            accY += y - lastY
            val step = SCROLL_STEP_DP * Resources.getSystem().displayMetrics.density
            // The swipe moves content with the fingers, like a touchscreen drag.
            while (abs(accY) >= step) {
                val dir = sign(accY)
                accY -= dir * step
                sendScroll(event, 0f, dir, dispatchGeneric)
            }
            while (abs(accX) >= step) {
                val dir = sign(accX)
                accX -= dir * step
                sendScroll(event, -dir, 0f, dispatchGeneric)
            }
        }
        lastX = x
        lastY = y
    }

    private fun sendScroll(
        event: MotionEvent,
        hScroll: Float,
        vScroll: Float,
        dispatchGeneric: (MotionEvent) -> Boolean
    ) {
        val props = MotionEvent.PointerProperties().also {
            it.id = 0
            it.toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().also {
            it.x = event.x
            it.y = event.y
            it.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
            it.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        }
        val scroll = MotionEvent.obtain(
            event.downTime, event.eventTime, MotionEvent.ACTION_SCROLL, 1,
            arrayOf(props), arrayOf(coords), event.metaState, 0, 1f, 1f,
            event.deviceId, 0, InputDevice.SOURCE_MOUSE, 0
        )
        try {
            dispatchGeneric(scroll)
        } finally {
            scroll.recycle()
        }
    }
}
