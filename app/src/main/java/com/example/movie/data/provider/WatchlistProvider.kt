package com.example.movie.data.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.example.movie.data.db.AppDatabase
import com.example.movie.data.db.WatchlistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 把主 App 的「看单」数据开放给伴生模块（另一个 APK）。
 *
 * 访问方式：content://com.example.movie.provider/watchlist
 *   - query  读取全部 / 单条
 *   - insert 新增一条看单
 *   - update 修改评分或提醒开关
 *   - delete 删除一条 / 清空
 */
class WatchlistProvider : ContentProvider() {

    companion object {
        private const val TAG = "WatchlistProvider"

        const val AUTHORITY = "com.example.movie.provider"
        const val PATH_WATCHLIST = "watchlist"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_WATCHLIST")

        private const val CODE_LIST = 1
        private const val CODE_ITEM = 2

        // 对外暴露的列名，伴生模块按这些名字读
        const val COL_ID = "id"
        const val COL_MEDIA_TYPE = "mediaType"
        const val COL_TITLE = "title"
        const val COL_POSTER_PATH = "posterPath"
        const val COL_OVERVIEW = "overview"
        const val COL_RELEASE_DATE = "releaseDate"
        const val COL_VOTE_AVERAGE = "voteAverage"
        const val COL_USER_RATING = "userRating"
        const val COL_REMIND = "remindEnabled"
        const val COL_ADDED_AT = "addedAt"

        val ALL_COLUMNS = arrayOf(
            COL_ID, COL_MEDIA_TYPE, COL_TITLE, COL_POSTER_PATH, COL_OVERVIEW,
            COL_RELEASE_DATE, COL_VOTE_AVERAGE, COL_USER_RATING, COL_REMIND, COL_ADDED_AT
        )
    }

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, PATH_WATCHLIST, CODE_LIST)
        addURI(AUTHORITY, "$PATH_WATCHLIST/#", CODE_ITEM)
    }

    private fun dao(context: Context) = AppDatabase.get(context).watchlistDao()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val ctx = requireNotNull(context) { "ContentProvider 未初始化" }
        val columns = projection ?: ALL_COLUMNS

        return try {
            val items: List<WatchlistEntity> = runBlocking(Dispatchers.IO) {
                when (matcher.match(uri)) {
                    CODE_LIST -> dao(ctx).getAll()
                    CODE_ITEM -> {
                        val id = uri.lastPathSegment?.toIntOrNull()
                            ?: throw IllegalArgumentException("非法 id：${uri.lastPathSegment}")
                        listOfNotNull(dao(ctx).getAll().firstOrNull { it.id == id })
                    }
                    else -> throw IllegalArgumentException("未知 URI：$uri")
                }
            }

            MatrixCursor(columns).apply {
                items.forEach { item ->
                    addRow(
                        columns.map { col ->
                            when (col) {
                                COL_ID -> item.id
                                COL_MEDIA_TYPE -> item.mediaType
                                COL_TITLE -> item.title
                                COL_POSTER_PATH -> item.posterPath
                                COL_OVERVIEW -> item.overview
                                COL_RELEASE_DATE -> item.releaseDate
                                COL_VOTE_AVERAGE -> item.voteAverage
                                COL_USER_RATING -> item.userRating
                                COL_REMIND -> if (item.remindEnabled) 1 else 0
                                COL_ADDED_AT -> item.addedAt
                                else -> null
                            }
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "query 失败：$uri", e)
            throw IllegalStateException("读取看单失败：${e.message}", e)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val ctx = requireNotNull(context) { "ContentProvider 未初始化" }
        if (values == null) throw IllegalArgumentException("ContentValues 不能为空")
        if (matcher.match(uri) != CODE_LIST) throw IllegalArgumentException("新增只支持 $CONTENT_URI")

        val id = values.getAsInteger(COL_ID)
            ?: throw IllegalArgumentException("缺少必填字段 $COL_ID")
        val title = values.getAsString(COL_TITLE)
            ?: throw IllegalArgumentException("缺少必填字段 $COL_TITLE")

        return try {
            val entity = WatchlistEntity(
                id = id,
                mediaType = values.getAsString(COL_MEDIA_TYPE) ?: "movie",
                title = title,
                posterPath = values.getAsString(COL_POSTER_PATH),
                overview = values.getAsString(COL_OVERVIEW),
                releaseDate = values.getAsString(COL_RELEASE_DATE),
                voteAverage = values.getAsDouble(COL_VOTE_AVERAGE) ?: 0.0,
                userRating = values.getAsFloat(COL_USER_RATING),
                remindEnabled = (values.getAsInteger(COL_REMIND) ?: 1) == 1,
                addedAt = values.getAsLong(COL_ADDED_AT) ?: System.currentTimeMillis()
            )
            runBlocking(Dispatchers.IO) { dao(ctx).upsert(entity) }
            val newUri = ContentUris.withAppendedId(CONTENT_URI, id.toLong())
            ctx.contentResolver.notifyChange(CONTENT_URI, null)
            newUri
        } catch (e: Exception) {
            Log.e(TAG, "insert 失败", e)
            throw IllegalStateException("新增看单失败：${e.message}", e)
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val ctx = requireNotNull(context) { "ContentProvider 未初始化" }
        if (values == null || values.size() == 0) return 0
        if (matcher.match(uri) != CODE_ITEM) {
            throw IllegalArgumentException("修改只支持 content://$AUTHORITY/$PATH_WATCHLIST/{id}")
        }
        val id = uri.lastPathSegment?.toIntOrNull()
            ?: throw IllegalArgumentException("非法 id：${uri.lastPathSegment}")

        return try {
            var changed = 0
            runBlocking(Dispatchers.IO) {
                if (values.containsKey(COL_USER_RATING)) {
                    dao(ctx).updateRating(id, values.getAsFloat(COL_USER_RATING))
                    changed++
                }
                if (values.containsKey(COL_REMIND)) {
                    dao(ctx).updateRemind(id, (values.getAsInteger(COL_REMIND) ?: 0) == 1)
                    changed++
                }
                if (values.containsKey(COL_TITLE)) {
                    val old = dao(ctx).getAll().firstOrNull { it.id == id }
                    if (old != null) {
                        dao(ctx).upsert(old.copy(title = values.getAsString(COL_TITLE) ?: old.title))
                        changed++
                    }
                }
            }
            ctx.contentResolver.notifyChange(CONTENT_URI, null)
            changed
        } catch (e: Exception) {
            Log.e(TAG, "update 失败：$uri", e)
            throw IllegalStateException("修改看单失败：${e.message}", e)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val ctx = requireNotNull(context) { "ContentProvider 未初始化" }

        return try {
            when (matcher.match(uri)) {
                CODE_ITEM -> {
                    val id = uri.lastPathSegment?.toIntOrNull()
                        ?: throw IllegalArgumentException("非法 id：${uri.lastPathSegment}")
                    var existed = false
                    runBlocking(Dispatchers.IO) {
                        existed = dao(ctx).getAll().any { it.id == id }
                        if (existed) dao(ctx).delete(id)
                    }
                    ctx.contentResolver.notifyChange(CONTENT_URI, null)
                    if (existed) 1 else 0
                }
                CODE_LIST -> {
                    var count = 0
                    runBlocking(Dispatchers.IO) {
                        val all = dao(ctx).getAll()
                        count = all.size
                        all.forEach { dao(ctx).delete(it.id) }
                    }
                    ctx.contentResolver.notifyChange(CONTENT_URI, null)
                    count
                }
                else -> throw IllegalArgumentException("未知 URI：$uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "delete 失败：$uri", e)
            throw IllegalStateException("删除看单失败：${e.message}", e)
        }
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        CODE_LIST -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_WATCHLIST"
        CODE_ITEM -> "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_WATCHLIST"
        else -> throw IllegalArgumentException("未知 URI：$uri")
    }
}
