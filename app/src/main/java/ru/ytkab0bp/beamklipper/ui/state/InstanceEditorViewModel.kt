package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ytkab0bp.beamklipper.InstanceIcon
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class InstanceEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val _editingInstance = MutableStateFlow<KlipperInstance?>(null)
    val editingInstance: StateFlow<KlipperInstance?> = _editingInstance.asStateFlow()

    private val _filesList = MutableStateFlow<List<String>>(emptyList())
    val filesList: StateFlow<List<String>> = _filesList.asStateFlow()

    private val _configFile = MutableStateFlow<String?>(null)
    val configFile: StateFlow<String?> = _configFile.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun loadForCreate() {
        _editingInstance.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KlipperApp.bundleInstallJob.await()
            } catch (_: Throwable) {}
            val files = runCatching {
                File(KlipperApp.INSTANCE.filesDir, "klipper/config").listFiles()?.map { it.name }?.sorted()
            }.getOrNull() ?: emptyList()
            _filesList.value = files
            _configFile.value = files.firstOrNull { it.lowercase().contains("example") } ?: files.firstOrNull()
        }
    }

    fun loadForEdit(instance: KlipperInstance) {
        _editingInstance.value = instance
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KlipperApp.bundleInstallJob.await()
            } catch (_: Throwable) {}
            val files = runCatching {
                File(KlipperApp.INSTANCE.filesDir, "klipper/config").listFiles()?.map { it.name }?.sorted()
            }.getOrNull() ?: emptyList()
            _filesList.value = files
            _configFile.value = files.firstOrNull { it.lowercase().contains("example") } ?: files.firstOrNull()
        }
    }

    fun selectConfig(file: String) {
        _configFile.value = file
    }

    fun save(name: String, autostart: Boolean, onDone: () -> Unit) {
        val editing = _editingInstance.value
        if (editing != null) {
            editing.name = name
            editing.autostart = autostart
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    KlipperApp.bundleInstallJob.await()
                } catch (_: Throwable) {}
                KlipperApp.DATABASE.update(editing)
                onDone()
            }
            return
        }

        if (runCatching { KlipperApp.DATABASE.getInstances().size }.getOrDefault(0) >= KlipperInstance.SLOTS_COUNT) {
            onDone()
            return
        }

        if (_configFile.value.isNullOrEmpty()) {
            onDone()
            return
        }

        val inst = KlipperInstance().apply {
            id = UUID.randomUUID().toString()
            this.name = name
            this.autostart = autostart
            this.icon = InstanceIcon.PRINTER
        }
        val cfg = File(inst.publicDirectory, "config/printer.cfg")
        val cfgText = _configFile.value!!
        _saving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try {
                    KlipperApp.bundleInstallJob.await()
                } catch (_: Throwable) {}
                cfg.parentFile?.mkdirs()
                try {
                    FileInputStream(File(KlipperApp.INSTANCE.filesDir, "klipper/config/$cfgText")).use { fis ->
                        FileOutputStream(cfg).use { fos -> fis.copyTo(fos) }
                    }
                } catch (e: Exception) {
                    Log.w("InstanceEditor", "Failed to copy config file", e)
                }
                KlipperApp.DATABASE.insert(inst)
            } finally {
                _saving.value = false
                onDone()
            }
        }
    }
}
