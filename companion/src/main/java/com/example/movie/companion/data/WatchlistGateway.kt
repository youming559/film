package com.example.movie.companion.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 主 App 看单条目（伴生模块视角的精简模型） */
data class CompanionItem(
    val id: Int,
    val title: String,
    val mediaType: String,
    val releaseDate: String?,
    val voteAverage: Double,
    val userRating: Float?
)

/**
 * 跨 App 数据访问层：全部通过 ContentResolver 访问主 App 的 ContentProvider。
 * 所有异常都转换成带中文说明的 IllegalStateException，方便 UI 直接展示。
 */
object WatchlistGateway {

    private const val TAG = "CompanionGateway"
    const val AUTHORITY = "com.example.movie.provider"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/watchlist")

    /** 读：主 App 的全部看单 */
    suspend fun load(context: Context): List<CompanionItem> = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<CompanionItem>()
            context.contentResolver.query(CONTENT_URI, null, null, null, null)?.use { cursor ->
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

    /** 写：开关单条的上映提醒 */
    suspend fun setRemind(context: Context, id: Int, enabled: Boolean): Int = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply { put("remindEnabled", if (enabled) 1 else 0) }
            context.contentResolver.update(Uri.withAppendedPath(CONTENT_URI, id.toString()), values, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "设置提醒失败", e)
            throw IllegalStateException("设置提醒失败：${e.message}", e)
        }
    }
}
