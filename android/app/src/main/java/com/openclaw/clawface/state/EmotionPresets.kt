package com.openclaw.clawface.state

/**
 * Predefined FaceParams for each of the 10 emotions.
 * Values are taken directly from the PRD appendix.
 */
object EmotionPresets {

    private val presets = mapOf(
        Emotion.NEUTRAL to FaceParams(
            color = 0xAABBCCDD.toInt(),
            glowColor = 0x88DDEEFF.toInt(),
            eyeScaleY = 1.0f,
            eyeTilt = 0f,
            eyeSquint = 0f,
            pupilScale = 1.0f,
            mouthCurve = 0f,
            mouthWidth = 0.5f,
            mouthOpen = 0f,
        ),
        Emotion.JOY to FaceParams(
            color = 0xAAFFDD33.toInt(),
            glowColor = 0x88FFFFCC.toInt(),
            eyeScaleY = 1.0f,
            eyeTilt = 0f,
            eyeSquint = 0f,
            pupilScale = 1.0f,
            mouthCurve = 1.0f,
            mouthWidth = 0.8f,
            mouthOpen = 0.5f,
        ),
        Emotion.ANXIETY to FaceParams(
            color = 0xAAFF7700.toInt(),
            glowColor = 0x88FFAA55.toInt(),
            eyeScaleY = 1.3f,
            eyeTilt = 0f,
            eyeSquint = 0f,
            pupilScale = 0.8f,
            mouthCurve = -0.2f,
            mouthWidth = 1.0f,
            mouthOpen = 0.1f,
        ),
        Emotion.ENVY to FaceParams(
            color = 0xAA00CCCC.toInt(),
            glowColor = 0x88AAFFFF.toInt(),
            eyeScaleY = 1.4f,
            eyeTilt = 5f,
            eyeSquint = 0f,
            pupilScale = 1.2f,
            mouthCurve = 0.3f,
            mouthWidth = 0.3f,
            mouthOpen = 0.2f,
        ),
        Emotion.EMBARRASSMENT to FaceParams(
            color = 0xAAFF6699.toInt(),
            glowColor = 0x88FFBBDD.toInt(),
            eyeScaleY = 0.8f,
            eyeTilt = -10f,
            eyeSquint = 0f,
            pupilScale = 0.9f,
            mouthCurve = -0.1f,
            mouthWidth = 0.2f,
            mouthOpen = 0f,
        ),
        Emotion.ENNUI to FaceParams(
            color = 0xAA444499.toInt(),
            glowColor = 0x887777AA.toInt(),
            eyeScaleY = 0.4f,
            eyeTilt = 0f,
            eyeSquint = 0f,
            pupilScale = 1.0f,
            mouthCurve = 0f,
            mouthWidth = 0.5f,
            mouthOpen = 0f,
        ),
        Emotion.DISGUST to FaceParams(
            color = 0xAA66CC33.toInt(),
            glowColor = 0x88AAEE88.toInt(),
            eyeScaleY = 0.7f,
            eyeTilt = 5f,
            eyeSquint = 0.5f,
            squintType = SquintType.BOTTOM,
            pupilScale = 1.0f,
            mouthCurve = -0.8f,
            mouthWidth = 0.6f,
            mouthOpen = 0.2f,
        ),
        Emotion.FEAR to FaceParams(
            color = 0xAA9966CC.toInt(),
            glowColor = 0x88CCAAEE.toInt(),
            eyeScaleY = 1.2f,
            eyeTilt = 15f,
            eyeSquint = 0f,
            pupilScale = 0.7f,
            mouthCurve = -0.5f,
            mouthWidth = 0.4f,
            mouthOpen = 0.8f,
        ),
        Emotion.ANGER to FaceParams(
            color = 0xAADD2222.toInt(),
            glowColor = 0x88FF5555.toInt(),
            eyeScaleY = 0.8f,
            eyeTilt = 20f,
            eyeSquint = 0.7f,
            squintType = SquintType.TOP,
            pupilScale = 0.9f,
            mouthCurve = -0.8f,
            mouthWidth = 0.9f,
            mouthOpen = 0.6f,
        ),
        Emotion.SADNESS to FaceParams(
            color = 0xAA3366CC.toInt(),
            glowColor = 0x8888AAEE.toInt(),
            eyeScaleY = 0.9f,
            eyeTilt = -20f,
            eyeSquint = 0f,
            pupilScale = 1.0f,
            mouthCurve = -1.0f,
            mouthWidth = 0.6f,
            mouthOpen = 0.1f,
        ),
    )

    fun getPreset(emotion: Emotion): FaceParams =
        presets[emotion] ?: presets[Emotion.NEUTRAL]!!

    /** Standby: no mouth, neutral eyes */
    val STANDBY = FaceParams(
        color = 0xAABBCCDD.toInt(),
        glowColor = 0x88DDEEFF.toInt(),
        eyeScaleY = 1.0f,
        eyeTilt = 0f,
        mouthVisible = false,
    )

    /** Offline: grey, closed eyes */
    val OFFLINE = FaceParams(
        color = 0xAA888888.toInt(),
        glowColor = 0x88AAAAAA.toInt(),
        eyeScaleY = 0.0f,
        eyeTilt = 0f,
        mouthVisible = false,
    )

    /** Thinking: half-closed eyes, no mouth */
    val THINKING = FaceParams(
        color = 0xAABBCCDD.toInt(),
        glowColor = 0x88DDEEFF.toInt(),
        eyeScaleY = 0.5f,
        eyeTilt = 0f,
        mouthVisible = false,
    )
}
