package ru.ytkab0bp.beamklipper.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.MainActivity
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.ui.components.BrutalButton
import ru.ytkab0bp.beamklipper.ui.components.BrutalSwitch
import ru.ytkab0bp.beamklipper.ui.components.BrutalTile
import ru.ytkab0bp.beamklipper.ui.components.bouncyClickable
import ru.ytkab0bp.beamklipper.ui.state.SettingsViewModel
import ru.ytkab0bp.beamklipper.ui.theme.BadgeBlue
import ru.ytkab0bp.beamklipper.ui.theme.BadgeGreen
import ru.ytkab0bp.beamklipper.ui.theme.BadgeOrange
import ru.ytkab0bp.beamklipper.ui.theme.BadgePink
import ru.ytkab0bp.beamklipper.ui.theme.BadgeSlate
import ru.ytkab0bp.beamklipper.ui.theme.BadgeTeal
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val webFrontend by viewModel.webFrontend.collectAsStateWithLifecycle()
    val usbNaming by viewModel.usbNaming.collectAsStateWithLifecycle()
    val cameraEnabled by viewModel.cameraEnabled.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
    val bottomInset = navBarInsets.calculateBottomPadding()

    var showListUsb by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshCameraSwitch(granted)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp)
    ) {
        val tileBg = MaterialTheme.colorScheme.surface
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

        BrutalSectionHeader(stringResource(R.string.EngineFrontend))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrutalTile(
                modifier = Modifier.weight(1f).height(140.dp),
                background = tileBg,
                onClick = { viewModel.cycleEngine() }
            ) {
                Column {
                    IconBadge(
                        icon = R.drawable.ic_memory_chip_outline_28,
                        background = BadgeBlue,
                        contentColor = Color.White
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.Firmware),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = viewModel.engineTitle(engine),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
            }
            BrutalTile(
                modifier = Modifier.weight(1f).height(140.dp),
                background = tileBg,
                onClick = { viewModel.cycleFrontend() }
            ) {
                Column {
                    IconBadge(
                        icon = R.drawable.ic_sync_outline_28,
                        background = BadgeGreen,
                        contentColor = Color.White
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.WebFrontend),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = viewModel.frontendTitle(webFrontend),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        BrutalSectionHeader(stringResource(R.string.USB))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrutalTile(
                modifier = Modifier.weight(1f).height(140.dp),
                background = tileBg,
                onClick = { viewModel.cycleUsbNaming() }
            ) {
                Column {
                    IconBadge(
                        icon = R.drawable.ic_usb_cable_28,
                        background = BadgeOrange,
                        contentColor = Color.White
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.USBDeviceNaming),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = viewModel.usbNamingTitle(usbNaming),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
            }
            BrutalTile(
                modifier = Modifier.weight(1f).height(140.dp),
                background = tileBg,
                onClick = { showListUsb = true }
            ) {
                Column {
                    IconBadge(
                        icon = R.drawable.ic_grid_layout_outline_28,
                        background = BadgeTeal,
                        contentColor = Color.White
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.ListUSB),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Transparent
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        BrutalSectionHeader(stringResource(R.string.AppSettings))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = tileBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column {
                GroupSwitchRow(
                    title = stringResource(R.string.EnableCamera),
                    checked = cameraEnabled,
                    icon = R.drawable.ic_camera_outline_28,
                    iconBackground = BadgePink,
                    onCheckedChange = { checked ->
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            viewModel.setCameraEnabled(checked)
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                GroupActionRow(
                    title = stringResource(R.string.AppLanguage),
                    subtitle = viewModel.languageTitle(appLanguage),
                    icon = R.drawable.ic_globe_outline_28,
                    iconBackground = BadgePink,
                    onClick = { showLanguage = true }
                )
            }
        }

        if (context is MainActivity && context.isCurrentLauncher()) {
            Spacer(Modifier.height(28.dp))
            BrutalSectionHeader(stringResource(R.string.Other))
            BrutalActionRow(
                title = stringResource(R.string.SystemSettings),
                icon = R.drawable.ic_services_outline_28,
                iconBackground = BadgeSlate,
                onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            )
        }

        Spacer(Modifier.height(28.dp))
        BrutalSectionHeader(stringResource(R.string.About))
        BrutalTile(
            modifier = Modifier.fillMaxWidth(),
            background = tileBg
        ) {
            Column {
                Text(
                    text = stringResource(R.string.IntroTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.IntroText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        BrutalTile(
            modifier = Modifier.fillMaxWidth(),
            background = Color.Unspecified
        ) {
            Text(
                text = stringResource(R.string.PrivacyNote),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        BrutalTile(
            modifier = Modifier.fillMaxWidth(),
            background = tileBg
        ) {
            Column {
                Text(
                    text = stringResource(R.string.CreditsTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.CreditsText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        BrutalSectionHeader("Information")
        BrutalTile(
            modifier = Modifier.fillMaxWidth(),
            background = Color.Unspecified,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/utkabobr/BeamKlipper")))
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_external_link_outline_24),
                    contentDescription = null,
                    tint = onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.OriginalBeamKlipper),
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right_28),
                    contentDescription = null,
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        BrutalTile(
            modifier = Modifier.fillMaxWidth(),
            background = Color.Unspecified,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ProtonKicker/Cream")))
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github_28),
                    contentDescription = null,
                    tint = onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.GitHubRepo),
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right_28),
                    contentDescription = null,
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(bottomInset + 156.dp))
    }

    if (showListUsb) {
        ListUsbDialog(context, onDismiss = { showListUsb = false })
    }
    if (showLanguage) {
        LanguageDialog(onDismiss = { showLanguage = false })
    }
}

@Composable
private fun BrutalSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )
}

@Composable
private fun GroupSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Int,
    iconBackground: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.98f) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, background = iconBackground, contentColor = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BrutalSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BrutalActionRow(
    title: String,
    subtitle: String? = null,
    icon: Int,
    iconBackground: Color,
    onClick: () -> Unit
) {
    BrutalTile(
        modifier = Modifier.fillMaxWidth(),
        background = Color.Unspecified,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = icon, background = iconBackground, contentColor = Color.White)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
}

@Composable
private fun GroupActionRow(
    title: String,
    subtitle: String? = null,
    icon: Int,
    iconBackground: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, background = iconBackground, contentColor = Color.White)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

@Composable
private fun BrutalAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 14.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    title()
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        text()
                    }
                    if (dismissButton != null || confirmButton != null) {
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            dismissButton?.let {
                                it()
                                Spacer(Modifier.width(8.dp))
                            }
                            confirmButton?.invoke()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBadge(
    icon: Int,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ListUsbDialog(context: Context, onDismiss: () -> Unit) {
    val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val list = mutableListOf<String>()
    for (dev in manager.deviceList.values) {
        val drv = KlipperProbeTable.getInstance().findDriver(dev)
        list.add(
            Integer.toHexString(dev.vendorId) + "/" + Integer.toHexString(dev.productId) +
                    " - " + dev.deviceName +
                    (if (drv != null) " - " + drv.name + "\n" +
                            File(KlipperApp.INSTANCE.filesDir, "serial/" + UsbSerialManager.getUID(dev)).absolutePath else "")
        )
    }
    BrutalAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.ListUSBTitle),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            if (list.isEmpty()) {
                Text(stringResource(R.string.ListUSBNoDevices), color = MaterialTheme.colorScheme.onSurface)
            } else {
                Column {
                    list.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            BrutalButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val options = listOf(
        KlipperApp.INSTANCE.getString(R.string.LanguageSystem),
        KlipperApp.INSTANCE.getString(R.string.LanguageEnglish),
        KlipperApp.INSTANCE.getString(R.string.LanguageRussian),
        KlipperApp.INSTANCE.getString(R.string.LanguageChineseSimplified),
        KlipperApp.INSTANCE.getString(R.string.LanguageChineseTraditional)
    )
    BrutalAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.AppLanguage),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Prefs.appLanguage = when (index) {
                                    0 -> Prefs.LANGUAGE_SYSTEM
                                    1 -> Prefs.LANGUAGE_ENGLISH
                                    2 -> Prefs.LANGUAGE_RUSSIAN
                                    3 -> Prefs.LANGUAGE_CHINESE_SIMPLIFIED
                                    else -> Prefs.LANGUAGE_CHINESE_TRADITIONAL
                                }
                                Prefs.applyAppLanguage()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    )
}
