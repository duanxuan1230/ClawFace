package com.openclaw.clawface.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.openclaw.clawface.R
import com.openclaw.clawface.animation.BlinkController
import com.openclaw.clawface.animation.profiles.*
import com.openclaw.clawface.app.MainActivity
import com.openclaw.clawface.config.AppConfig
import com.openclaw.clawface.network.ConnectionManager
import com.openclaw.clawface.protocol.Frame
import com.openclaw.clawface.state.EmotionPresets
import com.openclaw.clawface.state.Emotion
import com.openclaw.clawface.state.FaceMode
import com.openclaw.clawface.state.FaceParams
import com.openclaw.clawface.view.FaceView

/**
 * Foreground service hosting the ClawFace overlay window.
 * Manages window lifecycle, drag interaction, and basic animation loop.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var faceView: FaceView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Drag state
    private var isDragging = false
    private var longPressDetected = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var windowStartX = 0
    private var windowStartY = 0
    private var touchStartTime = 0L

    // Animation loop
    private var isRunning = false
    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            updateAnimation()
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // Animation engine
    private val blinkController = BlinkController()
    private var currentProfile: AnimationProfile = NeutralProfile

    // Current state
    private var currentParams = FaceParams()
    private var targetParams = EmotionPresets.getPreset(Emotion.NEUTRAL)
    private var currentEmotion = Emotion.NEUTRAL
    private var currentMode = FaceMode.ACTIVE

    // Network
    private var connectionManager: ConnectionManager? = null

    companion object {
        private const val LONG_PRESS_THRESHOLD_MS = 500L
        private const val TOUCH_SLOP_PX = 10
    }

    // Binder for Activity-Service communication
    private val binder = FaceBinder()

    inner class FaceBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        blinkController.configure(currentProfile)
        createNotificationChannel()
        startForeground(AppConfig.NOTIFICATION_ID, createNotification())
        createOverlayWindow()
        startAnimationLoop()
    }

    override fun onDestroy() {
        stopConnection()
        stopAnimationLoop()
        removeOverlayWindow()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AppConfig.NOTIFICATION_CHANNEL_ID,
            getString(R.string.overlay_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ClawFace overlay service"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, AppConfig.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val widthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            AppConfig.FACE_WIDTH_DP,
            resources.displayMetrics,
        ).toInt()
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            AppConfig.FACE_HEIGHT_DP,
            resources.displayMetrics,
        ).toInt()

        layoutParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        faceView = FaceView(this).apply {
            faceParams = targetParams
            faceMode = currentMode
        }

        setupTouchListener()
        windowManager?.addView(faceView, layoutParams)
    }

    private fun setupTouchListener() {
        faceView?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        windowStartX = layoutParams?.x ?: 0
                        windowStartY = layoutParams?.y ?: 0
                        touchStartTime = System.currentTimeMillis()
                        longPressDetected = false
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchStartX
                        val dy = event.rawY - touchStartY
                        val elapsed = System.currentTimeMillis() - touchStartTime

                        // Check for long press to enable drag
                        if (!longPressDetected && elapsed >= LONG_PRESS_THRESHOLD_MS) {
                            longPressDetected = true
                        }

                        // Allow drag after long press OR significant movement
                        if (longPressDetected ||
                            (dx * dx + dy * dy) > TOUCH_SLOP_PX * TOUCH_SLOP_PX
                        ) {
                            isDragging = true
                            layoutParams?.x = (windowStartX + dx).toInt()
                            layoutParams?.y = (windowStartY + dy).toInt()
                            windowManager?.updateViewLayout(faceView, layoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        longPressDetected = false
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun removeOverlayWindow() {
        faceView?.let {
            windowManager?.removeView(it)
        }
        faceView = null
    }

    private fun startAnimationLoop() {
        isRunning = true
        android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopAnimationLoop() {
        isRunning = false
    }

    private fun updateAnimation() {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val t = nowMs / 1000f

        // Compute procedural animation offset from current profile
        val offset = currentProfile.compute(t)
        faceView?.animationOffset = offset

        // Apply blink factor to eyeScaleY
        val blinkFactor = blinkController.update(nowMs)

        // Apply pupil jitter
        val pupilJitter = currentProfile.pupilJitter(t)
        val pupilJx = pupilJitter.first
        val pupilJy = pupilJitter.second

        // Build effective target with blink + pupil jitter applied
        val effectiveTarget = targetParams.copy(
            eyeScaleY = targetParams.eyeScaleY * blinkFactor,
            pupilOffsetX = targetParams.pupilOffsetX + pupilJx,
            pupilOffsetY = targetParams.pupilOffsetY + pupilJy,
        )

        // Lerp current params toward effective target
        currentParams = FaceParams.lerp(currentParams, effectiveTarget, AppConfig.LERP_FACTOR)
        faceView?.faceParams = currentParams
    }

    // --- Public API for controlling the face ---

    fun setEmotion(emotion: Emotion) {
        currentEmotion = emotion
        targetParams = EmotionPresets.getPreset(emotion)
        setMode(FaceMode.ACTIVE)
    }

    fun setMode(mode: FaceMode) {
        currentMode = mode
        faceView?.faceMode = mode
        when (mode) {
            FaceMode.STANDBY -> {
                targetParams = EmotionPresets.STANDBY
                applyProfile(StandbyProfile)
            }
            FaceMode.OFFLINE -> {
                targetParams = EmotionPresets.OFFLINE
                applyProfile(OfflineProfile)
            }
            FaceMode.THINKING -> {
                targetParams = EmotionPresets.THINKING
                applyProfile(ThinkingProfile)
            }
            FaceMode.ACTIVE -> {
                targetParams = EmotionPresets.getPreset(currentEmotion)
                applyProfile(getEmotionProfile(currentEmotion))
            }
        }
    }

    private fun applyProfile(profile: AnimationProfile) {
        currentProfile = profile
        blinkController.configure(profile)
    }

    private fun getEmotionProfile(emotion: Emotion): AnimationProfile = when (emotion) {
        Emotion.NEUTRAL -> NeutralProfile
        Emotion.JOY -> JoyProfile
        Emotion.ANXIETY -> AnxietyProfile
        Emotion.ENVY -> EnvyProfile
        Emotion.EMBARRASSMENT -> EmbarrassmentProfile
        Emotion.ENNUI -> EnnuiProfile
        Emotion.DISGUST -> DisgustProfile
        Emotion.FEAR -> FearProfile
        Emotion.ANGER -> AngerProfile
        Emotion.SADNESS -> SadnessProfile
    }

    fun setCustomColor(color: Int) {
        targetParams = targetParams.copy(color = color)
    }

    fun overrideParams(params: FaceParams) {
        targetParams = params
    }

    fun getTargetParams(): FaceParams = targetParams

    fun getCurrentEmotion(): Emotion = currentEmotion

    fun getCurrentMode(): FaceMode = currentMode

    // --- Network API ---

    fun startConnection(host: String, port: Int) {
        stopConnection()
        connectionManager = ConnectionManager().apply {
            onFrame = { frame -> handleFrame(frame) }
            onStateChange = { state -> handleConnectionState(state) }
            connect(host, port)
        }
    }

    fun stopConnection() {
        connectionManager?.disconnect()
        connectionManager = null
    }

    fun getConnectionState(): ConnectionManager.ConnectionState {
        return connectionManager?.connectionState?.value
            ?: ConnectionManager.ConnectionState.DISCONNECTED
    }

    private fun handleFrame(frame: Frame) {
        when (frame) {
            is Frame.EmotionFrame -> setEmotion(frame.emotion)
            is Frame.ExpressionFrame -> applyExpressionParams(frame.params)
            is Frame.ModeFrame -> setMode(frame.mode)
            is Frame.ColorFrame -> setCustomColor(frame.color)
            else -> { /* heartbeat/ack/unknown handled by ConnectionManager */ }
        }
    }

    private fun applyExpressionParams(params: Map<String, Float>) {
        targetParams = targetParams.copy(
            eyeScaleY = params["eyeScaleY"] ?: targetParams.eyeScaleY,
            eyeTilt = params["eyeTilt"] ?: targetParams.eyeTilt,
            eyeSquint = params["eyeSquint"] ?: targetParams.eyeSquint,
            pupilOffsetX = params["pupilOffsetX"] ?: targetParams.pupilOffsetX,
            pupilOffsetY = params["pupilOffsetY"] ?: targetParams.pupilOffsetY,
            pupilScale = params["pupilScale"] ?: targetParams.pupilScale,
            mouthCurve = params["mouthCurve"] ?: targetParams.mouthCurve,
            mouthWidth = params["mouthWidth"] ?: targetParams.mouthWidth,
            mouthOpen = params["mouthOpen"] ?: targetParams.mouthOpen,
        )
    }

    private fun handleConnectionState(state: ConnectionManager.ConnectionState) {
        when (state) {
            ConnectionManager.ConnectionState.OFFLINE -> setMode(FaceMode.OFFLINE)
            ConnectionManager.ConnectionState.CONNECTED -> {
                // Check if we should enter standby (no data frames for a while)
                val elapsed = connectionManager?.getTimeSinceLastFrame() ?: 0L
                if (elapsed >= AppConfig.STANDBY_TIMEOUT_MS && currentMode == FaceMode.ACTIVE) {
                    setMode(FaceMode.STANDBY)
                }
            }
            else -> { }
        }
    }
}
