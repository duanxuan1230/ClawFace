package com.openclaw.clawface.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.openclaw.clawface.R
import com.openclaw.clawface.network.QuotaApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面小组件：显示 Claude 订阅配额（5h / 本周 窗口）。
 *
 * - 数据来自 ClawFace daemon（GET /api/quota），服务器地址复用 App 保存的 host/port。
 * - 整块点击 → 快速重读服务端最近快照（轻）。
 * - 右上角刷新键 → 强制服务端立即抓一次（POST /api/quota/refresh，约 5-15s），用于主动了解最新配额。
 * - 系统每 30 分钟自动刷新一次（updatePeriodMillis）。
 * - 完全不依赖 App 进程存活：本组件是系统级 BroadcastReceiver，直接连服务器。
 */
class QuotaWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.openclaw.clawface.widget.ACTION_REFRESH"
        const val ACTION_MANUAL_REFRESH = "com.openclaw.clawface.widget.ACTION_MANUAL_REFRESH"
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        /** 供 App 在更新配额后顺手刷新桌面组件（App 进程内，直接起线程） */
        fun refreshAll(context: Context, force: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, QuotaWidgetProvider::class.java))
            if (ids.isEmpty()) return
            Thread { renderInto(context, mgr, ids, force) }.start()
        }

        /** 同步拉取并刷新给定 widget（在后台线程调用） */
        private fun renderInto(context: Context, mgr: AppWidgetManager, ids: IntArray, force: Boolean) {
            val server = QuotaApi.readServer(context)
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_quota)
                bindClicks(context, views)

                if (server == null) {
                    views.setTextViewText(R.id.widgetStatus, "请先在 App 里连接服务器")
                    views.setTextViewText(R.id.widgetUpdated, "")
                    mgr.updateAppWidget(id, views)
                    return@forEach
                }

                val (host, port) = server
                try {
                    val snap = if (force) QuotaApi.refresh(host, port) else QuotaApi.fetchQuota(host, port)
                    applySnapshot(views, snap)
                } catch (e: Exception) {
                    views.setTextViewText(R.id.widgetStatus, "连接失败：${e.message ?: "网络错误"}")
                    views.setTextViewText(R.id.widgetUpdated, "点击刷新")
                }
                mgr.updateAppWidget(id, views)
            }
        }

        private fun applySnapshot(views: RemoteViews, snap: QuotaApi.Snapshot) {
            bindBucket(views, snap.fiveHour, R.id.widgetBar5h, R.id.widgetPct5h, R.id.widgetReset5h)
            bindBucket(views, snap.sevenDay, R.id.widgetBar7d, R.id.widgetPct7d, R.id.widgetReset7d)

            when {
                snap.fiveHour == null && snap.sevenDay == null && snap.error != null ->
                    views.setTextViewText(R.id.widgetStatus, "暂无数据（在 App 打开开关，或点右上角刷新）")
                snap.error != null ->
                    views.setTextViewText(R.id.widgetStatus, "注意：${snap.error}")
                else ->
                    views.setTextViewText(R.id.widgetStatus, "")
            }

            val updated = snap.fetchedAt?.let { "更新于 ${timeFmt.format(Date(it))}" } ?: "点击刷新"
            views.setTextViewText(R.id.widgetUpdated, updated)
        }

        private fun bindBucket(
            views: RemoteViews,
            bucket: QuotaApi.Bucket?,
            barId: Int,
            pctId: Int,
            resetId: Int,
        ) {
            if (bucket == null) {
                views.setProgressBar(barId, 100, 0, false)
                views.setTextViewText(pctId, "--%")
                views.setTextViewText(resetId, "")
            } else {
                views.setProgressBar(barId, 100, bucket.utilization, false)
                views.setTextViewText(pctId, "${bucket.utilization}%")
                views.setTextViewText(resetId, QuotaApi.formatReset(bucket.resetsAt))
            }
        }

        private fun bindClicks(context: Context, views: RemoteViews) {
            // 整块点击 → 快速重读
            val quick = Intent(context, QuotaWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            views.setOnClickPendingIntent(
                R.id.widgetRoot,
                PendingIntent.getBroadcast(
                    context, 0, quick,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            // 右上角刷新键 → 强制刷新
            val force = Intent(context, QuotaWidgetProvider::class.java).apply { action = ACTION_MANUAL_REFRESH }
            views.setOnClickPendingIntent(
                R.id.widgetRefresh,
                PendingIntent.getBroadcast(
                    context, 1, force,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        /** 强制刷新前，先把组件标成「刷新中…」给即时反馈 */
        private fun markRefreshing(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_quota)
                bindClicks(context, views)
                views.setTextViewText(R.id.widgetUpdated, "刷新中…")
                views.setTextViewText(R.id.widgetStatus, "正在抓取最新配额…")
                mgr.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        doUpdate(context, appWidgetIds, force = false, showRefreshing = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> doUpdate(context, currentIds(context), force = false, showRefreshing = false)
            ACTION_MANUAL_REFRESH -> doUpdate(context, currentIds(context), force = true, showRefreshing = true)
        }
    }

    private fun currentIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, QuotaWidgetProvider::class.java))

    /** 用 goAsync 保活，在后台线程拉取，避免 onReceive 返回后进程被杀掉 */
    private fun doUpdate(context: Context, ids: IntArray, force: Boolean, showRefreshing: Boolean) {
        if (ids.isEmpty()) return
        val mgr = AppWidgetManager.getInstance(context)
        if (showRefreshing) markRefreshing(context, mgr, ids)
        val pending = goAsync()
        Thread {
            try {
                renderInto(context, mgr, ids, force)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
