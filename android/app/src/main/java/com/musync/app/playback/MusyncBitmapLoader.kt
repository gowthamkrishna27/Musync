package com.musync.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.Coil
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.musync.app.core.image.ImageQualityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dedicated Media3 BitmapLoader for Musync.
 * 
 * Automatically upgrades, loads, and center-crops media artwork into pixel-perfect,
 * edge-to-edge square software bitmaps for the Android system notification bar,
 * lock screen media controls, and Bluetooth AVRCP displays without black letterbox bars.
 */
@OptIn(UnstableApi::class)
class MusyncBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/") || mimeType.isBlank()
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap != null) {
                    future.set(cropAndFitToSquare(bitmap))
                } else {
                    future.setException(IllegalArgumentException("Could not decode bitmap bytes"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            try {
                val rawUrl = uri.toString()
                val hqUrl = ImageQualityHelper.getHighQualityArtworkUrl(rawUrl)
                val imageLoader = Coil.imageLoader(context)
                
                val request = ImageRequest.Builder(context)
                    .data(hqUrl)
                    .allowHardware(false) // Must be Software bitmap for System Notification RemoteViews
                    .size(720, 720)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .build()

                val result = imageLoader.execute(request)
                val drawable = result.drawable

                if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                    future.set(cropAndFitToSquare(drawable.bitmap))
                } else if (drawable != null) {
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    future.set(cropAndFitToSquare(bmp))
                } else {
                    // Fallback to decode directly via HTTP if coil returned null drawable
                    val connection = java.net.URL(hqUrl).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        future.set(cropAndFitToSquare(bmp))
                    } else {
                        future.setException(IllegalStateException("Failed to load artwork bitmap from $uri"))
                    }
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (metadata.artworkData != null) {
            return decodeBitmap(metadata.artworkData!!)
        }
        if (metadata.artworkUri != null) {
            return loadBitmap(metadata.artworkUri!!)
        }
        return null
    }

    companion object {
        /**
         * Crops 16:9 widescreen or non-square thumbnails into a centered 1:1 square
         * to guarantee that notification bar media player displays full edge-to-edge artwork.
         */
        fun cropAndFitToSquare(src: Bitmap): Bitmap {
            val width = src.width
            val height = src.height
            if (width <= 0 || height <= 0 || width == height) return src

            val size = minOf(width, height)
            val xOffset = (width - size) / 2
            val yOffset = (height - size) / 2

            return try {
                Bitmap.createBitmap(src, xOffset, yOffset, size, size)
            } catch (e: Exception) {
                src
            }
        }
    }
}
