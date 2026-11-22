package ru.ytkab0bp.beamklipper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.bouncyClickable
import ru.ytkab0bp.beamklipper.ui.screens.ConfigScreen
import ru.ytkab0bp.beamklipper.ui.screens.HomeScreen
import ru.ytkab0bp.beamklipper.ui.state.MainViewModel

const val NAV_HOME = 0
const val NAV_CONFIG = 1

@Composable
fun NavHost(
    isCurrentLauncher: Boolean,
    mainViewModel: MainViewModel
) {
    var nav by remember { mutableStateOf(NAV_HOME) }
    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
    val bottomInset = navBarInsets.calculateBottomPadding()
    val statusInsets = WindowInsets.statusBars.asPaddingValues()
    val topInset = statusInsets.calculateTopPadding()

    BackHandler(enabled = nav == NAV_CONFIG) {
        nav = NAV_HOME
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = nav,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset),
            transitionSpec = {
                fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(90))
            },
            label = "screen"
        ) { screen ->
            when (screen) {
                NAV_CONFIG -> ConfigScreen()
                else -> HomeScreen(
                    isCurrentLauncher = isCurrentLauncher,
                    mainViewModel = mainViewModel
                )
            }
        }

        FloatingNavBubble(
            active = nav,
            onChange = { nav = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp)
        )
    }
}

@Composable
private fun FloatingNavBubble(
    active: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavBubbleItem(
                    active = active == NAV_HOME,
                    icon = R.drawable.ic_home_outline_28,
                    contentDescription = stringResource(R.string.AppName),
                    onClick = { onChange(NAV_HOME) }
                )
                NavBubbleItem(
                    active = active == NAV_CONFIG,
                    icon = R.drawable.ic_settings_outline_28,
                    contentDescription = stringResource(R.string.Settings),
                    onClick = { onChange(NAV_CONFIG) }
                )
            }
        }
    }
}

@Composable
private fun NavBubbleItem(
    active: Boolean,
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val bg = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .size(52.dp)
            .bouncyClickable(
                pressedScale = 0.88f,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        shape = CircleShape,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
