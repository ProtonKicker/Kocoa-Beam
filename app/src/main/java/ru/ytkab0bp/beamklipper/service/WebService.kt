package ru.ytkab0bp.beamklipper.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import java.io.FileOutputStream
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.protocols.websockets.CloseCode
import org.nanohttpd.protocols.websockets.NanoWSD
import org.nanohttpd.protocols.websockets.OpCode
import org.nanohttpd.protocols.websockets.WebSocket
import org.nanohttpd.protocols.websockets.WebSocketFrame
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class WebService : Service() {
    companion object {
        const val PORT = 8889
        private const val ID = 300000
        private const val BEEPER_SAMPLE_RATE = 8000
        private val API_PATTERN = Pattern.compile("^/(printer|api|access|machine|server)/")
        private var mPrefs: SharedPreferences? = null
        private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ROOT)
        private val MOONRAKER_PORT_RE = Regex("port: (\\d+)")

        init {
            System.loadLibrary("beeper")
        }
    }

    private val httpServer = HttpServer()
    private var notificationManager: NotificationManager? = null
    private var beeperThread: HandlerThread? = null
    private var beeperHandler: Handler? = null

    override fun onBind(intent: Intent?): IBinder? {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
        else
            Notification.Builder(this)
        not.setContentTitle(getString(R.string.WebTitle))
            .setContentText(getString(R.string.WebDescription))
            .setSmallIcon(R.drawable.icon_adaptive_foreground)
            .setOngoing(true)
        notificationManager!!.notify(ID, not.build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, not.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, not.build())
        }
        return Binder()
    }

    override fun onCreate() {
        super.onCreate()
        mPrefs = KlipperApp.INSTANCE.getSharedPreferences("web", 0)
        beeperThread = HandlerThread("beeper").also { it.start() }
        beeperHandler = Handler(beeperThread!!.looper)
        try {
            httpServer.start()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer.stop()
        beeperThread?.quit()
        beeperThread = null
        beeperHandler = null
        stopForeground(true)
        notificationManager?.cancel(ID)
    }

    private fun getMoonrakerPort(): Int {
        for (inst in KlipperInstance.getInstances()) {
            if (inst.getState() == KlipperInstance.State.RUNNING) {
                val cfg = File(inst.publicDirectory, "config/moonraker.conf")
                if (cfg.exists()) {
                    try {
                        val m = MOONRAKER_PORT_RE.find(cfg.readText())
                        if (m != null) return m.groupValues[1].toInt()
                    } catch (_: Exception) {}
                }
            }
        }
        return 7125
    }

    private external fun generateTone(numSamples: Int, freq: Float): FloatArray

    private fun playTone(duration: Int, frequency: Int) {
        val numSamples = duration * BEEPER_SAMPLE_RATE
        val buffer = generateTone(numSamples, frequency.toFloat() / BEEPER_SAMPLE_RATE)
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioFormat(AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(BEEPER_SAMPLE_RATE)
                    .build())
                .setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setBufferSizeInBytes(2 * numSamples)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            AudioTrack(AudioManager.STREAM_MUSIC, BEEPER_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT, 2 * numSamples, AudioTrack.MODE_STATIC)
        }
        track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        beeperHandler?.postDelayed({ track.release() }, duration.toLong())
    }

    private data class ParsedCmd(
        val fps: Int?,
        val inputPath: String,
        val outputPath: String,
        val filter: String?
    )

    private fun parseFfmpegCmd(cmd: String): ParsedCmd {
        var fps: Int? = null
        var inputPath = ""
        var outputPath = ""
        var filter: String? = null

        val fpsMatch = Regex("""-r\s+(\d+)""").find(cmd)
        if (fpsMatch != null) {
            fps = fpsMatch.groupValues[1].toInt()
        }

        val inputMatch = Regex("""-i\s+'([^']+)'""").find(cmd)
        if (inputMatch != null) {
            inputPath = inputMatch.groupValues[1]
        }

        val filterMatch = Regex("""-vf\s+'([^']+)'""").find(cmd)
        if (filterMatch != null) {
            filter = filterMatch.groupValues[1]
        }

        val quotedPaths = Regex("""'([^']+)'""").findAll(cmd).map { it.groupValues[1] }.toList()
        if (quotedPaths.isNotEmpty()) {
            outputPath = quotedPaths.last()
        }

        return ParsedCmd(fps, inputPath, outputPath, filter)
    }

    private fun expandWildcard(path: String): List<File> {
        if (!path.contains("*") && !path.contains("?")) {
            val f = File(path)
            return if (f.exists()) listOf(f) else emptyList()
        }
        val parent = File(path).parentFile ?: File("/")
        val namePattern = File(path).name
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .toRegex()
        val files = parent.listFiles() ?: emptyArray()
        return files.filter { namePattern.matches(it.name) && it.isFile }.sortedBy { it.name }
    }

    private fun buildTransformMatrix(filter: String?, width: Int, height: Int): Matrix {
        val matrix = Matrix()
        if (filter == null) return matrix

        val filters = filter.split(",")
        for (f in filters) {
            val trimmed = f.trim()
            when {
                trimmed == "transpose=0" -> {
                    matrix.postRotate(-90f)
                    matrix.postScale(1f, -1f)
                }
                trimmed == "transpose=1" -> {
                    matrix.postRotate(90f)
                }
                trimmed == "transpose=2" -> {
                    matrix.postRotate(-90f)
                }
                trimmed == "transpose=3" -> {
                    matrix.postRotate(90f)
                    matrix.postScale(1f, -1f)
                }
                trimmed == "hflip" -> {
                    matrix.postScale(-1f, 1f)
                }
                trimmed == "vflip" -> {
                    matrix.postScale(1f, -1f)
                }
                trimmed.startsWith("rotate=") -> {
                    val radStr = trimmed.substringAfter("=")
                    val radians = radStr.toFloatOrNull() ?: 0f
                    val degrees = Math.toDegrees(radians.toDouble()).toFloat()
                    matrix.postRotate(degrees)
                }
            }
        }
        return matrix
    }

    private fun applyMatrixToBitmap(src: Bitmap, matrix: Matrix): Bitmap {
        val adjusted = Matrix(matrix)
        val mappedRect = android.graphics.RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        adjusted.mapRect(mappedRect)
        val outW = Math.round(mappedRect.width())
        val outH = Math.round(mappedRect.height())
        adjusted.postTranslate(-mappedRect.left, -mappedRect.top)
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, adjusted, paint)
        return result
    }

    private fun processSingleImage(cmd: ParsedCmd): String {
        val srcFile = File(cmd.inputPath)
        if (!srcFile.exists()) {
            Log.e("beam_ffmpeg", "Input file not found: ${cmd.inputPath}")
            return ""
        }
        val srcBmp = BitmapFactory.decodeFile(srcFile.absolutePath) ?: run {
            Log.e("beam_ffmpeg", "Failed to decode bitmap: ${cmd.inputPath}")
            return ""
        }
        val matrix = buildTransformMatrix(cmd.filter, srcBmp.width, srcBmp.height)
        val resultBmp = applyMatrixToBitmap(srcBmp, matrix)
        val outFile = File(cmd.outputPath)
        FileOutputStream(outFile).use { fos ->
            resultBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        if (srcBmp != resultBmp) {
            srcBmp.recycle()
        }
        resultBmp.recycle()
        val sizeKb = (outFile.length() / 1024).coerceAtLeast(1)
        return "frame=1 fps=0.0 video:${sizeKb}kB"
    }

    private fun processTimelapse(cmd: ParsedCmd): String {
        val fps = cmd.fps ?: 30
        val frames = expandWildcard(cmd.inputPath)
        if (frames.isEmpty()) {
            Log.e("beam_ffmpeg", "No input frames found for wildcard: ${cmd.inputPath}")
            return ""
        }

        val firstBmp = BitmapFactory.decodeFile(frames[0].absolutePath) ?: run {
            Log.e("beam_ffmpeg", "Failed to decode first frame")
            return ""
        }

        val previewMatrix = buildTransformMatrix(cmd.filter, firstBmp.width, firstBmp.height)
        val testRect = android.graphics.RectF(0f, 0f, firstBmp.width.toFloat(), firstBmp.height.toFloat())
        previewMatrix.mapRect(testRect)
        var encW = Math.round(testRect.width())
        var encH = Math.round(testRect.height())

        val maxW = 1280
        val maxH = 720
        val scale = minOf(maxW.toFloat() / encW, maxH.toFloat() / encH, 1f)
        encW = Math.round(encW * scale / 2f) * 2
        encH = Math.round(encH * scale / 2f) * 2
        if (encW <= 0 || encH <= 0) {
            firstBmp.recycle()
            return ""
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: android.view.Surface? = null
        var trackIndex = -1
        var muxerStarted = false

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(cmd.outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var frameIndex = 0
            var outputDone = false
            var inputDone = false
            val totalFrames = frames.size

            while (!outputDone) {
                if (!inputDone && frameIndex < totalFrames) {
                    val frameFile = frames[frameIndex]
                    val bmp = BitmapFactory.decodeFile(frameFile.absolutePath)
                    if (bmp != null) {
                        val frameMatrix = buildTransformMatrix(cmd.filter, bmp.width, bmp.height)
                        val transformed = applyMatrixToBitmap(bmp, frameMatrix)
                        val drawBmp = if (transformed.width != encW || transformed.height != encH) {
                            Bitmap.createScaledBitmap(transformed, encW, encH, true)
                        } else {
                            transformed
                        }

                        val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            inputSurface!!.lockHardwareCanvas()
                        } else {
                            @Suppress("DEPRECATION")
                            inputSurface!!.lockCanvas(null)
                        }
                        canvas.drawColor(android.graphics.Color.BLACK)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        canvas.drawBitmap(drawBmp, 0f, 0f, paint)
                        inputSurface!!.unlockCanvasAndPost(canvas)

                        if (drawBmp != transformed) drawBmp.recycle()
                        if (transformed != bmp) transformed.recycle()
                        bmp.recycle()
                    }

                    frameIndex++
                    if (frameIndex >= totalFrames) {
                        inputDone = true
                        encoder.signalEndOfInputStream()
                    }
                }

                var encoderOutputAvailable = true
                while (encoderOutputAvailable) {
                    val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    when {
                        status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            encoderOutputAvailable = false
                            if (!inputDone) break
                        }
                        status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) {
                                throw RuntimeException("Format changed twice")
                            }
                            val newFormat = encoder.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        status < 0 -> {
                        }
                        else -> {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encoder.releaseOutputBuffer(status, false)
                                continue
                            }
                            if (!muxerStarted) {
                                throw RuntimeException("Muxer not started before data")
                            }
                            if (bufferInfo.size > 0) {
                                val encodedData = encoder.getOutputBuffer(status)!!
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                if (bufferInfo.presentationTimeUs == 0L && frameIndex > 1) {
                                    bufferInfo.presentationTimeUs = (frameIndex - 1) * 1_000_000L / fps
                                }
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            encoder.releaseOutputBuffer(status, false)
                            if (eos) {
                                outputDone = true
                                encoderOutputAvailable = false
                            }
                        }
                    }
                }
            }

            firstBmp.recycle()
            val sizeKb = (File(cmd.outputPath).length() / 1024).coerceAtLeast(1)
            return "frame=$totalFrames fps=$fps video:${sizeKb}kB"
        } catch (e: Exception) {
            Log.e("beam_ffmpeg", "Timelapse encoding failed", e)
            try { firstBmp.recycle() } catch (_: Exception) {}
            return ""
        } finally {
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { inputSurface?.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun executeFfmpegReplacement(cmd: String): String {
        Log.d("beam_ffmpeg", "Received cmd: $cmd")
        val parsed = parseFfmpegCmd(cmd)
        Log.d("beam_ffmpeg", "Parsed: fps=${parsed.fps} input=${parsed.inputPath} output=${parsed.outputPath} filter=${parsed.filter}")

        if (parsed.inputPath.isBlank() || parsed.outputPath.isBlank()) {
            Log.e("beam_ffmpeg", "Missing input or output path")
            return ""
        }

        return if (parsed.fps == null) {
            processSingleImage(parsed)
        } else {
            processTimelapse(parsed)
        }
    }

    private inner class HttpServer : NanoWSD(PORT) {
        private fun serveStatic(path: String): Response {
            val ctx = KlipperApp.INSTANCE
            val resolvedPath = if (path == "/") "/index.html" else path
            try {
                val mimeType = when {
                    resolvedPath.endsWith(".js") -> "text/javascript"
                    resolvedPath.endsWith(".html") -> "text/html"
                    resolvedPath.endsWith(".css") -> "text/css"
                    else -> "text/plain"
                }
                val prefix = Prefs.webFrontend
                val assetPath = prefix + resolvedPath
                val input = ctx.assets.open(assetPath)
                val response = Response.newChunkedResponse(Status.OK, mimeType, input)
                response.addHeader("Date", dateFormat.format(Date()))
                response.addHeader("Last-Modified", lastModifiedString)
                if (resolvedPath.endsWith(".html") || resolvedPath.endsWith(".json")) {
                    response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                    response.addHeader("Pragma", "no-cache")
                    response.addHeader("Expires", "0")
                } else {
                    response.addHeader("Cache-Control", "max-age=604800, immutable")
                }
                return response
            } catch (e: IOException) {
                if (path == "/index.html" || path == "/") {
                    return Response.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
                }
                return serveStatic("/index.html")
            }
        }

        private val lastModifiedString: String
            get() {
                val lastModified = mPrefs!!.getLong("last_modified", System.currentTimeMillis())
                return dateFormat.format(Date(lastModified))
            }

        private fun checkRemote(session: IHTTPSession): Boolean =
            "127.0.0.1" != session.remoteIpAddress

        override fun serve(session: IHTTPSession): Response {
            when (session.uri) {
                "/beam/arduino_reset" -> {
                    if (checkRemote(session)) return Response.newFixedLengthResponse("")
                    val serial = session.parameters["serial"]?.get(0) ?: return Response.newFixedLengthResponse("")
                    val uid = serial.substring(
                        File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath.length + 1)
                    val device = UsbSerialManager.getDevice(uid)
                    if (device != null) {
                        UsbSerialManager.close(uid)
                        ViewUtils.postOnMainThread({
                            val prober = UsbSerialProber(KlipperProbeTable.getInstance())
                            val drv = prober.probeDevice(device)
                            if (drv != null) {
                                UsbSerialManager.connect(drv, UsbSerialManager.FLAG_RESET_ARDUINO)
                            }
                        }, 100)
                    }
                    return Response.newFixedLengthResponse("{\"ok\": true}")
                }
                "/beam/ffmpeg" -> {
                    if (checkRemote(session)) return Response.newFixedLengthResponse("")
                    val cmd = session.parameters["cmd"]?.get(0) ?: return Response.newFixedLengthResponse("")
                    val result = try {
                        executeFfmpegReplacement(cmd)
                    } catch (e: Exception) {
                        Log.e("beam_ffmpeg", "Execution failed", e)
                        ""
                    }
                    return Response.newFixedLengthResponse(result)
                }
                "/beam/play_tone" -> {
                    if (checkRemote(session)) return Response.newFixedLengthResponse("{\"ok\": false}")
                    val duration = session.parameters["duration"]?.get(0)?.toIntOrNull()
                        ?: return Response.newFixedLengthResponse("{\"ok\": false}")
                    val frequency = session.parameters["frequency"]?.get(0)?.toIntOrNull()
                        ?: return Response.newFixedLengthResponse("{\"ok\": false}")
                    playTone(duration, frequency)
                    return Response.newFixedLengthResponse("{\"ok\": true}")
                }
                "/beam/set_camera_flashlight" -> {
                    if (checkRemote(session)) return Response.newFixedLengthResponse("{\"ok\": false}")
                    val flashlight = session.parameters.containsKey("enabled") && session.parameters["enabled"]?.get(0) == "true"
                    KlipperApp.INSTANCE.sendBroadcast(
                        Intent(CameraService.ACTION_TOGGLE_FLASHLIGHT).putExtra(CameraService.KEY_FLASHLIGHT, flashlight),
                        KlipperApp.PERMISSION)
                    return Response.newFixedLengthResponse("{\"ok\": true}")
                }
                "/beam/set_camera_focus" -> {
                    if (checkRemote(session)) return Response.newFixedLengthResponse("{\"ok\": false}")
                    val autofocus = session.parameters.containsKey("autofocus") && session.parameters["autofocus"]?.get(0) == "true"
                    val distance = session.parameters["focus"]?.get(0)?.toFloatOrNull() ?: 0f
                    KlipperApp.INSTANCE.sendBroadcast(
                        Intent(CameraService.ACTION_TOGGLE_FOCUS)
                            .putExtra(CameraService.KEY_AUTOFOCUS, autofocus)
                            .putExtra(CameraService.KEY_FOCUS, distance),
                        KlipperApp.PERMISSION)
                    return Response.newFixedLengthResponse("{\"ok\": true}")
                }
            }

            val m = API_PATTERN.matcher(session.uri)
            if (m.find()) {
                try {
                    val qs = session.queryParameterString
                    val urlStr = "http://127.0.0.1:${getMoonrakerPort()}/${session.uri.substring(1)}" + if (qs.isNullOrEmpty()) "" else "?$qs"
                    val con = URL(urlStr).openConnection() as HttpURLConnection
                    con.requestMethod = session.method.name
                    if (session.method == Method.POST || session.method == Method.PUT || session.method == Method.PATCH) {
                        for ((key, value) in session.headers) {
                            con.addRequestProperty(key, value)
                        }
                        con.doOutput = true
                        session.inputStream.use { input ->
                            con.outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val responseStream = if (con.responseCode in 200..299) con.inputStream else con.errorStream
                    val resStatus = Status.lookup(con.responseCode) 
                        ?: object : org.nanohttpd.protocols.http.response.IStatus {
                            override fun getDescription(): String = con.responseMessage ?: "Unknown"
                            override fun getRequestStatus(): Int = con.responseCode
                        }
                    val r = Response.newChunkedResponse(resStatus, con.contentType, responseStream)
                    for ((key, values) in con.headerFields) {
                        if (key.isNullOrEmpty()) continue
                        if (key.equals("content-length", true)) continue
                        if (key.equals("transfer-encoding", true)) continue
                        if (key.equals("connection", true)) continue
                        val headerValues = values ?: continue
                        for (value in headerValues) {
                            r.addHeader(key, value)
                        }
                    }
                    r.addHeader("Connection", "close")
                    return r
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }

            return if (session.uri.startsWith("/index.html") || session.uri == "/") {
                serveStatic("/")
            } else {
                serveStatic(session.uri)
            }
        }

        override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
            return try {
                val localRef = AtomicReference<WebSocket>()
                val qs = handshake.queryParameterString
                val uriStr = "ws://127.0.0.1:${getMoonrakerPort()}/websocket" + if (qs.isNullOrEmpty()) "" else "?$qs"
                
                val remoteClient = object : WebSocketClient(URI(uriStr)) {
                    override fun onOpen(handshakedata: ServerHandshake) {}
                    override fun onMessage(message: String) {
                        val local = localRef.get()
                        if (local != null && local.isOpen) {
                            try { local.send(message) } catch (e: IOException) { onError(e) }
                        } else {
                            Log.e("websocket_proxy", "remoteClient.onMessage(String): local=$local isOpen=${local?.isOpen}. message=$message. Closing remote!")
                            close()
                        }
                    }
                    override fun onMessage(bytes: ByteBuffer) {
                        val local = localRef.get()
                        if (local != null && local.isOpen) {
                            try { local.send(bytes.array()) } catch (e: IOException) { onError(e) }
                        } else {
                            Log.e("websocket_proxy", "remoteClient.onMessage(bytes): local=$local isOpen=${local?.isOpen}. Closing remote!")
                            close()
                        }
                    }
                    override fun onClose(code: Int, reason: String, remote: Boolean) {
                        Log.d("websocket_proxy", "remoteClient.onClose: code=$code reason=$reason remote=$remote")
                        val local = localRef.get()
                        if (local != null && local.isOpen) {
                            try { local.close(CloseCode.NormalClosure, reason ?: "", false) } catch (e: IOException) {}
                        }
                    }
                    override fun onError(ex: Exception) {
                        Log.e("websocket_proxy", "Remote socket error", ex)
                        val local = localRef.get()
                        if (local != null && local.isOpen) {
                            try { local.close(CloseCode.InternalServerError, ex.message ?: "Error", false) } catch (e: IOException) {}
                        }
                    }
                }
                val local = object : WebSocket(handshake) {
                    override fun onOpen() {
                        Log.d("websocket_proxy", "Local socket opened, connecting to remote...")
                        try { 
                            if (!remoteClient.connectBlocking()) {
                                Log.e("websocket_proxy", "remoteClient.connectBlocking() returned false")
                                close(CloseCode.InternalServerError, "Failed to connect to Moonraker", false)
                            } else {
                                Log.d("websocket_proxy", "Connected to remote Moonraker!")
                            }
                        } catch (e: InterruptedException) { 
                            Log.e("websocket_proxy", "Interrupted during connectBlocking", e)
                            try { close(CloseCode.InternalServerError, "Interrupted", false) } catch (ex: IOException) {}
                        }
                    }
                    override fun onClose(code: CloseCode, reason: String, initiatedByRemote: Boolean) {
                        Log.d("websocket_proxy", "local.onClose: code=$code reason=$reason initiatedByRemote=$initiatedByRemote")
                        if (remoteClient.isOpen) {
                            remoteClient.close()
                        }
                    }
                    override fun onMessage(message: WebSocketFrame) {
                        if (!remoteClient.isOpen) {
                            try { close(CloseCode.NormalClosure, "", false) } catch (e: IOException) {}
                            return
                        }
                        if (message.opCode == OpCode.Text) {
                            remoteClient.send(message.textPayload)
                        } else {
                            remoteClient.send(message.binaryPayload)
                        }
                    }
                    override fun onPong(pong: WebSocketFrame) {}
                    override fun onException(exception: IOException) {
                        if (exception is SocketTimeoutException) return
                        Log.e("websocket_proxy", "Local socket error", exception)
                        if (remoteClient.isOpen) {
                            remoteClient.close()
                        }
                    }
                }
                localRef.set(local)
                local
            } catch (e: Exception) {
                null
            }
        }
    }
}
