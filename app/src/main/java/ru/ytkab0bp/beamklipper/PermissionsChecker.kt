package ru.ytkab0bp.beamklipper

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object PermissionsChecker {
    @JvmField
    val ENABLE_NOTIFICATIONS_CHANNEL_CHECK = false
    private var ignoreNotificationsChannel = false

    @JvmStatic
    fun setIgnoreNotificationsChannel(ignore: Boolean) {
        ignoreNotificationsChannel = ignore
    }

    @JvmStatic
    fun ignoreNotificationsChannel(): Boolean = ignoreNotificationsChannel

    @JvmStatic
    fun hasNotificationPerm(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(KlipperApp.INSTANCE, "android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isNotificationsChannelHidden(): Boolean {
        if (!ENABLE_NOTIFICATIONS_CHANNEL_CHECK) return true
        val notificationManager = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return ignoreNotificationsChannel || Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                notificationManager.getNotificationChannel(KlipperApp.SERVICES_CHANNEL) != null &&
                notificationManager.getNotificationChannel(KlipperApp.SERVICES_CHANNEL).importance == NotificationManager.IMPORTANCE_NONE
    }

    @JvmStatic
    fun hasBatteryPerm(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                !(KlipperApp.INSTANCE.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
    }

    @JvmStatic
    fun hasBatteryOptimizationIgnored(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true
        } else try {
            val pm = KlipperApp.INSTANCE.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(KlipperApp.INSTANCE.packageName)
        } catch (_: Throwable) {
            true
        }
    }

    @JvmStatic
    fun isNotBrokenBySDCard(): Boolean {
        val pm = KlipperApp.INSTANCE.packageManager
        return try {
            val info = pm.getApplicationInfo(KlipperApp.INSTANCE.packageName, 0)
            info.flags and android.content.pm.ApplicationInfo.FLAG_EXTERNAL_STORAGE == 0
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            true
        }
    }

    @JvmStatic
    fun needBlockStart(): Boolean {
        return !hasNotificationPerm() || !hasBatteryPerm() || !hasBatteryOptimizationIgnored() || !isNotBrokenBySDCard() || !isNotificationsChannelHidden()
    }
}
