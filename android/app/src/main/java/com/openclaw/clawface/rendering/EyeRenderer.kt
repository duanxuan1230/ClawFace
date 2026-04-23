package com.openclaw.clawface.rendering

import android.graphics.*
import com.openclaw.clawface.state.FaceParams
import com.openclaw.clawface.state.SquintType

/**
 * Kawaii dot-eye renderer — radial gradient fill with single white highlight.
 *
 * Matches the design mockup:
 *  - Dark radial gradient (#3a3550 → #0a0815) instead of flat solid
 *  - Single bright highlight dot (top-right, following pupil offset)
 *  - Squint clipping and tilt mechanics preserved
 */
class EyeRenderer {

    private val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xF5FFFFFF.toInt()  // 96% white
    }
    private val clipPath = Path()
    private val eyeRect = RectF()

    companion object {
        private const val BASE_EYE_RADIUS = 0.10f
        private const val EYE_SPACING = 0.30f
        private const val EYE_Y_OFFSET = -0.06f

        // Single highlight: slightly right of center, above midline
        private const val HL_RATIO = 0.22f
        private const val HL_OFF_X = 0.20f
        private const val HL_OFF_Y = -0.30f

        // Eye gradient colors (from design mockup)
        private val EYE_CENTER = 0xFF3A3550.toInt()
        private val EYE_EDGE = 0xFF0A0815.toInt()
    }

    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        faceSize: Float,
        params: FaceParams,
    ) {
        val eyeY = centerY + faceSize * EYE_Y_OFFSET
        val halfSpacing = faceSize * EYE_SPACING / 2f

        drawSingleEye(canvas, centerX - halfSpacing, eyeY, faceSize, params, isLeft = true)
        drawSingleEye(canvas, centerX + halfSpacing, eyeY, faceSize, params, isLeft = false)
    }

    private fun drawSingleEye(
        canvas: Canvas,
        cx: Float, cy: Float,
        faceSize: Float,
        params: FaceParams,
        isLeft: Boolean,
    ) {
        val r = faceSize * BASE_EYE_RADIUS
        val ry = r * params.eyeScaleY

        if (ry < 0.5f) return

        val ecx = cx + params.pupilOffsetX * faceSize * 0.018f
        val ecy = cy + params.pupilOffsetY * faceSize * 0.018f

        canvas.save()

        val tilt = if (isLeft) -params.eyeTilt else params.eyeTilt
        canvas.rotate(tilt, ecx, ecy)

        if (params.eyeSquint > 0f) {
            applySquintClip(canvas, ecx, ecy, r, ry, params.eyeSquint, params.squintType)
        }

        eyeRect.set(ecx - r, ecy - ry, ecx + r, ecy + ry)

        // Clip all eye drawing to the oval bounds (prevents overflow during close animation)
        clipPath.rewind()
        clipPath.addOval(eyeRect, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Ambient glow behind eye (emotion-colored soft halo)
        eyeGlowPaint.color = params.glowColor
        eyeGlowPaint.setShadowLayer(faceSize * 0.04f, 0f, 0f, params.glowColor)
        canvas.drawOval(eyeRect, eyeGlowPaint)

        // Dark radial gradient fill (matching design: cx=35%, cy=30%, r=70%)
        val gradCx = ecx - r * 0.15f   // offset gradient center to upper-left
        val gradCy = ecy - ry * 0.20f
        val gradR = r * 1.4f
        eyePaint.shader = RadialGradient(
            gradCx, gradCy, gradR,
            EYE_CENTER, EYE_EDGE,
            Shader.TileMode.CLAMP,
        )
        eyePaint.setShadowLayer(0f, 0f, 0f, 0)
        canvas.drawOval(eyeRect, eyePaint)

        // Single white highlight (follows pupil offset)
        val pupilOx = params.pupilOffsetX * r * 0.25f
        val hlR = r * HL_RATIO
        val hlX = ecx + r * HL_OFF_X + pupilOx
        val hlY = ecy + ry * HL_OFF_Y
        canvas.drawCircle(hlX, hlY, hlR, highlightPaint)

        canvas.restore()
    }

    private fun applySquintClip(
        canvas: Canvas,
        cx: Float, cy: Float,
        rx: Float, ry: Float,
        squintAmount: Float,
        squintType: SquintType,
    ) {
        clipPath.rewind()
        val margin = rx * 1.2f
        when (squintType) {
            SquintType.TOP -> {
                val cutY = cy - ry + (ry * 2f * squintAmount * 0.5f)
                clipPath.addRect(cx - margin, cutY, cx + margin, cy + ry + margin, Path.Direction.CW)
            }
            SquintType.BOTTOM -> {
                val cutY = cy + ry - (ry * 2f * squintAmount * 0.5f)
                clipPath.addRect(cx - margin, cy - ry - margin, cx + margin, cutY, Path.Direction.CW)
            }
            SquintType.NONE -> {
                // Symmetric squint: narrow from both top and bottom equally
                val inset = ry * squintAmount * 0.55f
                val topCut = cy - ry + inset
                val botCut = cy + ry - inset
                clipPath.addRect(cx - margin, topCut, cx + margin, botCut, Path.Direction.CW)
            }
        }
        canvas.clipPath(clipPath)
    }
}
