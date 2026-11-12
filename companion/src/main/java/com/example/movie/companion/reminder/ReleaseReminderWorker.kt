package com.example.movie.companion.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.movie.companion.data.WatchlistGateway
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * 定时提醒任务：每天检查一次主 App 的看单，把 7 天内要上映的条目做成通知。
 * 数据全部来自 ContentProvider，所以主 App 不需要运行，Provider 会把主 App 进程拉起来。
 */
class ReleaseReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ReleaseReminder"
        const val KEY_FORCE = "force_demo"   // 演示用：不管有没有临期条目都发一条
        private const val WINDOW_DAYS = 7L
        private const val MAX_NOTIFICATIONS = 3
    }

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        return try {
            NotificationHelper.ensureChannel(applicationContext)

            // 读主 App 数据（跨进程）
            val items = WatchlistGateway.load(applicationContext)
            val today = LocalDate.now()

            val upcoming = items.mapNotNull { item ->
                val raw = item.releaseDate ?: return@mapNotNull null
                val date = try {
                    LocalDate.parse(raw)
                } catch (e: DateTimeParseException) {
                    Log.w(TAG, "无法解析上映日期：$raw")
                    return@mapNotNull null
                }
                val days = ChronoUnit.DAYS.between(today, date)
                if (days in 0..WINDOW_DAYS) item to days.toInt() else null
            }.sortedBy { it.second }

            when {
                upcoming.isNotEmpty() -> upcoming.take(MAX_NOTIFICATIONS).forEach { (item, days) ->
                    NotificationHelper.showReminder(applicationContext, item, days)
                }

                force -> {
                    val first = items.firstOrNull()
                    if (first != null) {
                        NotificationHelper.showReminder(applicationContext, first, null)
                    } else {
                        NotificationHelper.showEmpty(applicationContext)
                    }
                }

                else -> Log.d(TAG, "近期没有待上映条目，本次不提醒")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "提醒任务失败（第 ${runAttemptCount + 1} 次）", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
