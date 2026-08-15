package com.missingcore.music.data.api

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnDecryptor {
    private const val TAG = "JioSaavnDecryptor"
    private const val DES_KEY = "38343638"

    fun decryptMediaUrl(encryptedUrl: String?): String? {
        if (encryptedUrl.isNullOrBlank()) return null
        return try {
            val keyBytes = DES_KEY.toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val decryptedUrl = String(decryptedBytes, Charsets.UTF_8).trim()
            formatStreamUrl(decryptedUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt media URL: ${e.message}")
            null
        }
    }

    fun formatStreamUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        var url = rawUrl.trim()
        if (url.startsWith("http://")) {
            url = "https://" + url.removePrefix("http://")
        }
        // Upgrade preview to full song stream if possible
        if (url.contains("preview.saavncdn.com") && url.endsWith("_preview.mp3")) {
            url = url.replace("preview.saavncdn.com", "aac.saavncdn.com")
                .replace("_preview.mp3", "_320.mp4")
        } else if (url.endsWith("_96.mp4")) {
            url = url.replace("_96.mp4", "_320.mp4")
        }
        return url
    }
}
