package ru.ytkab0bp.beamklipper.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.BrutalButton
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.Paper

@Composable
fun QRCodeDialog(
    link: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap: Bitmap? = remember(link) {
        val encoder = QRGEncoder(link, null, QRGContents.Type.TEXT, 600)
        encoder.setColorWhite(Color.WHITE)
        encoder.setColorBlack(Color.BLACK)
        runCatching { encoder.bitmap }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Paper, RectangleShape)
                    .border(2.dp, Ink, RectangleShape)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        stringResource(R.string.QRCode),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    bitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .size(264.dp)
                                .background(androidx.compose.ui.graphics.Color.White, RectangleShape)
                                .border(2.dp, Ink, RectangleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = link,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .border(2.dp, Ink, RectangleShape)
                                .clip(RectangleShape)
                                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                                .background(Paper)
                                .padding(horizontal = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = link,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    painterResource(R.drawable.ic_external_link_outline_24),
                                    contentDescription = stringResource(R.string.QROpen),
                                    tint = Ink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        BrutalButton(
                            text = stringResource(R.string.QRCopy),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("", link))
                            },
                            background = Paper,
                            contentColor = Ink,
                            minHeight = 44.dp,
                            modifier = Modifier.height(44.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BrutalButton(
                            text = stringResource(R.string.QRCancel),
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            background = Paper,
                            contentColor = Ink
                        )
                        BrutalButton(
                            text = stringResource(R.string.QROpen),
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) },
                            background = Accent,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )
                    }
                }
            }
        }
    }
}
