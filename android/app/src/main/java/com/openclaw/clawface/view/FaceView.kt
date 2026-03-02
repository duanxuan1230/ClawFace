package com.openclaw.clawface.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.openclaw.clawface.config.AppConfig
import com.openclaw.clawface.rendering.BodyRenderer
import com.openclaw.clawface.rendering.CheekRenderer
import com.openclaw.clawface.rendering.EyeRenderer
import com.openclaw.clawface.rendering.GlassCardRenderer
import com.openclaw.clawface.rendering.GlowEffect
import com.openclaw.clawface.rendering.MouthRenderer
import com.openclaw.clawface.state.AnimationOffset
import com.openclaw.clawface.state.FaceMode
import com.openclaw.clawface.state.FaceParams

/**
 * Core rendering view for ClawFace.
 * Draws a kawaii ghost character with frosted glass body, cheeks, dot-eyes, and tiny mouth.
 * Uses software rendering layer to ensure ShadowLayer glow effects work correctly.
 */
class FaceView(context: Context) : View(context) {

    private val eyeRenderer = EyeRenderer()
    private val mouthRenderer = MouthRenderer()
    private val glassCardRenderer = GlassCardRenderer()
    private val bodyRenderer = BodyRenderer()
    private val cheekRenderer = CheekRenderer()

    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    var faceParams: FaceParams = FaceParams()
        set(value) {
            field = value
            invalidate()
        }

    var animationOffset: AnimationOffset = AnimationOffset()
        set(value) {
            field = value
            invalidate()
        }

    var faceMode: FaceMode = FaceMode.ACTIVE
        set(value) {
            field = value
            invalidate()
        }

    private var faceWidthPx: Float = 0f
    private var faceHeightPx: Float = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        faceWidthPx = w.toFloat()
        faceHeightPx = h.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        if (faceWidthPx <= 0f || faceHeightPx <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val offset = animationOffset
        val faceSize = minOf(faceWidthPx, faceHeightPx)
        val cornerRadius = faceWidthPx * AppConfig.GLASS_CORNER_RADIUS_RATIO

        canvas.save()

        // Apply animation transforms
        canvas.translate(offset.offsetX, offset.offsetY)
        canvas.rotate(offset.rotation, cx, cy)
        canvas.scale(offset.scaleX, offset.scaleY, cx, cy)

        // 1. Ambient glow (behind everything)
        GlowEffect.drawAmbientGlow(
            canvas, cx, cy, faceWidthPx, faceHeightPx, cornerRadius,
            faceParams.color, faceParams.glowColor, offset.alpha,
        )

        // 2. Background glass card (subtle, behind body)
        glassCardRenderer.draw(
            canvas, faceWidthPx, faceHeightPx, cornerRadius,
            faceParams.color, offset.alpha,
        )

        // 3. Ghost body (frosted glass clipped to ghost shape)
        bodyRenderer.draw(
            canvas, cx, cy, faceWidthPx, faceHeightPx,
            faceParams, offset.alpha,
        )

        // 4. Cheek blush (on top of body, under eyes)
        cheekRenderer.draw(canvas, cx, cy, faceSize, faceParams)

        // 5. Eyes (kawaii dot style)
        eyeRenderer.draw(canvas, cx, cy, faceSize, faceParams)

        // 6. Mouth (small cute style)
        mouthRenderer.draw(canvas, cx, cy, faceSize, faceParams)

        // 7. "Zzz" for offline state
        if (faceMode == FaceMode.OFFLINE) {
            drawZzz(canvas, cx, cy, faceSize)
        }

        canvas.restore()
    }

    private fun drawZzz(canvas: Canvas, cx: Float, cy: Float, faceSize: Float) {
        val t = SystemClock.elapsedRealtime() / 1000f
        val bobY = kotlin.math.sin(t * 1.5) * faceSize * 0.02f

        zzzPaint.textSize = faceSize * 0.1f
        zzzPaint.color = 0xAABBBBBB.toInt()
        zzzPaint.setShadowLayer(faceSize * 0.03f, 0f, 0f, 0x88AAAAAA.toInt())

        val zzzX = cx + faceSize * 0.25f
        val zzzY = cy - faceSize * 0.2f + bobY.toFloat()
        canvas.drawText("Zzz", zzzX, zzzY, zzzPaint)
    }
}
