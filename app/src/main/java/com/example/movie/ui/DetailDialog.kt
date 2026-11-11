package com.example.movie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.movie.data.api.TmdbItem
import kotlin.math.round

/**
 * 详情弹窗：简介 + 我的评分 + 加入/移出看单 + 上映提醒开关
 */
@Composable
fun DetailDialog(
    item: TmdbItem,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    val watchlist by vm.watchlist.collectAsState()
    val saved = watchlist.find { it.id == item.id }
    val inWatchlist = saved != null

    var rating by remember(saved?.id) { mutableFloatStateOf(saved?.userRating ?: 0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                vm.toggleWatchlist(item)
                onDismiss()
            }) {
                Text(if (inWatchlist) "移出看单" else "加入看单")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("${item.displayTitle}（${item.typeLabel}）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                item.displayDate?.let {
                    Text("上映/首播：$it", style = MaterialTheme.typography.bodySmall)
                }
                Text("TMDb 评分：★ %.1f".format(item.voteAverage), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    item.overview?.ifBlank { "暂无简介" } ?: "暂无简介",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                )

                if (inWatchlist) {
                    Spacer(Modifier.height(16.dp))
                    Text("我的评分：${if (rating <= 0f) "未评分" else "%.1f".format(rating)}")
                    Slider(
                        value = rating,
                        onValueChange = { rating = round(it * 2) / 2 },  // 0.5 步进
                        onValueChangeFinished = { vm.rate(item.id, if (rating <= 0f) null else rating) },
                        valueRange = 0f..10f,
                        steps = 19
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("上映提醒", Modifier.weight(1f))
                        Switch(
                            checked = saved!!.remindEnabled,
                            onCheckedChange = { vm.setRemind(item.id, it) }
                        )
                    }
                }
            }
        }
    )
}
