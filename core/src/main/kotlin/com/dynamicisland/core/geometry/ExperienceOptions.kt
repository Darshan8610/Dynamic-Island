package com.dynamicisland.core.geometry

enum class GestureAction { OPEN_DASHBOARD, PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK, OPEN_SOURCE, NONE }
enum class ActivityPriority { BALANCED, MEDIA_FIRST, NAVIGATION_FIRST }
enum class AppearancePreset { MONOCHROME, SOFT_ACCENT, HIGH_CONTRAST }
enum class MotionPreset { NONE, GENTLE, RESPONSIVE }

/** Input routing depends on the visible activity, never background playback alone. */
object GesturePolicy {
    fun allowed(action: GestureAction, visibleMedia: Boolean, locked: Boolean): GestureAction = when {
        locked -> GestureAction.NONE
        action in setOf(GestureAction.PLAY_PAUSE, GestureAction.NEXT_TRACK, GestureAction.PREVIOUS_TRACK) && !visibleMedia -> GestureAction.NONE
        else -> action
    }
}
