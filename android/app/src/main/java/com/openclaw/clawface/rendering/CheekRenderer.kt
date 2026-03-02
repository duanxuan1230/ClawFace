package com.openclaw.clawface.rendering

import android.graphics.*
import com.openclaw.clawface.state.FaceParams

/**
 * Draws two soft rosy blush circles on the ghost's cheeks.
 * Uses RadialGradient for a natural soft-edge effect.
 */
class CheekRenderer {

    private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    companion object {
        private const val CHEEK_Y_OFFSET = 0.10f   // below center
        private const val CHEEK_X_OFFSET = 0.22f    // from center
        private const val CHEEK_RADIUS = 0.08f       // relative to faceSize
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, faceSize: Float, params: FaceParams) {
        if (params.cheekIntensity <= 0.01f) return

        val radius = faceSize * CHEEK_RADIUS
        val cheekY = cy + faceSize * CHEEK_Y_OFFSET
        val xOff = faceSize * CHEEK_X_OFFSET

        val baseAlpha = ((params.cheekColor ushr 24) and 0xFF) * params.cheekIntensity
        val centerColor = (baseAlpha.toInt().coerceIn(0, 255) shl 24) or
                (params.cheekColor and 0x00FFFFFF)
        val edgeColor = 0x00000000

        // Left cheek
        cheekPaint.shader = RadialGradient(
            cx - xOff, cheekY, radius,
            centerColor, edgeColor,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx - xOff, cheekY, radius, cheekPaint)

        // Right cheek
        cheekPaint.shader = RadialGradient(
            cx + xOff, cheekY, radius,
            centerColor, edgeColor,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx + xOff, cheekY, radius, cheekPaint)
    }
}
