package com.openclaw.clawface.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.openclaw.clawface.state.FaceParams

/**
 * Expressive mouth renderer — vibrant red bezier curves with tongue detail.
 *
 * Matches the design mockup:
 *  - Closed: red curved stroke (quadratic bezier)
 *  - Open: filled bezier path (#ff3d60) + inner tongue (#ff1f44, 55% opacity)
 *  - mouthCurve controls smile/frown, mouthWidth controls horizontal span,
 *    mouthOpen controls vertical opening
 */
class MouthRenderer {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val mouthPath = Path()
    private val tonguePath = Path()

    companion object {
        private const val MOUTH_Y_OFFSET = 0.16f
        private const val MAX_MOUTH_WIDTH = 0.19f      // larger than before (was 0.10)
        private const val MAX_CURVE_HEIGHT = 0.05f      // more expressive curve
        private const val MAX_OPEN_HEIGHT = 0.10f       // taller opening (was 0.06)

        private val MOUTH_COLOR = 0xFFFF3D60.toInt()    // vibrant red
        private val TONGUE_COLOR = 0x8CFF1F44.toInt()   // darker red, 55% opacity
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

        val leftX = centerX - halfWidth
        val rightX = centerX + halfWidth

        // Upper lip curve control point: positive mouthCurve = smile (curve upward)
        val curveHeight = faceSize * MAX_CURVE_HEIGHT * params.mouthCurve
        val topMidY = mouthY - curveHeight * halfWidth / (faceSize * MAX_MOUTH_WIDTH) * 2f

        mouthPath.rewind()

        if (params.mouthOpen <= 0.05f) {
            // Closed mouth: red curved stroke
            mouthPath.moveTo(leftX, mouthY)
            mouthPath.quadTo(centerX, topMidY, rightX, mouthY)

            strokePaint.color = MOUTH_COLOR
            strokePaint.strokeWidth = faceSize * 0.02f
            canvas.drawPath(mouthPath, strokePaint)
        } else {
            // Open mouth: bezier path with tongue
            val openH = faceSize * MAX_OPEN_HEIGHT * params.mouthOpen
            val botY = mouthY + openH

            // Upper lip: left → (curve via center control) → right
            // Lower lip: right → (curve via bottom center) → left → close
            mouthPath.moveTo(leftX, mouthY)
            mouthPath.quadTo(centerX, topMidY, rightX, mouthY)
            mouthPath.quadTo(centerX, botY, leftX, mouthY)
            mouthPath.close()

            fillPaint.color = MOUTH_COLOR
            canvas.drawPath(mouthPath, fillPaint)

            // Tongue: slightly inset inner shape
            if (openH > faceSize * 0.01f) {
                tonguePath.rewind()
                val inset = faceSize * 0.015f
                tonguePath.moveTo(leftX + inset, mouthY + faceSize * 0.005f)
                tonguePath.quadTo(centerX, botY - inset * 0.5f, rightX - inset, mouthY + faceSize * 0.005f)
                tonguePath.quadTo(centerX, mouthY + openH * 0.35f, leftX + inset, mouthY + faceSize * 0.005f)
                tonguePath.close()

                tonguePaint.color = TONGUE_COLOR
                canvas.drawPath(tonguePath, tonguePaint)
            }
        }
    }
}
