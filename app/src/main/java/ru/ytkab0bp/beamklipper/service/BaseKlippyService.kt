package ru.ytkab0bp.beamklipper.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import ru.ytkab0bp.beamklipper.BundleInstaller
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

open class BaseKlippyService(private val num: Int) : BasePythonService() {
    companion object {
        const val BASE_ID = 100000
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val stoppedByUser = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? {
        val b = super.onBind(intent) ?: return null
        acquireLocks()
        val inst = instance
        if (inst != null) {
            val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
            else
                Notification.Builder(this)
            not.setContentTitle(getString(R.string.KlippyTitle, inst.name))
                .setContentText(getString(R.string.KlippyDescription))
                .setSmallIcon(R.drawable.icon_adaptive_foreground)
                .setOngoing(true)
            notificationManager.notify(BASE_ID + num, not.build())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(BASE_ID + num, not.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(BASE_ID + num, not.build())
            }
        }
        return b
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val id = intent.getStringExtra(BasePythonService.KEY_INSTANCE)
            if (id != null && instance == null) {
                val inst = KlipperInstance.getInstance(id)
                val field = BasePythonService::class.java.getDeclaredField("instance")
                field.isAccessible = true
                try { field.set(this, inst) } catch (_: Throwable) {}
                acquireLocks()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (stoppedByUser.get()) return
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeamKlipper::KlippyWakeLock::$num").apply {
                    setReferenceCounted(false)
                    acquire(10 * 24 * 60 * 60 * 1000L)
                }
            }
        } catch (t: Throwable) {
            Log.e("klippy_$num", "Failed to acquire wakelock", t)
        }
        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL,
                    "BeamKlipper::KlippyWiFiLock::$num"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.e("klippy_$num", "Failed to acquire wifilock", t)
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

    override fun onDestroy() {
        stoppedByUser.set(true)
        releaseLocks()
        super.onDestroy()
        stopForeground(true)
        notificationManager.cancel(BASE_ID + num)
    }

    override fun onStartPython() {
        val inst = instance ?: return
        try {
            val logs = File(inst.publicDirectory, "logs/klippy.log")
            val config = File(inst.publicDirectory, "config")
            val socket = File(inst.directory, "klippy_uds")
            val virtualInput = File(inst.directory, "vinput")
            virtualInput.createNewFile()
            logs.parentFile?.mkdirs()
            config.mkdirs()
            File(inst.publicDirectory, "gcodes").mkdirs()
            File(inst.publicDirectory, "timelapses").mkdirs()
            val printerCfg = File(config, "printer.cfg")
            var str = try {
                printerCfg.readText(StandardCharsets.UTF_8)
            } catch (readEx: Exception) {
                try {
                    val defaultName = "config/example-cartesian.cfg"
                    val defaultContent = BundleInstaller.readString(KlipperApp.INSTANCE.assets, "klipper/$defaultName")
                    FileOutputStream(printerCfg).use { fos ->
                        fos.write(defaultContent.toByteArray(StandardCharsets.UTF_8))
                    }
                    Log.i("klippy_$num", "Seeded printer.cfg from default asset $defaultName")
                    defaultContent
                } catch (seedEx: Throwable) {
                    Log.w("klippy_$num", "Failed to seed default printer.cfg", seedEx)
                    return
                }
            }
            try {
                var changed = false

                val pattern = Pattern.compile("\\[virtual_sdcard][\\r\\n ]+path: ([^\\r\\n]+)", Pattern.DOTALL)
                val m = pattern.matcher(str)
                if (m.find()) {
                    val path = m.group(1)
                    if (!path.startsWith(inst.publicDirectory.absolutePath)) {
                        str = str.substring(0, m.start()) + str.substring(m.end() + 1)
                    }
                }
                if (!str.contains("[virtual_sdcard]")) {
                    str += "\n[virtual_sdcard]\npath: " + File(inst.publicDirectory, "gcodes").absolutePath + "\n"
                    changed = true
                }
                if (changed) {
                    FileOutputStream(printerCfg).use { fos ->
                        fos.write(str.toByteArray(StandardCharsets.UTF_8))
                    }
                }

                val beeperCfg = File(config, "beam_beeper.cfg")
                if (!beeperCfg.exists()) {
                    FileOutputStream(beeperCfg).use { fos ->
                        fos.write(BundleInstaller.readString(KlipperApp.INSTANCE.assets, "klipper/beam_beeper.cfg")
                            .toByteArray(StandardCharsets.UTF_8))
                    }
                }
            } catch (e: Exception) {
                Log.w("klippy_$num", "Failed to read/parse printer.cfg", e)
            }
            var engineDirName = Prefs.engineKey
            val bsName = "klipper_bs"
            val engineDir = File(KlipperApp.INSTANCE.filesDir, engineDirName)
            val bsFile = File(engineDir, "$bsName.py")
            if (!engineDir.isDirectory) engineDir.mkdirs()
            try {
                bsFile.writeText(
                    "import os\nimport importlib.util\nimport sys\n\ndef main():\n    here = os.path.dirname(os.path.abspath(__file__))\n    sys.path.insert(0, os.path.join(here, \"beam_ext\"))\n    sub = os.path.join(here, \"klippy\")\n    sys.path.insert(0, sub)\n    init = os.path.join(sub, \"__init__.py\")\n    if os.path.exists(init):\n        spec = importlib.util.spec_from_file_location(\"klippy\", init, submodule_search_locations=[sub])\n        m = importlib.util.module_from_spec(spec)\n        sys.modules[\"klippy\"] = m\n        spec.loader.exec_module(m)\n        from klippy.printer import main as _m\n        _m()\n    else:\n        entry = os.path.join(sub, \"klippy.py\")\n        spec = importlib.util.spec_from_file_location(\"klippy\", entry, submodule_search_locations=[sub])\n        m = importlib.util.module_from_spec(spec)\n        sys.modules[\"klippy\"] = m\n        spec.loader.exec_module(m)\n        if hasattr(m, \"main\"):\n            m.main()\n        else:\n            from klippy.printer import main as _m\n            _m()\n",
                    StandardCharsets.UTF_8
                )
            } catch (e: Throwable) {
                Log.w("klippy_$num", "Bootstrap write failed", e)
            }
            runPython(
                engineDir,
                bsName,
                "klippy.py",
                "-B",
                virtualInput.absolutePath,
                "-l",
                logs.absolutePath,
                "-a",
                socket.absolutePath,
                printerCfg.absolutePath
            )
        } catch (e: Exception) {
            Log.e("klippy_$num", "Failed to start klippy", e)
        }
    }
}
