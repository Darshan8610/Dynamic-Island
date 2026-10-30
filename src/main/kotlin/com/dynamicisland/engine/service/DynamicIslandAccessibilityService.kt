package com.dynamicisland.engine.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides TYPE_ACCESSIBILITY_OVERLAY elevation for the Dynamic Island.
 *
 * In Android's OS architecture, TYPE_APPLICATION_OVERLAY windows are strictly subordinated
 * to TYPE_STATUS_BAR, meaning system icons (battery, clock, wifi) draw over the island,
 * and status-bar notification shade gestures intercept touches inside the camera notch.
 *
 * TYPE_ACCESSIBILITY_OVERLAY sits ABOVE TYPE_STATUS_BAR, enabling:
 * 1. Rendering on top of status bar icons (True "Always on Top").
 * 2. 100% responsive touch delivery inside the camera notch and cutout.
 */
class DynamicIslandAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        _isAccessibilityEnabled.value = true
        activeServiceInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        _isAccessibilityEnabled.value = false
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
    }

    companion object {
        private val _isAccessibilityEnabled = MutableStateFlow(false)
        val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

        @Volatile
        var activeServiceInstance: DynamicIslandAccessibilityService? = null
            private set
    }
}