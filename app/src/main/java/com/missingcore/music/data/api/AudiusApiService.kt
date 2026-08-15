package com.missingcore.music.data.api

import com.missingcore.music.data.api.dto.AudiusPlaylistDto
import com.missingcore.music.data.api.dto.AudiusResponse
import com.missingcore.music.data.api.dto.AudiusTrackDto
import com.missingcore.music.data.api.dto.AudiusUserDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AudiusApiService {

    @GET("tracks/trending")
    suspend fun getTrendingTracks(
        @Query("app_name") appName: String = "musync",
        @Query("limit") limit: Int = 30
    ): Response<AudiusResponse<List<AudiusTrackDto>>>

    @GET("tracks/trending/underground")
    suspend fun getUndergroundTrending(
        @Query("app_name") appName: String = "musync",
        @Query("limit") limit: Int = 30
    ): Response<AudiusResponse<List<AudiusTrackDto>>>

    @GET("tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("app_name") appName: String = "musync",
        @Query("limit") limit: Int = 30
    ): Response<AudiusResponse<List<AudiusTrackDto>>>

    @GET("users/search")
    suspend fun searchUsers(
        @Query("query") query: String,
        @Query("app_name") appName: String = "musync",
        @Query("limit") limit: Int = 20
    ): Response<AudiusResponse<List<AudiusUserDto>>>

    @GET("playlists/search")
    suspend fun searchPlaylists(
        @Query("query") query: String,
        @Query("app_name") appName: String = "musync",
        @Query("limit") limit: Int = 20
    ): Response<AudiusResponse<List<AudiusPlaylistDto>>>

    @GET("tracks/{id}")
    suspend fun getTrack(
        @Path("id") trackId: String,
        @Query("app_name") appName: String = "musync"
    ): Response<AudiusResponse<AudiusTrackDto>>

    @GET("users/{id}")
    suspend fun getUser(
        @Path("id") userId: String,
        @Query("app_name") appName: String = "musync"
    ): Response<AudiusResponse<AudiusUserDto>>

    @GET("users/{id}/tracks")
    suspend fun getUserTracks(
        @Path("id") userId: String,
        @Query("app_name") appName: String = "musync",
        @Query("limit") limit: Int = 30
    ): Response<AudiusResponse<List<AudiusTrackDto>>>

    @GET("playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") playlistId: String,
        @Query("app_name") appName: String = "musync"
    ): Response<AudiusResponse<List<AudiusPlaylistDto>>>
}
