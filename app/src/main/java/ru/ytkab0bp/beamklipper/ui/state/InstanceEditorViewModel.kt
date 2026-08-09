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
import ru.ytkab0bp.beamklipper.R
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

    private val _defaultName = MutableStateFlow<String?>(null)
    val defaultName: StateFlow<String?> = _defaultName.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private fun computeDefaultName(): String {
        val ctx = getApplication<Application>()
        val (dbNames, slotNames) = collectExistingNames()
        val existing = (dbNames + slotNames).distinct()
        for (n in 1..1000) {
            val candidate = ctx.getString(R.string.InstanceDefaultName, n)
            if (candidate !in existing) {
                val (dbNames2, slotNames2) = collectExistingNames()
                val existing2 = (dbNames2 + slotNames2).distinct()
                if (candidate !in existing2) return candidate
            }
        }
        val fallbackBase = runCatching {
            stripTrailingNumber(ctx.getString(R.string.InstanceDefaultName, 1))
        }.getOrDefault("Printer").ifEmpty { "Printer" }
        for (n in 1..1000) {
            val candidate = "$fallbackBase $n"
            val (dbNames2, slotNames2) = collectExistingNames()
            val existing2 = (dbNames2 + slotNames2).distinct()
            if (candidate !in existing2) return candidate
        }
        return runCatching { ctx.getString(R.string.InstanceDefaultName, 1) }.getOrDefault("Printer 1")
    }

    private fun stripTrailingNumber(value: String): String {
        val s1 = value.trim()
        val m1 = Regex("""^(.*?)\s*\(\s*\d+\s*\)\s*$""").matchEntire(s1)
        if (m1 != null) {
            return m1.groupValues[1].trim()
        }
        val m2 = Regex("""^(.*?)\s+\d+\s*$""").matchEntire(s1)
        if (m2 != null) {
            return m2.groupValues[1].trim()
        }
        return s1
    }

    private fun collectExistingNames(): Pair<List<String>, List<String>> {
        val dbNames = runCatching {
            KlipperApp.getDatabaseOrNull()?.getInstances()?.map { it.name }.orEmpty()
        }.getOrDefault(emptyList())
        val slotNames = runCatching {
            KlipperInstance.getSlotInstancesNames()
        }.getOrDefault(emptyList())
        return dbNames to slotNames
    }

    private fun ensureUniqueName(desired: String, editing: KlipperInstance?): String {
        if (desired.isEmpty()) return computeDefaultName()
        val (dbNames, slotNames) = collectExistingNames()
        val existing = (dbNames + slotNames)
            .filter { name ->
                when {
                    editing == null -> true
                    editing.id == null -> true
                    else -> {
                        val matchDb = runCatching {
                            KlipperApp.getDatabaseOrNull()?.getInstances()?.firstOrNull { it.name == name }?.id
                        }.getOrNull()
                        matchDb != editing.id
                    }
                }
            }
            .distinct()
        if (desired !in existing) {
            val (dbNames2, slotNames2) = collectExistingNames()
            val existing2 = (dbNames2 + slotNames2)
                .filter { name ->
                    when {
                        editing == null -> true
                        editing.id == null -> true
                        else -> {
                            val matchDb = runCatching {
                                KlipperApp.getDatabaseOrNull()?.getInstances()?.firstOrNull { it.name == name }?.id
                            }.getOrNull()
                            matchDb != editing.id
                        }
                    }
                }
            if (desired !in existing2) return desired
        }
        val base = stripTrailingNumber(desired).ifEmpty { "Printer" }
        for (n in 2..1000) {
            val candidate = "$base $n"
            val (dbNames2, slotNames2) = collectExistingNames()
            val existing2 = (dbNames2 + slotNames2)
                .filter { name ->
                    when {
                        editing == null -> true
                        editing.id == null -> true
                        else -> {
                            val matchDb = runCatching {
                                KlipperApp.getDatabaseOrNull()?.getInstances()?.firstOrNull { it.name == name }?.id
                            }.getOrNull()
                            matchDb != editing.id
                        }
                    }
                }
            if (candidate !in existing2) return candidate
        }
        return desired
    }

    fun loadForCreate() {
        _editingInstance.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KlipperApp.bundleInstallJob.await()
            } catch (_: Throwable) {}
            _defaultName.value = computeDefaultName()
            val files = runCatching {
                File(KlipperApp.INSTANCE.filesDir, "klipper/config").listFiles()?.map { it.name }?.sorted()
            }.getOrNull() ?: emptyList()
            _filesList.value = files
            _configFile.value = files.firstOrNull { it.lowercase().contains("example") } ?: files.firstOrNull()
        }
    }

    fun loadForEdit(instance: KlipperInstance) {
        _editingInstance.value = instance
        _defaultName.value = instance.name
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
        val finalizedName = run {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                ensureUniqueName(trimmed, editing)
            } else {
                computeDefaultName()
            }
        }
        if (editing != null) {
            editing.name = finalizedName
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
            this.name = finalizedName
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
