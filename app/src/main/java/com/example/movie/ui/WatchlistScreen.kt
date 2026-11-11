package com.example.movie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.movie.data.db.WatchlistEntity

/** 我的看单页 */
@Composable
fun WatchlistScreen(vm: MainViewModel, onItemClick: (WatchlistEntity) -> Unit) {
    val list by vm.watchlist.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("我的看单（${list.size}）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("还没有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("去「发现」页搜索并加入看单吧", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(list, key = { it.id }) { item ->
                    val subtitle = item.userRating?.let { "我的评分 ★ %.1f".format(it) }
                        ?: item.releaseDate?.let { "上映：$it" }
                        ?: "★ %.1f".format(item.voteAverage)
                    MovieCard(
                        title = item.title,
                        posterUrl = item.posterUrl,
                        subtitle = subtitle,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}
