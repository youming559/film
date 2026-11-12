package com.example.movie.companion.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.movie.companion.CompanionActivity
import com.example.movie.companion.R
import com.example.movie.companion.data.CompanionItem

/**
 * 通知构造工具：负责渠道创建 + 带操作按钮的通知。
 * 通知里的按钮不是打开 App，而是直接触发 [NotificationActionReceiver] 去改主 App 的数据。
 */
object NotificationHelper {

    const val CHANNEL_ID = "companion_reminder"
    private const val CHANNEL_NAME = "看单提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "看单中的影视作品即将上映时提醒你" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, CompanionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun actionIntent(context: Context, action: String, title: String, id: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_ID, id)
            putExtra(NotificationActionReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            id * 10 + action.hashCode().and(0xF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 上映提醒：带「删除该条」「评 8 分」两个直接操作按钮 */
    fun showReminder(context: Context, item: CompanionItem, daysLeft: Int?) {
        ensureChannel(context)

        val text = when {
            daysLeft == null -> "看单提醒 · TMDb ★%.1f".format(item.voteAverage)
            daysLeft == 0 -> "今天上映！"
            else -> "${daysLeft} 天后上映（${item.releaseDate}）"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("《${item.title}》")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n可直接在通知里删除该条或打分，操作会写回主 App。"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .addAction(
                android.R.drawable.ic_menu_delete,
                "删除该条",
                actionIntent(context, NotificationActionReceiver.ACTION_DELETE, item.title, item.id)
            )
            .addAction(
                android.R.drawable.star_on,
                "评 8 分",
                actionIntent(context, NotificationActionReceiver.ACTION_RATE, item.title, item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "打开看单",
                contentIntent(context)
            )
            .build()

        notifySafely(context, item.id, notification)
    }

    /** 看单为空时的提示 */
    fun showEmpty(context: Context) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("看单提醒")
            .setContentText("主 App 的看单还是空的，先去添加几部电影吧")
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        notifySafely(context, 1000, notification)
    }

    private fun notifySafely(context: Context, id: Int, notification: android.app.Notification) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // 用户拒绝了通知权限，静默跳过
        }
    }
}
