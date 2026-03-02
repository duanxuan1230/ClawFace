package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.openclaw.clawface.state.FaceParams

/**
 * Kawaii mouth renderer — small, cute, dark-filled.
 *
 * Closed: tiny dark curved stroke.
 * Open: small dark rounded oval/rect (the "O" mouth).
 */
class MouthRenderer {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val mouthPath = Path()
    private val mouthRect = RectF()

    companion object {
        private const val MOUTH_Y_OFFSET = 0.16f     // below face center
        private const val MAX_MOUTH_WIDTH = 0.10f     // much smaller than before (was 0.30)
        private const val MAX_CURVE_HEIGHT = 0.04f    // subtler curve (was 0.12)
        private const val MAX_OPEN_HEIGHT = 0.06f     // smaller opening (was 0.10)
        private const val MOUTH_DARK_COLOR = 0xFF222233.toInt()
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

        val curveOffset = faceSize * MAX_CURVE_HEIGHT * params.mouthCurve

        val leftX = centerX - halfWidth
        val rightX = centerX + halfWidth

        mouthPath.rewind()

        if (params.mouthOpen <= 0.01f) {
            drawClosedMouth(canvas, leftX, rightX, mouthY, curveOffset, faceSize)
        } else {
            drawOpenMouth(canvas, centerX, mouthY, halfWidth, faceSize, params)
        }
    }

    private fun drawClosedMouth(
        canvas: Canvas,
        leftX: Float, rightX: Float,
        mouthY: Float,
        curveOffset: Float,
        faceSize: Float,
    ) {
        mouthPath.moveTo(leftX, mouthY)
        mouthPath.quadTo(
            (leftX + rightX) / 2f,
            mouthY + curveOffset,
            rightX,
            mouthY,
        )

        strokePaint.color = MOUTH_DARK_COLOR
        strokePaint.strokeWidth = faceSize * 0.015f
        strokePaint.setShadowLayer(0f, 0f, 0f, 0)
        canvas.drawPath(mouthPath, strokePaint)
    }

    private fun drawOpenMouth(
        canvas: Canvas,
        centerX: Float,
        mouthY: Float,
        halfWidth: Float,
        faceSize: Float,
        params: FaceParams,
    ) {
        val openH = faceSize * MAX_OPEN_HEIGHT * params.mouthOpen
        val w = halfWidth.coerceAtLeast(faceSize * 0.03f)

        // Small dark rounded oval
        mouthRect.set(
            centerX - w,
            mouthY - openH * 0.2f,
            centerX + w,
            mouthY + openH,
        )
        val cornerR = w * 0.8f

        fillPaint.color = MOUTH_DARK_COLOR
        fillPaint.setShadowLayer(0f, 0f, 0f, 0)
        canvas.drawRoundRect(mouthRect, cornerR, cornerR, fillPaint)
    }
}
