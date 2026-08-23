package com.musync.app.core.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Native, zero-dependency client-side YouTube audio stream extractor.
 * 
 * Runs directly on the user's mobile cellular or home Wi-Fi IP address,
 * completely bypassing Google datacenter bot-challenges and IP blocks.
 */
object NativeYouTubeStreamExtractor {

    private const val TAG = "NativeYTExtractor"
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Public Piped gateway mesh accessible from residential mobile IPs
    private val PIPED_GATEWAYS = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pa.il.ax",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.leptons.xyz"
    )

    /**
     * Extract a direct playable audio stream URL for a given YouTube video ID.
     */
    suspend fun extractStreamUrl(videoId: String, quality: String = "low"): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")

        // 1. Try Innertube TVHTML5 Embedded Player directly from phone's IP
        val innertubeUrl = extractViaInnertube(cleanId, quality)
        if (!innertubeUrl.isNullOrBlank()) {
            Log.i(TAG, "✓ Extracted direct audio stream via Innertube for $cleanId")
            return@withContext innertubeUrl
        }

        // 2. Try Client-Side Piped Gateway from phone's IP
        val pipedUrl = extractViaPiped(cleanId)
        if (!pipedUrl.isNullOrBlank()) {
            Log.i(TAG, "✓ Extracted direct audio stream via Piped Gateway for $cleanId")
            return@withContext pipedUrl
        }

        null
    }

    private fun extractViaInnertube(videoId: String, quality: String): String? {
        val clients = listOf(
            // TV Embedded client (returns direct unthrottled streaming URLs)
            JsonObject().apply {
                addProperty("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                addProperty("clientVersion", "2.0")
                addProperty("hl", "en")
                addProperty("gl", "US")
            },
            // Android mobile client
            JsonObject().apply {
                addProperty("clientName", "ANDROID")
                addProperty("clientVersion", "19.09.37")
                addProperty("androidSdkVersion", 34)
                addProperty("hl", "en")
                addProperty("gl", "US")
            }
        )

        for (clientObj in clients) {
            try {
                val payload = JsonObject().apply {
                    addProperty("videoId", videoId)
                    add("context", JsonObject().apply { add("client", clientObj) })
                }

                val body = payload.toString().toRequestBody(jsonMediaType)
                val req = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(body)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Content-Type", "application/json")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val respBody = resp.body?.string() ?: continue
                    val root = gson.fromJson(respBody, JsonObject::class.java)
                    val streamingData = root.getAsJsonObject("streamingData") ?: continue

                    val formatsList = mutableListOf<JsonObject>()
                    streamingData.getAsJsonArray("adaptiveFormats")?.forEach {
                        if (it.isJsonObject) formatsList.add(it.asJsonObject)
                    }
                    streamingData.getAsJsonArray("formats")?.forEach {
                        if (it.isJsonObject) formatsList.add(it.asJsonObject)
                    }

                    // Filter for audio streams with direct URLs
                    val audioStreams = formatsList.filter { fmt ->
                        val mime = fmt.get("mimeType")?.asString ?: ""
                        val hasAudio = mime.contains("audio") || fmt.get("itag")?.asInt in listOf(18, 140, 251, 139, 249, 250)
                        val url = fmt.get("url")?.asString
                        hasAudio && !url.isNullOrBlank() && url.startsWith("http")
                    }

                    if (audioStreams.isNotEmpty()) {
                        // Prefer itag 140 (128kbps AAC M4A) or itag 251 (Opus WebM)
                        val chosen = audioStreams.find { it.get("itag")?.asInt == 140 }
                            ?: audioStreams.find { it.get("itag")?.asInt == 251 }
                            ?: audioStreams.find { it.get("itag")?.asInt == 18 }
                            ?: audioStreams.first()

                        val url = chosen.get("url")?.asString
                        if (!url.isNullOrBlank()) return url
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Innertube extraction attempt failed: ${e.message}")
            }
        }
        return null
    }

    private fun extractViaPiped(videoId: String): String? {
        for (instance in PIPED_GATEWAYS) {
            try {
                val req = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", "Musync-Android/1.0")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: continue
                    val root = gson.fromJson(body, JsonObject::class.java)
                    val audioStreams = root.getAsJsonArray("audioStreams") ?: continue

                    var bestUrl: String? = null
                    var highestBitrate = 0

                    audioStreams.forEach { element ->
                        if (element.isJsonObject) {
                            val obj = element.asJsonObject
                            val url = obj.get("url")?.asString
                            val bitrate = obj.get("bitrate")?.asInt ?: 0
                            if (!url.isNullOrBlank() && url.startsWith("http") && bitrate >= highestBitrate) {
                                highestBitrate = bitrate
                                bestUrl = url
                            }
                        }
                    }

                    if (!bestUrl.isNullOrBlank()) {
                        return bestUrl
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped extraction attempt ($instance) failed: ${e.message}")
            }
        }
        return null
    }
}
