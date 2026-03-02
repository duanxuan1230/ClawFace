package com.openclaw.clawface.rendering

import android.graphics.*
import com.openclaw.clawface.state.FaceParams

/**
 * Draws the ghost body silhouette with frosted glass effect.
 * The body shape is a kawaii ghost: rounded dome top, small arm bumps,
 * wavy scalloped bottom edge. All glass morphism layers are clipped
 * to this shape.
 */
class BodyRenderer {

    private val bodyPath = Path()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun draw(
        canvas: Canvas,
        cx: Float, cy: Float,
        viewW: Float, viewH: Float,
        params: FaceParams,
        alpha: Float,
    ) {
        buildBodyPath(cx, cy, viewW, viewH, params)

        // --- Frosted glass layers clipped to body shape ---
        canvas.save()
        canvas.clipPath(bodyPath)

        // Layer 1: Semi-transparent white base
        basePaint.color = applyAlpha(0x40FFFFFF, alpha)
        basePaint.shader = null
        canvas.drawPath(bodyPath, basePaint)

        // Layer 2: Emotion color tint
        val tintColor = (params.color and 0x00FFFFFF) or 0x25000000
        tintPaint.color = applyAlpha(tintColor, alpha)
        tintPaint.shader = null
        canvas.drawPath(bodyPath, tintPaint)

        // Layer 3: Diagonal highlight gradient (top-left bright → bottom-right transparent)
        highlightPaint.shader = LinearGradient(
            cx - viewW * 0.35f, cy - viewH * 0.35f,
            cx + viewW * 0.35f, cy + viewH * 0.35f,
            applyAlpha(0x35FFFFFF, alpha),
            0x00000000,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(bodyPath, highlightPaint)

        canvas.restore()

        // Layer 4: Thin border outline (outside clip so shadow is visible)
        borderPaint.color = applyAlpha(0x44FFFFFF, alpha)
        borderPaint.strokeWidth = viewW * 0.007f
        borderPaint.setShadowLayer(viewW * 0.02f, 0f, 0f, applyAlpha(0x22FFFFFF, alpha))
        canvas.drawPath(bodyPath, borderPaint)
    }

    /**
     * Build the ghost silhouette path (clockwise from top-center).
     *
     * Shape: rounded dome top → right side with arm bump → wavy bottom → left side with arm bump → close.
     */
    private fun buildBodyPath(
        cx: Float, cy: Float,
        viewW: Float, viewH: Float,
        params: FaceParams,
    ) {
        bodyPath.rewind()

        val bodyHalfW = viewW * 0.38f
        val topY = cy - viewH * 0.38f
        val bottomY = cy + viewH * 0.36f
        val armY = cy + viewH * 0.0f   // arm level at center

        // Arm angle offsets: positive = raise up
        val rightArmRaise = params.armRightAngle / 30f  // normalize to -1..1
        val leftArmRaise = params.armLeftAngle / 30f

        val armBulgeX = viewW * 0.07f
        val wobbleAmp = viewH * 0.035f * (1f + params.bodyWobble * 0.5f)

        // --- Start at top center ---
        bodyPath.moveTo(cx, topY)

        // --- Top-right dome ---
        bodyPath.cubicTo(
            cx + bodyHalfW * 0.65f, topY,
            cx + bodyHalfW, topY + viewH * 0.16f,
            cx + bodyHalfW, armY,
        )

        // --- Right arm bump ---
        val rArmTopY = armY - viewH * 0.03f * rightArmRaise
        val rArmBotY = armY + viewH * 0.12f
        bodyPath.cubicTo(
            cx + bodyHalfW + armBulgeX, rArmTopY + viewH * 0.03f,
            cx + bodyHalfW + armBulgeX, rArmTopY + viewH * 0.09f,
            cx + bodyHalfW, rArmBotY,
        )

        // --- Right side down to bottom ---
        bodyPath.cubicTo(
            cx + bodyHalfW * 0.95f, bottomY - viewH * 0.06f,
            cx + bodyHalfW * 0.80f, bottomY,
            cx + bodyHalfW * 0.55f, bottomY,
        )

        // --- Bottom wavy edge (3 scallops) ---
        bodyPath.quadTo(
            cx + bodyHalfW * 0.30f, bottomY - wobbleAmp,
            cx + bodyHalfW * 0.10f, bottomY,
        )
        bodyPath.quadTo(
            cx, bottomY + wobbleAmp,
            cx - bodyHalfW * 0.10f, bottomY,
        )
        bodyPath.quadTo(
            cx - bodyHalfW * 0.30f, bottomY - wobbleAmp,
            cx - bodyHalfW * 0.55f, bottomY,
        )

        // --- Left side up from bottom ---
        bodyPath.cubicTo(
            cx - bodyHalfW * 0.80f, bottomY,
            cx - bodyHalfW * 0.95f, bottomY - viewH * 0.06f,
            cx - bodyHalfW, armY + viewH * 0.12f,
        )

        // --- Left arm bump ---
        val lArmTopY = armY - viewH * 0.03f * leftArmRaise
        bodyPath.cubicTo(
            cx - bodyHalfW - armBulgeX, lArmTopY + viewH * 0.09f,
            cx - bodyHalfW - armBulgeX, lArmTopY + viewH * 0.03f,
            cx - bodyHalfW, armY,
        )

        // --- Left side up to top ---
        bodyPath.cubicTo(
            cx - bodyHalfW, topY + viewH * 0.16f,
            cx - bodyHalfW * 0.65f, topY,
            cx, topY,
        )

        bodyPath.close()
    }

    companion object {
        private fun applyAlpha(color: Int, alpha: Float): Int {
            val a = (((color ushr 24) and 0xFF) * alpha).toInt().coerceIn(0, 255)
            return (a shl 24) or (color and 0x00FFFFFF)
        }
    }
}
