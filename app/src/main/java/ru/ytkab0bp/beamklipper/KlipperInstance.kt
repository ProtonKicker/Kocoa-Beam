package ru.ytkab0bp.beamklipper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import ru.ytkab0bp.beamklipper.events.InstanceStateChangedEvent
import ru.ytkab0bp.beamklipper.events.InstancesRefreshedEvent
import ru.ytkab0bp.beamklipper.events.WebStateChangedEvent
import ru.ytkab0bp.beamklipper.service.*
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import kotlin.math.max

class KlipperInstance {
    @JvmField
    var name: String = ""
    @JvmField
    var id: String? = null
    @JvmField
    var icon: InstanceIcon = InstanceIcon.PRINTER
    @JvmField
    var autostart = false

    private var state: State = State.IDLE
    private var klippyIntent: Intent? = null
    private var klippyConnection: ServiceConnection? = null
    private var klippyConnected = false
    private var moonrakerIntent: Intent? = null
    private var moonrakerConnection: ServiceConnection? = null
    private var moonrakerConnected = false
    private var slot = 0
    private var lastRunning = 0L
    private var watchdogCount = 0
    private var isUserStop = false
    private var wasPrinting = false

    fun getState(): State = state

    val directory: File
        get() = File(KlipperApp.INSTANCE.filesDir, "instance${File.separator}$id")

    val publicDirectory: File
        get() = File(directory, "public")

    fun start() {
        if (state != State.IDLE) return
        isUserStop = false
        watchdogCount = 0
        notifyStateChanged(State.STARTING)

        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Failed to create instance directory ($id)")
            stop()
            return
        }
        if (!publicDirectory.exists() && !publicDirectory.mkdirs()) {
            Log.w(TAG, "Failed to create public instance directory ($id)")
            stop()
            return
        }
        val cfg = File(publicDirectory, "config")
        if (!cfg.exists() && !cfg.mkdirs()) {
            Log.w(TAG, "Failed to create config directory ($id)")
        }
        val gcodes = File(publicDirectory, "gcodes")
        if (!gcodes.exists() && !gcodes.mkdirs()) {
            Log.w(TAG, "Failed to create gcodes directory ($id)")
        }
        val logs = File(publicDirectory, "logs")
        if (!logs.exists() && !logs.mkdirs()) {
            Log.w(TAG, "Failed to create logs directory ($id)")
        }
        val timelapses = File(publicDirectory, "timelapses")
        if (!timelapses.exists() && !timelapses.mkdirs()) {
            Log.w(TAG, "Failed to create timelapses directory ($id)")
        }

        slot = -1
        if (slots.isEmpty()) {
            slot = 0
        } else if (slots.size < SLOTS_COUNT) {
            val cl = slots.values
            for (i in 0 until SLOTS_COUNT) {
                if (!cl.contains(i)) {
                    slot = i
                    break
                }
            }
        } else {
            throw IllegalStateException("Can't start $id: out of slots")
        }
        slots[this] = slot
        val instId = id
        mainHandler.post {
            try {
                val kIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.KlippyService_$slot"))
                klippyIntent = kIntent
                kIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                val b1 = KlipperApp.INSTANCE.bindService(kIntent, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        Log.i("beam_service", "klippy connected!"); klippyConnected = true
                        watchdogCount = 0
                        if (moonrakerConnected) {
                            notifyStateChanged(State.RUNNING)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        Log.w("beam_service", "klippy onServiceDisconnected for id=$instId")
                        onKlippyUnbound(watchdogRestart = true)
                    }

                    override fun onBindingDied(name: ComponentName) {
                        Log.w("beam_service", "klippy onBindingDied for id=$instId")
                        onKlippyUnbound(watchdogRestart = true)
                    }
                }.also { klippyConnection = it }, Context.BIND_AUTO_CREATE); Log.i("beam_service", "bind klippy: $b1")
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
            try {
                val mIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.MoonrakerService_$slot"))
                moonrakerIntent = mIntent
                mIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                KlipperApp.INSTANCE.bindService(mIntent, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        Log.i("beam_service", "moonraker connected!"); moonrakerConnected = true
                        if (klippyConnected) {
                            notifyStateChanged(State.RUNNING)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        Log.w("beam_service", "moonraker onServiceDisconnected for id=$instId")
                        onMoonrakerUnbound(watchdogRestart = true)
                    }

                    override fun onBindingDied(name: ComponentName) {
                        Log.w("beam_service", "moonraker onBindingDied for id=$instId")
                        onMoonrakerUnbound(watchdogRestart = true)
                    }
                }.also { moonrakerConnection = it }, Context.BIND_AUTO_CREATE)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
        }
    }

    fun stop() {
        if (state != State.RUNNING && state != State.STARTING) return
        Log.d(TAG, "stop() called for instance id=$id name=$name, currentState=$state")
        isUserStop = true
        watchdogCount = 0
        notifyStateChanged(State.STOPPING)

        mainHandler.post {
            val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (klippyConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(klippyConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(klippyIntent) } catch (_: Throwable) {}
                onKlippyUnbound()
                try { nm.cancel(BaseKlippyService.BASE_ID + slot) } catch (_: Throwable) {}
            }
            if (moonrakerConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(moonrakerConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(moonrakerIntent) } catch (_: Throwable) {}
                onMoonrakerUnbound()
                try { nm.cancel(BaseMoonrakerService.BASE_ID + slot) } catch (_: Throwable) {}
            }
            klippyConnected = false
            moonrakerConnected = false
            klippyConnection = null
            moonrakerConnection = null
            klippyIntent = null
            moonrakerIntent = null
            try { nm.cancel(WATCHDOG_BASE_ID + slot) } catch (_: Throwable) {}
            if (state == State.STOPPING) {
                notifyStateChanged(State.IDLE)
            }
        }
    }

    private fun fireWatchdogNotification(serviceName: String, attempt: Int, isRestarting: Boolean) {
        try {
            val ctx = KlipperApp.INSTANCE
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val mainIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getActivity(ctx, WATCHDOG_REQ_ID + slot, mainIntent, flags)
            val title = ctx.getString(R.string.WatchdogCrashTitle, name)
            val text = if (isRestarting) {
                ctx.getString(R.string.WatchdogCrashRestarting, serviceName, attempt, WATCHDOG_MAX_RETRIES)
            } else {
                ctx.getString(R.string.WatchdogCrashGaveUp, serviceName, WATCHDOG_MAX_RETRIES)
            }
            val notif = NotificationCompat.Builder(ctx, KlipperApp.WATCHDOG_CHANNEL)
                .setSmallIcon(R.drawable.icon_static)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(if (isRestarting) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_SOUND or Notification.DEFAULT_LIGHTS)
                .setOngoing(!isRestarting)
                .setAutoCancel(isRestarting)
                .setColor(0xFFD4A017.toInt())
                .setContentIntent(pi)
                .build()
            nm.notify(WATCHDOG_BASE_ID + slot, notif)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to fire watchdog notification", t)
        }
    }

    private fun tryWatchdogRestart(whichService: String) {
        if (isUserStop) {
            Log.i(TAG, "tryWatchdogRestart suppressed: user initiated stop for id=$id")
            return
        }
        val currentState = state
        if (currentState != State.RUNNING && currentState != State.STARTING) {
            Log.i(TAG, "tryWatchdogRestart suppressed: state=$currentState for id=$id")
            return
        }
        val wasShort = (System.currentTimeMillis() - lastRunning) < WATCHDOG_MIN_RUN_MS && watchdogCount > 0
        watchdogCount++
        val shouldRestart = watchdogCount <= WATCHDOG_MAX_RETRIES && !wasShort
        Log.w(TAG, "tryWatchdogRestart: id=$id which=$whichService count=$watchdogCount max=$WATCHDOG_MAX_RETRIES lastRunDelta=${System.currentTimeMillis() - lastRunning}ms wasShort=$wasShort shouldRestart=$shouldRestart")
        if (wasShort) {
            Log.w(TAG, "watchdog: service kept dying too fast (looped death), giving up to avoid thrashing")
        }
        fireWatchdogNotification(whichService, watchdogCount, shouldRestart)

        if (!shouldRestart) {
            stop()
            return
        }
        notifyStateChanged(State.STARTING)

        try {
            val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (klippyConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(klippyConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(klippyIntent) } catch (_: Throwable) {}
                try { nm.cancel(BaseKlippyService.BASE_ID + slot) } catch (_: Throwable) {}
            }
            if (moonrakerConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(moonrakerConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(moonrakerIntent) } catch (_: Throwable) {}
                try { nm.cancel(BaseMoonrakerService.BASE_ID + slot) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        klippyConnected = false
        moonrakerConnected = false
        klippyConnection = null
        moonrakerConnection = null
        klippyIntent = null
        moonrakerIntent = null

        val delayMs = max(500L + watchdogCount * 1200L, WATCHDOG_RESTART_DELAY_MS)
        val instId = id
        val capturedSlot = slot
        val capturedKlippyClass = try { Class.forName("ru.ytkab0bp.beamklipper.service.KlippyService_$capturedSlot") } catch (_: Throwable) { null }
        val capturedMoonrakerClass = try { Class.forName("ru.ytkab0bp.beamklipper.service.MoonrakerService_$capturedSlot") } catch (_: Throwable) { null }
        mainHandler.postDelayed({
            if (isUserStop) return@postDelayed
            Log.i(TAG, "watchdog: restarting services for id=$instId (attempt $watchdogCount)")
            try {
                if (capturedKlippyClass != null) {
                    val kIntent = Intent(KlipperApp.INSTANCE, capturedKlippyClass)
                    klippyIntent = kIntent
                    kIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                    KlipperApp.INSTANCE.bindService(kIntent, object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            Log.i("beam_service", "watchdog klippy reconnected for id=$instId")
                            klippyConnected = true
                            watchdogCount = max(0, watchdogCount - 1)
                            if (moonrakerConnected) {
                                notifyStateChanged(State.RUNNING)
                            }
                        }
                        override fun onServiceDisconnected(name: ComponentName) { onKlippyUnbound(watchdogRestart = true) }
                        override fun onBindingDied(name: ComponentName) { onKlippyUnbound(watchdogRestart = true) }
                    }.also { klippyConnection = it }, Context.BIND_AUTO_CREATE)
                }
                if (capturedMoonrakerClass != null) {
                    val mIntent = Intent(KlipperApp.INSTANCE, capturedMoonrakerClass)
                    moonrakerIntent = mIntent
                    mIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                    KlipperApp.INSTANCE.bindService(mIntent, object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            Log.i("beam_service", "watchdog moonraker reconnected for id=$instId")
                            moonrakerConnected = true
                            if (klippyConnected) {
                                notifyStateChanged(State.RUNNING)
                            }
                        }
                        override fun onServiceDisconnected(name: ComponentName) { onMoonrakerUnbound(watchdogRestart = true) }
                        override fun onBindingDied(name: ComponentName) { onMoonrakerUnbound(watchdogRestart = true) }
                    }.also { moonrakerConnection = it }, Context.BIND_AUTO_CREATE)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "watchdog restart failed for id=$instId", t)
            }
        }, delayMs)
    }

    private fun onKlippyUnbound(watchdogRestart: Boolean = false) {
        klippyConnection = null
        klippyConnected = false
        if (!moonrakerConnected) {
            if (watchdogRestart) tryWatchdogRestart("Klippy")
            if (!watchdogRestart || watchdogCount > WATCHDOG_MAX_RETRIES) notifyStateChanged(State.IDLE)
        } else if (watchdogRestart) {
            tryWatchdogRestart("Klippy")
        }
    }

    private fun onMoonrakerUnbound(watchdogRestart: Boolean = false) {
        moonrakerConnection = null
        moonrakerConnected = false
        if (!klippyConnected) {
            if (watchdogRestart) tryWatchdogRestart("Moonraker")
            if (!watchdogRestart || watchdogCount > WATCHDOG_MAX_RETRIES) notifyStateChanged(State.IDLE)
        } else if (watchdogRestart) {
            tryWatchdogRestart("Moonraker")
        }
    }

    private fun notifyStateChanged(state: State) {
        Log.d(TAG, "notifyStateChanged: id=$id name=$name state=$state")
        if (state == State.RUNNING) lastRunning = System.currentTimeMillis()
        this.state = state
        KlipperApp.EVENT_BUS.fireEvent(InstanceStateChangedEvent(requireNotNull(id), state))

        if (state == State.IDLE) {
            slots.remove(this)
            for (entry in slots.keys.toList()) {
                if (entry.id == this.id) {
                    slots.remove(entry)
                }
            }
            try {
                val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(WATCHDOG_BASE_ID + slot)
            } catch (_: Throwable) {}
            mainHandler.post { stopServerServicesIfNoSlots() }
        } else if (state == State.RUNNING) {
            mainHandler.post {
                if (webServerConnection == null) {
                    KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.STARTING))
                    KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, WebService::class.java), object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.RUNNING))
                        }

                        override fun onServiceDisconnected(name: ComponentName) {}
                    }.also { webServerConnection = it }, Context.BIND_AUTO_CREATE)
                }

                if (Prefs.isCameraEnabled) {
                    if (cameraServerConnection == null) {
                        KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, CameraService::class.java), object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                            override fun onServiceDisconnected(name: ComponentName) {}
                        }.also { cameraServerConnection = it }, Context.BIND_AUTO_CREATE)
                    }
                }
            }
        }
    }

    enum class State {
        IDLE, STARTING, RUNNING, STOPPING
    }

    companion object {
        @JvmField
        val SLOTS_COUNT: Int = BuildConfig.SLOTS_COUNT
        private const val TAG = "beam_instance"

        private const val WATCHDOG_BASE_ID = 9000
        private const val WATCHDOG_REQ_ID = 9000
        private const val WATCHDOG_MAX_RETRIES = 3
        private const val WATCHDOG_MIN_RUN_MS = 8000L
        private const val WATCHDOG_RESTART_DELAY_MS = 1500L

        private val mainHandler = Handler(Looper.getMainLooper())
        private val slots = HashMap<KlipperInstance, Int>()
        private var webServerConnection: ServiceConnection? = null
        private var cameraServerConnection: ServiceConnection? = null
        private var instances: List<KlipperInstance> = emptyList()
        private val instanceMap = object : HashMap<String, KlipperInstance>() {
            override fun get(key: String): KlipperInstance? {
                var inst = super.get(key)
                if (inst == null) {
                    for (i in instances) {
                        if (key == i.id) {
                            put(key, i)
                            inst = i
                            break
                        }
                    }
                }
                return inst
            }
        }

        @JvmStatic
        fun onInstancesLoadedFromDB(loaded: List<KlipperInstance>) {
            Log.i("beam_instance", "onInstancesLoadedFromDB: count=${loaded.size}")
            for (inst in loaded) {
                val was = getInstance(inst.id ?: continue)
                if (was != null) {
                    inst.state = was.state
                    inst.klippyConnection = was.klippyConnection
                    inst.klippyConnected = was.klippyConnected
                    inst.klippyIntent = was.klippyIntent
                    inst.moonrakerConnection = was.moonrakerConnection
                    inst.moonrakerConnected = was.moonrakerConnected
                    inst.moonrakerIntent = was.moonrakerIntent
                    inst.slot = was.slot
                    slots.remove(was)
                    if (was.state != State.IDLE) {
                        slots[inst] = was.slot
                    }
                }
            }

            val loadedIds = loaded.mapNotNull { it.id }.toHashSet()
            for (was in slots.keys.toList()) {
                val wasId = was.id ?: continue
                if (wasId in loadedIds) continue
                Log.i(TAG, "instance ${was.name} no longer in DB, stopping (state=${was.getState()})")
                slots.remove(was)
                try { was.stop() } catch (_: Throwable) {}
            }
            mainHandler.post { stopServerServicesIfNoSlots() }

            instances = loaded
            instanceMap.clear()
            KlipperApp.EVENT_BUS.fireEvent(InstancesRefreshedEvent())

            for (inst in instances) {
                Log.i("beam_instance", "instance id=${inst.id} name=${inst.name} autostart=${inst.autostart} state=${inst.getState()}")
                if (inst.autostart && inst.getState() == State.IDLE) {
                    Log.i("beam_instance", "  -> calling start()")
                    inst.start()
                }
            }
        }

        @JvmStatic
        fun getInstance(id: String): KlipperInstance? {
            var inst = instanceMap[id]
            if (inst == null) {
                for (i in instances) {
                    if (id == i.id) {
                        instanceMap[id] = i
                        inst = i
                        break
                    }
                }
            }
            if (inst == null) {
                val db = try { KlipperApp.DATABASE } catch (_: Throwable) { return null }
                val all = try { db.getInstances() } catch (_: Throwable) { return null }
                for (i in all) {
                    if (id == i.id) {
                        inst = i
                        instanceMap[id] = i
                        if (instances.isEmpty()) {
                            instances = all
                        }
                        break
                    }
                }
            }
            return inst
        }

        @JvmStatic
        fun getInstances(): List<KlipperInstance> {
            if (instances.isEmpty()) {
                try { KlipperApp.DATABASE } catch (_: Throwable) { return emptyList() } ?: return emptyList()
                val loaded = try { KlipperApp.DATABASE.getInstances() } catch (_: Throwable) { emptyList() }
                if (loaded.isNotEmpty()) {
                    instances = loaded
                }
            }
            return instances
        }

        @JvmStatic
        fun hasFreeSlots(): Boolean = slots.size < SLOTS_COUNT

        @JvmStatic
        fun isWebServerRunning(): Boolean = webServerConnection != null

        private fun hasAnyRunningInstance(): Boolean {
            for (inst in slots.keys) {
                val s = inst.getState()
                if (s == State.RUNNING || s == State.STARTING || s == State.STOPPING) {
                    return true
                }
            }
            return false
        }

        private fun stopServerServicesIfNoSlots() {
            Log.d(TAG, "stopServerServicesIfNoSlots: slots.size=${slots.size}, hasRunning=${hasAnyRunningInstance()}, webConn=${webServerConnection != null}")
            if (hasAnyRunningInstance()) {
                Log.d(TAG, "stopServerServicesIfNoSlots: returning early due to running instances")
                return
            }
            Log.d(TAG, "stopServerServicesIfNoSlots: proceeding to stop services")
            slots.clear()
            if (webServerConnection != null) {
                KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.STOPPING))
                try { KlipperApp.INSTANCE.unbindService(webServerConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, WebService::class.java)) } catch (_: Throwable) {}
                KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.IDLE))
                webServerConnection = null
            }
            if (cameraServerConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(cameraServerConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java)) } catch (_: Throwable) {}
                cameraServerConnection = null
            }
        }

        @JvmStatic
        fun onCameraConfigChanged(enable: Boolean) {
            mainHandler.post {
                if (cameraServerConnection == null && slots.isNotEmpty() && enable) {
                    KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, CameraService::class.java), object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                        override fun onServiceDisconnected(name: ComponentName) {}
                    }.also { cameraServerConnection = it }, Context.BIND_AUTO_CREATE)
                } else if (cameraServerConnection != null && !enable) {
                    try { KlipperApp.INSTANCE.unbindService(cameraServerConnection!!) } catch (_: Throwable) {}
                    try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java)) } catch (_: Throwable) {}
                    cameraServerConnection = null
                }
            }
        }

        @JvmStatic
        fun resetSlotsForFreshStart() {
            Log.i(TAG, "resetSlotsForFreshStart: clearing slots (was size=${slots.size}), webServerConnection=${webServerConnection != null}, cameraServerConnection=${cameraServerConnection != null}")
            slots.clear()
            webServerConnection = null
            cameraServerConnection = null
        }
    }
}
