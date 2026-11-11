package com.example.movie

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.movie.data.api.TmdbClient
import com.example.movie.worker.ReleaseReminderWorker
import java.util.concurrent.TimeUnit

class MovieApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleReleaseCheck()
    }

    /** Coil 全局 ImageLoader：复用带代理设置的 OkHttp，海报自动走内存+磁盘缓存 */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .callFactory { TmdbClient.okHttp }  // Coil 2.x 内置 OkHttp 网络层
            .respectCacheHeaders(false)   // TMDb 海报 URL 内容不变，激进缓存省流量
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ReleaseReminderWorker.CHANNEL_ID,
            "上映提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "看单中的影视作品即将上映时通知你" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 每天检查一次看单中即将上映的条目 */
    private fun scheduleReleaseCheck() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "release_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReleaseReminderWorker>(1, TimeUnit.DAYS).build()
        )
    }
}
