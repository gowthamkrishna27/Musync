package com.musync.app.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton Media3 Cache Provider.
 * Stores up to 200MB of streamed audio locally using LRU eviction.
 * Repeated listens and track loops require 0kb of network bandwidth and start instantly.
 */
@OptIn(UnstableApi::class)
object MediaCacheManager {

    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_SIZE_BYTES = 200L * 1024 * 1024 // 200 MB

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "musync_media_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    @Synchronized
    fun releaseCache() {
        try {
            simpleCache?.release()
        } catch (_: Exception) {}
        simpleCache = null
    }
}

