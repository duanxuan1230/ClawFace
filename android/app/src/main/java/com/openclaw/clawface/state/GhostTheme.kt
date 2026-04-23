package com.openclaw.clawface.state

/**
 * Color theme for the ghost body.
 * Each theme defines a base radial gradient, two accent tint spots,
 * and a blush color — matching the design mockup's 5-theme system.
 *
 * All colors are in 0xAARRGGBB format.
 */
data class GhostTheme(
    /** 3-stop base radial gradient (center bright → edge tinted) */
    val baseStops: List<Int>,
    /** Lower-left accent radial tint */
    val tintA: RadialTint,
    /** Upper-right accent radial tint */
    val tintB: RadialTint,
    /** Cheek blush color (fully opaque, alpha applied by CheekRenderer) */
    val blush: Int,
) {

    data class RadialTint(
        /** Center X as fraction of body width (0..1) */
        val cx: Float,
        /** Center Y as fraction of body height (0..1) */
        val cy: Float,
        /** Radius as fraction of body diagonal (0..1) */
        val r: Float,
        /** Color at center */
        val color: Int,
        /** Opacity at center (0..1) */
        val opacity: Float,
    )

    companion object {
        val PASTEL = GhostTheme(
            baseStops = listOf(0xFFFFFFFF.toInt(), 0xFFFBF5FF.toInt(), 0xFFE4ECFF.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFFFC8D8.toInt(), 0.75f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFB8D4FF.toInt(), 0.80f),
            blush = 0xFFFF9AB3.toInt(),
        )

        val MINT = GhostTheme(
            baseStops = listOf(0xFFFFFFFF.toInt(), 0xFFF0FDFA.toInt(), 0xFFD5F5EA.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFA8F0D8.toInt(), 0.85f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFB0E8FF.toInt(), 0.80f),
            blush = 0xFF7AD9B5.toInt(),
        )

        val SUNSET = GhostTheme(
            baseStops = listOf(0xFFFFFAF5.toInt(), 0xFFFFF0E5.toInt(), 0xFFFFD5C0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFFFB896.toInt(), 0.85f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFFFC4D2.toInt(), 0.85f),
            blush = 0xFFFF7A8E.toInt(),
        )

        val LILAC = GhostTheme(
            baseStops = listOf(0xFFFFFFFF.toInt(), 0xFFF8F0FF.toInt(), 0xFFE0D0FF.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFD8B8FF.toInt(), 0.90f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFFFB8EC.toInt(), 0.75f),
            blush = 0xFFC48FE0.toInt(),
        )

        val SKY = GhostTheme(
            baseStops = listOf(0xFFFFFFFF.toInt(), 0xFFF0F8FF.toInt(), 0xFFC8DFFF.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFA8C8FF.toInt(), 0.90f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFBCE5FF.toInt(), 0.85f),
            blush = 0xFF7AA8E0.toInt(),
        )

        private val themeMap = mapOf(
            "pastel" to PASTEL,
            "mint" to MINT,
            "sunset" to SUNSET,
            "lilac" to LILAC,
            "sky" to SKY,
        )

        fun fromName(name: String): GhostTheme = themeMap[name] ?: PASTEL

        val ALL_NAMES: List<String> = themeMap.keys.toList()
    }
}
