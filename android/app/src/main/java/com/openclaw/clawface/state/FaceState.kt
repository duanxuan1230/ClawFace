package com.openclaw.clawface.state

/**
 * Core face rendering parameters. All visual output is derived from this.
 * Designed for zero-allocation Lerp interpolation between states.
 */
data class FaceParams(
    val color: Int = 0xAABBCCDD.toInt(),
    val glowColor: Int = 0x88DDEEFF.toInt(),
    val eyeScaleY: Float = 1.0f,
    val eyeTilt: Float = 0f,
    val eyeSquint: Float = 0f,
    val squintType: SquintType = SquintType.NONE,
    val pupilOffsetX: Float = 0f,
    val pupilOffsetY: Float = 0f,
    val pupilScale: Float = 1.0f,
    val mouthCurve: Float = 0f,
    val mouthWidth: Float = 0.5f,
    val mouthOpen: Float = 0f,
    val mouthVisible: Boolean = true,
    // Ghost body parameters
    val cheekIntensity: Float = 0.6f,
    val cheekColor: Int = 0xFFFF8FAA.toInt(),
    val armLeftAngle: Float = 0f,
    val armRightAngle: Float = 0f,
    val bodySquish: Float = 0f,
    val bodyWobble: Float = 0f,
) {
    companion object {
        fun lerp(from: FaceParams, to: FaceParams, t: Float): FaceParams {
            val f = t.coerceIn(0f, 1f)
            return FaceParams(
                color = lerpColor(from.color, to.color, f),
                glowColor = lerpColor(from.glowColor, to.glowColor, f),
                eyeScaleY = lerp(from.eyeScaleY, to.eyeScaleY, f),
                eyeTilt = lerp(from.eyeTilt, to.eyeTilt, f),
                eyeSquint = lerp(from.eyeSquint, to.eyeSquint, f),
                squintType = if (f < 0.5f) from.squintType else to.squintType,
                pupilOffsetX = lerp(from.pupilOffsetX, to.pupilOffsetX, f),
                pupilOffsetY = lerp(from.pupilOffsetY, to.pupilOffsetY, f),
                pupilScale = lerp(from.pupilScale, to.pupilScale, f),
                mouthCurve = lerp(from.mouthCurve, to.mouthCurve, f),
                mouthWidth = lerp(from.mouthWidth, to.mouthWidth, f),
                mouthOpen = lerp(from.mouthOpen, to.mouthOpen, f),
                mouthVisible = if (f < 0.5f) from.mouthVisible else to.mouthVisible,
                cheekIntensity = lerp(from.cheekIntensity, to.cheekIntensity, f),
                cheekColor = lerpColor(from.cheekColor, to.cheekColor, f),
                armLeftAngle = lerp(from.armLeftAngle, to.armLeftAngle, f),
                armRightAngle = lerp(from.armRightAngle, to.armRightAngle, f),
                bodySquish = lerp(from.bodySquish, to.bodySquish, f),
                bodyWobble = lerp(from.bodyWobble, to.bodyWobble, f),
            )
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        private fun lerpColor(from: Int, to: Int, t: Float): Int {
            val fa = (from shr 24) and 0xFF
            val fr = (from shr 16) and 0xFF
            val fg = (from shr 8) and 0xFF
            val fb = from and 0xFF
            val ta = (to shr 24) and 0xFF
            val tr = (to shr 16) and 0xFF
            val tg = (to shr 8) and 0xFF
            val tb = to and 0xFF
            val a = (fa + (ta - fa) * t).toInt()
            val r = (fr + (tr - fr) * t).toInt()
            val g = (fg + (tg - fg) * t).toInt()
            val b = (fb + (tb - fb) * t).toInt()
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

enum class SquintType {
    NONE, TOP, BOTTOM
}

/** Animation offsets applied on top of face rendering (position, scale, rotation). */
data class AnimationOffset(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
)

/** High-level connection/activity state. */
enum class FaceMode {
    ACTIVE,
    STANDBY,
    THINKING,
    OFFLINE,
}

/** The 10 supported emotions. */
enum class Emotion {
    NEUTRAL,
    JOY,
    ANXIETY,
    ENVY,
    EMBARRASSMENT,
    ENNUI,
    DISGUST,
    FEAR,
    ANGER,
    SADNESS,
}
