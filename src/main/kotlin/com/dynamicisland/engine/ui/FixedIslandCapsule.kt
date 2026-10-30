package com.dynamicisland.engine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import com.dynamicisland.core.geometry.*
import com.dynamicisland.engine.settings.BehaviorOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.engine.core.model.*
import com.dynamicisland.engine.ui.icons.IslandGlyph

/** Activity content changes; the window and capsule footprint never depend on it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FixedIslandCapsule(state: IslandState, playback: MediaPlaybackSnapshot, locked: Boolean,
    rotation: Float, onOpen: () -> Unit, onPlayPause: () -> Unit,
    options: BehaviorOptions = BehaviorOptions(),
    onGesture: ((GestureAction) -> Unit)? = null,
    secondary: Boolean = false, cornerRadius: androidx.compose.ui.unit.Dp? = null) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = rotation == 90f || rotation == -90f
        val w = if (side) maxHeight else maxWidth
        val h = if (side) maxWidth else maxHeight
        val activity = when (state) {
            is IslandState.Compact -> state.activity
            is IslandState.Minimal -> state.primary
            is IslandState.Expanded -> state.activity
            else -> null
        }
        val media = activity?.kind == ActivityKind.MEDIA_SESSION
        val bitmap = if (!locked && media) (activity?.compactLeading as? IslandSlotContent.Icon)?.iconBitmap else null
        val image = remember(bitmap) { bitmap?.asImageBitmap() }
        val symbol = when {
            locked -> IslandSymbol.BELL
            state is IslandState.AlertPeek -> (state.alert.leadingSlot as? IslandSlotContent.Symbol)?.symbol ?: IslandSymbol.BELL
            activity?.kind in listOf(ActivityKind.LIVE_CALL, ActivityKind.RINGING_CALL, ActivityKind.EMERGENCY_CALL) -> IslandSymbol.PHONE
            activity?.kind == ActivityKind.LIVE_TIMER -> IslandSymbol.TIMER
            activity?.kind == ActivityKind.LIVE_STOPWATCH -> IslandSymbol.TIMER
            activity?.kind == ActivityKind.NAVIGATION -> IslandSymbol.NAVIGATION
            activity?.kind == ActivityKind.BATTERY_CHARGING -> IslandSymbol.BOLT
            activity?.kind == ActivityKind.ACCESSORY_BATTERY -> IslandSymbol.HEADSET
            media -> IslandSymbol.MUSIC_NOTE
            else -> IslandSymbol.BELL
        }
        val dispatch by rememberUpdatedState(onGesture)
        val currentOptions by rememberUpdatedState(options)
        val visibleMedia by rememberUpdatedState(media)
        var drag by remember { mutableFloatStateOf(0f) }
        fun route(action: GestureAction) {
            val allowed = GesturePolicy.allowed(action, visibleMedia, locked)
            if (allowed == GestureAction.NONE) return
            if (dispatch != null) dispatch?.invoke(allowed)
            else if (allowed == GestureAction.OPEN_DASHBOARD || allowed == GestureAction.OPEN_SOURCE) onOpen()
        }
        val tint = when(options.appearance) {
            AppearancePreset.SOFT_ACCENT -> Color(0xFFB7D5C3)
            else -> Color.White
        }
        val duration = if(options.reducedMotion || !android.animation.ValueAnimator.areAnimatorsEnabled()) 0 else when(options.motion) {
            MotionPreset.NONE -> 0; MotionPreset.GENTLE -> 180; MotionPreset.RESPONSIVE -> 90
        }
        val shape = if (cornerRadius != null) RoundedCornerShape(cornerRadius) else RoundedCornerShape(percent = 50)
        Row(Modifier.requiredSize(w, h).graphicsLayer { rotationZ = rotation }
            .clip(shape).background(Color.Black)
            .border(if(options.appearance==AppearancePreset.HIGH_CONTRAST) 1.dp else 0.dp,if(options.appearance==AppearancePreset.HIGH_CONTRAST) Color.White else Color.Transparent,shape)
            .pointerInput(locked,media) {
                detectHorizontalDragGestures(onDragStart={drag=0f},
                    onHorizontalDrag={ change, amount -> change.consume(); drag += amount },
                    onDragEnd={ if(kotlin.math.abs(drag) >= 36.dp.toPx()) route(if(drag<0) currentOptions.swipeLeftAction else currentOptions.swipeRightAction);drag=0f },
                    onDragCancel={drag=0f})
            }
            .combinedClickable(onClick = { route(options.tapAction) }, onLongClick = { route(options.holdAction) })
            .semantics { contentDescription = if (locked) "Dynamic Island. Unlock to open controls" else if(secondary) "Secondary activity. ${activity?.title ?: "Open controls"}" else "Primary island. Open controls" }
            .padding(horizontal = if(secondary) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, "Album cover", Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
                else if (activity != null || state is IslandState.AlertPeek || locked) Crossfade(symbol, animationSpec = tween(duration), label="Activity icon") { glyph -> IslandGlyph(glyph, tint, Modifier.size(18.dp)) }
            }
            // Leave the hardware aperture area clear. Never place controls over the hole.
            if (!secondary) Spacer(Modifier.weight(1f))
            if (!secondary && media && !locked && playback.canPlayPause) {
                // One tap target across the capsule. Transport actions live in the expanded card.
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    IslandGlyph(if (playback.isPlaying) IslandSymbol.PAUSE else IslandSymbol.PLAY, Color.White, Modifier.size(17.dp))
                }
            } else if (!secondary && (activity != null || state is IslandState.AlertPeek)) {
                Text("•", color = tint, fontSize = 20.sp)
            } else if (!secondary) Spacer(Modifier.size(26.dp))
        }
    }
}
