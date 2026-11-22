package ru.ytkab0bp.beamklipper.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.service.WebService
import ru.ytkab0bp.beamklipper.ui.components.BrutalButton
import ru.ytkab0bp.beamklipper.ui.components.BrutalTextButton
import ru.ytkab0bp.beamklipper.ui.components.bouncyClickable
import ru.ytkab0bp.beamklipper.ui.components.bouncyCombinedClickable
import ru.ytkab0bp.beamklipper.ui.state.MainViewModel
import ru.ytkab0bp.beamklipper.ui.theme.BadgeGreen
import ru.ytkab0bp.beamklipper.ui.theme.BadgeTeal
import ru.ytkab0bp.beamklipper.ui.theme.badgeColorFor
import ru.ytkab0bp.beamklipper.utils.Prefs

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isCurrentLauncher: Boolean,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val instances by mainViewModel.instances.collectAsStateWithLifecycle()
    val instanceStates by mainViewModel.instanceStates.collectAsStateWithLifecycle()
    val webState by mainViewModel.webState.collectAsStateWithLifecycle()
    val webFrontend by mainViewModel.webFrontend.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var deleteInstance by remember { mutableStateOf<KlipperInstance?>(null) }
    var noFreeSlots by remember { mutableStateOf(false) }
    var editorInstance by remember { mutableStateOf<KlipperInstance?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    val anyRunning by remember(instances, instanceStates) {
        derivedStateOf {
            instances.any {
                val s = instanceStates[it.id ?: it.name] ?: it.getState()
                s == KlipperInstance.State.RUNNING || s == KlipperInstance.State.STARTING
            }
        }
    }
    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
    val bottomInset = navBarInsets.calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = bottomInset + 156.dp)
        ) {
            Text(
                text = stringResource(R.string.Instances),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))

            val isFluidd = webFrontend == Prefs.FRONTEND_FLUIDD
            val running = webState == KlipperInstance.State.RUNNING

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable(
                            pressedScale = 0.98f,
                            onClick = {
                                when {
                                    running -> openWebFrontend(context)
                                    webState == KlipperInstance.State.STARTING ||
                                        webState == KlipperInstance.State.STOPPING ||
                                        anyRunning -> Unit
                                    else -> mainViewModel.runStopAll()
                                }
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LeadingIcon(
                        drawable = if (isFluidd) R.drawable.ic_square_stack_up_outline_28 else R.drawable.ic_sailing_24,
                        background = BadgeTeal
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (isFluidd) R.string.Fluidd else R.string.Mainsail),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(
                            visible = running,
                            enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                            exit = fadeOut(tween(100)) + shrinkVertically(tween(120))
                        ) {
                            Text(
                                text = webIpInfo(context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right_28),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (instances.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        instances.forEachIndexed { index, inst ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            val k = inst.id ?: inst.name
                            val stateVal = instanceStates[k] ?: inst.getState()
                            InstanceRow(
                                instance = inst,
                                state = stateVal,
                                accent = badgeColorFor(index),
                                onClick = {
                                    inst.id?.let { id ->
                                        KlipperInstance.getInstance(id)?.let {
                                            editorInstance = it
                                            editorVisible = true
                                        }
                                    }
                                },
                                onLongClick = {
                                    inst.id?.let { id ->
                                        KlipperInstance.getInstance(id)?.let { deleteInstance = it }
                                    }
                                },
                                onToggle = {
                                    when (stateVal) {
                                        KlipperInstance.State.STARTING,
                                        KlipperInstance.State.STOPPING -> Unit
                                        KlipperInstance.State.IDLE -> {
                                            if (!KlipperInstance.hasFreeSlots()) {
                                                noFreeSlots = true
                                            } else {
                                                mainViewModel.toggle(inst)
                                            }
                                        }
                                        else -> mainViewModel.toggle(inst)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        FloatingCircleButton(
            icon = R.drawable.ic_add_outline_28,
            contentDescription = stringResource(R.string.NewInstance),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp)
                .padding(bottom = bottomInset + 22.dp),
            onClick = {
                if (instances.size >= KlipperInstance.SLOTS_COUNT) {
                    noFreeSlots = true
                } else {
                    editorInstance = null
                    editorVisible = true
                }
            }
        )
    }

    deleteInstance?.let { inst ->
        Dialog(onDismissRequest = { deleteInstance = null }) {
            val dlgShape = MaterialTheme.shapes.large
            Box(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, dlgShape)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.InstanceDelete, inst.name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.InstanceDeleteConfirm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            BrutalTextButton(
                                text = stringResource(android.R.string.cancel),
                                onClick = { deleteInstance = null }
                            )
                            Spacer(Modifier.width(8.dp))
                            BrutalButton(
                                text = stringResource(android.R.string.ok),
                                onClick = {
                                    mainViewModel.delete(inst)
                                    deleteInstance = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (noFreeSlots) {
        Dialog(onDismissRequest = { noFreeSlots = false }) {
            val dlgShape = MaterialTheme.shapes.large
            Box(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, dlgShape)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.NoFreeSlotsDescription, KlipperInstance.SLOTS_COUNT),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            BrutalButton(
                                text = stringResource(android.R.string.ok),
                                onClick = { noFreeSlots = false }
                            )
                        }
                    }
                }
            }
        }
    }

    if (editorVisible) {
        InstanceEditorSheet(
            editInstance = editorInstance,
            onDismiss = { editorVisible = false }
        )
    }
}

@Composable
private fun LeadingIcon(
    drawable: Int,
    background: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(drawable),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun InstanceRow(
    instance: KlipperInstance,
    state: KlipperInstance.State,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (state) {
        KlipperInstance.State.STARTING -> stringResource(R.string.InstanceStarting)
        KlipperInstance.State.STOPPING -> stringResource(R.string.InstanceStopping)
        KlipperInstance.State.RUNNING -> stringResource(R.string.InstanceRunning)
        KlipperInstance.State.IDLE -> stringResource(R.string.InstanceIdle)
    }
    val statusColor = when (state) {
        KlipperInstance.State.RUNNING -> BadgeGreen
        KlipperInstance.State.STARTING, KlipperInstance.State.STOPPING -> MaterialTheme.colorScheme.primary
        KlipperInstance.State.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val running = state == KlipperInstance.State.RUNNING
    val toggleColor by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(200),
        label = "toggleColor"
    )
    val toggleTint by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "toggleTint"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .bouncyCombinedClickable(
                pressedScale = 0.98f,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(drawable = instance.icon.drawable, background = accent)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instance.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val toggleEnabled = state != KlipperInstance.State.STARTING && state != KlipperInstance.State.STOPPING
        Surface(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .bouncyClickable(
                    enabled = toggleEnabled,
                    pressedScale = 0.85f,
                    onClick = onToggle
                ),
            shape = CircleShape,
            color = toggleColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(
                        if (state == KlipperInstance.State.RUNNING || state == KlipperInstance.State.STOPPING) {
                            R.drawable.ic_stop_24
                        } else {
                            R.drawable.ic_play_28
                        }
                    ),
                    contentDescription = null,
                    tint = toggleTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingCircleButton(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(56.dp)
            .bouncyClickable(pressedScale = 0.88f, onClickLabel = contentDescription, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private fun openWebFrontend(context: Context) {
    val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val i = wm.connectionInfo.ipAddress
    val ip = if (i == 0 || !KlipperInstance.isWebServerRunning()) "127.0.0.1" else Formatter.formatIpAddress(i)
    val t = System.currentTimeMillis()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip:${WebService.PORT}/?t=$t"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun webIpInfo(context: Context): String {
    val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return context.getString(R.string.IPInfo, Formatter.formatIpAddress(wm.connectionInfo.ipAddress), WebService.PORT)
}
