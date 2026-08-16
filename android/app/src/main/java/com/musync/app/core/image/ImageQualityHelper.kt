package com.musync.app.core.image

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Image Quality Optimization Utility.
 *
 * Automatically elevates low-resolution / downscaled album artwork and thumbnails
 * into Crystal-Clear High Definition (800x800 / 1080p) artwork across the entire Musync app.
 */
object ImageQualityHelper {

    /**
     * Upgrades low-resolution YouTube/Google Music artwork URLs to High-Definition/Ultra-HD.
     */
    fun getHighQualityArtworkUrl(rawUrl: String?, videoId: String? = null): String {
        val cleanVideoId = videoId?.removePrefix("yt_")?.takeIf { it.isNotBlank() }

        if (rawUrl.isNullOrBlank()) {
            return if (!cleanVideoId.isNullOrBlank()) {
                "https://i.ytimg.com/vi/$cleanVideoId/hq720.jpg"
            } else {
                ""
            }
        }

        return when {
            // 1. Google / YouTube Music UserContent & CDN Album Art (upgrade =w120-h120 -> =w800-h800-l90-rj)
            rawUrl.contains("googleusercontent.com") || rawUrl.contains("ggpht.com") -> {
                rawUrl
                    .replace(Regex("=w\\d+-h\\d+[^=]*"), "=w800-h800-l90-rj")
                    .replace(Regex("=s\\d+[^=]*"), "=s800-c-k-c0x00ffffff-no-rj")
            }

            // 2. YouTube Vi Thumbnails (upgrade mqdefault/default 320x180 -> hq720 1280x720 / maxresdefault)
            rawUrl.contains("i.ytimg.com") -> {
                rawUrl
                    .replace("/mqdefault.jpg", "/hq720.jpg")
                    .replace("/default.jpg", "/hq720.jpg")
                    .replace("/sddefault.jpg", "/hq720.jpg")
            }

            // 3. Fallback for ID-based URLs
            else -> rawUrl
        }
    }

    /**
     * Builds an optimized Coil ImageRequest with aggressive memory/disk caching and GPU acceleration.
     */
    fun buildOptimizedImageRequest(context: Context, url: String?, videoId: String? = null): ImageRequest {
        val hqUrl = getHighQualityArtworkUrl(url, videoId)
        return ImageRequest.Builder(context)
            .data(hqUrl)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
    }
}
