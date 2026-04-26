package com.openclaw.clawface.rendering

import android.graphics.*
import com.openclaw.clawface.state.FaceParams
import com.openclaw.clawface.state.GhostTheme
import kotlin.math.sqrt

/**
 * Draws the ghost body with multi-layered radial gradients, matching the
 * design mockup's frosted translucent aesthetic.
 *
 * Rendering layers (bottom to top):
 *  1. Base radial gradient (3-stop, center bright → edge tinted)
 *  2. TintA radial splash (lower-left, themed color)
 *  3. TintB radial splash (upper-right, themed color)
 *  4. Shine highlight (top-center, white)
 *  5. Bottom fade (vertical linear gradient, subtle white at bottom)
 *  6. Grain texture (repeating noise bitmap, very subtle)
 *  7. Border stroke (semi-transparent white)
 *
 * Wave hands are rendered as separate ellipses with body+tint fill.
 */
class BodyRenderer {

    private val bodyPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xB3FFFFFF.toInt()  // rgba(255,255,255,0.7)
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Cached grain noise bitmap (generated once, reused)
    private var grainBitmap: Bitmap? = null
    private var grainShader: BitmapShader? = null

    fun draw(
        canvas: Canvas,
        cx: Float, cy: Float,
        viewW: Float, viewH: Float,
        params: FaceParams,
        alpha: Float,
    ) {
        val theme = GhostTheme.fromName(params.theme)

        buildBodyPath(cx, cy, viewW, viewH, params)
        ensureGrainBitmap()

        // Bounding box for gradient coordinate mapping
        val bodyHalfW = viewW * 0.38f
        val topY = cy - viewH * 0.38f
        val bottomY = cy + viewH * 0.36f
        val left = cx - bodyHalfW
        val right = cx + bodyHalfW
        val bodyW = right - left
        val bodyH = bottomY - topY
        val diagonal = sqrt(bodyW * bodyW + bodyH * bodyH)

        // --- Wave hands (behind body) ---
        drawWaveHands(canvas, cx, cy, viewW, viewH, params, theme, alpha, left, bodyW, bodyH, topY, diagonal)

        // --- Body layers clipped to ghost shape ---
        canvas.save()
        canvas.clipPath(bodyPath)

        // Overall ghost opacity (matching design's 0.95)
        val layerAlpha = alpha * 0.95f

        // Layer 1: Base radial gradient (cx=50%, cy=35%, r=75%)
        val baseCx = left + bodyW * 0.50f
        val baseCy = topY + bodyH * 0.35f
        val baseR = diagonal * 0.75f
        paint.shader = RadialGradient(
            baseCx, baseCy, baseR,
            intArrayOf(
                applyAlpha(theme.baseStops[0], layerAlpha),
                applyAlpha(theme.baseStops[1], layerAlpha),
                applyAlpha(theme.baseStops[2], layerAlpha),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(bodyPath, paint)

        // Layer 2: TintA radial splash (lower-left accent)
        drawTintLayer(canvas, theme.tintA, left, topY, bodyW, bodyH, diagonal, layerAlpha)

        // Layer 3: TintB radial splash (upper-right accent)
        drawTintLayer(canvas, theme.tintB, left, topY, bodyW, bodyH, diagonal, layerAlpha)

        // Layer 4: Shine highlight (top-center, white glow)
        val shineCx = left + bodyW * 0.40f
        val shineCy = topY + bodyH * 0.22f
        val shineR = diagonal * 0.32f
        paint.shader = RadialGradient(
            shineCx, shineCy, shineR,
            intArrayOf(
                applyAlpha(0xF2FFFFFF.toInt(), layerAlpha),  // 95% white
                0x00FFFFFF,                                   // transparent
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(bodyPath, paint)

        // Layer 5: Bottom fade (vertical gradient, subtle white at bottom edge)
        paint.shader = LinearGradient(
            cx, topY, cx, bottomY,
            intArrayOf(
                0x00FFFFFF,
                0x00FFFFFF,
                applyAlpha(0x38FFFFFF, layerAlpha),  // ~22% white at bottom
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(bodyPath, paint)

        // Layer 6: Grain texture (very subtle noise overlay)
        grainShader?.let { shader ->
            paint.shader = shader
            paint.alpha = (layerAlpha * 0.15f * 255f).toInt().coerceIn(0, 255)
            canvas.drawPath(bodyPath, paint)
            paint.alpha = 255
        }

        canvas.restore()

        // Layer 7: Border stroke (on top, not clipped)
        borderPaint.strokeWidth = viewW * 0.012f
        borderPaint.alpha = (alpha * 0.7f * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(bodyPath, borderPaint)
    }

    private fun drawTintLayer(
        canvas: Canvas,
        tint: GhostTheme.RadialTint,
        left: Float, topY: Float,
        bodyW: Float, bodyH: Float,
        diagonal: Float,
        layerAlpha: Float,
    ) {
        val tCx = left + bodyW * tint.cx
        val tCy = topY + bodyH * tint.cy
        val tR = diagonal * tint.r
        val centerAlpha = (tint.opacity * layerAlpha * 255f).toInt().coerceIn(0, 255)
        val centerColor = (centerAlpha shl 24) or (tint.color and 0x00FFFFFF)
        val edgeAlpha = (tint.opacity * layerAlpha * 0f * 255f).toInt()  // fade to transparent
        val edgeColor = (edgeAlpha shl 24) or (tint.color and 0x00FFFFFF)

        paint.shader = RadialGradient(
            tCx, tCy, tR,
            intArrayOf(centerColor, edgeColor),
            floatArrayOf(0f, 0.7f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(bodyPath, paint)
    }

    /**
     * Draw wave hands as independent ellipses (matching Ghost.jsx's wave ellipses).
     * Left hand gets tintA coloring, right hand gets tintB coloring.
     */
    private fun drawWaveHands(
        canvas: Canvas,
        cx: Float, cy: Float,
        viewW: Float, viewH: Float,
        params: FaceParams,
        theme: GhostTheme,
        alpha: Float,
        bodyLeft: Float, bodyW: Float, bodyH: Float,
        bodyTop: Float, diagonal: Float,
    ) {
        val handAlpha = alpha * 0.9f
        val bodyHalfW = viewW * 0.38f

        // Hand size relative to body (from design: rx=22, ry=17 in 200-viewBox → ~11% width)
        val handRx = viewW * 0.11f
        val handRy = viewH * 0.085f
        val armY = cy + viewH * 0.06f  // slightly below center

        // Left hand — rotated -28 degrees
        val leftHandCx = cx - bodyHalfW
        val leftHandCy = armY
        val leftArmRotation = -28f + params.armLeftAngle * 0.5f

        canvas.save()
        canvas.rotate(leftArmRotation, leftHandCx, leftHandCy)

        // Base gradient
        val baseCx = bodyLeft + bodyW * 0.50f
        val baseCy = bodyTop + bodyH * 0.35f
        val baseR = diagonal * 0.75f
        wavePaint.shader = RadialGradient(
            baseCx, baseCy, baseR,
            intArrayOf(
                applyAlpha(theme.baseStops[0], handAlpha),
                applyAlpha(theme.baseStops[1], handAlpha),
                applyAlpha(theme.baseStops[2], handAlpha),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(
            leftHandCx - handRx, leftHandCy - handRy,
            leftHandCx + handRx, leftHandCy + handRy,
            wavePaint,
        )

        // TintA overlay on left hand
        val tintACenterAlpha = (theme.tintA.opacity * handAlpha * 0.7f * 255f).toInt().coerceIn(0, 255)
        wavePaint.shader = null
        wavePaint.color = (tintACenterAlpha shl 24) or (theme.tintA.color and 0x00FFFFFF)
        canvas.drawOval(
            leftHandCx - handRx, leftHandCy - handRy,
            leftHandCx + handRx, leftHandCy + handRy,
            wavePaint,
        )
        canvas.restore()

        // Right hand — rotated +28 degrees
        val rightHandCx = cx + bodyHalfW
        val rightHandCy = armY
        val rightArmRotation = 28f - params.armRightAngle * 0.5f

        canvas.save()
        canvas.rotate(rightArmRotation, rightHandCx, rightHandCy)

        wavePaint.shader = RadialGradient(
            baseCx, baseCy, baseR,
            intArrayOf(
                applyAlpha(theme.baseStops[0], handAlpha),
                applyAlpha(theme.baseStops[1], handAlpha),
                applyAlpha(theme.baseStops[2], handAlpha),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(
            rightHandCx - handRx, rightHandCy - handRy,
            rightHandCx + handRx, rightHandCy + handRy,
            wavePaint,
        )

        // TintB overlay on right hand
        val tintBCenterAlpha = (theme.tintB.opacity * handAlpha * 0.8f * 255f).toInt().coerceIn(0, 255)
        wavePaint.shader = null
        wavePaint.color = (tintBCenterAlpha shl 24) or (theme.tintB.color and 0x00FFFFFF)
        canvas.drawOval(
            rightHandCx - handRx, rightHandCy - handRy,
            rightHandCx + handRx, rightHandCy + handRy,
            wavePaint,
        )
        canvas.restore()
    }

    /**
     * Build the ghost silhouette path (clockwise from top-center).
     * Shape: rounded dome top -> right side with arm bump -> wavy bottom -> left side with arm bump -> close.
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
        val armY = cy + viewH * 0.0f

        val rightArmRaise = params.armRightAngle / 30f
        val leftArmRaise = params.armLeftAngle / 30f

        val armBulgeX = viewW * 0.07f
        val wobbleAmp = viewH * 0.035f * (1f + params.bodyWobble * 0.5f)

        bodyPath.moveTo(cx, topY)

        bodyPath.cubicTo(
            cx + bodyHalfW * 0.65f, topY,
            cx + bodyHalfW, topY + viewH * 0.16f,
            cx + bodyHalfW, armY,
        )

        val rArmTopY = armY - viewH * 0.03f * rightArmRaise
        val rArmBotY = armY + viewH * 0.12f
        bodyPath.cubicTo(
            cx + bodyHalfW + armBulgeX, rArmTopY + viewH * 0.03f,
            cx + bodyHalfW + armBulgeX, rArmTopY + viewH * 0.09f,
            cx + bodyHalfW, rArmBotY,
        )

        bodyPath.cubicTo(
            cx + bodyHalfW * 0.95f, bottomY - viewH * 0.06f,
            cx + bodyHalfW * 0.80f, bottomY,
            cx + bodyHalfW * 0.55f, bottomY,
        )

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

        bodyPath.cubicTo(
            cx - bodyHalfW * 0.80f, bottomY,
            cx - bodyHalfW * 0.95f, bottomY - viewH * 0.06f,
            cx - bodyHalfW, armY + viewH * 0.12f,
        )

        val lArmTopY = armY - viewH * 0.03f * leftArmRaise
        bodyPath.cubicTo(
            cx - bodyHalfW - armBulgeX, lArmTopY + viewH * 0.09f,
            cx - bodyHalfW - armBulgeX, lArmTopY + viewH * 0.03f,
            cx - bodyHalfW, armY,
        )

        bodyPath.cubicTo(
            cx - bodyHalfW, topY + viewH * 0.16f,
            cx - bodyHalfW * 0.65f, topY,
            cx, topY,
        )

        bodyPath.close()
    }

    /** Lazily create a small noise bitmap for grain texture effect. */
    private fun ensureGrainBitmap() {
        if (grainBitmap != null) return
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rng = java.util.Random(42)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = rng.nextInt(256)
                bmp.setPixel(x, y, Color.argb(55, v, v, v))  // subtle white noise
            }
        }
        grainBitmap = bmp
        grainShader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    companion object {
        fun applyAlpha(color: Int, alpha: Float): Int {
            val a = (((color ushr 24) and 0xFF) * alpha).toInt().coerceIn(0, 255)
            return (a shl 24) or (color and 0x00FFFFFF)
        }
    }
}
