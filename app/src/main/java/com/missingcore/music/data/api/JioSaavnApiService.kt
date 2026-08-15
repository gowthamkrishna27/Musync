package com.missingcore.music.data.api

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApiService {

    @GET("result/")
    suspend fun search(
        @Query("query") query: String,
        @Query("lyrics") lyrics: Boolean = false
    ): JsonElement

    @GET("song/")
    suspend fun getSong(
        @Query("query") query: String,
        @Query("lyrics") lyrics: Boolean = false
    ): JsonElement

    @GET("playlist/")
    suspend fun getPlaylist(
        @Query("query") query: String,
        @Query("lyrics") lyrics: Boolean = false
    ): JsonElement

    @GET("album/")
    suspend fun getAlbum(
        @Query("query") query: String,
        @Query("lyrics") lyrics: Boolean = false
    ): JsonElement

    @GET("lyrics/")
    suspend fun getLyrics(
        @Query("query") query: String
    ): JsonElement
}
