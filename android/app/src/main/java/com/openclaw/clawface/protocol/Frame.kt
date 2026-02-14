package com.openclaw.clawface.protocol

import com.openclaw.clawface.state.Emotion
import com.openclaw.clawface.state.FaceMode

/**
 * Sealed class representing all supported network frame types.
 * Each UDP packet contains one JSON-encoded frame.
 */
sealed class Frame {
    data class EmotionFrame(val emotion: Emotion) : Frame()
    data class ExpressionFrame(val params: Map<String, Float>) : Frame()
    data class ModeFrame(val mode: FaceMode) : Frame()
    data class ColorFrame(val color: Int) : Frame()
    object Heartbeat : Frame()
    object HeartbeatAck : Frame()
    object Unknown : Frame()
}
