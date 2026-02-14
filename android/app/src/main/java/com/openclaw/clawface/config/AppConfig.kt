package com.openclaw.clawface.config

object AppConfig {
    // Rendering
    const val TARGET_FPS = 30
    const val STANDBY_FPS = 10
    const val FACE_WIDTH_DP = 140f
    const val FACE_HEIGHT_DP = 160f
    const val GLASS_CORNER_RADIUS_RATIO = 0.16f  // corner radius as fraction of width

    // Animation
    const val LERP_FACTOR = 0.08f
    const val BLINK_DURATION_MS = 200L
    const val SLOW_BLINK_DURATION_MS = 1500L
    const val BLINK_INTERVAL_MIN_MS = 2000L
    const val BLINK_INTERVAL_MAX_MS = 6000L

    // State
    const val STANDBY_TIMEOUT_MS = 5000L
    const val THINKING_ALPHA_FACTOR = 0.5f

    // Network
    const val DEFAULT_PORT = 9527
    const val HEARTBEAT_INTERVAL_MS = 30000L
    const val HEARTBEAT_TIMEOUT_COUNT = 3
    const val RECONNECT_BASE_DELAY_MS = 1000L
    const val RECONNECT_MAX_DELAY_MS = 30000L
    const val UDP_BUFFER_SIZE = 4096

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "clawface_overlay"
    const val NOTIFICATION_ID = 1001
}
