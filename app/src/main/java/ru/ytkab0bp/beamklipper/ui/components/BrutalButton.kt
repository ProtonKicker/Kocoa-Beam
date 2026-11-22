package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    minHeight: Dp = 40.dp
) {
    val resolvedBackground = if (background == Color.Unspecified) MaterialTheme.colorScheme.primary else background
    val resolvedContentColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onPrimary else contentColor
    val bg = if (enabled) resolvedBackground else resolvedBackground.copy(alpha = 0.4f)
    Surface(
        modifier = modifier.heightIn(min = minHeight),
        shape = MaterialTheme.shapes.large,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(Color.Transparent)
                .bouncyClickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) resolvedContentColor else resolvedContentColor.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BrutalTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified
) {
    val resolvedContentColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    Surface(
        modifier = modifier.height(40.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .bouncyClickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = resolvedContentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
