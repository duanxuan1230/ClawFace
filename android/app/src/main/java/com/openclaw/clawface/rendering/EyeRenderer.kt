package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.openclaw.clawface.state.FaceParams
import com.openclaw.clawface.state.SquintType

/**
 * Kawaii dot-eye renderer.
 * Eyes are solid dark circles with dual white highlight dots,
 * giving the classic cute ghost look from the concept image.
 */
class EyeRenderer {

    private val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF111122.toInt()
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val clipPath = Path()
    private val eyeRect = RectF()

    companion object {
        private const val BASE_EYE_RADIUS = 0.10f     // circle radius (unified X/Y)
        private const val EYE_SPACING = 0.30f           // closer together for cute look
        private const val EYE_Y_OFFSET = -0.06f         // above center

        // Large highlight: top-left
        private const val HL_LARGE_RATIO = 0.30f
        private const val HL_LARGE_OFF_X = -0.25f
        private const val HL_LARGE_OFF_Y = -0.25f

        // Small highlight: bottom-right
        private const val HL_SMALL_RATIO = 0.12f
        private const val HL_SMALL_OFF_X = 0.20f
        private const val HL_SMALL_OFF_Y = 0.25f
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
        val ry = r * params.eyeScaleY  // vertical scale for blink

        if (ry < 0.5f) return

        canvas.save()

        // Tilt rotation (mirrored for left eye)
        val tilt = if (isLeft) -params.eyeTilt else params.eyeTilt
        canvas.rotate(tilt, cx, cy)

        // Squint clipping
        if (params.eyeSquint > 0f && params.squintType != SquintType.NONE) {
            applySquintClip(canvas, cx, cy, r, ry, params.eyeSquint, params.squintType)
        }

        // Ambient glow behind eye (emotion-colored soft halo)
        eyeGlowPaint.color = params.glowColor
        eyeGlowPaint.setShadowLayer(faceSize * 0.04f, 0f, 0f, params.glowColor)
        eyeRect.set(cx - r, cy - ry, cx + r, cy + ry)
        canvas.drawOval(eyeRect, eyeGlowPaint)

        // Solid dark eye fill
        eyePaint.color = 0xFF111122.toInt()
        eyePaint.setShadowLayer(0f, 0f, 0f, 0)
        canvas.drawOval(eyeRect, eyePaint)

        // Large highlight (top-left, bright white)
        val hlR1 = r * HL_LARGE_RATIO
        val hlX1 = cx + r * HL_LARGE_OFF_X
        val hlY1 = cy + ry * HL_LARGE_OFF_Y
        highlightPaint.color = 0xEEFFFFFF.toInt()
        canvas.drawCircle(hlX1, hlY1, hlR1, highlightPaint)

        // Small highlight (bottom-right, dimmer)
        val hlR2 = r * HL_SMALL_RATIO
        val hlX2 = cx + r * HL_SMALL_OFF_X
        val hlY2 = cy + ry * HL_SMALL_OFF_Y
        highlightPaint.color = 0x99FFFFFF.toInt()
        canvas.drawCircle(hlX2, hlY2, hlR2, highlightPaint)

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
            SquintType.NONE -> return
        }
        canvas.clipPath(clipPath)
    }
}
