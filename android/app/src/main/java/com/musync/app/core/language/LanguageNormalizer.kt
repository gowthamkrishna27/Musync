package com.musync.app.core.language

import com.musync.app.domain.model.Track

object LanguageNormalizer {

    // Supported ISO 639-1 language code to display name mapping
    private val CODE_TO_NAME = mapOf(
        "te" to "Telugu",
        "hi" to "Hindi",
        "en" to "English",
        "ta" to "Tamil",
        "kn" to "Kannada",
        "ml" to "Malayalam",
        "bn" to "Bengali",
        "mr" to "Marathi",
        "pa" to "Punjabi",
        "gu" to "Gujarati"
    )

    private val NAME_TO_CODE = mapOf(
        "telugu" to "te",
        "hindi" to "hi",
        "english" to "en",
        "tamil" to "ta",
        "kannada" to "kn",
        "malayalam" to "ml",
        "bengali" to "bn",
        "marathi" to "mr",
        "punjabi" to "pa",
        "gujarati" to "gu"
    )

    /**
     * Converts any input (display name or ISO code) to standard lowercase ISO code (e.g. "te").
     */
    fun toCode(input: String?): String {
        if (input.isNullOrBlank()) return "en"
        val trimmed = input.trim().lowercase()
        if (CODE_TO_NAME.containsKey(trimmed)) return trimmed
        return NAME_TO_CODE[trimmed] ?: "en"
    }

    /**
     * Converts standard ISO code (e.g. "te") to display name ("Telugu").
     */
    fun toDisplayName(codeOrName: String?): String {
        if (codeOrName.isNullOrBlank()) return "English"
        val code = toCode(codeOrName)
        return CODE_TO_NAME[code] ?: codeOrName.replaceFirstChar { it.uppercase() }
    }

    /**
     * Normalizes a collection of languages to unique ISO codes.
     */
    fun toCodeSet(languages: Collection<String>?): Set<String> {
        if (languages.isNullOrEmpty()) return setOf("te", "hi", "en")
        return languages.map { toCode(it) }.toSet()
    }

    /**
     * Normalizes a collection of languages to unique Display Names.
     */
    fun toDisplayNameSet(languages: Collection<String>?): Set<String> {
        if (languages.isNullOrEmpty()) return setOf("Telugu", "Hindi", "English")
        return languages.map { toDisplayName(it) }.toSet()
    }

    /**
     * Smart heuristic language detector for tracks without explicit provider language tags.
     */
    fun inferLanguage(track: Track): String {
        if (!track.language.isNullOrBlank()) {
            return toCode(track.language)
        }

        val text = "${track.title} ${track.artist.name} ${track.genre ?: ""}".lowercase()

        // 1. Keyword-based matching
        if (text.contains("telugu") || text.contains("tollywood")) return "te"
        if (text.contains("tamil") || text.contains("kollywood")) return "ta"
        if (text.contains("hindi") || text.contains("bollywood")) return "hi"
        if (text.contains("kannada") || text.contains("sandalwood")) return "kn"
        if (text.contains("malayalam") || text.contains("mollywood")) return "ml"
        if (text.contains("punjabi")) return "pa"
        if (text.contains("bengali")) return "bn"
        if (text.contains("marathi")) return "mr"

        // 2. Unicode script block inspection
        for (char in text) {
            val code = char.code
            when (code) {
                in 0x0C00..0x0C7F -> return "te" // Telugu script
                in 0x0900..0x097F -> return "hi" // Devanagari (Hindi/Marathi)
                in 0x0B80..0x0BFF -> return "ta" // Tamil script
                in 0x0C80..0x0CFF -> return "kn" // Kannada script
                in 0x0D00..0x0D7F -> return "ml" // Malayalam script
                in 0x0A00..0x0A7F -> return "pa" // Gurmukhi (Punjabi)
                in 0x0980..0x09FF -> return "bn" // Bengali script
            }
        }

        return "en"
    }

    /**
     * Computes candidate allocation weights given user's preferred languages and dynamic affinities.
     */
    fun computeLanguageWeights(
        preferredCodes: Set<String>,
        affinities: Map<String, Float>
    ): Map<String, Float> {
        val activeCodes = if (preferredCodes.isNotEmpty()) preferredCodes else setOf("te", "hi", "en")
        val rawWeights = mutableMapOf<String, Float>()

        for (code in activeCodes) {
            val affinity = affinities[code] ?: 0.5f
            // Base weight from preference (1.0) multiplied by affinity modulation (0.5 to 1.5)
            val weight = (0.5f + affinity).coerceIn(0.2f, 2.0f)
            rawWeights[code] = weight
        }

        val total = rawWeights.values.sum()
        if (total <= 0f) return activeCodes.associateWith { 1f / activeCodes.size }
        return rawWeights.mapValues { it.value / total }
    }
}
