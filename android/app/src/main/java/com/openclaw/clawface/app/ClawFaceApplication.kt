package com.openclaw.clawface.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.openclaw.clawface.widget.QuotaWidgetScheduler

class ClawFaceApplication : Application() {

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                QuotaWidgetScheduler.refreshIfStale(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        QuotaWidgetScheduler.ensureScheduled(this)
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
