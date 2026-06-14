package com.openclaw.clawface.app

import android.Manifest
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.openclaw.clawface.R
import com.openclaw.clawface.databinding.ActivityMainBinding
import com.openclaw.clawface.config.AppConfig
import com.openclaw.clawface.network.ConnectionManager
import com.openclaw.clawface.network.QuotaApi
import com.openclaw.clawface.widget.QuotaWidgetProvider
import com.openclaw.clawface.service.OverlayService
import com.openclaw.clawface.state.Emotion
import com.openclaw.clawface.state.EmotionPresets
import com.openclaw.clawface.state.FaceMode
import com.openclaw.clawface.state.FaceParams

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var overlayService: OverlayService? = null
    private var isBound = false

    // Slider configs: label, min, max, getter from FaceParams, setter lambda
    private data class SliderConfig(
        val label: String,
        val min: Float,
        val max: Float,
        val get: (FaceParams) -> Float,
        val set: (FaceParams, Float) -> FaceParams,
    )

    private val sliderConfigs = listOf(
        SliderConfig("eyeScaleY", 0f, 1.5f, { it.eyeScaleY }, { p, v -> p.copy(eyeScaleY = v) }),
        SliderConfig("eyeTilt", -20f, 20f, { it.eyeTilt }, { p, v -> p.copy(eyeTilt = v) }),
        SliderConfig("eyeSquint", 0f, 1f, { it.eyeSquint }, { p, v -> p.copy(eyeSquint = v) }),
        SliderConfig("pupilOffsetX", -1f, 1f, { it.pupilOffsetX }, { p, v -> p.copy(pupilOffsetX = v) }),
        SliderConfig("mouthCurve", -1f, 1f, { it.mouthCurve }, { p, v -> p.copy(mouthCurve = v) }),
        SliderConfig("mouthWidth", 0f, 1f, { it.mouthWidth }, { p, v -> p.copy(mouthWidth = v) }),
        SliderConfig("mouthOpen", 0f, 1f, { it.mouthOpen }, { p, v -> p.copy(mouthOpen = v) }),
    )

    private val sliderViews = mutableListOf<Triple<Slider, TextView, SliderConfig>>()
    private var suppressSliderUpdates = false

    // Glass tinting
    private var emotionOverlay: GradientDrawable? = null
    private var currentTintColor: Int = 0x1AFFFFFF
    private var tintAnimator: ValueAnimator? = null
    private lateinit var emotionButtons: List<MaterialButton>

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val faceBinder = binder as OverlayService.FaceBinder
            overlayService = faceBinder.getService()
            isBound = true
            syncSlidersToCurrentEmotion()
            // Resume connection status polling if there's an active connection
            val state = overlayService?.getConnectionState()
            if (state != null && state != ConnectionManager.ConnectionState.DISCONNECTED) {
                startConnectionStatusPolling()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            overlayService = null
            isBound = false
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionUI()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, service can still run */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGlassTinting()
        setupPermissionButtons()
        setupServiceButtons()
        setupEmotionButtons()
        setupModeButtons()
        setupSliders()
        setupThemeButtons()
        setupNetworkButtons()
        setupQuota()
        updatePermissionUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
        tryBindService()
        refreshQuotaCard()
    }

    override fun onPause() {
        super.onPause()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            overlayService = null
        }
    }

    // --- Setup ---

    private fun setupGlassTinting() {
        // Get the base layer of the Emotions card background to tint dynamically
        val bg = binding.cardEmotions.background
        if (bg is LayerDrawable && bg.numberOfLayers > 0) {
            val baseLayer = bg.getDrawable(0)
            if (baseLayer is GradientDrawable) {
                emotionOverlay = baseLayer.mutate() as GradientDrawable
            }
        }
    }

    private fun setupPermissionButtons() {
        binding.btnGrantPermission.setOnClickListener { requestOverlayPermission() }
    }

    private fun setupServiceButtons() {
        binding.btnStart.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                requestNotificationPermissionIfNeeded()
                startForegroundService(Intent(this, OverlayService::class.java))
                // Bind after a short delay to let service start
                binding.root.postDelayed({ tryBindService() }, 300)
            } else {
                requestOverlayPermission()
            }
        }

        binding.btnStop.setOnClickListener {
            if (isBound) {
                unbindService(serviceConnection)
                isBound = false
                overlayService = null
            }
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    private fun setupEmotionButtons() {
        val emotionMap = mapOf(
            binding.btnNeutral to Emotion.NEUTRAL,
            binding.btnJoy to Emotion.JOY,
            binding.btnAnxiety to Emotion.ANXIETY,
            binding.btnEnvy to Emotion.ENVY,
            binding.btnEmbarrassment to Emotion.EMBARRASSMENT,
            binding.btnEnnui to Emotion.ENNUI,
            binding.btnDisgust to Emotion.DISGUST,
            binding.btnFear to Emotion.FEAR,
            binding.btnAnger to Emotion.ANGER,
            binding.btnSadness to Emotion.SADNESS,
        )

        emotionButtons = emotionMap.keys.toList()

        emotionMap.forEach { (button, emotion) ->
            button.setOnClickListener {
                overlayService?.setEmotion(emotion)
                syncSlidersToEmotion(emotion)
                applyEmotionTint(EmotionPresets.getPreset(emotion).color)
                highlightButton(button, emotionButtons)
            }
        }
    }

    private lateinit var themeButtons: List<MaterialButton>

    private fun setupThemeButtons() {
        val themeMap = mapOf(
            binding.btnThemePastel to "pastel",
            binding.btnThemeMint to "mint",
            binding.btnThemeSunset to "sunset",
            binding.btnThemeLilac to "lilac",
            binding.btnThemeSky to "sky",
        )

        themeButtons = themeMap.keys.toList()
        // Default: pastel highlighted
        highlightButton(binding.btnThemePastel, themeButtons)

        themeMap.forEach { (button, theme) ->
            button.setOnClickListener {
                overlayService?.setTheme(theme)
                highlightButton(button, themeButtons)
            }
        }
    }

    private fun setupNetworkButtons() {
        // Restore last used host/port
        val prefs = getSharedPreferences("clawface_prefs", MODE_PRIVATE)
        prefs.getString("last_host", null)?.let { binding.etHost.setText(it) }
        val savedPort = prefs.getString("last_port", null)
        if (!savedPort.isNullOrEmpty()) binding.etPort.setText(savedPort)

        binding.btnConnect.setOnClickListener {
            val host = binding.etHost.text.toString().trim()
            val portStr = binding.etPort.text.toString().trim()
            if (host.isEmpty()) {
                binding.tvConnectionStatus.text = "Status: Enter host IP"
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull() ?: AppConfig.DEFAULT_PORT

            // Save for next launch
            prefs.edit()
                .putString("last_host", host)
                .putString("last_port", portStr)
                .apply()

            overlayService?.startConnection(host, port)
            binding.tvConnectionStatus.text = "Status: Connecting..."
            startConnectionStatusPolling()
        }

        binding.btnDisconnect.setOnClickListener {
            overlayService?.stopConnection()
            binding.tvConnectionStatus.text = "Status: Disconnected"
        }
    }

    // --- Quota（Claude 订阅配额） ---

    private var suppressQuotaSwitch = false

    private fun setupQuota() {
        binding.switchQuota.setOnCheckedChangeListener { _, isChecked ->
            if (suppressQuotaSwitch) return@setOnCheckedChangeListener
            val server = QuotaApi.readServer(this@MainActivity)
            if (server == null) {
                binding.tvQuotaStatus.text = "请先在下方「NETWORK」连接服务器"
                suppressQuotaSwitch = true
                binding.switchQuota.isChecked = false
                suppressQuotaSwitch = false
                return@setOnCheckedChangeListener
            }
            val (host, port) = server
            binding.tvQuotaStatus.text = if (isChecked) "正在开启并获取配额…" else "已关闭"
            Thread {
                try {
                    QuotaApi.setPoller(host, port, isChecked)
                    if (isChecked) {
                        var snap: QuotaApi.Snapshot? = null
                        for (i in 0 until 25) {
                            Thread.sleep(1000)
                            val s = QuotaApi.fetchQuota(host, port)
                            if (s.fiveHour != null || s.sevenDay != null || s.error != null) {
                                snap = s; break
                            }
                        }
                        val result = snap
                        runOnUiThread {
                            if (result != null) applyQuotaToCard(result)
                            else binding.tvQuotaStatus.text = "已开启，等待数据…"
                        }
                    } else {
                        runOnUiThread { binding.tvQuotaStatus.text = "已关闭（不再获取配额）" }
                    }
                    QuotaWidgetProvider.refreshAll(this@MainActivity)
                } catch (e: Exception) {
                    runOnUiThread { binding.tvQuotaStatus.text = "操作失败：${e.message}" }
                }
            }.start()
        }
        refreshQuotaCard()
    }

    private fun refreshQuotaCard() {
        val server = QuotaApi.readServer(this@MainActivity) ?: run {
            binding.tvQuotaStatus.text = "请先连接服务器后再开启配额"
            return
        }
        val (host, port) = server
        Thread {
            try {
                val status = QuotaApi.fetchStatus(host, port)
                val snap = QuotaApi.fetchQuota(host, port)
                runOnUiThread {
                    suppressQuotaSwitch = true
                    binding.switchQuota.isChecked = status.enabled
                    suppressQuotaSwitch = false
                    applyQuotaToCard(snap)
                    when {
                        !status.configured -> binding.tvQuotaStatus.text = "服务端未配置凭据"
                        !status.enabled -> binding.tvQuotaStatus.text = "开关打开后开始实时获取配额"
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { binding.tvQuotaStatus.text = "未连接到服务器" }
            }
        }.start()
    }

    private fun applyQuotaToCard(snap: QuotaApi.Snapshot) {
        bindBucketCard(snap.fiveHour, binding.progressQuota5h, binding.tvQuota5hPct, binding.tvQuota5hReset)
        bindBucketCard(snap.sevenDay, binding.progressQuota7d, binding.tvQuota7dPct, binding.tvQuota7dReset)
        binding.tvQuotaStatus.text = when {
            snap.error != null && snap.fiveHour == null && snap.sevenDay == null ->
                "暂无数据：${snap.error}"
            snap.error != null -> "注意：${snap.error}"
            snap.fetchedAt != null ->
                "更新于 " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(snap.fetchedAt))
            else -> ""
        }
    }

    private fun bindBucketCard(
        bucket: QuotaApi.Bucket?,
        bar: ProgressBar,
        pct: TextView,
        reset: TextView,
    ) {
        if (bucket == null) {
            bar.progress = 0
            pct.text = "--%"
            reset.text = ""
        } else {
            bar.progress = bucket.utilization
            pct.text = "${bucket.utilization}%"
            reset.text = QuotaApi.formatReset(bucket.resetsAt)
        }
    }

    private fun startConnectionStatusPolling() {
        binding.root.postDelayed(object : Runnable {
            override fun run() {
                val state = overlayService?.getConnectionState()
                    ?: ConnectionManager.ConnectionState.DISCONNECTED
                val statusText = when (state) {
                    ConnectionManager.ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionManager.ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionManager.ConnectionState.CONNECTED -> "Connected"
                    ConnectionManager.ConnectionState.OFFLINE -> "Reconnecting..."
                }
                binding.tvConnectionStatus.text = "Status: $statusText"
                // Continue polling while connected/connecting
                if (state != ConnectionManager.ConnectionState.DISCONNECTED && isBound) {
                    binding.root.postDelayed(this, 2000)
                }
            }
        }, 1000)
    }

    private fun setupModeButtons() {
        binding.btnModeActive.setOnClickListener {
            overlayService?.setMode(FaceMode.ACTIVE)
            overlayService?.getCurrentEmotion()?.let { syncSlidersToEmotion(it) }
        }
        binding.btnModeStandby.setOnClickListener {
            overlayService?.setMode(FaceMode.STANDBY)
        }
        binding.btnModeThinking.setOnClickListener {
            overlayService?.setMode(FaceMode.THINKING)
        }
        binding.btnModeOffline.setOnClickListener {
            overlayService?.setMode(FaceMode.OFFLINE)
        }
    }

    private fun setupSliders() {
        val sliderViewIds = listOf(
            R.id.sliderEyeScaleY,
            R.id.sliderEyeTilt,
            R.id.sliderEyeSquint,
            R.id.sliderPupilOffsetX,
            R.id.sliderMouthCurve,
            R.id.sliderMouthWidth,
            R.id.sliderMouthOpen,
        )

        sliderViewIds.forEachIndexed { index, viewId ->
            val container = findViewById<View>(viewId)
            val slider = container.findViewById<Slider>(R.id.slider)
            val tvLabel = container.findViewById<TextView>(R.id.tvLabel)
            val tvValue = container.findViewById<TextView>(R.id.tvValue)
            val config = sliderConfigs[index]

            tvLabel.text = config.label

            // Map real range to slider 0-100
            slider.valueFrom = 0f
            slider.valueTo = 100f
            slider.stepSize = 1f
            slider.value = rangeToSlider(config.get(FaceParams()), config.min, config.max)

            tvValue.text = formatValue(config.get(FaceParams()))

            slider.addOnChangeListener { _, value, fromUser ->
                if (!fromUser || suppressSliderUpdates) return@addOnChangeListener
                val realValue = sliderToRange(value, config.min, config.max)
                tvValue.text = formatValue(realValue)

                overlayService?.let { service ->
                    val current = service.getTargetParams()
                    service.overrideParams(config.set(current, realValue))
                }
            }

            sliderViews.add(Triple(slider, tvValue, config))
        }
    }

    // --- Slider sync ---

    private fun syncSlidersToEmotion(emotion: Emotion) {
        syncSlidersToParams(EmotionPresets.getPreset(emotion))
    }

    private fun syncSlidersToCurrentEmotion() {
        overlayService?.let {
            syncSlidersToParams(it.getTargetParams())
        }
    }

    private fun syncSlidersToParams(params: FaceParams) {
        suppressSliderUpdates = true
        sliderViews.forEach { (slider, tvValue, config) ->
            val realValue = config.get(params)
            slider.value = rangeToSlider(realValue, config.min, config.max)
            tvValue.text = formatValue(realValue)
        }
        suppressSliderUpdates = false
    }

    // --- Glass tinting ---

    private fun applyEmotionTint(emotionArgb: Int) {
        val overlay = emotionOverlay ?: return
        // Extract RGB, apply 20% alpha for glass tint
        val targetTint = (emotionArgb and 0x00FFFFFF) or 0x33000000

        tintAnimator?.cancel()
        tintAnimator = ValueAnimator.ofArgb(currentTintColor, targetTint).apply {
            duration = 250
            addUpdateListener {
                val color = it.animatedValue as Int
                currentTintColor = color
                overlay.setColor(color)
            }
            start()
        }
    }

    private fun highlightButton(selected: MaterialButton, all: List<MaterialButton>) {
        all.forEach { btn ->
            btn.alpha = if (btn == selected) 1.0f else 0.55f
        }
    }

    // --- Helpers ---

    private fun rangeToSlider(value: Float, min: Float, max: Float): Float {
        return ((value - min) / (max - min) * 100f).coerceIn(0f, 100f).let {
            kotlin.math.round(it)
        }
    }

    private fun sliderToRange(sliderValue: Float, min: Float, max: Float): Float {
        return min + (sliderValue / 100f) * (max - min)
    }

    private fun formatValue(value: Float): String {
        return "%.2f".format(value)
    }

    private fun updatePermissionUI() {
        val hasPermission = Settings.canDrawOverlays(this)
        binding.tvPermissionWarning.visibility = if (hasPermission) View.GONE else View.VISIBLE
        binding.btnGrantPermission.visibility = if (hasPermission) View.GONE else View.VISIBLE
        binding.btnStart.isEnabled = hasPermission
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun tryBindService() {
        if (!isBound) {
            val intent = Intent(this, OverlayService::class.java)
            bindService(intent, serviceConnection, 0)
        }
    }
}
