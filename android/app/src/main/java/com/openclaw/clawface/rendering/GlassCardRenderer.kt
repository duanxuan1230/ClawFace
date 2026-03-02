package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Simplified glass card — now a subtle background layer behind the ghost body.
 * Only frosted base + faint emotion tint. Highlight and border moved to BodyRenderer.
 */
class GlassCardRenderer {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cardRect = RectF()

    companion object {
        const val CARD_INSET_RATIO = 0.12f  // more inset than before (was 0.08)
    }

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

        // Layer 1: Very faint frosted base (~8% opacity, was ~16%)
        basePaint.color = applyAlpha(0x14FFFFFF, alpha)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, basePaint)

        // Layer 2: Very faint emotion tint (~6% opacity, was ~12%)
        val tintColor = (emotionColor and 0x00FFFFFF) or 0x10000000
        tintPaint.color = applyAlpha(tintColor, alpha)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, tintPaint)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val origAlpha = (color ushr 24) and 0xFF
        val newAlpha = (origAlpha * alpha).toInt().coerceIn(0, 255)
        return (newAlpha shl 24) or (color and 0x00FFFFFF)
    }
}
