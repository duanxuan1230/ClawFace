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

        // --- Emotion-specific themes (not exposed in UI theme picker) ---

        /** Joy: sunny golden warmth */
        val SUNNY = GhostTheme(
            baseStops = listOf(0xFFFFFFF8.toInt(), 0xFFFFF8E8.toInt(), 0xFFFFEBC0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFFFD080.toInt(), 0.85f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFFFE8A0.toInt(), 0.80f),
            blush = 0xFFFF88AA.toInt(),
        )

        /** Anxiety: nervous amber */
        val AMBER = GhostTheme(
            baseStops = listOf(0xFFFFFCF5.toInt(), 0xFFFFF0D8.toInt(), 0xFFFFD8A0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFFFB870.toInt(), 0.85f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFFFC8A0.toInt(), 0.80f),
            blush = 0xFFFFBBCC.toInt(),
        )

        /** Envy: cold teal */
        val TEAL = GhostTheme(
            baseStops = listOf(0xFFF8FFFD.toInt(), 0xFFE8FFF5.toInt(), 0xFFC0EED8.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFF80D8B0.toInt(), 0.85f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFF70C8C8.toInt(), 0.80f),
            blush = 0xFFAADDCC.toInt(),
        )

        /** Embarrassment: hot rose */
        val ROSE = GhostTheme(
            baseStops = listOf(0xFFFFFFFF.toInt(), 0xFFFFF0F5.toInt(), 0xFFFFD0E0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFFFB0C8.toInt(), 0.90f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFFFC0D8.toInt(), 0.85f),
            blush = 0xFFFF5577.toInt(),
        )

        /** Ennui: dull grey-lavender */
        val ASH = GhostTheme(
            baseStops = listOf(0xFFF8F8FA.toInt(), 0xFFECECF2.toInt(), 0xFFD8D8E2.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFC8C0D8.toInt(), 0.70f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFCCC8D8.toInt(), 0.65f),
            blush = 0xFFCCBBDD.toInt(),
        )

        /** Disgust: sickly olive-green */
        val OLIVE = GhostTheme(
            baseStops = listOf(0xFFFCFFF5.toInt(), 0xFFF0F5E0.toInt(), 0xFFD8E0B0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFC0D080.toInt(), 0.85f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFD0D898.toInt(), 0.75f),
            blush = 0xFFCCDD88.toInt(),
        )

        /** Fear: cold violet */
        val VIOLET = GhostTheme(
            baseStops = listOf(0xFFFCF8FF.toInt(), 0xFFF0E8FF.toInt(), 0xFFD8C0F0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFC098E0.toInt(), 0.90f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFD0B0F0.toInt(), 0.85f),
            blush = 0xFFDDBBEE.toInt(),
        )

        /** Anger: fiery crimson */
        val FLAME = GhostTheme(
            baseStops = listOf(0xFFFFF8F5.toInt(), 0xFFFFE0D8.toInt(), 0xFFFFC0B0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFFF8870.toInt(), 0.90f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFFFAA60.toInt(), 0.85f),
            blush = 0xFFFF2222.toInt(),
        )

        /** Sadness: deep blue */
        val OCEAN = GhostTheme(
            baseStops = listOf(0xFFF8FAFF.toInt(), 0xFFE8F0FF.toInt(), 0xFFC0D0F0.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFF98B0E0.toInt(), 0.90f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFA8C0F0.toInt(), 0.85f),
            blush = 0xFFBBAADD.toInt(),
        )

        /** Offline: cold grey */
        val GREY = GhostTheme(
            baseStops = listOf(0xFFF5F5F5.toInt(), 0xFFE8E8EC.toInt(), 0xFFD0D0D8.toInt()),
            tintA = RadialTint(0.25f, 0.80f, 0.55f, 0xFFC0C0CC.toInt(), 0.60f),
            tintB = RadialTint(0.85f, 0.55f, 0.50f, 0xFFC8C8D0.toInt(), 0.55f),
            blush = 0xFFBBBBCC.toInt(),
        )

        private val themeMap = mapOf(
            "pastel" to PASTEL,
            "mint" to MINT,
            "sunset" to SUNSET,
            "lilac" to LILAC,
            "sky" to SKY,
            "sunny" to SUNNY,
            "amber" to AMBER,
            "teal" to TEAL,
            "rose" to ROSE,
            "ash" to ASH,
            "olive" to OLIVE,
            "violet" to VIOLET,
            "flame" to FLAME,
            "ocean" to OCEAN,
            "grey" to GREY,
        )

        fun fromName(name: String): GhostTheme = themeMap[name] ?: PASTEL

        /** Only the 5 user-selectable themes (for UI picker & MCP validation) */
        val ALL_NAMES: List<String> = listOf("pastel", "mint", "sunset", "lilac", "sky")
    }
}
