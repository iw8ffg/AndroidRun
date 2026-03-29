package com.androidrun.autostartblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.androidrun.autostartblocker.service.AppKillerService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        Log.i(TAG, "Boot event received: $action")

        try {
            val serviceIntent = Intent(context, AppKillerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "AppKillerService started as foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground service", e)
        }
    }
}
