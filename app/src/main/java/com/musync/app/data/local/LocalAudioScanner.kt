package com.musync.app.data.local

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Track

class LocalAudioScanner(private val context: Context) {

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun scanLocalAudio(): List<Track> {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // Broader selection to catch all valid audio files >= 10 seconds and size > 50KB
        val selection = "(${MediaStore.Audio.Media.DURATION} >= 10000 OR ${MediaStore.Audio.Media.IS_MUSIC} != 0) AND ${MediaStore.Audio.Media.SIZE} > 50000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val urisToQuery = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        )

        for (queryUri in urisToQuery) {
            try {
                context.contentResolver.query(
                    queryUri,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                    if (idColumn == -1 || titleColumn == -1) return@use

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Track"
                        val artistName = if (artistColumn != -1) {
                            cursor.getString(artistColumn)?.takeIf { it.isNotBlank() && !it.contains("<unknown>", true) } ?: "Local Artist"
                        } else "Local Artist"

                        val durationMs = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                        val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1L

                        val contentUri: Uri = ContentUris.withAppendedId(queryUri, id)

                        val artworkUri = if (albumId != -1L) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        } else null

                        tracks.add(
                            Track(
                                id = "local_${queryUri.lastPathSegment}_$id",
                                title = title,
                                artist = Artist(id = "local_artist_$id", name = artistName),
                                durationMs = durationMs,
                                streamUrl = contentUri.toString(),
                                artworkUrl = artworkUri,
                                genre = "Local"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalAudioScanner", "Error querying $queryUri: ${e.message}")
            }
        }

        // Deduplicate tracks by streamUrl or title+duration
        return tracks.distinctBy { "${it.title}_${it.durationMs}" }
    }
}

