package com.example.movie.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.movie.data.api.TmdbItem
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val id: Int,
    val mediaType: String,          // "movie" 或 "tv"
    val title: String,
    val posterPath: String?,
    val overview: String?,
    val releaseDate: String?,       // yyyy-MM-dd，用于上映提醒
    val voteAverage: Double,        // TMDb 评分
    val userRating: Float? = null,  // 我的评分 0~10
    val remindEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
) {
    val posterUrl: String? get() = posterPath?.let { "https://images.tmdb.org/t/p/w500$it" }
}

fun TmdbItem.toEntity() = WatchlistEntity(
    id = id,
    mediaType = type,
    title = displayTitle,
    posterPath = posterPath,
    overview = overview,
    releaseDate = displayDate,
    voteAverage = voteAverage
)

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist")
    suspend fun getAll(): List<WatchlistEntity>

    @Query("SELECT id FROM watchlist")
    fun observeIds(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE watchlist SET userRating = :rating WHERE id = :id")
    suspend fun updateRating(id: Int, rating: Float?)

    @Query("UPDATE watchlist SET remindEnabled = :enabled WHERE id = :id")
    suspend fun updateRemind(id: Int, enabled: Boolean)
}

@Database(entities = [WatchlistEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "film.db"
                ).build().also { instance = it }
            }
    }
}
