package dev.nexo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Auto-start do backend no boot */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Boot completed, starting backend service")
            val serviceIntent = Intent(context, BackendService::class.java)
            serviceIntent.action = BackendService.ACTION_START
            context.startForegroundService(serviceIntent)
        }
    }
}