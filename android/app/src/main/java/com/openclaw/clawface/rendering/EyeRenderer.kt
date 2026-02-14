package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.openclaw.clawface.state.FaceParams
import com.openclaw.clawface.state.SquintType

/**
 * Renders a single eye as a parametric ellipse with optional squint clipping and pupil.
 * All Paint/Path/RectF objects are pre-allocated to avoid GC in onDraw.
 */
class EyeRenderer {

    // Pre-allocated objects for zero-allocation rendering
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99FFFFFF.toInt()
    }
    private val clipPath = Path()
    private val eyePath = Path()
    private val eyeRect = RectF()

    companion object {
        private const val BASE_EYE_RADIUS_X = 0.15f  // relative to face size
        private const val BASE_EYE_RADIUS_Y = 0.15f
        private const val EYE_SPACING = 0.35f         // distance between eye centers
        private const val PUPIL_RADIUS_RATIO = 0.35f   // pupil size relative to eye
        private const val HIGHLIGHT_RADIUS_RATIO = 0.12f
        private const val HIGHLIGHT_OFFSET_X = -0.25f
        private const val HIGHLIGHT_OFFSET_Y = -0.25f
    }

    /**
     * Draw both eyes.
     * @param canvas Canvas to draw on
     * @param centerX face center X
     * @param centerY face center Y (eyes are slightly above center)
     * @param faceSize the overall face size in pixels
     * @param params current face parameters
     */
    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        faceSize: Float,
        params: FaceParams,
    ) {
        val eyeY = centerY - faceSize * 0.08f
        val halfSpacing = faceSize * EYE_SPACING / 2f

        // Left eye (tilt is mirrored)
        drawSingleEye(
            canvas,
            centerX - halfSpacing,
            eyeY,
            faceSize,
            params,
            isLeft = true,
        )

        // Right eye
        drawSingleEye(
            canvas,
            centerX + halfSpacing,
            eyeY,
            faceSize,
            params,
            isLeft = false,
        )
    }

    private fun drawSingleEye(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        faceSize: Float,
        params: FaceParams,
        isLeft: Boolean,
    ) {
        val rx = faceSize * BASE_EYE_RADIUS_X
        val ry = faceSize * BASE_EYE_RADIUS_Y * params.eyeScaleY

        // Skip if eye is fully closed
        if (ry < 0.5f) return

        canvas.save()

        // Apply tilt rotation (mirrored for left eye)
        val tilt = if (isLeft) -params.eyeTilt else params.eyeTilt
        canvas.rotate(tilt, cx, cy)

        // Apply squint clipping
        if (params.eyeSquint > 0f && params.squintType != SquintType.NONE) {
            applySquintClip(canvas, cx, cy, rx, ry, params.eyeSquint, params.squintType)
        }

        // Draw eye ellipse
        eyePaint.color = params.color
        eyePaint.setShadowLayer(faceSize * 0.06f, 0f, 0f, params.glowColor)
        eyeRect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(eyeRect, eyePaint)

        // Draw pupil
        drawPupil(canvas, cx, cy, rx, ry, faceSize, params)

        canvas.restore()
    }

    private fun applySquintClip(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        squintAmount: Float,
        squintType: SquintType,
    ) {
        clipPath.rewind()
        val margin = rx * 1.2f
        when (squintType) {
            SquintType.TOP -> {
                // Cut top portion: clip rect from (squint-adjusted top) to bottom
                val cutY = cy - ry + (ry * 2f * squintAmount * 0.5f)
                clipPath.addRect(cx - margin, cutY, cx + margin, cy + ry + margin, Path.Direction.CW)
            }
            SquintType.BOTTOM -> {
                // Cut bottom portion: clip rect from top to (squint-adjusted bottom)
                val cutY = cy + ry - (ry * 2f * squintAmount * 0.5f)
                clipPath.addRect(cx - margin, cy - ry - margin, cx + margin, cutY, Path.Direction.CW)
            }
            SquintType.NONE -> return
        }
        canvas.clipPath(clipPath)
    }

    private fun drawPupil(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        faceSize: Float,
        params: FaceParams,
    ) {
        val pupilRadius = minOf(rx, ry) * PUPIL_RADIUS_RATIO * params.pupilScale

        // Pupil position: offset within eye bounds
        val maxOffsetX = rx * 0.4f
        val maxOffsetY = ry * 0.4f
        val px = cx + params.pupilOffsetX * maxOffsetX
        val py = cy + params.pupilOffsetY * maxOffsetY

        // Pupil is darker / contrasting
        pupilPaint.color = 0xFF111122.toInt()
        canvas.drawCircle(px, py, pupilRadius, pupilPaint)

        // Small highlight dot for liveliness
        val hlRadius = minOf(rx, ry) * HIGHLIGHT_RADIUS_RATIO
        val hlX = px + pupilRadius * HIGHLIGHT_OFFSET_X
        val hlY = py + pupilRadius * HIGHLIGHT_OFFSET_Y
        canvas.drawCircle(hlX, hlY, hlRadius, highlightPaint)
    }
}
