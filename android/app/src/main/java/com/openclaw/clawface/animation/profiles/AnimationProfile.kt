package com.openclaw.clawface.animation.profiles

import com.openclaw.clawface.state.AnimationOffset

/**
 * Each emotion implements this to produce per-frame procedural animation offsets.
 * @param t time in seconds (from SystemClock.elapsedRealtime)
 */
interface AnimationProfile {
    fun compute(t: Float): AnimationOffset

    /** Blink duration in ms. Override for slow-blink emotions like Ennui. */
    val blinkDurationMs: Long get() = 200L

    /** Min/max interval between auto-blinks in ms. */
    val blinkIntervalMinMs: Long get() = 2000L
    val blinkIntervalMaxMs: Long get() = 6000L

    /** Whether to occasionally trigger double-blinks. */
    val doubleBlink: Boolean get() = false

    /** Extra pupil jitter offset per frame (for Anxiety etc). Returns (dx, dy). */
    fun pupilJitter(t: Float): Pair<Float, Float> = 0f to 0f
}
