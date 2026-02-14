package com.openclaw.clawface.animation.profiles

import com.openclaw.clawface.state.AnimationOffset
import kotlin.math.abs
import kotlin.math.sin

/** Neutral: subtle breathing pulse + normal blink */
object NeutralProfile : AnimationProfile {
    override fun compute(t: Float): AnimationOffset {
        val breathe = sin(t * 1.5f) * 0.015f
        return AnimationOffset(scaleX = 1f + breathe, scaleY = 1f + breathe)
    }
}

/** Joy: balloon float bobbing + double blink */
object JoyProfile : AnimationProfile {
    override val doubleBlink = true

    override fun compute(t: Float): AnimationOffset {
        val bob = sin(t * 2f) * 10f
        return AnimationOffset(offsetY = bob)
    }
}

/** Anxiety: high-frequency random jitter */
object AnxietyProfile : AnimationProfile {
    override val blinkIntervalMinMs = 1000L
    override val blinkIntervalMaxMs = 3000L

    override fun compute(t: Float): AnimationOffset {
        val jx = (Math.random().toFloat() - 0.5f) * 6f
        val jy = (Math.random().toFloat() - 0.5f) * 6f
        return AnimationOffset(offsetX = jx, offsetY = jy)
    }

    override fun pupilJitter(t: Float): Pair<Float, Float> {
        return (Math.random().toFloat() - 0.5f) * 0.15f to
               (Math.random().toFloat() - 0.5f) * 0.1f
    }
}

/** Envy: leaning sway + heartbeat pulse */
object EnvyProfile : AnimationProfile {
    override fun compute(t: Float): AnimationOffset {
        val sway = sin(t * 1.5f) * 5f
        val pulse = 1f + sin(t * 3f) * 0.05f
        return AnimationOffset(offsetX = sway, scaleX = pulse, scaleY = pulse)
    }
}

/** Embarrassment: turtle shell - sink down and squash */
object EmbarrassmentProfile : AnimationProfile {
    override val blinkIntervalMinMs = 1500L
    override val blinkIntervalMaxMs = 4000L

    override fun compute(t: Float): AnimationOffset {
        val hide = abs(sin(t * 0.8f)) * 15f
        return AnimationOffset(offsetY = hide, scaleX = 1.02f, scaleY = 0.98f)
    }
}

/** Ennui: melting - ultra slow squash + slow blink */
object EnnuiProfile : AnimationProfile {
    override val blinkDurationMs = 1500L
    override val blinkIntervalMinMs = 3000L
    override val blinkIntervalMaxMs = 8000L

    override fun compute(t: Float): AnimationOffset {
        val melt = sin(t * 0.5f) * 0.05f
        return AnimationOffset(scaleY = 1f + melt)
    }
}

/** Disgust: judgmental head shake / recoil */
object DisgustProfile : AnimationProfile {
    override fun compute(t: Float): AnimationOffset {
        val shake = sin(t * 3f) * 5f
        val rot = shake * -0.5f
        return AnimationOffset(offsetX = shake, rotation = rot)
    }
}

/** Fear: intermittent shiver */
object FearProfile : AnimationProfile {
    override val blinkIntervalMinMs = 1500L
    override val blinkIntervalMaxMs = 4000L

    override fun compute(t: Float): AnimationOffset {
        val cycle = sin(t * 0.5f)
        return if (cycle > 0.8f) {
            val shiver = sin(t * 50f) * 3f
            AnimationOffset(offsetX = shiver)
        } else {
            AnimationOffset()
        }
    }
}

/** Anger: boiling - build up + occasional thud */
object AngerProfile : AnimationProfile {
    override fun compute(t: Float): AnimationOffset {
        val buildUp = abs(sin(t * 2f)) * 0.1f
        val sx = 1f + buildUp
        val sy = 1f + buildUp

        // Occasional thud
        var ox = 0f
        var oy = 0f
        if (Math.random() < 0.02) {
            oy = -5f
            ox = (Math.random().toFloat() - 0.5f) * 10f
        }

        return AnimationOffset(offsetX = ox, offsetY = oy, scaleX = sx, scaleY = sy)
    }
}

/** Sadness: drooping sigh + wobble */
object SadnessProfile : AnimationProfile {
    override val blinkIntervalMinMs = 3000L
    override val blinkIntervalMaxMs = 7000L

    override fun compute(t: Float): AnimationOffset {
        val sigh = sin(t * 0.3f)
        val oy = if (sigh > 0.8f) (sigh - 0.8f) * 20f else 0f
        val rot = sin(t * 1f) * 2f
        return AnimationOffset(offsetY = oy, rotation = rot)
    }
}

/** Standby: gentle breathing + very occasional blink */
object StandbyProfile : AnimationProfile {
    override val blinkIntervalMinMs = 4000L
    override val blinkIntervalMaxMs = 10000L

    override fun compute(t: Float): AnimationOffset {
        val breathe = sin(t * 1.0f) * 0.03f
        val alphaBreath = 0.85f + sin(t * 1.0f) * 0.15f
        return AnimationOffset(
            scaleX = 1f + breathe,
            scaleY = 1f + breathe,
            alpha = alphaBreath,
        )
    }
}

/** Thinking: slow eye drift left-right + reduced alpha */
object ThinkingProfile : AnimationProfile {
    override val blinkIntervalMinMs = 3000L
    override val blinkIntervalMaxMs = 6000L

    override fun compute(t: Float): AnimationOffset {
        val drift = sin(t * 0.8f) * 3f
        return AnimationOffset(offsetX = drift, alpha = 0.6f)
    }

    override fun pupilJitter(t: Float): Pair<Float, Float> {
        return sin(t * 0.8f) * 0.3f to 0f
    }
}

/** Offline: static grey, no animation */
object OfflineProfile : AnimationProfile {
    override val blinkIntervalMinMs = 999999L
    override val blinkIntervalMaxMs = 999999L

    override fun compute(t: Float): AnimationOffset = AnimationOffset(alpha = 0.7f)
}
