package com.example.movie.companion

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.movie.companion.data.CompanionItem
import com.example.movie.companion.data.WatchlistGateway
import com.example.movie.companion.reminder.ReminderScheduler
import kotlinx.coroutines.launch

/**
 * 伴生模块主界面：
 * 1) 读/写主 App 的看单（ContentResolver）
 * 2) 通知提醒中心（WorkManager 定时 + 通知内直接操作）
 */
class CompanionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    CompanionScreen()
                }
            }
        }
    }
}

@Composable
private fun CompanionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<CompanionItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var reminderOn by remember { mutableStateOf(ReminderScheduler.isEnabled(context)) }
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!notificationsEnabled) message = "没拿到通知权限，提醒只会静默失败"
    }

    // Android 13+ 首次进入就申请通知权限
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            rows = WatchlistGateway.load(context)
        } catch (e: Exception) {
            error = e.message ?: "未知错误"
            rows = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("伴生模块 · 看单助手", style = MaterialTheme.typography.titleLarge)
        Text(
            "数据来源：主 App 的 ContentProvider（content://${WatchlistGateway.AUTHORITY}/watchlist）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // ---------- 通知提醒中心 ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("通知提醒中心", style = MaterialTheme.typography.titleMedium)
                Text(
                    "每天检查一次看单，7 天内上映会自动推送；通知里的按钮可以直接删除或打分，不用打开 App。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (reminderOn) "状态：每日提醒已开启" else "状态：每日提醒未开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reminderOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!notificationsEnabled) {
                    Text(
                        "通知权限未开启，提醒不会显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = {
                        if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            message = "请先允许通知权限，再开启每日提醒"
                        } else {
                            ReminderScheduler.enableDaily(context)
                            reminderOn = true
                            message = "已开启每日提醒（1 分钟后会先跑一次）"
                        }
                    }) { Text("开启每日提醒") }

                    OutlinedButton(onClick = {
                        ReminderScheduler.runNow(context)
                        message = "已触发一次提醒检查，稍等通知"
                    }) { Text("立即提醒一次") }

                    TextButton(onClick = {
                        ReminderScheduler.disable(context)
                        reminderOn = false
                        message = "已关闭每日提醒"
                    }, enabled = reminderOn) { Text("关闭") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { refresh() } }) { Text("刷新") }
            OutlinedButton(onClick = {
                scope.launch {
                    message = null
                    try {
                        message = WatchlistGateway.addSample(context)
                        refresh()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            }) { Text("写入一条") }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Card {
                Text(
                    "错误：$it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(12.dp))

        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            rows.isEmpty() && error == null -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("主 App 的看单是空的", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("去「影视追踪」里加几部电影，再回来刷新", style = MaterialTheme.typography.bodySmall)
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { item ->
                    val typeLabel = if (item.mediaType == "tv") "剧集" else "电影"
                    val tmdbScore = "%.1f".format(item.voteAverage)
                    val myScore = item.userRating?.let { "我的 ★" + "%.1f".format(it) } ?: "未评分"
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$typeLabel · TMDb ★$tmdbScore · $myScore",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            item.releaseDate?.let {
                                Text("上映：$it", style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            WatchlistGateway.rate(context, item.id, 8.0f)
                                            message = "已给《${item.title}》评 8.0 分"
                                            refresh()
                                        } catch (e: Exception) {
                                            error = e.message
                                        }
                                    }
                                }) { Text("评 8 分") }

                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            val n = WatchlistGateway.delete(context, item.id)
                                            message = if (n > 0) "已从主 App 删除《${item.title}》" else "删除失败：条目不存在"
                                            refresh()
                                        } catch (e: Exception) {
                                            error = e.message
                                        }
                                    }
                                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}
