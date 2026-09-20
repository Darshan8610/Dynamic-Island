package dev.island.feature.island.layout

import dev.island.core.platform.CutoutKind
import dev.island.domain.model.IslandAppearance
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandPosition
import dev.island.domain.model.LandscapeBehavior

/**
 * Everything the island needs to lay itself out, in dp, derived from live insets.
 * No resolution is assumed: a 320dp phone, a 428dp phone, a tablet and a folded/unfolded
 * foldable all produce different, valid values.
 */
data class IslandLayoutInput(
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val safeTopDp: Float,
    val statusBarHeightDp: Float,
    val cutoutKind: CutoutKind = CutoutKind.NONE,
    val hasCutout: Boolean = false,
    val cutoutTopDp: Float = 0f,
    val cutoutBottomDp: Float = 0f,
    val cutoutLeftDp: Float = 0f,
    val cutoutRightDp: Float = 0f,
    val landscape: Boolean = false,
    val isLargeScreen: Boolean = false,
    val fontScale: Float = 1f,
    val position: IslandPosition = IslandPosition.AUTO_CUTOUT,
    val customVerticalOffsetDp: Float = 0f,
    val landscapeBehavior: LandscapeBehavior = LandscapeBehavior.ADAPT,
    val appearance: IslandAppearance = IslandAppearance(),
)

/** Where the overlay window sits and how large it is, in dp. */
data class IslandBounds(
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float,
    val yDp: Float,
    val cornerRadiusDp: Float,
    val visible: Boolean = true,
    /** Landscape "minimize" mode: a tiny indicator instead of a card. */
    val minimized: Boolean = false,
)

/**
 * The island's geometry engine — pure, deterministic and unit-tested.
 *
 * Defaults follow the design language (collapsed ≈ 120–170 × 32–42 dp) but every value is
 * derived from the screen, the cutout, the user's scale and the system font scale, never
 * hard-coded per device.
 */
object IslandMetrics {

    const val COLLAPSED_WIDTH_DP = 148f
    const val COLLAPSED_HEIGHT_DP = 36f
    const val MIN_COLLAPSED_WIDTH_DP = 96f
    const val MAX_COLLAPSED_WIDTH_DP = 210f
    const val MIN_COLLAPSED_HEIGHT_DP = 28f
    const val MAX_COLLAPSED_HEIGHT_DP = 52f
    const val SIDE_MARGIN_DP = 12f
    const val STATUS_BAR_GAP_DP = 3f
    const val LOWER_OFFSET_DP = 14f
    const val MAX_EXPANDED_WIDTH_DP = 420f
    const val CUTOUT_CLEARANCE_DP = 4f

    /** Punch-hole devices: the pill is centred on the camera and tall enough to cover it. */
    private val punchHoleKinds = setOf(
        CutoutKind.CENTER_PUNCH_HOLE,
        CutoutKind.LEFT_PUNCH_HOLE,
        CutoutKind.RIGHT_PUNCH_HOLE,
    )

    fun collapsed(input: IslandLayoutInput): IslandBounds {
        if (!input.visibleInLandscape()) return hidden(input)
        if (input.landscape && input.landscapeBehavior == LandscapeBehavior.MINIMIZE) {
            return minimized(input)
        }

        val appearance = input.appearance
        val fontBoost = 1f + ((input.fontScale - 1f).coerceIn(-0.3f, 0.8f) * 0.35f)
        val rawWidth = COLLAPSED_WIDTH_DP * appearance.sizeScale * fontBoost
        val rawHeight = COLLAPSED_HEIGHT_DP * appearance.sizeScale

        var width = rawWidth.coerceIn(MIN_COLLAPSED_WIDTH_DP, MAX_COLLAPSED_WIDTH_DP)
        width = width.coerceAtMost(input.screenWidthDp - SIDE_MARGIN_DP * 2)

        var height = rawHeight.coerceIn(MIN_COLLAPSED_HEIGHT_DP, MAX_COLLAPSED_HEIGHT_DP)
        if (input.cutoutKind in punchHoleKinds) {
            val cutoutHeight = (input.cutoutBottomDp - input.cutoutTopDp).coerceAtLeast(0f)
            height = height.coerceAtLeast(cutoutHeight + CUTOUT_CLEARANCE_DP * 2)
        }
        if (input.landscape) {
            width = width.coerceAtMost(input.screenWidthDp * LANDSCAPE_MAX_WIDTH_RATIO)
            height = (height * LANDSCAPE_HEIGHT_SCALE).coerceAtLeast(MIN_COLLAPSED_HEIGHT_DP)
        }

        val y = anchorY(input, height)
        val x = centeredX(input, width, height, y)

        return IslandBounds(
            widthDp = width,
            heightDp = height,
            xDp = x,
            yDp = y,
            cornerRadiusDp = (height / 2f).coerceAtMost(appearance.cornerRadiusDp * appearance.sizeScale),
        )
    }

    fun expanded(input: IslandLayoutInput, type: IslandEventType?): IslandBounds {
        if (!input.visibleInLandscape()) return hidden(input)
        if (input.landscape && input.landscapeBehavior == LandscapeBehavior.MINIMIZE) {
            return minimized(input)
        }

        val appearance = input.appearance
        val available = input.screenWidthDp - SIDE_MARGIN_DP * 2
        val width = (if (input.isLargeScreen) MAX_EXPANDED_WIDTH_DP else available)
            .coerceAtMost(available)
            .coerceAtLeast(MIN_COLLAPSED_WIDTH_DP)

        val height = (expandedHeightDp(type, appearance) * (if (input.landscape) LANDSCAPE_EXPANDED_SCALE else 1f))
            .coerceAtMost(input.screenHeightDp * MAX_EXPANDED_HEIGHT_RATIO)

        val collapsedHeight = COLLAPSED_HEIGHT_DP * appearance.sizeScale
        val y = anchorY(input, collapsedHeight)
        val x = centeredX(input, width, height, y, cutoutAware = false)

        return IslandBounds(
            widthDp = width,
            heightDp = height,
            xDp = x,
            yDp = y,
            cornerRadiusDp = (appearance.cornerRadiusDp * appearance.sizeScale).coerceIn(8f, 40f),
        )
    }

    /** Per-event expanded height: each renderer gets exactly the space it needs, no more. */
    fun expandedHeightDp(type: IslandEventType?, appearance: IslandAppearance): Float {
        val base = when (type) {
            IslandEventType.MEDIA -> 196f
            IslandEventType.CALL -> 156f
            IslandEventType.TIMER -> 132f
            IslandEventType.STOPWATCH -> 150f
            IslandEventType.CHARGING, IslandEventType.BATTERY -> 132f
            IslandEventType.DOWNLOAD -> 142f
            IslandEventType.NAVIGATION -> 118f
            IslandEventType.BLUETOOTH -> 108f
            IslandEventType.ALARM -> 124f
            IslandEventType.NOTIFICATION -> 116f
            IslandEventType.SYSTEM, IslandEventType.CUSTOM -> 112f
            null -> 116f
        }
        return (base * appearance.sizeScale * (0.9f + appearance.textScale * 0.1f)).coerceIn(84f, 260f)
    }

    /** Top edge of the island, respecting cutout type and the user's position preference. */
    private fun anchorY(input: IslandLayoutInput, height: Float): Float {
        val belowStatusBar = (input.safeTopDp.coerceAtLeast(input.statusBarHeightDp)) + STATUS_BAR_GAP_DP
        return when (input.position) {
            IslandPosition.AUTO_CUTOUT ->
                if (input.hasCutout && input.cutoutKind in punchHoleKinds) {
                    val cutoutCenter = (input.cutoutTopDp + input.cutoutBottomDp) / 2f
                    (cutoutCenter - height / 2f).coerceAtLeast(0f)
                } else {
                    belowStatusBar
                }

            IslandPosition.TOP_CENTER -> belowStatusBar
            IslandPosition.SLIGHTLY_LOWER -> belowStatusBar + LOWER_OFFSET_DP
            IslandPosition.CUSTOM -> (belowStatusBar + input.customVerticalOffsetDp).coerceAtLeast(0f)
        } + input.appearance.verticalOffsetDp
    }

    /**
     * Horizontal placement. Centred by default; if the device has an unusual cutout that the
     * centred pill would cover, the pill shifts just enough to clear it.
     */
    private fun centeredX(
        input: IslandLayoutInput,
        width: Float,
        height: Float,
        y: Float,
        cutoutAware: Boolean = true,
    ): Float {
        val centered = ((input.screenWidthDp - width) / 2f) + input.appearance.horizontalOffsetDp
        // Covering a centred punch hole is the whole point of AUTO_CUTOUT: the pill must stay
        // centred on the camera instead of dodging it. Only an *unintended* overlap shifts the pill.
        val deliberatelyCovering = input.position == IslandPosition.AUTO_CUTOUT &&
            input.cutoutKind in punchHoleKinds
        if (!cutoutAware || !input.hasCutout || deliberatelyCovering) {
            return centered.coerceAtLeast(SIDE_MARGIN_DP / 2f)
        }

        val pillLeft = centered
        val pillRight = centered + width
        val pillTop = y
        val pillBottom = y + height
        val overlaps = pillLeft < input.cutoutRightDp && pillRight > input.cutoutLeftDp &&
            pillTop < input.cutoutBottomDp && pillBottom > input.cutoutTopDp
        if (!overlaps) return centered

        // Shift away from the cutout by the smallest amount that clears it.
        val shiftRight = (input.cutoutRightDp + CUTOUT_CLEARANCE_DP) - pillLeft
        val shiftLeft = pillRight - (input.cutoutLeftDp - CUTOUT_CLEARANCE_DP)
        val shift = if (shiftRight <= shiftLeft) shiftRight else -shiftLeft
        return (centered + shift).coerceIn(SIDE_MARGIN_DP / 2f, (input.screenWidthDp - width - SIDE_MARGIN_DP / 2f).coerceAtLeast(0f))
    }

    private fun minimized(input: IslandLayoutInput): IslandBounds {
        val size = MINIMIZED_SIZE_DP * input.appearance.sizeScale
        val y = anchorY(input, size)
        return IslandBounds(
            widthDp = size,
            heightDp = size,
            xDp = ((input.screenWidthDp - size) / 2f + input.appearance.horizontalOffsetDp).coerceAtLeast(0f),
            yDp = y,
            cornerRadiusDp = size / 2f,
            minimized = true,
        )
    }

    private fun hidden(input: IslandLayoutInput): IslandBounds = IslandBounds(
        widthDp = 0f,
        heightDp = 0f,
        xDp = 0f,
        yDp = 0f,
        cornerRadiusDp = 0f,
        visible = false,
    )

    private fun IslandLayoutInput.visibleInLandscape(): Boolean =
        !landscape || landscapeBehavior != LandscapeBehavior.HIDE

    private const val LANDSCAPE_MAX_WIDTH_RATIO = 0.46f
    private const val LANDSCAPE_HEIGHT_SCALE = 0.9f
    private const val LANDSCAPE_EXPANDED_SCALE = 0.86f
    private const val MAX_EXPANDED_HEIGHT_RATIO = 0.55f
    private const val MINIMIZED_SIZE_DP = 26f
}
