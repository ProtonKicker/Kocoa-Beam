package ru.ytkab0bp.beamklipper.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.PermissionsChecker
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.BrutalButton
import ru.ytkab0bp.beamklipper.ui.components.BrutalSwitch
import androidx.compose.material3.Surface

@Composable
fun PermissionScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    var batteryChecked by remember { mutableStateOf(PermissionsChecker.hasBatteryPerm()) }
    var dozeChecked by remember { mutableStateOf(PermissionsChecker.hasBatteryOptimizationIgnored()) }
    var notificationsChecked by remember { mutableStateOf(PermissionsChecker.hasNotificationPerm()) }
    var hideChannelChecked by remember { mutableStateOf(PermissionsChecker.isNotificationsChannelHidden()) }
    var sdcardChecked by remember { mutableStateOf(PermissionsChecker.isNotBrokenBySDCard()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryChecked = PermissionsChecker.hasBatteryPerm()
                dozeChecked = PermissionsChecker.hasBatteryOptimizationIgnored()
                notificationsChecked = PermissionsChecker.hasNotificationPerm()
                hideChannelChecked = PermissionsChecker.isNotificationsChannelHidden()
                sdcardChecked = PermissionsChecker.isNotBrokenBySDCard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsChecked = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.AppName),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PermissionRow(
                            title = stringResource(R.string.IgnoreBatteryOptimization),
                            checked = dozeChecked,
                            onRowClick = {
                                if (!dozeChecked) {
                                    try {
                                        val pkg = context.packageName
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                            .setData(Uri.parse("package:$pkg"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (t: Throwable) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        )
                    }
                    PermissionRow(
                        title = stringResource(R.string.BatteryOptimizationExclusion),
                        checked = batteryChecked,
                        onRowClick = {
                            if (!batteryChecked) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionRow(
                            title = stringResource(R.string.Notifications),
                            checked = notificationsChecked,
                            onRowClick = {
                                if (!notificationsChecked) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                    if (PermissionsChecker.ENABLE_NOTIFICATIONS_CHANNEL_CHECK &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !PermissionsChecker.ignoreNotificationsChannel()
                    ) {
                        PermissionRow(
                            title = stringResource(R.string.HideNotificationsChannel),
                            checked = hideChannelChecked,
                            onRowClick = {
                                if (!hideChannelChecked) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.HideNotificationsChannelInfo, context.getString(R.string.ServicesChannel)),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .putExtra(Settings.EXTRA_CHANNEL_ID, KlipperApp.SERVICES_CHANNEL)
                                    )
                                }
                            }
                        )
                    }
                    if (!PermissionsChecker.isNotBrokenBySDCard()) {
                        PermissionRow(
                            title = stringResource(R.string.NotOnSdcard),
                            checked = sdcardChecked,
                            onRowClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${KlipperApp.INSTANCE.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                Toast.makeText(context, R.string.NotOnSdcardInfo, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BrutalButton(
                            text = stringResource(R.string.Next),
                            onClick = onNext,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    checked: Boolean,
    onRowClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onRowClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            BrutalSwitch(checked = checked, onCheckedChange = null)
        }
    }
}
