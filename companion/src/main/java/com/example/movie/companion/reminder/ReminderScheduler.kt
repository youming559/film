package com.example.movie.companion.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** 提醒任务的开关与手动触发 */
object ReminderScheduler {

    private const val UNIQUE_NAME = "companion_daily_reminder"
    private const val PREFS = "companion_prefs"
    private const val KEY_ENABLED = "reminder_enabled"

    /** 开启每日提醒（WorkManager 最小周期就是 15 分钟，这里用 1 天） */
    fun enableDaily(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            androidx.work.PeriodicWorkRequestBuilder<ReleaseReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.MINUTES)   // 方便演示：装完 1 分钟后就能收到
                .build()
        )
        prefs(context).edit().putBoolean(KEY_ENABLED, true).apply()
    }

    /** 立即跑一次（演示/测试用） */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReleaseReminderWorker>()
                .setInputData(workDataOf(ReleaseReminderWorker.KEY_FORCE to true))
                .build()
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
