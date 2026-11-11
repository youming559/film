package com.example.movie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.movie.data.WatchlistRepository
import com.example.movie.data.api.TmdbItem
import com.example.movie.data.db.AppDatabase
import com.example.movie.data.db.WatchlistEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WatchlistRepository(AppDatabase.get(app).watchlistDao())

    val trending = MutableStateFlow<List<TmdbItem>>(emptyList())
    val searchResults = MutableStateFlow<List<TmdbItem>>(emptyList())
    val query = MutableStateFlow("")
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    val watchlist: StateFlow<List<WatchlistEntity>> =
        repo.watchlist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchlistIds: StateFlow<List<Int>> =
        repo.watchlistIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadTrending()
    }

    fun loadTrending() = run("加载热门失败，请检查网络/代理") {
        trending.value = repo.trending()
    }

    fun search() {
        val q = query.value.trim()
        if (q.isEmpty()) {
            loadTrending()
            return
        }
        run("搜索失败，请检查网络/代理") {
            searchResults.value = repo.search(q)
        }
    }

    fun toggleWatchlist(item: TmdbItem) {
        viewModelScope.launch {
            if (watchlistIds.value.contains(item.id)) repo.remove(item.id)
            else repo.add(item)
        }
    }

    fun rate(id: Int, rating: Float?) {
        viewModelScope.launch { repo.rate(id, rating) }
    }

    fun setRemind(id: Int, enabled: Boolean) {
        viewModelScope.launch { repo.setRemind(id, enabled) }
    }

    fun clearError() {
        error.value = null
    }

    private fun run(errorMsg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                block()
            } catch (e: Exception) {
                error.value = "$errorMsg：${e.message}"
            } finally {
                loading.value = false
            }
        }
    }
}
