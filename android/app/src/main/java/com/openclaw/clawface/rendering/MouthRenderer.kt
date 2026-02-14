package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.openclaw.clawface.state.FaceParams

/**
 * Renders the mouth as a Bezier curve (closed or open) driven by curve/width/open parameters.
 *
 * When open=0: single stroke bezier line (closed mouth).
 * When open>0: two bezier curves forming a filled closed region (open mouth).
 */
class MouthRenderer {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val mouthPath = Path()

    companion object {
        private const val MOUTH_Y_OFFSET = 0.18f   // below face center
        private const val MAX_MOUTH_WIDTH = 0.30f   // relative to face size
        private const val MAX_CURVE_HEIGHT = 0.12f  // max bezier control point offset
        private const val MAX_OPEN_HEIGHT = 0.10f   // max vertical opening
    }

    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        faceSize: Float,
        params: FaceParams,
    ) {
        if (!params.mouthVisible) return

        val mouthY = centerY + faceSize * MOUTH_Y_OFFSET
        val halfWidth = faceSize * MAX_MOUTH_WIDTH * params.mouthWidth

        if (halfWidth < 1f) return

        // Control point Y offset for curve
        val curveOffset = faceSize * MAX_CURVE_HEIGHT * params.mouthCurve

        // Start and end points
        val leftX = centerX - halfWidth
        val rightX = centerX + halfWidth

        mouthPath.rewind()

        if (params.mouthOpen <= 0.01f) {
            // Closed mouth: single bezier stroke
            drawClosedMouth(canvas, leftX, rightX, mouthY, curveOffset, faceSize, params)
        } else {
            // Open mouth: filled region between upper and lower curves
            drawOpenMouth(canvas, leftX, rightX, mouthY, curveOffset, halfWidth, faceSize, params)
        }
    }

    private fun drawClosedMouth(
        canvas: Canvas,
        leftX: Float,
        rightX: Float,
        mouthY: Float,
        curveOffset: Float,
        faceSize: Float,
        params: FaceParams,
    ) {
        mouthPath.moveTo(leftX, mouthY)
        mouthPath.quadTo(
            (leftX + rightX) / 2f,
            mouthY + curveOffset,
            rightX,
            mouthY,
        )

        strokePaint.color = params.color
        strokePaint.strokeWidth = faceSize * 0.02f
        strokePaint.setShadowLayer(faceSize * 0.04f, 0f, 0f, params.glowColor)
        canvas.drawPath(mouthPath, strokePaint)
    }

    private fun drawOpenMouth(
        canvas: Canvas,
        leftX: Float,
        rightX: Float,
        mouthY: Float,
        curveOffset: Float,
        halfWidth: Float,
        faceSize: Float,
        params: FaceParams,
    ) {
        val openHeight = faceSize * MAX_OPEN_HEIGHT * params.mouthOpen
        val midX = (leftX + rightX) / 2f

        // Upper lip curve
        mouthPath.moveTo(leftX, mouthY)
        mouthPath.quadTo(midX, mouthY + curveOffset, rightX, mouthY)

        // Lower lip curve (closes the shape)
        // The lower curve goes back from right to left, displaced downward
        val lowerCurveY = mouthY + openHeight
        val lowerControlY = lowerCurveY + curveOffset * 0.5f
        mouthPath.quadTo(midX, lowerControlY, leftX, mouthY)

        mouthPath.close()

        // Fill the mouth area
        fillPaint.color = adjustAlpha(params.color, 0.7f)
        fillPaint.setShadowLayer(faceSize * 0.04f, 0f, 0f, params.glowColor)
        canvas.drawPath(mouthPath, fillPaint)

        // Stroke outline for definition
        strokePaint.color = params.color
        strokePaint.strokeWidth = faceSize * 0.015f
        strokePaint.setShadowLayer(faceSize * 0.04f, 0f, 0f, params.glowColor)
        canvas.drawPath(mouthPath, strokePaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = ((color shr 24) and 0xFF) * factor
        return (a.toInt() shl 24) or (color and 0x00FFFFFF)
    }
}
