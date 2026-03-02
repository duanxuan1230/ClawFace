package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Ambient glow/halo behind the ghost.
 * Softer and rounder than before to match the ghost body shape.
 */
object GlowEffect {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glowRect = RectF()

    fun drawAmbientGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        viewWidth: Float,
        viewHeight: Float,
        cornerRadius: Float,
        @Suppress("UNUSED_PARAMETER") color: Int,
        glowColor: Int,
        alpha: Float,
    ) {
        val adjustedAlpha = ((glowColor ushr 24) and 0xFF) * alpha
        val adjustedGlowColor = (adjustedAlpha.toInt().coerceIn(0, 255) shl 24) or
                (glowColor and 0x00FFFFFF)

        // Slightly larger and rounder to wrap the ghost shape
        val halfW = viewWidth * 0.40f
        val halfH = viewHeight * 0.42f
        glowRect.set(
            centerX - halfW, centerY - halfH,
            centerX + halfW, centerY + halfH,
        )

        glowPaint.color = adjustedGlowColor
        glowPaint.setShadowLayer(viewWidth * 0.18f, 0f, 0f, adjustedGlowColor)
        glowPaint.strokeWidth = viewWidth * 0.01f

        // More rounded corners to match ghost body
        val r = cornerRadius * 1.5f
        canvas.drawRoundRect(glowRect, r, r, glowPaint)
    }
}
