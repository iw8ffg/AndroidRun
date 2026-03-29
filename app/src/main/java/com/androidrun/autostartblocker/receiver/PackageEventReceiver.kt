package com.androidrun.autostartblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.androidrun.autostartblocker.service.AppKillerService

class PackageEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageEventReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return

        Log.d(TAG, "Package event: $action for $packageName")

        // Trigger a kill cycle — the service will check if the package is blocked
        try {
            val serviceIntent = Intent(context, AppKillerService::class.java).apply {
                putExtra(AppKillerService.EXTRA_SOURCE, "package_event")
                putExtra(AppKillerService.EXTRA_TARGET_PACKAGE, packageName)
            }
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start kill service for $packageName", e)
            AppKillerService.scheduleOneShot(context)
        }
    }
}
