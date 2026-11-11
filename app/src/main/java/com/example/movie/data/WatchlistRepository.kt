package com.example.movie.data

import com.example.movie.data.api.TmdbClient
import com.example.movie.data.api.TmdbItem
import com.example.movie.data.db.toEntity
import com.example.movie.data.db.WatchlistDao
import com.example.movie.data.db.WatchlistEntity
import kotlinx.coroutines.flow.Flow

class WatchlistRepository(private val dao: WatchlistDao) {

    val watchlist: Flow<List<WatchlistEntity>> = dao.observeAll()
    val watchlistIds: Flow<List<Int>> = dao.observeIds()

    suspend fun trending(): List<TmdbItem> =
        TmdbClient.api.trending().results.filter { it.type == "movie" || it.type == "tv" }

    suspend fun search(query: String): List<TmdbItem> =
        TmdbClient.api.searchMulti(query).results.filter { it.type == "movie" || it.type == "tv" }

    suspend fun add(item: TmdbItem) = dao.upsert(item.toEntity())

    suspend fun remove(id: Int) = dao.delete(id)

    suspend fun rate(id: Int, rating: Float?) = dao.updateRating(id, rating)

    suspend fun setRemind(id: Int, enabled: Boolean) = dao.updateRemind(id, enabled)
}
