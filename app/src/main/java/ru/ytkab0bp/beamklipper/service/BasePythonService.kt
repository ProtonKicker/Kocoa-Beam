package ru.ytkab0bp.beamklipper.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance

open class BasePythonService : Service() {
    companion object {
        const val KEY_INSTANCE = "instance"
        private const val DESCRIPTOR = "ru.ytkab0bp.beamklipper.IBasePythonService"
    }

    private val TAG = "python_service"
    private var py: Python? = null
    private var pythonThread: HandlerThread? = null
    private var pythonHandler: Handler? = null
    private val pythonReady = CountDownLatch(1)
    protected var instance: KlipperInstance? = null
    protected lateinit var notificationManager: NotificationManager

    private val serviceBinder = object : Binder() {
        override fun getInterfaceDescriptor(): String = DESCRIPTOR
    }

    override fun onBind(intent: Intent?): IBinder? {
        val id = intent?.getStringExtra(KEY_INSTANCE); Log.i("beam_service", "onBind id: $id"); if(id==null) return null
        instance = KlipperInstance.getInstance(id)
        pythonHandler?.post {
            runBlocking { KlipperApp.bundleInstallJob.await() }
            pythonReady.await()
            onStartPython()
        }
        return serviceBinder
    }

    override fun onCreate() {
        super.onCreate()
        android.os.Process.setThreadPriority(-4)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        pythonThread = HandlerThread(javaClass.name).also { it.start() }
        pythonHandler = Handler(pythonThread!!.looper)
        pythonHandler?.post {
            android.os.Process.setThreadPriority(-4)
            runBlocking { KlipperApp.bundleInstallJob.await() }
            KlipperApp.seedChaquopyDirLocked(this@BasePythonService)
            var retries = 0
            var lastErr: Throwable? = null
            while (retries < 4 && py == null) {
                try {
                    KlipperApp.withChaquopyLock(this@BasePythonService) {
                        val platform = AndroidPlatform(this@BasePythonService)
                        platform.path
                        Python.start(platform)
                        py = Python.getInstance()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start Python (attempt ${retries + 1}/4)", e)
                    lastErr = e
                    if (e is IllegalStateException) break
                    val isFileIOErr = (e.cause is java.io.FileNotFoundException) ||
                        (e is java.io.FileNotFoundException) ||
                        (e.cause is java.io.IOException) ||
                        (e is java.io.IOException) ||
                        e.message?.contains("No such file or directory") == true ||
                        e.message?.contains("Failed to create") == true
                    if (isFileIOErr) {
                        try {
                            val chaquopyDir = File(filesDir, "chaquopy")
                            if (chaquopyDir.exists()) {
                                chaquopyDir.deleteRecursively()
                            }
                        } catch (_: Throwable) {}
                    }
                    retries++
                    try { Thread.sleep(500L * (retries + 1)) } catch (_: InterruptedException) {}
                }
            }
            pythonReady.countDown()
            if (py == null) {
                Log.e(TAG, "Failed to start Python after ${retries + 1} attempts, stopping service", lastErr)
                stopSelf()
                try { Thread.sleep(100) } catch (_: Throwable) {}
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pythonThread?.quitSafely()
        pythonThread = null
        pythonHandler = null
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    protected open fun onStartPython() {}

    protected fun runPython(dir: File, module: String, vararg args: String) {
        val p = py ?: return
        val sysPath = p.getModule("sys")!!["path"]!!
        sysPath.callAttr("insert", 0, dir.absolutePath)
        val pyModule = Python.getInstance().getModule(module)
        val argv = pyModule!!["sys"]!!["argv"]!!.asList()
        argv.clear()
        for (arg in args) argv.add(PyObject.fromJava(arg))
        try {
            pyModule.callAttr("main")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start $module", e)
        }
    }
}
