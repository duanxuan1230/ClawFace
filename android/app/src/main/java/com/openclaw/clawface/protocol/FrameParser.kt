package com.openclaw.clawface.protocol

import android.graphics.Color
import android.util.Log
import com.openclaw.clawface.state.Emotion
import com.openclaw.clawface.state.FaceMode
import org.json.JSONObject

/**
 * Parses JSON strings into Frame objects.
 * Uses Android built-in org.json — no external dependencies.
 * Malformed input returns Frame.Unknown (never throws).
 */
object FrameParser {

    private const val TAG = "FrameParser"

    fun parse(json: String): Frame {
        return try {
            val obj = JSONObject(json.trim())
            when (obj.optString("type", "")) {
                "emotion" -> parseEmotion(obj)
                "expression" -> parseExpression(obj)
                "mode" -> parseMode(obj)
                "color" -> parseColor(obj)
                "heartbeat" -> Frame.Heartbeat
                "heartbeat_ack" -> Frame.HeartbeatAck
                else -> {
                    Log.w(TAG, "Unknown frame type: ${obj.optString("type")}")
                    Frame.Unknown
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse frame: ${e.message}")
            Frame.Unknown
        }
    }

    private fun parseEmotion(obj: JSONObject): Frame {
        val name = obj.optString("emotion", "").uppercase()
        val emotion = try {
            Emotion.valueOf(name)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown emotion: $name")
            return Frame.Unknown
        }
        return Frame.EmotionFrame(emotion)
    }

    private fun parseExpression(obj: JSONObject): Frame {
        val paramsObj = obj.optJSONObject("params") ?: return Frame.Unknown
        val params = mutableMapOf<String, Float>()
        for (key in paramsObj.keys()) {
            val value = paramsObj.optDouble(key, Double.NaN)
            if (!value.isNaN()) {
                params[key] = value.toFloat()
            }
        }
        return Frame.ExpressionFrame(params)
    }

    private fun parseMode(obj: JSONObject): Frame {
        val name = obj.optString("mode", "").uppercase()
        val mode = try {
            FaceMode.valueOf(name)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown mode: $name")
            return Frame.Unknown
        }
        return Frame.ModeFrame(mode)
    }

    private fun parseColor(obj: JSONObject): Frame {
        val colorStr = obj.optString("color", "")
        return try {
            val color = Color.parseColor(colorStr)
            Frame.ColorFrame(color)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid color: $colorStr")
            Frame.Unknown
        }
    }
}
