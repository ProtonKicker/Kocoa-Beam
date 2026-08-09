package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val instances: StateFlow<List<KlipperInstance>> = AppState.instances
    val instanceStates: StateFlow<Map<String, KlipperInstance.State>> = AppState.instanceStates
    val webState: StateFlow<KlipperInstance.State> = AppState.webState
    val webFrontend: StateFlow<String> = AppState.webFrontend

    val anyRunning: Boolean
        get() = instances.value.any {
            it.getState() == KlipperInstance.State.RUNNING ||
                it.getState() == KlipperInstance.State.STARTING
        }

    fun toggle(instance: KlipperInstance) {
        val id = instance.id ?: return
        val canonical = KlipperInstance.getInstance(id) ?: return
        val state = canonical.getState()
        when (state) {
            KlipperInstance.State.RUNNING, KlipperInstance.State.STARTING -> {
                canonical.stop()
                if (canonical.autostart) {
                    canonical.autostart = false
                    KlipperApp.DATABASE.update(canonical)
                }
            }
            KlipperInstance.State.STOPPING, KlipperInstance.State.IDLE -> {
                if (state == KlipperInstance.State.IDLE && !KlipperInstance.hasFreeSlots()) return
                canonical.start()
            }
        }
    }

    fun runStopAll() {
        val instances = instances.value.mapNotNull { inst ->
            inst.id?.let { id -> KlipperInstance.getInstance(id) }
        }
        if (instances.isEmpty()) return
        val anyActive = instances.any {
            it.getState() == KlipperInstance.State.RUNNING ||
                it.getState() == KlipperInstance.State.STARTING
        }
        if (anyActive) {
            for (inst in instances) {
                val state = inst.getState()
                if (state == KlipperInstance.State.RUNNING || state == KlipperInstance.State.STARTING) {
                    inst.stop()
                    if (inst.autostart) {
                        inst.autostart = false
                        KlipperApp.DATABASE.update(inst)
                    }
                }
            }
        } else {
            for (inst in instances) {
                val state = inst.getState()
                if (state == KlipperInstance.State.IDLE || state == KlipperInstance.State.STOPPING) {
                    if (state == KlipperInstance.State.IDLE && !KlipperInstance.hasFreeSlots()) return
                    inst.start()
                }
            }
        }
    }

    fun delete(instance: KlipperInstance) {
        val id = instance.id ?: return
        val canonical = KlipperInstance.getInstance(id) ?: return
        canonical.stop()
        viewModelScope.launch(Dispatchers.IO) {
            KlipperApp.DATABASE.delete(canonical)
        }
    }
}
