package ru.ytkab0bp.beamklipper.serial

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object UsbSerialManager {
    const val ACTION_ON_DEVICE_CONNECTED = "${BuildConfig.APPLICATION_ID}.action.DEVICE_CONNECTED"
    const val FLAG_RESET_ARDUINO = 1
    private const val DEBUG = false
    private const val TAG = "beam_usb_serial"

    private val portMap = HashMap<String, UsbSerialPort>()
    private val nativePortMap = HashMap<String, NativeSerialPort>()
    private val readThreadMap = HashMap<String, ReadThread>()
    private var mUsbManager: UsbManager? = null

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED || intent.action == ACTION_ON_DEVICE_CONNECTED) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    if (mUsbManager?.hasPermission(device) == true) {
                        val prober = UsbSerialProber(KlipperProbeTable.getInstance())
                        val drv = prober.probeDevice(device)
                        if (drv != null && drv.ports.isNotEmpty()) {
                            connect(drv)
                        }
                    } else {
                        if (intent.action == ACTION_ON_DEVICE_CONNECTED && !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (DEBUG) {
                                Log.d(TAG, "Failed to acquire usb permission")
                            }
                            return
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Failed to connect, no permission: ${getUID(device)}")
                        }
                    }
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    close(getUID(device))
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun init(ctx: Application) {
        val f = File(KlipperApp.INSTANCE.filesDir, "serial")
        if (f.exists()) {
            for (c in f.listFiles() ?: emptyArray()) {
                if (c.delete() && DEBUG) {
                    Log.d(TAG, "Deleted old ${c.absolutePath}")
                }
            }
        } else f.mkdirs()

        mUsbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_ON_DEVICE_CONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }

        connectAll()
    }

    fun connectAll() {
        val prober = UsbSerialProber(KlipperProbeTable.getInstance())
        val manager = KlipperApp.INSTANCE.getSystemService(Context.USB_SERVICE) as UsbManager
        for (drv in prober.findAllDrivers(manager)) {
            if (drv.ports.isNotEmpty()) {
                connect(drv)
            }
        }
    }

    fun disconnectAll() {
        for (uid in portMap.keys) {
            close(uid)
        }
    }

    fun getUID(device: UsbDevice): String {
        return when (Prefs.usbDeviceNaming) {
            Prefs.USB_DEVICE_NAMING_BY_VID_PID ->
                Integer.toHexString(device.vendorId) + "_" + Integer.toHexString(device.productId)
            else ->
                device.deviceName.replace("/", "_")
        }
    }

    fun connect(drv: UsbSerialDriver) = connect(drv, 0)

    fun connect(drv: UsbSerialDriver, flags: Int) {
        if (mUsbManager?.hasPermission(drv.device) != true) {
            mUsbManager?.requestPermission(
                drv.device,
                PendingIntent.getBroadcast(
                    KlipperApp.INSTANCE,
                    0,
                    Intent(ACTION_ON_DEVICE_CONNECTED).setPackage(KlipperApp.INSTANCE.packageName),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return
        }

        val resetArduino = (flags and FLAG_RESET_ARDUINO) != 0
        val currentBaudRate = AtomicInteger(250000)
        val port = drv.ports[0]

        try {
            val usbManager = mUsbManager ?: return
            port.open(usbManager.openDevice(drv.device))
            port.rts = true
            if (resetArduino) {
                port.dtr = true
            }
            port.setParameters(currentBaudRate.get(), UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open device ${drv.device}", e)
            return
        }

        val uid = getUID(drv.device)
        val nativePort = NativeSerialPort(uid)
        nativePort.setProxy { data ->
            try {
                port.write(data, 0)
                if (DEBUG) {
                    Log.d(TAG, "Write ${data.contentToString()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to USB serial", e)
                close(uid)
            }
        }
        val thread = ReadThread(uid, port, nativePort)
        ViewUtils.postOnMainThread { thread.start() }

        nativePortMap[uid] = nativePort
        portMap[uid] = port
        readThreadMap[uid] = thread
        if (DEBUG) {
            Log.d(TAG, "Connected ${nativePort.file}")
        }
    }

    fun getDevice(uid: String): UsbDevice? {
        val port = portMap[uid] ?: return null
        return port.device
    }

    fun close(uid: String) {
        val nativePort = nativePortMap.remove(uid)
        val port = portMap.remove(uid)
        val thread = readThreadMap.remove(uid)
        nativePort?.release()
        try {
            port?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close USB serial port", e)
        }
        thread?.interrupt()
    }

    private class ReadThread(
        private val uid: String,
        private val port: UsbSerialPort,
        private val nativePort: NativeSerialPort
    ) : Thread() {
        private val buffer = ByteArray(16384)

        init {
            android.os.Process.setThreadPriority(-20)
        }

        override fun run() {
            while (!isInterrupted) {
                try {
                    val c = port.read(buffer, 0)
                    if (c > 0) {
                        nativePort.write(buffer, c)
                        if (DEBUG) {
                            Log.d(TAG, "Read ${buffer.contentToString()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading serial", e)
                    if (e is IOException) {
                        close(uid)
                    }
                }
            }
        }
    }
}
