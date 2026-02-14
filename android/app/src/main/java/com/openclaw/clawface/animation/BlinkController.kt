package com.openclaw.clawface.animation

import com.openclaw.clawface.animation.profiles.AnimationProfile

/**
 * Controls automatic blinking with configurable duration, interval, and double-blink support.
 * Returns a blink factor (1.0 = fully open, 0.0 = fully closed) to multiply with eyeScaleY.
 */
class BlinkController {

    private var nextBlinkTime = 0L
    private var blinkStartTime = 0L
    private var isBlinking = false
    private var isDoubleBlink = false
    private var doubleBlinkPhase = 0 // 0=first close, 1=brief open, 2=second close

    private var blinkDuration = 200L
    private var intervalMin = 2000L
    private var intervalMax = 6000L
    private var allowDouble = false

    fun configure(profile: AnimationProfile) {
        blinkDuration = profile.blinkDurationMs
        intervalMin = profile.blinkIntervalMinMs
        intervalMax = profile.blinkIntervalMaxMs
        allowDouble = profile.doubleBlink
    }

    /**
     * @return blink factor 0.0 (closed) to 1.0 (open)
     */
    fun update(nowMs: Long): Float {
        if (!isBlinking) {
            if (nowMs >= nextBlinkTime) {
                startBlink(nowMs)
            }
            return 1f
        }

        val elapsed = nowMs - blinkStartTime

        return if (isDoubleBlink) {
            updateDoubleBlink(elapsed, nowMs)
        } else {
            updateSingleBlink(elapsed, nowMs)
        }
    }

    private fun startBlink(nowMs: Long) {
        isBlinking = true
        blinkStartTime = nowMs
        isDoubleBlink = allowDouble && Math.random() < 0.3
        doubleBlinkPhase = 0
    }

    private fun updateSingleBlink(elapsed: Long, nowMs: Long): Float {
        val half = blinkDuration / 2
        return when {
            elapsed < half -> {
                // Closing
                1f - (elapsed.toFloat() / half)
            }
            elapsed < blinkDuration -> {
                // Opening
                (elapsed - half).toFloat() / half
            }
            else -> {
                endBlink(nowMs)
                1f
            }
        }
    }

    private fun updateDoubleBlink(elapsed: Long, nowMs: Long): Float {
        val closeDur = blinkDuration / 2
        val openDur = blinkDuration / 4
        val totalDouble = closeDur * 2 + openDur + closeDur * 2

        return when {
            // First blink close
            elapsed < closeDur -> 1f - elapsed.toFloat() / closeDur
            // First blink open
            elapsed < closeDur * 2 -> (elapsed - closeDur).toFloat() / closeDur
            // Brief gap
            elapsed < closeDur * 2 + openDur -> 1f
            // Second blink close
            elapsed < closeDur * 3 + openDur -> {
                val t = elapsed - closeDur * 2 - openDur
                1f - t.toFloat() / closeDur
            }
            // Second blink open
            elapsed < totalDouble -> {
                val t = elapsed - closeDur * 3 - openDur
                t.toFloat() / closeDur
            }
            else -> {
                endBlink(nowMs)
                1f
            }
        }
    }

    private fun endBlink(nowMs: Long) {
        isBlinking = false
        isDoubleBlink = false
        nextBlinkTime = nowMs + intervalMin +
            (Math.random() * (intervalMax - intervalMin)).toLong()
    }

    fun reset() {
        isBlinking = false
        nextBlinkTime = System.currentTimeMillis() + 1000L
    }
}
