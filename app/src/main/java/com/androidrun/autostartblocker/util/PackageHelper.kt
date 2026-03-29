package com.androidrun.autostartblocker.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.androidrun.autostartblocker.model.AppInfo

object PackageHelper {

    /**
     * Returns a list of installed applications.
     *
     * On Android 11+ (API 30) the calling app must declare <queries> in the manifest
     * or hold the QUERY_ALL_PACKAGES permission to see all packages.
     *
     * @param context        application or activity context
     * @param includeSystemApps whether to include system-level apps in the result
     * @return list of [AppInfo] sorted alphabetically by app name
     */
    fun getInstalledApps(context: Context, includeSystemApps: Boolean = false): List<AppInfo> {
        val pm = context.packageManager

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ApplicationInfoFlags.of(0L)
        } else {
            // Pre-33: use the integer overload
            null
        }

        val installedApps: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && flags != null) {
            pm.getInstalledApplications(flags)
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        return installedApps
            .filter { appInfo ->
                if (includeSystemApps) {
                    true
                } else {
                    // Exclude apps flagged as system apps
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
            }
            .filter { it.packageName != context.packageName } // exclude ourselves
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null },
                    isBlocked = false // caller should reconcile with persisted blocked state
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * Checks whether a given package currently has a running process via [ActivityManager].
     *
     * Note: Starting from Android 5.0 (Lollipop), [ActivityManager.getRunningAppProcesses]
     * only returns information about the caller's own processes on most devices.
     * This helper therefore also checks running services as a secondary signal.
     */
    fun isAppRunning(context: Context, packageName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Check running app processes (may be limited on newer APIs)
        val runningProcesses = am.runningAppProcesses
        if (runningProcesses != null) {
            for (process in runningProcesses) {
                if (process.pkgList?.contains(packageName) == true) {
                    return true
                }
            }
        }

        // Fallback: check running services (deprecated but still functional for our own permission level)
        @Suppress("DEPRECATION")
        val runningServices = am.getRunningServices(Int.MAX_VALUE)
        if (runningServices != null) {
            for (service in runningServices) {
                if (service.service.packageName == packageName) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Retrieves [AppInfo] for a single package, or null if the package is not installed.
     */
    fun getAppInfo(context: Context, packageName: String): AppInfo? {
        val pm = context.packageManager

        val appInfo: ApplicationInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        return AppInfo(
            packageName = appInfo.packageName,
            appName = appInfo.loadLabel(pm).toString(),
            icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null },
            isBlocked = false
        )
    }
}
