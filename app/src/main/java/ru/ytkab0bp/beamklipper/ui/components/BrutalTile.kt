package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BrutalTile(
    modifier: Modifier = Modifier,
    background: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    offsetX: Int = 0,
    offsetY: Int = 0,
    borderWidth: Int = 2,
    content: @Composable () -> Unit
) {
    val targetColor = if (background == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        background
    }
    val surfaceColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(200),
        label = "tileColor"
    )
    val surfaceModifier = if (onClick != null) {
        modifier.bouncyClickable(pressedScale = 0.97f, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = surfaceModifier,
        shape = MaterialTheme.shapes.large,
        color = surfaceColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}
