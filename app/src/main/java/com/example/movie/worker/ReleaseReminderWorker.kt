package com.example.movie.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.movie.data.db.AppDatabase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 每日检查看单，3 天内上映/开播的条目发通知
 */
class ReleaseReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "release_reminder"
    }

    override suspend fun doWork(): Result {
        if (ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val today = LocalDate.now()
        val items = AppDatabase.get(applicationContext).watchlistDao().getAll()
        val nm = NotificationManagerCompat.from(applicationContext)

        items.filter { it.remindEnabled }.forEach { item ->
            val date = item.releaseDate
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@forEach
            val days = ChronoUnit.DAYS.between(today, date)
            if (days in 0..3) {
                val text = when (days) {
                    0L -> "今天上映，别错过！"
                    1L -> "明天上映（${item.releaseDate}）"
                    else -> "${days} 天后上映（${item.releaseDate}）"
                }
                nm.notify(
                    item.id,
                    NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("《${item.title}》即将上映")
                        .setContentText(text)
                        .setAutoCancel(true)
                        .build()
                )
            }
        }
        return Result.success()
    }
}
