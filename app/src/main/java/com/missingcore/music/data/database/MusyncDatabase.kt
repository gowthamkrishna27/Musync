package com.missingcore.music.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.missingcore.music.data.database.dao.FavoritesDao
import com.missingcore.music.data.database.dao.PlaylistDao
import com.missingcore.music.data.database.dao.RecentlyPlayedDao
import com.missingcore.music.data.database.dao.TrackCacheDao
import com.missingcore.music.data.database.entity.CachedTrackEntity
import com.missingcore.music.data.database.entity.FavoriteEntity
import com.missingcore.music.data.database.entity.PlaylistEntity
import com.missingcore.music.data.database.entity.PlaylistItemEntity
import com.missingcore.music.data.database.entity.RecentlyPlayedEntity

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        RecentlyPlayedEntity::class,
        CachedTrackEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusyncDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun trackCacheDao(): TrackCacheDao

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
