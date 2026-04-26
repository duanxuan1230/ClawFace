package com.openclaw.clawface.rendering

import android.graphics.*
import com.openclaw.clawface.state.FaceParams
import com.openclaw.clawface.state.GhostTheme

/**
 * Draws two soft elliptical blush patches on the ghost's cheeks.
 *
 * Matches the design mockup:
 *  - Elliptical shape (wider than tall, rx=0.10, ry=0.065)
 *  - Larger than before for a cuter look
 *  - Color sourced from GhostTheme (theme-aware blush)
 *  - 3-stop radial gradient: center 90% → 70% at 18% → edge 0%
 */
class CheekRenderer {

    private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cheekRect = RectF()

    companion object {
        private const val CHEEK_Y_OFFSET = 0.08f    // slightly higher than before
        private const val CHEEK_X_OFFSET = 0.22f
        private const val CHEEK_RX = 0.10f           // wider (was 0.08 radius)
        private const val CHEEK_RY = 0.065f          // shorter (elliptical)
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, faceSize: Float, params: FaceParams) {
        if (params.cheekIntensity <= 0.01f) return

        val rx = faceSize * CHEEK_RX
        val ry = faceSize * CHEEK_RY
        val cheekY = cy + faceSize * CHEEK_Y_OFFSET
        val xOff = faceSize * CHEEK_X_OFFSET

        // Use theme blush color, falling back to params.cheekColor
        val theme = GhostTheme.fromName(params.theme)
        val blushRGB = theme.blush and 0x00FFFFFF

        // 3-stop gradient matching design: 90% → 18% → 0% opacity
        val centerAlpha = (0.90f * params.cheekIntensity * 255f).toInt().coerceIn(0, 255)
        val midAlpha = (0.18f * params.cheekIntensity * 255f).toInt().coerceIn(0, 255)
        val colors = intArrayOf(
            (centerAlpha shl 24) or blushRGB,
            (midAlpha shl 24) or blushRGB,
            blushRGB,  // 0 alpha
        )
        val stops = floatArrayOf(0f, 0.7f, 1f)

        // Left cheek (ellipse via scaled circle draw)
        drawCheekEllipse(canvas, cx - xOff, cheekY, rx, ry, colors, stops)

        // Right cheek
        drawCheekEllipse(canvas, cx + xOff, cheekY, rx, ry, colors, stops)
    }

    private fun drawCheekEllipse(
        canvas: Canvas,
        cx: Float, cy: Float,
        rx: Float, ry: Float,
        colors: IntArray, stops: FloatArray,
    ) {
        // Use canvas scale to stretch a circular gradient into an ellipse
        canvas.save()
        canvas.scale(1f, ry / rx, cx, cy)

        cheekPaint.shader = RadialGradient(
            cx, cy, rx,
            colors, stops,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, rx, cheekPaint)

        canvas.restore()
    }
}
