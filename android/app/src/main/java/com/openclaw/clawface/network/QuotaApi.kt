package com.openclaw.clawface.network

import android.content.Context
import com.openclaw.clawface.config.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 访问 ClawFace daemon 的配额接口（HTTP，端口默认 9527）。
 *
 *   GET  /api/quota         → 最近一次配额快照
 *   GET  /api/quota/poller  → 轮询开关状态
 *   POST /api/quota/poller  → { enabled: true|false } 启停轮询
 *
 * 所有方法都是【同步阻塞】，调用方需放到后台线程执行。
 * 服务器地址复用 App 连接表情时存的 SharedPreferences("clawface_prefs")。
 */
object QuotaApi {

    private const val PREFS = "clawface_prefs"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // 强制刷新会触发服务端 Playwright 登录抓取，耗时较长，单独给长超时
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    data class Bucket(val utilization: Int, val resetsAt: String?)

    data class Snapshot(
        val fiveHour: Bucket?,
        val sevenDay: Bucket?,
        val sevenDayOpus: Bucket?,
        val sevenDaySonnet: Bucket?,
        val fetchedAt: Long?,
        val error: String?,
    )

    data class PollerStatus(
        val enabled: Boolean,
        val intervalMs: Long,
        val polling: Boolean,
        val lastFetchedAt: Long?,
        val lastError: String?,
        val hasData: Boolean,
        val configured: Boolean,
    )

    /** 读取 App 里保存的服务器 host/port，未配置返回 null */
    fun readServer(context: Context): Pair<String, Int>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString("last_host", null)?.trim()
        if (host.isNullOrEmpty()) return null
        val port = prefs.getString("last_port", null)?.toIntOrNull() ?: AppConfig.DEFAULT_PORT
        return host to port
    }

    private fun base(host: String, port: Int) = "http://$host:$port"

    fun fetchQuota(host: String, port: Int): Snapshot {
        val req = Request.Builder().url("${base(host, port)}/api/quota").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            return parseSnapshot(JSONObject(body))
        }
    }

    fun fetchStatus(host: String, port: Int): PollerStatus {
        val req = Request.Builder().url("${base(host, port)}/api/quota/poller").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            return parseStatus(JSONObject(body))
        }
    }

    /** 打开/关闭轮询，返回最新状态 */
    fun setPoller(host: String, port: Int, enabled: Boolean): PollerStatus {
        val payload = JSONObject().put("enabled", enabled).toString()
        val req = Request.Builder()
            .url("${base(host, port)}/api/quota/poller")
            .post(payload.toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val obj = JSONObject(body)
            val statusObj = obj.optJSONObject("status") ?: obj
            return parseStatus(statusObj)
        }
    }

    /** 手动强制刷新：服务端立即抓一次（不论开关状态），返回最新快照。耗时较长。 */
    fun refresh(host: String, port: Int): Snapshot {
        val req = Request.Builder()
            .url("${base(host, port)}/api/quota/refresh")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        refreshClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            return parseSnapshot(JSONObject(body))
        }
    }

    // --- 解析 ---

    private fun parseBucket(obj: JSONObject?): Bucket? {
        if (obj == null) return null
        val util = obj.optDouble("utilization", Double.NaN)
        if (util.isNaN()) return null
        val reset = if (obj.isNull("resets_at")) null else obj.optString("resets_at")
        return Bucket(util.toInt(), reset)
    }

    private fun parseSnapshot(obj: JSONObject): Snapshot {
        val data = obj.optJSONObject("data")
        return Snapshot(
            fiveHour = parseBucket(data?.optJSONObject("five_hour")),
            sevenDay = parseBucket(data?.optJSONObject("seven_day")),
            sevenDayOpus = parseBucket(data?.optJSONObject("seven_day_opus")),
            sevenDaySonnet = parseBucket(data?.optJSONObject("seven_day_sonnet")),
            fetchedAt = if (obj.isNull("fetchedAt")) null else obj.optLong("fetchedAt").takeIf { it > 0 },
            error = if (obj.isNull("error")) null else obj.optString("error"),
        )
    }

    private fun parseStatus(obj: JSONObject): PollerStatus {
        return PollerStatus(
            enabled = obj.optBoolean("enabled", false),
            intervalMs = obj.optLong("intervalMs", 600000L),
            polling = obj.optBoolean("polling", false),
            lastFetchedAt = obj.optLong("lastFetchedAt").takeIf { it > 0 },
            lastError = if (obj.isNull("lastError")) null else obj.optString("lastError"),
            hasData = obj.optBoolean("hasData", false),
            configured = obj.optBoolean("configured", false),
        )
    }

    /** 把 resets_at（ISO8601）格式化成 "Xh Ym 后重置" */
    fun formatReset(resetsAt: String?): String {
        if (resetsAt.isNullOrEmpty()) return ""
        return try {
            val resetMs = parseIso(resetsAt)
            val diff = resetMs - System.currentTimeMillis()
            if (diff <= 0) return "已重置"
            val totalMin = diff / 60000
            val days = totalMin / (60 * 24)
            val hours = (totalMin / 60) % 24
            val mins = totalMin % 60
            when {
                days > 0 -> "${days}天${hours}小时后重置"
                hours > 0 -> "${hours}小时${mins}分后重置"
                else -> "${mins}分后重置"
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseIso(iso: String): Long {
        // 支持带毫秒/时区偏移的 ISO8601，统一交给 java.time（minSdk 26 起可用）
        return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    }
}
