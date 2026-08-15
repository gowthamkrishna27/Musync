package com.musync.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.musync.app.BuildConfig
import com.musync.app.data.datastore.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersion: String = "",
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val changelog: String = "",
    val downloadUrl: String = "",
    val releaseDate: String = "",
    val fileName: String = "Musync-Update.apk"
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class Available(val info: UpdateInfo) : UpdateDownloadState()
    object UpToDate : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val GITHUB_API_URL = "https://api.github.com/repos/gowthamkrishna27/Musync/releases/latest"
        private const val GITHUB_REPO_URL = "https://github.com/gowthamkrishna27/Musync"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val _updateState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateState: StateFlow<UpdateDownloadState> = _updateState.asStateFlow()

    suspend fun checkForUpdates(silent: Boolean = false): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        if (!silent) _updateState.value = UpdateDownloadState.Checking

        try {
            val req = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Musync-App/${BuildConfig.VERSION_NAME}")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val obj = gson.fromJson(body, JsonObject::class.java)

                val tagName = obj.get("tag_name")?.asString ?: "v1.0.0"
                val rawVersion = tagName.removePrefix("v").trim()
                val changelog = obj.get("body")?.asString ?: "Bug fixes and performance improvements."
                val publishedAt = obj.get("published_at")?.asString?.take(10) ?: ""

                // Locate APK asset in release
                var downloadUrl = ""
                var fileName = "Musync.apk"
                val assets = obj.getAsJsonArray("assets")
                if (assets != null && assets.size() > 0) {
                    for (i in 0 until assets.size()) {
                        val asset = assets.get(i).asJsonObject
                        val name = asset.get("name")?.asString ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.get("browser_download_url")?.asString ?: ""
                            fileName = name
                            break
                        }
                    }
                }

                if (downloadUrl.isBlank()) {
                    downloadUrl = "$GITHUB_REPO_URL/releases/latest/download/Musync.apk"
                }

                val currentVer = BuildConfig.VERSION_NAME
                val isNewer = isVersionNewer(rawVersion, currentVer)

                val info = UpdateInfo(
                    isUpdateAvailable = isNewer,
                    latestVersion = rawVersion,
                    currentVersion = currentVer,
                    changelog = changelog,
                    downloadUrl = downloadUrl,
                    releaseDate = publishedAt,
                    fileName = fileName
                )

                if (isNewer) {
                    _updateState.value = UpdateDownloadState.Available(info)
                } else {
                    if (!silent) _updateState.value = UpdateDownloadState.UpToDate
                }

                Log.d(TAG, "Update check result: current=$currentVer, latest=$rawVersion, available=$isNewer")
                return@withContext Result.success(info)
            } else {
                // Fallback check against active backend server
                val baseUrl = preferencesManager.getBaseUrl()
                val serverUpdateUrl = "$baseUrl/update/check"
                try {
                    val serverReq = Request.Builder().url(serverUpdateUrl).build()
                    val serverResp = httpClient.newCall(serverReq).execute()
                    if (serverResp.isSuccessful) {
                        val sBody = serverResp.body?.string() ?: ""
                        val sObj = gson.fromJson(sBody, JsonObject::class.java)
                        val sVer = sObj.get("version")?.asString ?: BuildConfig.VERSION_NAME
                        val sChangelog = sObj.get("changelog")?.asString ?: "General improvements"
                        val sUrl = sObj.get("download_url")?.asString ?: "$baseUrl/update/latest.apk"
                        val isNewer = isVersionNewer(sVer, BuildConfig.VERSION_NAME)

                        val sInfo = UpdateInfo(
                            isUpdateAvailable = isNewer,
                            latestVersion = sVer,
                            currentVersion = BuildConfig.VERSION_NAME,
                            changelog = sChangelog,
                            downloadUrl = sUrl,
                            releaseDate = ""
                        )
                        if (isNewer) _updateState.value = UpdateDownloadState.Available(sInfo)
                        else if (!silent) _updateState.value = UpdateDownloadState.UpToDate
                        return@withContext Result.success(sInfo)
                    }
                } catch (_: Exception) {}

                val err = "No newer updates found"
                if (!silent) _updateState.value = UpdateDownloadState.UpToDate
                return@withContext Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking updates: ${e.message}", e)
            if (!silent) _updateState.value = UpdateDownloadState.Error(e.message ?: "Failed to check for updates")
            return@withContext Result.failure(e)
        }
    }

    suspend fun downloadAndInstallUpdate(
        downloadUrl: String,
        fileName: String = "Musync-Update.apk"
    ) = withContext(Dispatchers.IO) {
        _updateState.value = UpdateDownloadState.Downloading(0, 0, 0)
        Log.d(TAG, "Starting OTA download from: $downloadUrl")

        try {
            val req = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Musync-App/${BuildConfig.VERSION_NAME}")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                throw Exception("Server returned HTTP ${resp.code}")
            }

            val body = resp.body ?: throw Exception("Empty response body from update server")
            val totalBytes = body.contentLength()

            val updatesDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val outputFile = File(updatesDir, fileName)
            if (outputFile.exists()) outputFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReportTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 200 || totalRead == totalBytes) {
                            lastReportTime = now
                            val progress = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else 0
                            _updateState.value = UpdateDownloadState.Downloading(progress, totalRead, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            Log.d(TAG, "✓ Download complete (${outputFile.length()} bytes). Launching installer...")
            _updateState.value = UpdateDownloadState.ReadyToInstall(outputFile)

            // Trigger Android Package Installer
            launchPackageInstaller(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            _updateState.value = UpdateDownloadState.Error("Download failed: ${e.message}")
        }
    }

    fun launchPackageInstaller(apkFile: File) {
        try {
            if (!apkFile.exists()) return

            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching package installer: ${e.message}", e)
            _updateState.value = UpdateDownloadState.Error("Cannot launch package installer: ${e.message}")
        }
    }

    fun resetState() {
        _updateState.value = UpdateDownloadState.Idle
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        return try {
            val rParts = remote.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val cParts = current.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(rParts.size, cParts.size)

            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            false
        } catch (_: Exception) {
            remote != current
        }
    }
}

