package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Utility for rendering glow/halo effects around the face.
 * Uses Paint.setShadowLayer for outer glow on software-rendered layers.
 * Renders as rounded rectangle to match the glass card shape.
 */
object GlowEffect {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val glowRect = RectF()

    /**
     * Draw a subtle rounded-rectangle glow around the face card area.
     */
    fun drawAmbientGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        viewWidth: Float,
        viewHeight: Float,
        cornerRadius: Float,
        color: Int,
        glowColor: Int,
        alpha: Float,
    ) {
        val adjustedAlpha = ((glowColor shr 24) and 0xFF) * alpha
        val adjustedGlowColor = (adjustedAlpha.toInt() shl 24) or (glowColor and 0x00FFFFFF)

        // Glow rect matches card inset (0.08f)
        val halfW = viewWidth * 0.42f * 0.95f
        val halfH = viewHeight * 0.42f * 0.95f
        glowRect.set(
            centerX - halfW, centerY - halfH,
            centerX + halfW, centerY + halfH,
        )

        glowPaint.color = adjustedGlowColor
        glowPaint.setShadowLayer(viewWidth * 0.15f, 0f, 0f, adjustedGlowColor)
        glowPaint.strokeWidth = viewWidth * 0.015f

        canvas.drawRoundRect(glowRect, cornerRadius, cornerRadius, glowPaint)
    }
}
