package com.openclaw.clawface.state

/**
 * Predefined FaceParams for each of the 10 emotions.
 * Kawaii ghost style — soft pastels, rosy cheeks, expressive arms.
 */
object EmotionPresets {

    private val presets = mapOf(
        // Calm and collected, subtle hint of warmth
        Emotion.NEUTRAL to FaceParams(
            color = 0xBBCCDDEE.toInt(),
            glowColor = 0x88DDEEFF.toInt(),
            eyeScaleY = 1.0f,
            eyeTilt = 0f,
            eyeSquint = 0f,
            pupilScale = 1.0f,
            mouthCurve = 0.15f,
            mouthWidth = 0.5f,
            mouthOpen = 0f,
            cheekIntensity = 0.35f,
            cheekColor = 0xFFFF8FAA.toInt(),
            armLeftAngle = 0f,
            armRightAngle = 5f,
            bodyWobble = 0f,
        ),
        // Exuberant happiness — big eyes, huge grin, arms raised
        Emotion.JOY to FaceParams(
            color = 0xBBFFEE55.toInt(),
            glowColor = 0x88FFFF88.toInt(),
            eyeScaleY = 1.3f,
            eyeTilt = -5f,
            eyeSquint = 0.25f,
            squintType = SquintType.BOTTOM,
            pupilScale = 1.2f,
            mouthCurve = 1.2f,
            mouthWidth = 1.0f,
            mouthOpen = 0.75f,
            cheekIntensity = 1.0f,
            cheekColor = 0xFFFF88AA.toInt(),
            armLeftAngle = 25f,
            armRightAngle = 28f,
            bodyWobble = 0.5f,
        ),
        // Nervous dread — wide alarmed eyes, shaky body
        Emotion.ANXIETY to FaceParams(
            color = 0xBBFFAA55.toInt(),
            glowColor = 0x88FFCC77.toInt(),
            eyeScaleY = 1.45f,
            eyeTilt = -12f,
            eyeSquint = 0f,
            pupilScale = 0.65f,
            mouthCurve = -0.45f,
            mouthWidth = 0.45f,
            mouthOpen = 0.3f,
            cheekIntensity = 0.45f,
            cheekColor = 0xFFFFBBCC.toInt(),
            armLeftAngle = -15f,
            armRightAngle = -15f,
            bodyWobble = 0.9f,
        ),
        // Cold covetous stare — narrowed calculating gaze, thin pursed mouth
        Emotion.ENVY to FaceParams(
            color = 0xBB44CCCC.toInt(),
            glowColor = 0x8888EEEE.toInt(),
            eyeScaleY = 0.65f,
            eyeTilt = 8f,
            eyeSquint = 0.35f,
            squintType = SquintType.TOP,
            pupilScale = 1.3f,
            mouthCurve = 0.2f,
            mouthWidth = 0.25f,
            mouthOpen = 0f,
            cheekIntensity = 0.1f,
            cheekColor = 0xFFAADDCC.toInt(),
            armLeftAngle = 8f,
            armRightAngle = -12f,
            bodyWobble = 0.1f,
        ),
        // Frozen red-faced embarrassment — averted gaze, cherry blush
        Emotion.EMBARRASSMENT to FaceParams(
            color = 0xBBFFAABB.toInt(),
            glowColor = 0x88FFCCDD.toInt(),
            eyeScaleY = 0.7f,
            eyeTilt = -18f,
            eyeSquint = 0.2f,
            squintType = SquintType.BOTTOM,
            pupilScale = 0.85f,
            pupilOffsetX = 0f,
            pupilOffsetY = 0.25f,
            mouthCurve = -0.15f,
            mouthWidth = 0.2f,
            mouthOpen = 0f,
            cheekIntensity = 1.0f,
            cheekColor = 0xFFFF5577.toInt(),
            armLeftAngle = -18f,
            armRightAngle = -18f,
            bodyWobble = 0f,
        ),
        // Dead inside — barely-open slitted eyes, slack jaw, limp arms
        Emotion.ENNUI to FaceParams(
            color = 0xBB8888BB.toInt(),
            glowColor = 0x88AAAACC.toInt(),
            eyeScaleY = 0.28f,
            eyeTilt = 0f,
            eyeSquint = 0f,
            pupilScale = 1.0f,
            mouthCurve = -0.1f,
            mouthWidth = 0.5f,
            mouthOpen = 0.15f,
            cheekIntensity = 0.08f,
            cheekColor = 0xFFCCBBDD.toInt(),
            armLeftAngle = -12f,
            armRightAngle = -10f,
            bodyWobble = 0f,
        ),
        // Physical revulsion — asymmetric sneer, aggressive recoil
        Emotion.DISGUST to FaceParams(
            color = 0xBBAADD66.toInt(),
            glowColor = 0x88BBEE88.toInt(),
            eyeScaleY = 0.6f,
            eyeTilt = 10f,
            eyeSquint = 0.65f,
            squintType = SquintType.BOTTOM,
            pupilScale = 1.0f,
            mouthCurve = -1.0f,
            mouthWidth = 0.7f,
            mouthOpen = 0.35f,
            cheekIntensity = 0f,
            cheekColor = 0xFFCCDD88.toInt(),
            armLeftAngle = 12f,
            armRightAngle = -20f,
            bodyWobble = 0.25f,
        ),
        // Absolute terror — maximum eyes, scream mouth, body cowering
        Emotion.FEAR to FaceParams(
            color = 0xBBCC99EE.toInt(),
            glowColor = 0x88DDBBFF.toInt(),
            eyeScaleY = 1.5f,
            eyeTilt = 20f,
            eyeSquint = 0f,
            pupilScale = 0.5f,
            pupilOffsetX = 0f,
            pupilOffsetY = -0.25f,
            mouthCurve = -0.2f,
            mouthWidth = 0.7f,
            mouthOpen = 1.0f,
            cheekIntensity = 0.15f,
            cheekColor = 0xFFDDBBEE.toInt(),
            armLeftAngle = -25f,
            armRightAngle = -25f,
            bodyWobble = 0.85f,
        ),
        // Explosive rage — slit eyes, snarl, arms raised aggressively
        Emotion.ANGER to FaceParams(
            color = 0xBBFF3333.toInt(),
            glowColor = 0x88FF6666.toInt(),
            eyeScaleY = 0.42f,
            eyeTilt = 25f,
            eyeSquint = 0.9f,
            squintType = SquintType.TOP,
            pupilScale = 0.8f,
            mouthCurve = -1.0f,
            mouthWidth = 1.1f,
            mouthOpen = 0.75f,
            cheekIntensity = 0.85f,
            cheekColor = 0xFFFF2222.toInt(),
            armLeftAngle = 22f,
            armRightAngle = 22f,
            bodyWobble = 0.65f,
        ),
        // Deep sorrow — drooping eyes, trembling frown, arms hanging
        Emotion.SADNESS to FaceParams(
            color = 0xBB5577CC.toInt(),
            glowColor = 0x888899DD.toInt(),
            eyeScaleY = 0.65f,
            eyeTilt = -25f,
            eyeSquint = 0.3f,
            squintType = SquintType.TOP,
            pupilScale = 1.1f,
            pupilOffsetX = 0f,
            pupilOffsetY = 0.3f,
            mouthCurve = -1.2f,
            mouthWidth = 0.35f,
            mouthOpen = 0f,
            cheekIntensity = 0.1f,
            cheekColor = 0xFFBBAADD.toInt(),
            armLeftAngle = -22f,
            armRightAngle = -22f,
            bodyWobble = 0f,
        ),
    )

    fun getPreset(emotion: Emotion): FaceParams =
        presets[emotion] ?: presets[Emotion.NEUTRAL]!!

    /** Standby: no mouth, neutral eyes */
    val STANDBY = FaceParams(
        color = 0xBBCCDDEE.toInt(),
        glowColor = 0x88DDEEFF.toInt(),
        eyeScaleY = 1.0f,
        eyeTilt = 0f,
        mouthVisible = false,
        cheekIntensity = 0.3f,
        armLeftAngle = 0f,
        armRightAngle = 0f,
    )

    /** Offline: grey, closed eyes */
    val OFFLINE = FaceParams(
        color = 0xBBAAAABB.toInt(),
        glowColor = 0x88BBBBCC.toInt(),
        eyeScaleY = 0.0f,
        eyeTilt = 0f,
        mouthVisible = false,
        cheekIntensity = 0f,
        armLeftAngle = -5f,
        armRightAngle = -5f,
    )

    /** Thinking: half-closed eyes, no mouth */
    val THINKING = FaceParams(
        color = 0xBBCCDDEE.toInt(),
        glowColor = 0x88DDEEFF.toInt(),
        eyeScaleY = 0.5f,
        eyeTilt = 0f,
        mouthVisible = false,
        cheekIntensity = 0.3f,
        armLeftAngle = 0f,
        armRightAngle = 5f,
    )
}
