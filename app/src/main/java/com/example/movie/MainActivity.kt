package com.example.movie

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movie.data.api.TmdbItem
import com.example.movie.data.db.WatchlistEntity
import com.example.movie.ui.DetailDialog
import com.example.movie.ui.DiscoverScreen
import com.example.movie.ui.MainViewModel
import com.example.movie.ui.WatchlistScreen
import com.example.movie.ui.theme.MovieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MovieTheme {
                AppRoot()
                RequestNotificationPermission()
            }
        }
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<TmdbItem?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("发现") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("看单") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> DiscoverScreen(vm, onItemClick = { selected = it })
                else -> WatchlistScreen(vm, onItemClick = { selected = it.toTmdbItem() })
            }
        }
        selected?.let { item ->
            DetailDialog(item = item, vm = vm, onDismiss = { selected = null })
        }
    }
}

/** Android 13+ 需要动态申请通知权限 */
@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* 用户拒绝也不影响其他功能 */ }
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** 看单实体转回 TmdbItem 以便复用详情弹窗 */
private fun WatchlistEntity.toTmdbItem() = TmdbItem(
    id = id,
    title = if (mediaType == "movie") title else null,
    name = if (mediaType == "tv") title else null,
    mediaType = mediaType,
    posterPath = posterPath,
    overview = overview,
    releaseDate = if (mediaType == "movie") releaseDate else null,
    firstAirDate = if (mediaType == "tv") releaseDate else null,
    voteAverage = voteAverage
)
