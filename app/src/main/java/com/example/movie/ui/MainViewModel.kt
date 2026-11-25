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

    /**
     * 加入 / 移出看单。
     *
     * 不再依赖 watchlistIds 这个 StateFlow 的当前值做判断 ——
     * WhileSubscribed 的流在收集者短暂脱离（弹窗重组、切页）时可能仍是空列表，
     * 导致「移出看单」被误判成「加入看单」，onConflict = REPLACE 直接把数据写回去，
     * 表现为「主 App 删不掉看单」。
     *
     * 改为直接查库（source of truth），保证删除一定执行。
     */
    fun toggleWatchlist(item: TmdbItem) {
        viewModelScope.launch {
            try {
                if (repo.exists(item.id)) repo.remove(item.id) else repo.add(item)
            } catch (e: Exception) {
                error.value = "操作看单失败：${e.message}"
            }
        }
    }

    /** 明确移出看单（详情弹窗、看单页长按/滑动都用这个，不再靠 toggle 猜） */
    fun removeFromWatchlist(id: Int) {
        viewModelScope.launch {
            try {
                repo.remove(id)
            } catch (e: Exception) {
                error.value = "移出看单失败：${e.message}"
            }
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
