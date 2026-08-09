package ru.ytkab0bp.beamklipper.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.BrutalButton
import ru.ytkab0bp.beamklipper.ui.components.BrutalSwitch
import ru.ytkab0bp.beamklipper.ui.state.InstanceEditorViewModel
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.InkMuted
import ru.ytkab0bp.beamklipper.ui.theme.Paper

@Composable
fun InstanceEditorSheet(
    editInstance: KlipperInstance?,
    onDismiss: () -> Unit,
    viewModel: InstanceEditorViewModel = viewModel()
) {
    val filesList by viewModel.filesList.collectAsState()
    val configFile by viewModel.configFile.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val defaultName by viewModel.defaultName.collectAsState()
    val context = LocalContext.current

    var name by remember(editInstance) {
        mutableStateOf(
            editInstance?.name?.let { TextFieldValue(it) } ?: TextFieldValue("")
        )
    }
    var nameEdited by remember(editInstance) { mutableStateOf(false) }
    var autostart by remember(editInstance) { mutableStateOf(editInstance?.autostart ?: false) }
    var showConfigPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editInstance, defaultName) {
        if (editInstance != null) return@LaunchedEffect
        if (nameEdited) return@LaunchedEffect
        val preset = defaultName ?: return@LaunchedEffect
        if (name.text.isEmpty()) {
            name = TextFieldValue(preset)
        }
    }

    LaunchedEffect(editInstance) {
        if (editInstance == null) viewModel.loadForCreate() else viewModel.loadForEdit(editInstance)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Paper)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Paper, RectangleShape)
                            .border(2.dp, Ink, RectangleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_left_28),
                            contentDescription = stringResource(android.R.string.cancel),
                            tint = Ink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(if (editInstance == null) R.string.NewInstance else R.string.EditInstance),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp, top = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.InstanceName),
                        style = MaterialTheme.typography.titleSmall,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = name,
                        onValueChange = { v: TextFieldValue ->
                            nameEdited = true
                            name = v
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(Ink),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Paper, RectangleShape)
                                    .border(2.dp, Ink, RectangleShape)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                if (name.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.InstanceDefaultNameHint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = InkMuted
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(Modifier.height(20.dp))

                    if (editInstance == null) {
                        Text(
                            text = stringResource(R.string.InstanceConfig),
                            style = MaterialTheme.typography.titleSmall,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        val filesReady = filesList.isNotEmpty()
                        val filesLoading = configFile == null && !filesReady
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Paper, RectangleShape)
                                .border(2.dp, Ink, RectangleShape)
                                .clickable { if (filesReady) showConfigPicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        configFile != null -> configFile!!
                                        filesLoading -> "⏳ ${stringResource(R.string.InstanceConfigLoading)}"
                                        else -> stringResource(R.string.InstanceConfigHint)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (configFile != null) Ink else InkMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    painterResource(R.drawable.ic_chevron_down_28),
                                    contentDescription = null,
                                    tint = Ink
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Paper, RectangleShape)
                                .border(2.dp, Ink, RectangleShape)
                                .clickable { openInstanceFolder(context, editInstance) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_folder_outline_28),
                                    contentDescription = null,
                                    tint = Ink
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.EditOpenDirectory),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Ink
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Paper, RectangleShape)
                            .border(2.dp, Ink, RectangleShape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.Autostart),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Ink,
                                modifier = Modifier.weight(1f)
                            )
                            BrutalSwitch(checked = autostart, onCheckedChange = { autostart = it })
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    BrutalButton(
                        text = stringResource(if (editInstance == null) R.string.InstanceCreate else R.string.InstanceOK),
                        onClick = {
                            var nameStr = name.text.trim()
                            if (editInstance == null && configFile.isNullOrEmpty()) {
                                error = context.getString(R.string.ErrorConfigEmpty)
                                return@BrutalButton
                            }
                            if (nameStr.isEmpty() && editInstance == null) {
                                nameStr = defaultName ?: context.getString(R.string.InstanceDefaultName, 1)
                            }
                            viewModel.save(nameStr, autostart) {
                                onDismiss()
                            }
                        },
                        enabled = !saving,
                        background = Accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                }
            }
        }
    }

    if (showConfigPicker) {
        Dialog(
            onDismissRequest = { showConfigPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val dialogMaxHeight = maxHeight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dialogMaxHeight)
                        .background(Paper, RectangleShape)
                        .border(2.dp, Ink, RectangleShape)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = dialogMaxHeight)
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.InstanceConfig),
                                style = MaterialTheme.typography.titleLarge,
                                color = Ink,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            BrutalButton(
                                text = stringResource(android.R.string.cancel),
                                onClick = { showConfigPicker = false },
                                background = Paper,
                                contentColor = Ink,
                                minHeight = 36.dp,
                                modifier = Modifier.height(36.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 12.dp)
                        ) {
                            filesList.forEach { f ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectConfig(f)
                                            showConfigPicker = false
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(f, style = MaterialTheme.typography.bodyLarge, color = Ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    error?.let {
        Dialog(onDismissRequest = { error = null }) {
            Box(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Paper, RectangleShape)
                        .border(2.dp, Ink, RectangleShape)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.Error),
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            BrutalButton(
                                text = stringResource(android.R.string.ok),
                                onClick = { error = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openInstanceFolder(context: android.content.Context, instance: KlipperInstance) {
    val uri = android.provider.DocumentsContract.buildRootUri(context.packageName, instance.id)
    try {
        try {
            try {
                context.startActivity(Intent("android.intent.action.VIEW").setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR))
            } catch (_: android.content.ActivityNotFoundException) {
                context.startActivity(Intent("android.provider.action.BROWSE").setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR))
            }
        } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent("android.provider.action.BROWSE_DOCUMENT_ROOT").setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR))
        }
    } catch (_: Throwable) {}
}
