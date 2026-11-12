package com.example.movie.companion.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.movie.companion.data.WatchlistGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 通知按钮的接收器：不打开界面，直接通过 ContentResolver 改主 App 的数据。
 * - ACTION_DELETE：删除该条看单
 * - ACTION_RATE  ：把该条评分设为 8 分
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifyAction"
        const val ACTION_DELETE = "com.example.movie.companion.action.DELETE"
        const val ACTION_RATE = "com.example.movie.companion.action.RATE"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        private const val DEMO_RATING = 8.0f
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (id <= 0) {
            Log.w(TAG, "收到无效的条目 id：$id")
            return
        }

        // 广播接收器需要快速返回，这里用 runBlocking 完成一次轻量数据库操作
        val message = try {
            when (action) {
                ACTION_DELETE -> {
                    val deleted = runBlocking(Dispatchers.IO) {
                        WatchlistGateway.delete(context, id)
                    }
                    if (deleted > 0) {
                        cancelNotification(context, id)
                        "已从主 App 看单删除《$title》"
                    } else {
                        "删除失败：主 App 里没有《$title》"
                    }
                }

                ACTION_RATE -> {
                    runBlocking(Dispatchers.IO) {
                        WatchlistGateway.rate(context, id, DEMO_RATING)
                    }
                    cancelNotification(context, id)
                    "已给《$title》评 ${DEMO_RATING.toInt()} 分"
                }

                else -> {
                    Log.w(TAG, "未知操作：$action")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "通知操作失败：$action", e)
            "操作失败：${e.message}"
        }

        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun cancelNotification(context: Context, id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (e: SecurityException) {
            Log.w(TAG, "取消通知失败", e)
        }
    }
}
