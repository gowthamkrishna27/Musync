package com.missingcore.music.data.api.dto

import com.google.gson.annotations.SerializedName

data class JioSaavnSongDto(
    @SerializedName("songid") val songId: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("song") val song: String? = null,
    @SerializedName("singers") val singers: String? = null,
    @SerializedName("primary_artists") val primaryArtists: String? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("album_url") val albumUrl: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("url") val mediaUrl: String? = null,
    @SerializedName("media_url") val directMediaUrl: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("has_lyrics") val hasLyrics: String? = null,
    @SerializedName("lyrics") val lyrics: String? = null
)
