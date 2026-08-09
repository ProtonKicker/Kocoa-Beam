package ru.ytkab0bp.beamklipper

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import androidx.multidex.MultiDexApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.db.BeamDB
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.eventbus.EventBus

class KlipperApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        Prefs.init(this)
        DATABASE = BeamDB(this)
        EventBus.registerImpl(this)
        if (Prefs.appLanguage != Prefs.LANGUAGE_SYSTEM) {
            Prefs.applyAppLanguage()
        }

        hasUpdateInfo = try {
            assets.open("update.json").close()
            true
        } catch (_: java.io.IOException) {
            false
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(SERVICES_CHANNEL, getString(R.string.ServicesChannel), NotificationManager.IMPORTANCE_LOW))
            val watchdog = NotificationChannel(WATCHDOG_CHANNEL, getString(R.string.WatchdogChannel), NotificationManager.IMPORTANCE_HIGH)
            watchdog.description = getString(R.string.WatchdogChannelDesc)
            watchdog.enableLights(true)
            watchdog.enableVibration(true)
            watchdog.setShowBadge(true)
            nm.createNotificationChannel(watchdog)
        }

        val isMainProcess = getProcessNameCompat() == packageName

        bundleInstallJob = appScope.async(Dispatchers.IO) {
            BundleInstaller.init(this@KlipperApp)
        }

        if (isMainProcess) {
            appScope.launch {
                bundleInstallJob.await()
                Log.i("beam_app", "BundleInstaller done, loading instances from DB")
                KlipperInstance.resetSlotsForFreshStart()
                val instances = withContext(Dispatchers.IO) {
                    DATABASE.getInstances()
                }
                Log.i("beam_app", "DB.getInstances() returned ${instances.size} rows")
                KlipperInstance.onInstancesLoadedFromDB(instances)
            }
            appScope.launch(Dispatchers.IO) {
                UsbSerialManager.init(this@KlipperApp)
            }
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun getProcessNameCompat(): String {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentProcessName")
            method.invoke(null) as String
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private const val CHAQUOPY_SEED_MARKER = ".chaquopy_seed_v1"
        private const val CHAQUOPY_LOCK_NAME = ".chaquopy_lock"
        private const val MOONRAKER_LOCK_NAME = ".moonraker_port_lock"

        fun withChaquopyLock(ctx: Context, action: () -> Unit) {
            val lockFile = File(ctx.filesDir, CHAQUOPY_LOCK_NAME)
            var raf: RandomAccessFile? = null
            var lock: FileLock? = null
            try {
                raf = RandomAccessFile(lockFile, "rw")
                lock = raf.channel.lock()
                action()
            } finally {
                try { lock?.release() } catch (_: Throwable) {}
                try { raf?.close() } catch (_: Throwable) {}
            }
        }

        fun withMoonrakerPortLock(ctx: Context, action: () -> Unit) {
            val lockFile = File(ctx.filesDir, MOONRAKER_LOCK_NAME)
            val deadline = System.currentTimeMillis() + 15_000
            var acquired = false
            while (System.currentTimeMillis() < deadline) {
                try {
                    if (lockFile.createNewFile()) {
                        acquired = true
                        break
                    }
                } catch (_: Throwable) {}
                try {
                    if (System.currentTimeMillis() - lockFile.lastModified() > 10_000) {
                        lockFile.delete()
                    }
                } catch (_: Throwable) {}
                try { Thread.sleep(30) } catch (_: InterruptedException) { break }
            }
            if (!acquired) {
                try { lockFile.delete() } catch (_: Throwable) {}
                try { acquired = lockFile.createNewFile() } catch (_: Throwable) {}
            }
            try {
                action()
            } finally {
                if (acquired) {
                    try { lockFile.delete() } catch (_: Throwable) {}
                }
            }
        }

        fun seedChaquopyDirLocked(ctx: Context) {
            withChaquopyLock(ctx) {
                val marker = File(ctx.filesDir, CHAQUOPY_SEED_MARKER)
                if (marker.exists()) return@withChaquopyLock
                try {
                    val platform = AndroidPlatform(ctx)
                    platform.path
                    try {
                        if (!Python.isStarted()) {
                            Python.start(platform)
                        }
                    } catch (_: IllegalStateException) {
                    } catch (_: Throwable) {
                        try { File(ctx.filesDir, "chaquopy").deleteRecursively() } catch (_: Throwable) {}
                        try {
                            if (!Python.isStarted()) {
                                Python.start(AndroidPlatform(ctx))
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
                try {
                    File(ctx.filesDir, "chaquopy").mkdirs()
                    marker.createNewFile()
                } catch (_: Throwable) {}
            }
        }

        val PERMISSION: String
            get() = INSTANCE.packageName + ".permission.INTERNAL_BROADCASTS"
        @JvmField
        val SERVICES_CHANNEL = "services"
        @JvmField
        val WATCHDOG_CHANNEL = "watchdog"
        @JvmField
        val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        lateinit var bundleInstallJob: Deferred<Unit>

        lateinit var INSTANCE: KlipperApp
        lateinit var DATABASE: BeamDB
        @JvmField
        var EVENT_BUS: EventBus = EventBus.newBus("main")
        @JvmField
        var hasUpdateInfo = false
    }
}
