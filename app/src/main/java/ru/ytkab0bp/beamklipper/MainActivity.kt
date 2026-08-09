package ru.ytkab0bp.beamklipper

import android.Manifest
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialProber
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.ui.CreamApp
import ru.ytkab0bp.beamklipper.ui.state.AppState
import ru.ytkab0bp.beamklipper.ui.theme.CreamTheme
import ru.ytkab0bp.beamklipper.utils.Prefs

class MainActivity : AppCompatActivity() {
    private var isTV = false
    private var isCurrentLauncher = false

    @android.annotation.SuppressLint("BatteryLife", "InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature("android.hardware.touchscreen") ||
            !packageManager.hasSystemFeature("android.hardware.telephony")
        ) {
            isTV = true
            PermissionsChecker.setIgnoreNotificationsChannel(true)
        }
        if (Build.MANUFACTURER.lowercase(java.util.Locale.ROOT).contains("meizu") ||
            Build.BRAND.lowercase(java.util.Locale.ROOT).contains("meizu")
        ) {
            PermissionsChecker.setIgnoreNotificationsChannel(true)
        }
        isCurrentLauncher = intent?.categories?.contains(Intent.CATEGORY_HOME) == true

        AppState.start()

        processIntent(intent)

        setContent {
            CreamTheme {
                CreamApp(
                    isCurrentLauncher = isCurrentLauncher
                )
            }
        }

        if (Prefs.getLastCommit() != BuildConfig.COMMIT && KlipperApp.hasUpdateInfo) {
            Prefs.setLastCommit()
            ChangeLogDialog(this).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppState.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    fun isCurrentLauncher(): Boolean = isCurrentLauncher

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.Notifications, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processIntent(intent: Intent?) {
        if (intent != null && intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val prober = UsbSerialProber(KlipperProbeTable.getInstance())
            val manager = getSystemService(Context.USB_SERVICE) as UsbManager
            for (drv in prober.findAllDrivers(manager)) {
                if (!manager.hasPermission(drv.device)) {
                    manager.requestPermission(drv.device,
                        PendingIntent.getBroadcast(this, 0,
                            Intent(UsbSerialManager.ACTION_ON_DEVICE_CONNECTED).setPackage(packageName),
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE))
                } else {
                    sendBroadcast(Intent(UsbSerialManager.ACTION_ON_DEVICE_CONNECTED)
                        .putExtra(UsbManager.EXTRA_DEVICE, drv.device)
                        .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true)
                        .setPackage(packageName))
                }
            }
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 100
        private const val REQUEST_CAMERA = 200
    }
}
