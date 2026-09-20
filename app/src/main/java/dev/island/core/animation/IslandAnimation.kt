package dev.island.core.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import dev.island.core.ui.theme.IslandMotion
import dev.island.domain.model.AnimationStyle

/**
 * The island's motion system.
 *
 * One place defines how the pill expands, collapses, bounces, morphs and crossfades, so every
 * renderer moves the same way. Three knobs change everything globally:
 * - [speed] (0.5×–1.5×) from the user's "animation intensity" setting,
 * - [style] (springy / smooth / snappy) which maps to a damping ratio,
 * - [reduceMotion] which replaces springs with short linear fades (also honoured when the system
 *   animator scale is 0 or the user enabled the accessibility setting).
 *
 * Nothing here ever uses `visible = true` style transitions: size, alpha and content are all
 * animated, and the renderer reports completion so the state machine can settle.
 */
object IslandAnimation {

    /** Width/height growth from pill to card. */
    fun expand(speed: Float, style: AnimationStyle, reduceMotion: Boolean): AnimationSpec<Dp> =
        if (reduceMotion) {
            tween(durationMs(IslandMotion.expandDurationMs, speed), easing = LinearEasing)
        } else {
            spring(dampingRatio = IslandMotion.dampingFor(style), stiffness = IslandMotion.EXPAND_STIFFNESS * speedFactor(speed))
        }

    /** Card back to pill, or pill to nothing. Slightly stiffer than expand so it feels decisive. */
    fun collapse(speed: Float, style: AnimationStyle, reduceMotion: Boolean): AnimationSpec<Dp> =
        if (reduceMotion) {
            tween(durationMs(IslandMotion.collapseDurationMs, speed), easing = LinearEasing)
        } else {
            spring(dampingRatio = IslandMotion.dampingFor(style), stiffness = IslandMotion.COLLAPSE_STIFFNESS * speedFactor(speed))
        }

    /** Attention pulse used when a new event takes focus (subtle, never a large overshoot). */
    fun bounce(speed: Float, style: AnimationStyle, reduceMotion: Boolean): AnimationSpec<Dp> =
        if (reduceMotion) {
            tween(durationMs(IslandMotion.expandDurationMs, speed), easing = LinearEasing)
        } else {
            spring(
                dampingRatio = (IslandMotion.dampingFor(style) * 0.82f).coerceAtLeast(Spring.DampingRatioNoBouncy),
                stiffness = IslandMotion.BOUNCE_STIFFNESS * speedFactor(speed),
            )
        }

    /** Content morphing inside a fixed container (e.g. timer digits, queue switch). */
    fun morph(speed: Float, style: AnimationStyle, reduceMotion: Boolean): AnimationSpec<Dp> =
        if (reduceMotion) {
            tween(durationMs(IslandMotion.queueSwitchMs, speed), easing = LinearEasing)
        } else {
            spring(dampingRatio = IslandMotion.SNAPPY_DAMPING, stiffness = IslandMotion.COLLAPSE_STIFFNESS * speedFactor(speed))
        }

    /** Horizontal movement (swipe between events). */
    fun slide(speed: Float, reduceMotion: Boolean): AnimationSpec<Dp> =
        if (reduceMotion) {
            tween(durationMs(IslandMotion.queueSwitchMs, speed), easing = LinearEasing)
        } else {
            spring(dampingRatio = IslandMotion.DEFAULT_DAMPING, stiffness = IslandMotion.COLLAPSE_STIFFNESS * speedFactor(speed))
        }

    /** Alpha for content crossfades. */
    fun crossfade(speed: Float, reduceMotion: Boolean): AnimationSpec<Float> =
        tween(
            durationMs(IslandMotion.contentCrossfadeMs, speed),
            easing = if (reduceMotion) LinearEasing else androidx.compose.animation.core.FastOutSlowInEasing,
        )

    /** Alpha for the whole island (appear/disappear). */
    fun fade(speed: Float, reduceMotion: Boolean): AnimationSpec<Float> =
        tween(durationMs(if (reduceMotion) 120 else 220, speed), easing = LinearEasing)

    /** Progress/bar animation: linear so a download bar tracks the source honestly. */
    fun progress(speed: Float): AnimationSpec<Float> = tween(durationMs(240, speed), easing = LinearEasing)

    fun durationMs(base: Int, speed: Float): Int =
        (base / speed.coerceIn(0.25f, 4f)).toInt().coerceAtLeast(1)

    private fun speedFactor(speed: Float): Float = speed.coerceIn(0.4f, 2.5f)
}
