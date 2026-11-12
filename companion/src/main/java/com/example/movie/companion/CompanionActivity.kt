package com.example.movie.companion

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 伴生模块主界面：
 * 独立启动 → 通过 ContentResolver 读取/删除/评分主 App 的看单数据。
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

// ---------- 跨 App 数据访问层 ----------

private data class CompanionItem(
    val id: Int,
    val title: String,
    val mediaType: String,
    val releaseDate: String?,
    val voteAverage: Double,
    val userRating: Float?
)

private object WatchlistGateway {

    private const val TAG = "CompanionGateway"
    private const val AUTHORITY = "com.example.movie.provider"
    private val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/watchlist")

    /** 读：主 App 的全部看单 */
    suspend fun load(context: Context): List<CompanionItem> = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<CompanionItem>()
            context.contentResolver.query(
                CONTENT_URI,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                val typeIdx = cursor.getColumnIndexOrThrow("mediaType")
                val dateIdx = cursor.getColumnIndexOrThrow("releaseDate")
                val voteIdx = cursor.getColumnIndexOrThrow("voteAverage")
                val ratingIdx = cursor.getColumnIndexOrThrow("userRating")
                while (cursor.moveToNext()) {
                    list += CompanionItem(
                        id = cursor.getInt(idIdx),
                        title = cursor.getString(titleIdx) ?: "未命名",
                        mediaType = cursor.getString(typeIdx) ?: "movie",
                        releaseDate = cursor.getString(dateIdx),
                        voteAverage = cursor.getDouble(voteIdx),
                        userRating = if (cursor.isNull(ratingIdx)) null else cursor.getFloat(ratingIdx)
                    )
                }
            } ?: throw IllegalStateException("主 App 返回了空数据（Provider 可能被禁用）")
            list
        } catch (e: SecurityException) {
            Log.e(TAG, "读取被拒绝", e)
            throw IllegalStateException("没有权限读取主 App 数据：${e.message}", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Provider 不存在", e)
            throw IllegalStateException("找不到主 App 的数据源，请先安装并打开「影视追踪」", e)
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "读取失败", e)
            throw IllegalStateException("读取失败：${e.message}", e)
        }
    }

    /** 写：往主 App 的看单里加一条示例数据 */
    suspend fun addSample(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put("id", 900000 + (System.currentTimeMillis() % 1000).toInt())
                put("mediaType", "movie")
                put("title", "伴生模块添加的示例")
                put("posterPath", null as String?)
                put("overview", "这条数据由伴生模块写入，主 App 打开后可以看到")
                put("releaseDate", "2026-12-01")
                put("voteAverage", 7.5)
                put("remindEnabled", 1)
                put("addedAt", System.currentTimeMillis())
            }
            val uri = context.contentResolver.insert(CONTENT_URI, values)
                ?: throw IllegalStateException("主 App 未返回插入结果")
            "已写入主 App 看单：$uri"
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("找不到主 App 的数据源，请先安装并打开「影视追踪」", e)
        } catch (e: Exception) {
            Log.e(TAG, "写入失败", e)
            throw IllegalStateException("写入失败：${e.message}", e)
        }
    }

    /** 写：删除主 App 里的一条看单 */
    suspend fun delete(context: Context, id: Int): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(Uri.withAppendedPath(CONTENT_URI, id.toString()), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "删除失败", e)
            throw IllegalStateException("删除失败：${e.message}", e)
        }
    }

    /** 写：给主 App 里的条目打分（0~10） */
    suspend fun rate(context: Context, id: Int, rating: Float): Int = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply { put("userRating", rating) }
            context.contentResolver.update(Uri.withAppendedPath(CONTENT_URI, id.toString()), values, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "评分失败", e)
            throw IllegalStateException("评分失败：${e.message}", e)
        }
    }
}

// ---------- UI ----------

@Composable
private fun CompanionScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<CompanionItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

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
        Text("伴生模块 · 看单查看器", style = MaterialTheme.typography.titleLarge)
        Text(
            "数据来源：主 App 的 ContentProvider（content://com.example.movie.provider/watchlist）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                    "⚠ $it",
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
