package com.musync.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.musync.app.data.local.database.dao.FavoritesDao
import com.musync.app.data.local.database.dao.PlaylistDao
import com.musync.app.data.local.database.dao.RecentlyPlayedDao
import com.musync.app.data.local.database.dao.TrackCacheDao
import com.musync.app.data.local.database.entity.CachedTrackEntity
import com.musync.app.data.local.database.entity.FavoriteEntity
import com.musync.app.data.local.database.entity.PlaylistEntity
import com.musync.app.data.local.database.entity.PlaylistItemEntity
import com.musync.app.data.local.database.entity.RecentlyPlayedEntity

import com.musync.app.data.local.database.dao.DownloadDao
import com.musync.app.data.local.database.entity.DownloadEntity
import com.musync.app.data.local.database.dao.UserDao
import com.musync.app.data.local.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        RecentlyPlayedEntity::class,
        CachedTrackEntity::class,
        DownloadEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MusyncDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun trackCacheDao(): TrackCacheDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: MusyncDatabase? = null

        fun getDatabase(context: Context): MusyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusyncDatabase::class.java,
                    "musync_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

