package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * Renders a glass morphism card background behind the face.
 * Layers: frosted base → emotion color tint → diagonal highlight → border gradient.
 * All Paint/RectF pre-allocated for zero-allocation onDraw().
 */
class GlassCardRenderer {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val cardRect = RectF()

    // Padding from view edges to leave room for glow
    companion object {
        const val CARD_INSET_RATIO = 0.08f
    }

    /**
     * Draw the glass card background.
     * @param canvas Canvas to draw on
     * @param viewWidth total view width in px
     * @param viewHeight total view height in px
     * @param cornerRadius rounded corner radius in px
     * @param emotionColor current emotion ARGB color (used for tinting)
     * @param alpha animation alpha (0-1)
     */
    fun draw(
        canvas: Canvas,
        viewWidth: Float,
        viewHeight: Float,
        cornerRadius: Float,
        emotionColor: Int,
        alpha: Float,
    ) {
        val insetX = viewWidth * CARD_INSET_RATIO
        val insetY = viewHeight * CARD_INSET_RATIO
        cardRect.set(insetX, insetY, viewWidth - insetX, viewHeight - insetY)

        // Layer 1: Frosted white base (~16% opacity)
        basePaint.color = applyAlpha(0x28FFFFFF, alpha)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, basePaint)

        // Layer 2: Emotion color tint (~12% opacity)
        val tintColor = (emotionColor and 0x00FFFFFF) or 0x20000000
        tintPaint.color = applyAlpha(tintColor, alpha)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, tintPaint)

        // Layer 3: Diagonal highlight gradient (top-left bright → bottom-right transparent)
        highlightPaint.shader = LinearGradient(
            cardRect.left, cardRect.top,
            cardRect.right, cardRect.bottom,
            applyAlpha(0x22FFFFFF, alpha),
            0x00000000,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, highlightPaint)

        // Layer 4: Border highlight (top-left bright → bottom-right dim)
        borderPaint.shader = LinearGradient(
            cardRect.left, cardRect.top,
            cardRect.right, cardRect.bottom,
            applyAlpha(0x55FFFFFF, alpha),
            applyAlpha(0x15FFFFFF, alpha),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val origAlpha = (color shr 24) and 0xFF
        val newAlpha = (origAlpha * alpha).toInt().coerceIn(0, 255)
        return (newAlpha shl 24) or (color and 0x00FFFFFF)
    }
}
