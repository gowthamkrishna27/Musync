package com.musync.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AudiusResponse<T>(
    @SerializedName("data") val data: T? = null
)

data class AudiusTrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("user") val user: AudiusUserDto?,
    @SerializedName("artwork") val artwork: AudiusArtworkDto?,
    @SerializedName("stream") val stream: AudiusStreamDto? = null,
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("play_count") val playCount: Long? = null,
    @SerializedName("permalink") val permalink: String? = null,
    @SerializedName("is_streamable") val isStreamable: Boolean? = true
)

data class AudiusStreamDto(
    @SerializedName("url") val url: String? = null
)

data class AudiusUserDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String?,
    @SerializedName("handle") val handle: String?,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("profile_picture") val profilePicture: AudiusArtworkDto? = null,
    @SerializedName("track_count") val trackCount: Int? = null
)

data class AudiusArtworkDto(
    @SerializedName("150x150") val small: String? = null,
    @SerializedName("480x480") val medium: String? = null,
    @SerializedName("1000x1000") val large: String? = null
)

data class AudiusPlaylistDto(
    @SerializedName("id") val id: String,
    @SerializedName("playlist_name") val playlistName: String?,
    @SerializedName("description") val description: String? = null,
    @SerializedName("artwork") val artwork: AudiusArtworkDto? = null,
    @SerializedName("user") val user: AudiusUserDto? = null,
    @SerializedName("tracks") val tracks: List<AudiusTrackDto>? = null,
    @SerializedName("track_count") val trackCount: Int? = null
)

