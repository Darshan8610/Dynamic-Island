package com.dynamicisland.engine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Spec-accurate privacy indicator dot.
 *
 * iOS renders these as 4 pt solid circles in the status-bar OLED pixel array:
 *  - camera only     -> green    #34C759
 *  - microphone only -> orange   #FF9500
 *  - both            -> blue     #0A84FF
 *
 * The dot never sits in a corner. While the island is compact it lives at the
 * right edge of the pill, vertically centred. The moment the island expands it
 * detaches outward to the right of the active pill (see the service overlay),
 * still on the pill's vertical centreline — never pinned to a corner.
 */
private val CameraColor = Color(0xFF34C759)
private val MicrophoneColor = Color(0xFFFF9500)
private val BothColor = Color(0xFF0A84FF)

/** Diameter matching the iOS 4 pt privacy indicator. */
val PRIVACY_INDICATOR_DIAMETER_DP = 4.dp

@Composable
fun PrivacyIndicatorDot(
    microphoneActive: Boolean,
    cameraActive: Boolean,
    modifier: Modifier = Modifier
) {
    val color = remember(microphoneActive, cameraActive) {
        when {
            cameraActive && microphoneActive -> BothColor
            cameraActive -> CameraColor
            else -> MicrophoneColor
        }
    }
    Box(
        modifier = modifier
            .size(PRIVACY_INDICATOR_DIAMETER_DP)
            .background(color, CircleShape)
    )
}
