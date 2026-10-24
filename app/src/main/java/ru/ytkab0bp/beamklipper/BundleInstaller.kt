package ru.ytkab0bp.beamklipper

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object BundleInstaller {
    private const val VERSION_MARKER = ".bundle_version"

    @JvmStatic
    fun init(ctx: Context) {
        val prefs = ctx.getSharedPreferences("installation", 0)
        val assets = ctx.assets
        try {
            val pm = ctx.packageManager
            val info = pm.getPackageInfo(ctx.packageName, 0)
            var ver = readString(assets, "bundle_version") + "_beam-" + info.versionName

            val root = ctx.filesDir
            val marker = File(root, VERSION_MARKER)
            // SharedPreferences is cached per process, so a process that started before
            // another one finished unpacking can still see the old version. The marker
            // file is read after the cross-process lock, making the check reliable.
            val installed = prefs.getString("version", "") == ver ||
                runCatching { marker.readText() }.getOrNull() == ver
            if (!installed) {
                val index = JSONObject(readString(assets, "index.json"))
                unpack(assets, index, root, "klipper")
                unpack(assets, index, root, "kalico")
                unpack(assets, index, root, "moonraker")
                prefs.edit().putString("version", ver).commit()
                marker.writeText(ver)
            } else if (!marker.exists()) {
                marker.writeText(ver)
            }

            val nativeDir = File(info.applicationInfo!!.nativeLibraryDir)

            patchBundledFile(root, assets, "klipper", "klippy/chelper/__init__.py") {
                it.replace("\${DEST_LIB}", File(nativeDir, "libklippy_chelper.so").absolutePath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/chelper/__init__.py") {
                it.replace("\${DEST_LIB}", File(nativeDir, "libkalico_chelper.so").absolutePath)
            }

            var str = readString(assets, "moonraker/moonraker/utils/sysfs_devs.py")
            str = str.replace("TTY_PATH = \"/sys/class/tty\"",
                "TTY_PATH = \"" + File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath + "\"")
            FileOutputStream(File(root, "moonraker/moonraker/utils/sysfs_devs.py")).use {
                it.write(str.toByteArray(StandardCharsets.UTF_8))
            }

            val tempPath = File(KlipperApp.INSTANCE.cacheDir, "resonances").absolutePath
            patchBundledFile(root, assets, "klipper", "klippy/extras/resonance_tester.py") {
                it.replace("\${TEMP_PATH}", tempPath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/extras/resonance_tester.py") {
                it.replace("\${TEMP_PATH}", tempPath)
            }

            val ttyPath = "'" + File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath + "'"
            patchBundledFile(root, assets, "klipper", "klippy/mcu.py") {
                it.replace("\${TTY_PATH}", ttyPath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/mcu.py") {
                it.replace("\${TTY_PATH}", ttyPath)
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun unpack(assets: android.content.res.AssetManager, index: JSONObject, root: File, key: String) {
        val dir = File(root, key)
        dir.deleteRecursively()

        val arr = index.optJSONArray(key)
        if (arr == null) {
            return
        }
        for (i in 0 until arr.length()) {
            val file = arr.optString(i)
            val into = File(dir, file)
            into.parentFile?.mkdirs()
            assets.open("$key/$file").use { inp ->
                FileOutputStream(into).use { fos ->
                    inp.copyTo(fos)
                }
            }
        }
    }

    private fun patchBundledFile(
        root: File,
        assets: android.content.res.AssetManager,
        bundleKey: String,
        relativePath: String,
        transform: (String) -> String,
    ) {
        val target = File(root, "$bundleKey/$relativePath")
        if (!target.exists()) {
            return
        }
        val updated = transform(readString(assets, "$bundleKey/$relativePath"))
        FileOutputStream(target).use {
            it.write(updated.toByteArray(StandardCharsets.UTF_8))
        }
    }

    @JvmStatic
    fun readString(assets: android.content.res.AssetManager, key: String): String {
        return assets.open(key).use { inp ->
            inp.readBytes().toString(StandardCharsets.UTF_8)
        }
    }
}
