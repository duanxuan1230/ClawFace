package com.openclaw.clawface.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * WorkManager 周期刷新桌面小组件（15 分钟一次，走轻量 GET /api/quota）。
 * 相比 AppWidgetManager 原生 updatePeriodMillis，在 Doze/后台策略下更稳定。
 */
class QuotaWidgetWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val hasWidgets = QuotaWidgetProvider.refreshAllSync(applicationContext, force = false)
        if (!hasWidgets) {
            QuotaWidgetScheduler.cancel(applicationContext)
        }
        return Result.success()
    }
}

object QuotaWidgetScheduler {

    private const val UNIQUE_WORK_NAME = "clawface_quota_widget_periodic"
    private const val PERIOD_MINUTES = 15L
    private const val UNLOCK_DEBOUNCE_MS = 120_000L

    private val lastRefreshElapsedMs = AtomicLong(0L)

    fun hasWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, QuotaWidgetProvider::class.java))
        return ids.isNotEmpty()
    }

    /** 桌面上存在小组件时注册 15 分钟周期任务；否则取消。 */
    fun ensureScheduled(context: Context) {
        val appCtx = context.applicationContext
        if (!hasWidgets(appCtx)) {
            cancel(appCtx)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<QuotaWidgetWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun markRefreshed() {
        lastRefreshElapsedMs.set(SystemClock.elapsedRealtime())
    }

    /** 解锁亮屏时触发：距上次刷新超过防抖间隔且存在小组件，则轻量拉一次缓存快照。 */
    fun refreshIfStale(context: Context, minIntervalMs: Long = UNLOCK_DEBOUNCE_MS) {
        val appCtx = context.applicationContext
        if (!hasWidgets(appCtx)) return
        val now = SystemClock.elapsedRealtime()
        val last = lastRefreshElapsedMs.get()
        if (last > 0L && now - last < minIntervalMs) return
        QuotaWidgetProvider.refreshAll(appCtx, force = false)
    }
}
