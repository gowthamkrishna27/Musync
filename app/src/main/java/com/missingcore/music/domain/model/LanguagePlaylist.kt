package com.missingcore.music.domain.model

data class LanguagePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val language: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long,
    val searchQuery: String
)

object CuratedLanguagePlaylists {
    val TELUGU = listOf(
        LanguagePlaylist(
            id = "pl_tel_top_2026",
            title = "Top Telugu Hits 2026",
            subtitle = "Tollywood Chartbusters & Latest Beats",
            language = "Telugu",
            gradientStartHex = 0xFFFF512F,
            gradientEndHex = 0xFFDD2476,
            searchQuery = "Latest Telugu Songs 2026"
        ),
        LanguagePlaylist(
            id = "pl_tel_melody",
            title = "Telugu Melody Magic",
            subtitle = "Soulful Melodies & Heartstrings",
            language = "Telugu",
            gradientStartHex = 0xFF8A2387,
            gradientEndHex = 0xFFE94057,
            searchQuery = "Telugu Melodies All Time Hits"
        ),
        LanguagePlaylist(
            id = "pl_tel_mass",
            title = "Mass Masala Telugu",
            subtitle = "High Voltage Dance & Fast Beats",
            language = "Telugu",
            gradientStartHex = 0xFFF7971E,
            gradientEndHex = 0xFFFFD200,
            searchQuery = "Telugu Mass Party Songs"
        ),
        LanguagePlaylist(
            id = "pl_tel_classics",
            title = "Telugu Golden Classics",
            subtitle = "SPB, Keeravani & 90s Hits",
            language = "Telugu",
            gradientStartHex = 0xFF11998E,
            gradientEndHex = 0xFF38EF7D,
            searchQuery = "Telugu 90s Evergreen Hits"
        )
    )

    val TAMIL = listOf(
        LanguagePlaylist(
            id = "pl_tam_hot_50",
            title = "Kollywood Hot 50",
            subtitle = "Anirudh, AR Rahman & Top Hits",
            language = "Tamil",
            gradientStartHex = 0xFF4E54C8,
            gradientEndHex = 0xFF8F94FB,
            searchQuery = "Latest Tamil Songs 2026"
        ),
        LanguagePlaylist(
            id = "pl_tam_romance",
            title = "Tamil Romance & Love",
            subtitle = "Evergreen Soulful Melodies",
            language = "Tamil",
            gradientStartHex = 0xFFD31027,
            gradientEndHex = 0xFFEA384D,
            searchQuery = "Tamil Romantic Melody Hits"
        ),
        LanguagePlaylist(
            id = "pl_tam_kuthu",
            title = "Kuthu Beats 2026",
            subtitle = "High Energy Dappankuthu & Beats",
            language = "Tamil",
            gradientStartHex = 0xFFFF416C,
            gradientEndHex = 0xFFFF4B2B,
            searchQuery = "Tamil Kuthu Party Hits"
        ),
        LanguagePlaylist(
            id = "pl_tam_yuvan_harris",
            title = "Yuvan & Harris Hits",
            subtitle = "Iconic Vibe Nostalgia",
            language = "Tamil",
            gradientStartHex = 0xFF00B4DB,
            gradientEndHex = 0xFF0083B0,
            searchQuery = "Yuvan Shankar Raja Harris Jayaraj Hits"
        )
    )

    val HINDI = listOf(
        LanguagePlaylist(
            id = "pl_hin_top_50",
            title = "Bollywood Top 50",
            subtitle = "Arijit Singh, Badshah & Hits",
            language = "Hindi",
            gradientStartHex = 0xFF00C9FF,
            gradientEndHex = 0xFF92FE9D,
            searchQuery = "Latest Bollywood Songs 2026"
        ),
        LanguagePlaylist(
            id = "pl_hin_romantic",
            title = "Hindi Love Melodies",
            subtitle = "Late Night Romance & Acoustic",
            language = "Hindi",
            gradientStartHex = 0xFFB92B27,
            gradientEndHex = 0xFF1565C0,
            searchQuery = "Hindi Romantic Songs All Time"
        ),
        LanguagePlaylist(
            id = "pl_hin_party",
            title = "Desi Hip-Hop & Party",
            subtitle = "Club Hits & Bass Bangers",
            language = "Hindi",
            gradientStartHex = 0xFFF12711,
            gradientEndHex = 0xFFF5AF19,
            searchQuery = "Bollywood Party Dance Songs"
        ),
        LanguagePlaylist(
            id = "pl_hin_90s",
            title = "90s Bollywood Gold",
            subtitle = "Golden Nostalgia Classics",
            language = "Hindi",
            gradientStartHex = 0xFF654EA3,
            gradientEndHex = 0xFFEAAFC8,
            searchQuery = "90s Bollywood Evergreen Songs"
        )
    )

    val OTHER_LANGUAGES = listOf(
        LanguagePlaylist(
            id = "pl_eng_global",
            title = "Global Top 50",
            subtitle = "Billboard & Viral English Pop",
            language = "English",
            gradientStartHex = 0xFF1F1C2C,
            gradientEndHex = 0xFF928DAB,
            searchQuery = "Global Top Billboard Hits 2026"
        ),
        LanguagePlaylist(
            id = "pl_mal_hits",
            title = "Mollywood Magic",
            subtitle = "Sushin Shyam & Top Malayalam Hits",
            language = "Malayalam",
            gradientStartHex = 0xFF0F2027,
            gradientEndHex = 0xFF2C5364,
            searchQuery = "Latest Malayalam Songs 2026"
        ),
        LanguagePlaylist(
            id = "pl_kan_hits",
            title = "Sandalwood Superhits",
            subtitle = "Top Kannada Beats & Melodies",
            language = "Kannada",
            gradientStartHex = 0xFFE65C00,
            gradientEndHex = 0xFFF9D423,
            searchQuery = "Latest Kannada Hit Songs 2026"
        ),
        LanguagePlaylist(
            id = "pl_pun_vibe",
            title = "Punjabi Drip & Pop",
            subtitle = "Diljit, AP Dhillon & Sidhu Hits",
            language = "Punjabi",
            gradientStartHex = 0xFF833AB4,
            gradientEndHex = 0xFFFD1D1D,
            searchQuery = "Punjabi Top Trending Songs 2026"
        )
    )

    val ALL_PLAYLISTS = TELUGU + TAMIL + HINDI + OTHER_LANGUAGES
}
