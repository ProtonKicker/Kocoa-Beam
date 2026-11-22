package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

const val DEFAULT_PRESSED_SCALE = 0.95f

@Composable
private fun pressScaleOf(interaction: MutableInteractionSource, pressedScale: Float): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        // Stiff spring: instant, snappy press feedback with just a hint of bounce.
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh),
        label = "pressScale"
    )
    return scale
}

/** Clickable with a springy MIUI-style press-down feedback. */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = DEFAULT_PRESSED_SCALE,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScaleOf(interaction, pressedScale)
    this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
}

/** [bouncyClickable] variant that also handles long presses. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyCombinedClickable(
    enabled: Boolean = true,
    pressedScale: Float = DEFAULT_PRESSED_SCALE,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScaleOf(interaction, pressedScale)
    this
        .scale(scale)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick
        )
}
