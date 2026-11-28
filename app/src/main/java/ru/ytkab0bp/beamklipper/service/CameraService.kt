package ru.ytkab0bp.beamklipper.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.util.Range
import android.view.Surface
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

@SuppressLint("MissingPermission")
class CameraService : Service() {
    companion object {
        const val ACTION_TOGGLE_FLASHLIGHT = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_FLASHLIGHT"
        const val ACTION_TOGGLE_FOCUS = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_FOCUS"
        const val KEY_FLASHLIGHT = "flashlight"
        const val KEY_AUTOFOCUS = "autofocus"
        const val KEY_FOCUS = "focus"
        private const val TAG = "beam_camera"
        private const val DESCRIPTOR = "ru.ytkab0bp.beamklipper.ICameraService"
        private val PATH_PATTERN = Pattern.compile("GET ([^\\r\\n]+) HTTP/1\\.[0-1]")
        private const val PORT = 8889
        private const val ID = 400000
        private val IO_POOL = Executors.newSingleThreadExecutor()
        private val handlerThreads = CopyOnWriteArrayList<CameraHandlerThread>()
    }

    private var notificationManager: NotificationManager? = null
    private var serverThread: ServerThread? = null
    private lateinit var cameraManager: CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var captureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val stoppedByUser = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var cameraDevice: CameraDevice? = null
    @Volatile private var imageReader: ImageReader? = null

    private val serviceBinder = object : Binder() {
        override fun getInterfaceDescriptor(): String = DESCRIPTOR
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE_FLASHLIGHT -> {
                    val flashlight = intent.getBooleanExtra(KEY_FLASHLIGHT, false)
                    Prefs.isFlashlightEnabled = flashlight
                    try {
                        captureRequestBuilder?.set(CaptureRequest.FLASH_MODE,
                            if (flashlight) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                        captureSession?.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to update camera settings", e)
                    }
                }
                ACTION_TOGGLE_FOCUS -> {
                    val autofocus = intent.getBooleanExtra(KEY_AUTOFOCUS, false)
                    Prefs.isAutofocusEnabled = autofocus
                    val focus = intent.getFloatExtra(KEY_FOCUS, 0f)
                    Prefs.focusDistance = focus
                    try {
                        captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE,
                            if (autofocus) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
                        captureRequestBuilder?.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus)
                        captureSession?.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to update camera settings", e)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        acquireLocks()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
        else
            Notification.Builder(this)
        not.setContentTitle(getString(R.string.CameraTitle))
            .setContentText(getString(R.string.CameraDescription))
            .setSmallIcon(R.drawable.icon_adaptive_foreground)
            .setOngoing(true)
        notificationManager!!.notify(ID, not.build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, not.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(ID, not.build())
        }
        return serviceBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_REDELIVER_INTENT

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (stoppedByUser.get()) return
    }

    private fun acquireLocks() {
        try {
            if (wakeLock?.isHeld != true) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeamKlipper::CameraWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(10 * 24 * 60 * 60 * 1000L)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wakelock", t)
        }
        try {
            if (wifiLock?.isHeld != true) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL,
                    "BeamKlipper::CameraWiFiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wifilock", t)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {}
        wakeLock = null
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {}
        wifiLock = null
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        acquireLocks()

        cameraThread = HandlerThread("camera").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
        cameraHandler?.post { Process.setThreadPriority(-10) }

        serverThread = ServerThread().also { it.start() }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val idList = cameraManager.cameraIdList
            if (idList.isEmpty()) {
                Log.w(TAG, "No camera devices available")
                return
            }
            val preferred = Prefs.cameraId
            val selected = if (preferred != null && idList.contains(preferred)) preferred else idList[0]
            cameraManager.openCamera(selected, object : CameraDevice.StateCallback() {

                override fun onOpened(camera: CameraDevice) {
                    try {
                        var width = Prefs.cameraWidth
                        var height = Prefs.cameraHeight
                        val chars = cameraManager.getCameraCharacteristics(camera.id)
                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
                        if (sizes.isNotEmpty()) {
                            val best = sizes.minByOrNull {
                                val dw = it.width - width
                                val dh = it.height - height
                                dw * dw + dh * dh
                            }
                            if (best != null) {
                                width = best.width
                                height = best.height
                            }
                        }
                        val targets = ArrayList<Surface>()
                        val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 4)
                        imageReader = reader
                        cameraDevice = camera
                        reader.setOnImageAvailableListener({ r ->
                            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                            if (handlerThreads.isEmpty()) {
                                img.close()
                                return@setOnImageAvailableListener
                            }
                            IO_POOL.submit {
                                try {
                                    val yPlane = img.planes[0]
                                    val uPlane = img.planes[1]
                                    val vPlane = img.planes[2]

                                    val nv21 = yuv420ToNv21(img.width, img.height, yPlane, uPlane, vPlane)
                                    val yuvImage = YuvImage(nv21, ImageFormat.NV21, img.width, img.height, null)
                                    val conv = ByteArrayOutputStream()
                                    yuvImage.compressToJpeg(Rect(0, 0, img.width, img.height), 85, conv)
                                    val converted = conv.toByteArray()
                                    deliverFrame(converted, converted.size) {}
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Failed to process camera frame", t)
                                } finally {
                                    try { img.close() } catch (_: Exception) {}
                                }
                            }
                        }, cameraHandler)
                        targets.add(reader.surface)
                        camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                Log.d(TAG, "Configured")
                                captureSession = session
                                try {
                                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    captureRequestBuilder = builder
                                    val rangeArray = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                                    if (rangeArray != null && rangeArray.isNotEmpty()) {
                                        val selectedRange = rangeArray.firstOrNull { it.upper < 25 } ?: rangeArray[0]
                                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedRange)
                                    }
                                    builder.set(CaptureRequest.FLASH_MODE,
                                        if (Prefs.isFlashlightEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                                    builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        if (Prefs.isAutofocusEnabled) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
                                    if (!Prefs.isAutofocusEnabled) {
                                        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Prefs.focusDistance)
                                    }
                                    builder.addTarget(reader.surface)
                                    session.setRepeatingRequest(builder.build(), null, cameraHandler)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to configure camera capture session", e)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.d(TAG, "Configure failed")
                            }
                        }, cameraHandler)
                    } catch (e: CameraAccessException) {
                        throw RuntimeException(e)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.d(TAG, "Disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.d(TAG, "Error $error")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera")
        }

        val filter = IntentFilter(ACTION_TOGGLE_FLASHLIGHT).apply { addAction(ACTION_TOGGLE_FOCUS) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, KlipperApp.PERMISSION, ViewUtils.getUiHandler(), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter, KlipperApp.PERMISSION, ViewUtils.getUiHandler())
        }
    }

    private fun yuv420ToNv21(
        width: Int,
        height: Int,
        yPlane: Image.Plane,
        uPlane: Image.Plane,
        vPlane: Image.Plane
    ): ByteArray {
        val ySize = width * height
        val out = ByteArray(ySize + (width * height / 2))

        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        yBuffer.rewind(); uBuffer.rewind(); vBuffer.rewind()

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var pos = 0
        for (row in 0 until height) {
            val rowStart = row * yRowStride
            if (yPixelStride == 1) {
                yBuffer.position(rowStart)
                yBuffer.get(out, pos, width)
                pos += width
            } else {
                for (col in 0 until width) {
                    out[pos++] = yBuffer.get(rowStart + col * yPixelStride)
                }
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        for (row in 0 until chromaHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowStart + col * uPixelStride
                val vIndex = vRowStart + col * vPixelStride
                out[pos++] = vBuffer.get(vIndex)
                out[pos++] = uBuffer.get(uIndex)
            }
        }
        return out
    }

    private fun deliverFrame(data: ByteArray, size: Int, onRelease: () -> Unit) {
        val clients = handlerThreads.toList()
        val total = clients.size
        if (total == 0) {
            onRelease()
            return
        }
        val done = AtomicInteger(0)
        for (t in clients) {
            val h = try { t.handler } catch (_: Throwable) { null }
            if (h == null) {
                if (done.incrementAndGet() == total) onRelease()
                continue
            }
            h.post {
                try {
                    val out = t.out
                    if (!t.oneShot) {
                        out.write("--camera-frame\r\n".toByteArray())
                        out.write("Content-Type: image/jpeg\r\nContent-Length: $size\r\n\r\n".toByteArray())
                    }
                    out.write(data, 0, size)
                    if (!t.oneShot) {
                        out.write("\r\n\r\n".toByteArray())
                    }
                    out.flush()
                    if (t.oneShot) {
                        t.quit()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deliver frame", e)
                    t.quit()
                } finally {
                    if (done.incrementAndGet() == total) {
                        onRelease()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stoppedByUser.set(true)
        super.onDestroy()
        try { captureSession?.close() } catch (_: Throwable) {}
        captureSession = null
        try { imageReader?.close() } catch (_: Throwable) {}
        imageReader = null
        try { cameraDevice?.close() } catch (_: Throwable) {}
        cameraDevice = null
        for (h in handlerThreads) h.quit()
        handlerThreads.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread?.interrupt()
        cameraThread?.quitSafely()
        cameraHandler = null
        cameraThread = null
        try { stopForeground(true) } catch (_: Throwable) {}
        notificationManager?.cancel(ID)
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        releaseLocks()
    }

    private inner class ServerThread : Thread("beam_camera_server") {
        init { isDaemon = true }

        override fun run() {
            Process.setThreadPriority(-10)
            try {
                val socket = ServerSocket(PORT)
                serverSocket = socket
                while (!isInterrupted) {
                    val sock = try { socket.accept() } catch (_: Exception) {
                        if (isInterrupted || socket.isClosed) break else continue
                    }
                    try {
                        CameraHandlerThread(sock)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to handle camera client", t)
                        try { sock.close() } catch (_: Exception) {}
                    }
                }
                try { socket.close() } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Camera server thread failed", e)
            } finally {
                serverSocket = null
            }
        }
    }

    private class CameraHandlerThread(sock: Socket) : HandlerThread("beam_camera_handler", -10) {
        companion object {
            private const val HEADERS = "HTTP/1.0 200 OK\r\nConnection: close\r\nMax-Age: 0\r\nExpires: 0\r\nCache-Control: no-cache, private\r\nPragma: no-cache\r\nContent-Type: multipart/x-mixed-replace; boundary=camera-frame\r\n\r\n"
        }

        val socket: Socket = sock
        val out: OutputStream = sock.outputStream
        val oneShot: Boolean
        val handler: Handler

        init {
            val input = sock.getInputStream()
            val r = BufferedReader(InputStreamReader(input))
            val line = try { r.readLine() } catch (_: Exception) { null }
            if (line != null) {
                val m = PATH_PATTERN.matcher(line)
                oneShot = m.find() && m.group(1)?.startsWith("/snapshot") == true
            } else {
                oneShot = false
            }

            start()
            handler = Handler(looper)
            handler.post {
                try {
                    out.write(HEADERS.toByteArray())
                    out.flush()
                    handlerThreads.add(this@CameraHandlerThread)
                } catch (e: Exception) {
                    Log.w(name, "Failed to write headers", e)
                    quit()
                }
            }
        }

        override fun quit(): Boolean {
            try { socket.close() } catch (_: Exception) {}
            handlerThreads.remove(this@CameraHandlerThread)
            return super.quit()
        }
    }
}
