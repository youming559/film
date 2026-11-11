package com.example.movie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.movie.data.api.TmdbItem

/** 发现页：热门榜单 + 搜索 */
@Composable
fun DiscoverScreen(vm: MainViewModel, onItemClick: (TmdbItem) -> Unit) {
    val trending by vm.trending.collectAsState()
    val results by vm.searchResults.collectAsState()
    val query by vm.query.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    val displayList = if (query.isBlank()) trending else results

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索电影 / 剧集") },
                singleLine = true
            )
            IconButton(onClick = { vm.search() }) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        }

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        Text(
            if (query.isBlank()) "本周热门" else "搜索结果",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayList, key = { "${it.type}_${it.id}" }) { item ->
                MovieCard(
                    title = item.displayTitle,
                    posterUrl = item.posterUrl,
                    subtitle = "★ %.1f".format(item.voteAverage),
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun MovieCard(title: String, posterUrl: String?, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            )
            Column(Modifier.padding(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
