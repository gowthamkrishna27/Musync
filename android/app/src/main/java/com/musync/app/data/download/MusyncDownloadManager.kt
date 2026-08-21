package com.musync.app.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.musync.app.data.local.database.dao.DownloadDao
import com.musync.app.data.local.database.entity.DownloadEntity
import com.musync.app.data.local.datastore.PreferencesManager
import com.musync.app.data.remote.UniversalMusicProvider
import com.musync.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadProgressState(
    val trackId: String,
    val status: String, // QUEUED, DOWNLOADING, COMPLETED, PAUSED, FAILED, CANCELLED
    val progress: Float, // 0.0 to 1.0
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
)

class MusyncDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val preferencesManager: PreferencesManager,
    private val universalMusicProvider: UniversalMusicProvider
) {
    companion object {
        private const val TAG = "MusyncDownloadManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _downloadStates = MutableStateFlow<Map<String, DownloadProgressState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadProgressState>> = _downloadStates.asStateFlow()

    private val downloadsDir: File by lazy {
        File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
    }

    private val artworksDir: File by lazy {
        File(context.filesDir, "artworks").apply { if (!exists()) mkdirs() }
    }

    /**
     * Enqueues and initiates a real MP4 audio download for [track].
     */
    fun download(track: Track) {
        val trackId = track.id
        if (activeJobs.containsKey(trackId)) {
            Log.d(TAG, "Download already active for track $trackId")
            return
        }

        val job = scope.launch {
            try {
                // 1. Check Wi-Fi Only Setting
                val isWifiOnly = preferencesManager.getDownloadWifiOnly()
                if (isWifiOnly && !isWifiConnected()) {
                    val errMsg = "Wi-Fi required for download"
                    updateState(trackId, "FAILED", 0f, errorMessage = errMsg)
                    downloadDao.insertOrUpdate(
                        DownloadEntity(
                            id = trackId,
                            title = track.title,
                            artistName = track.artist.name,
                            artistId = track.artist.id,
                            albumName = track.album?.name,
                            albumId = track.album?.id,
                            artworkUrl = track.artworkUrl,
                            localArtworkPath = null,
                            localFilePath = "",
                            status = "FAILED",
                            progress = 0f,
                            errorMessage = errMsg
                        )
                    )
                    return@launch
                }

                // 2. Check if already downloaded locally
                val cleanId = trackId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                val localFile = File(downloadsDir, "$cleanId.mp4")
                if (localFile.exists() && localFile.length() > 50_000) {
                    val existing = downloadDao.getDownload(trackId)
                    if (existing != null && existing.status == "COMPLETED") {
                        updateState(trackId, "COMPLETED", 1f, bytesDownloaded = localFile.length(), totalBytes = localFile.length())
                        return@launch
                    }
                }

                updateState(trackId, "QUEUED", 0f)

                // 3. Resolve Stream URL with Download Quality
                val downloadQuality = preferencesManager.getDownloadQuality()
                var streamUrl = universalMusicProvider.getStreamUrl(track)
                if (streamUrl.isNullOrBlank()) {
                    val cleanVideoId = trackId.removePrefix("yt_")
                    val baseUrl = preferencesManager.getBaseUrl()
                    streamUrl = "$baseUrl/stream?id=$cleanVideoId&quality=$downloadQuality"
                } else if (streamUrl.contains("/stream?id=") && !streamUrl.contains("&quality=")) {
                    streamUrl = "$streamUrl&quality=$downloadQuality"
                }

                updateState(trackId, "DOWNLOADING", 0.05f)

                // 4. Download and Cache Local Artwork
                var localArtworkPath: String? = null
                val artworkUrl = track.artworkUrl
                if (!artworkUrl.isNullOrBlank()) {
                    try {
                        val artworkFile = File(artworksDir, "$cleanId.jpg")
                        if (!artworkFile.exists() || artworkFile.length() == 0L) {
                            val artReq = Request.Builder().url(artworkUrl).build()
                            httpClient.newCall(artReq).execute().use { artResp ->
                                if (artResp.isSuccessful) {
                                    artResp.body?.byteStream()?.use { input ->
                                        FileOutputStream(artworkFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        }
                        if (artworkFile.exists() && artworkFile.length() > 0) {
                            localArtworkPath = artworkFile.absolutePath
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to download artwork offline: ${e.message}")
                    }
                }

                // 5. Download MP4 Audio Stream with Real Byte Progress and Auto-Retry
                var lastErr: Exception? = null
                var downloadSuccess = false

                for (attempt in 1..2) {
                    try {
                        val currentTargetUrl = streamUrl ?: run {
                            val cleanVideoId = trackId.removePrefix("yt_")
                            val baseUrl = preferencesManager.getBaseUrl()
                            "$baseUrl/stream?id=$cleanVideoId&quality=$downloadQuality"
                        }

                        val request = Request.Builder()
                            .url(currentTargetUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .header("Accept", "*/*")
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw IllegalStateException("Server returned HTTP ${response.code}")
                            }
                            val body = response.body ?: throw IllegalStateException("Empty audio response body")
                            val totalBytes = body.contentLength().takeIf { it > 0 } ?: (track.durationMs?.let { it * 16L } ?: 5_000_000L)
                            var bytesDownloaded = 0L

                            body.byteStream().use { input ->
                                FileOutputStream(localFile).use { output ->
                                    val buffer = ByteArray(32 * 1024)
                                    var read: Int
                                    var lastProgressTime = System.currentTimeMillis()

                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        bytesDownloaded += read

                                        val now = System.currentTimeMillis()
                                        if (now - lastProgressTime > 120) {
                                            lastProgressTime = now
                                            val progress = (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                                            updateState(trackId, "DOWNLOADING", progress, bytesDownloaded, totalBytes)
                                        }
                                    }
                                    output.flush()
                                }
                            }

                            // 6. Validation: verify file exists and is non-empty
                            if (!localFile.exists() || localFile.length() < 10_000) {
                                localFile.delete()
                                throw IllegalStateException("Downloaded file validation failed (corrupted or incomplete)")
                            }

                            downloadSuccess = true
                        }

                        if (downloadSuccess) {
                            break
                        }
                    } catch (e: Exception) {
                        lastErr = e
                        if (attempt < 2) {
                            kotlinx.coroutines.delay(1000)
                            val cleanVideoId = trackId.removePrefix("yt_")
                            val baseUrl = preferencesManager.getBaseUrl()
                            streamUrl = "$baseUrl/stream?id=$cleanVideoId&quality=$downloadQuality"
                        }
                    }
                }

                if (!downloadSuccess) {
                    throw lastErr ?: IllegalStateException("Download failed")
                }

                val finalSize = localFile.length()

                // 7. Save metadata into Room Database
                val downloadEntity = DownloadEntity(
                    id = trackId,
                    title = track.title,
                    artistName = track.artist.name,
                    artistId = track.artist.id,
                    albumName = track.album?.name,
                    albumId = track.album?.id,
                    artworkUrl = track.artworkUrl,
                    localArtworkPath = localArtworkPath,
                    localFilePath = localFile.absolutePath,
                    fileSizeBytes = finalSize,
                    durationMs = track.durationMs ?: 0L,
                    format = "mp4",
                    downloadedAt = System.currentTimeMillis(),
                    status = "COMPLETED",
                    progress = 1.0f,
                    errorMessage = null
                )
                downloadDao.insertOrUpdate(downloadEntity)

                updateState(trackId, "COMPLETED", 1.0f, finalSize, finalSize)
                Log.i(TAG, "✓ Download completed: '${track.title}' ($finalSize bytes) at ${localFile.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Download failed for track $trackId: ${e.message}", e)
                val errMsg = e.message ?: "Download failed"
                updateState(trackId, "FAILED", 0f, errorMessage = errMsg)
                downloadDao.updateStatusAndProgress(trackId, "FAILED", 0f, errMsg)
            } finally {
                activeJobs.remove(trackId)
            }
        }

        activeJobs[trackId] = job
    }

    fun cancel(trackId: String) {
        activeJobs[trackId]?.cancel()
        activeJobs.remove(trackId)
        updateState(trackId, "CANCELLED", 0f)
        scope.launch {
            val cleanId = trackId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val localFile = File(downloadsDir, "$cleanId.mp4")
            if (localFile.exists()) localFile.delete()
            downloadDao.deleteDownload(trackId)
        }
    }

    fun delete(trackId: String) {
        cancel(trackId)
        scope.launch {
            val cleanId = trackId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val localFile = File(downloadsDir, "$cleanId.mp4")
            if (localFile.exists()) localFile.delete()
            val artworkFile = File(artworksDir, "$cleanId.jpg")
            if (artworkFile.exists()) artworkFile.delete()
            downloadDao.deleteDownload(trackId)
            _downloadStates.update { current ->
                val next = current.toMutableMap()
                next.remove(trackId)
                next
            }
        }
    }

    fun clearAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _downloadStates.value = emptyMap()
        scope.launch {
            downloadsDir.listFiles()?.forEach { it.delete() }
            artworksDir.listFiles()?.forEach { it.delete() }
            downloadDao.clearAllDownloads()
        }
    }

    suspend fun getLocalFileForTrack(trackId: String): File? {
        val download = downloadDao.getDownload(trackId)
        if (download != null && download.status == "COMPLETED") {
            val file = File(download.localFilePath)
            if (file.exists() && file.length() > 10_000) {
                return file
            }
        }
        return null
    }

    private fun updateState(
        trackId: String,
        status: String,
        progress: Float,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = 0L,
        errorMessage: String? = null
    ) {
        _downloadStates.update { current ->
            current + (trackId to DownloadProgressState(
                trackId = trackId,
                status = status,
                progress = progress,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                errorMessage = errorMessage
            ))
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
