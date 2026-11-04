package com.example.auralocalai.data.briefing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.auralocalai.AppLocale
import com.example.auralocalai.MainActivity
import com.example.auralocalai.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily background job: only downloads the news (light work) and shows a notification.
 * Summarizing with the local model happens when the user opens the briefing, because
 * loading a multi-GB model in the background drains the battery and is often killed by Android.
 */
class BriefingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = BriefingRepository(applicationContext.filesDir)
        val config = repository.loadConfig()
        if (!config.autoUpdateEnabled) return Result.success()

        val topics = config.topics.filter { it.enabled }
        if (topics.isEmpty()) return Result.success()
        val keywords = topics.flatMap { it.allKeywords }.distinctBy { it.lowercase() }

        val results = NewsFetcher().fetchAll(config.sources, keywords)
        val items = results.flatMap { it.items }
        if (items.isEmpty()) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        repository.saveCache(FetchCache(System.currentTimeMillis(), items))

        val matched = topics.sumOf { TopicMatcher.selectForTopic(items, it, config.maxItemsPerTopic).size }
        showReadyNotification(matched)
        return Result.success()
    }

    private fun showReadyNotification(articleCount: Int) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val localized = AppLocale.localized(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                localized.getString(R.string.briefing_notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_BRIEFING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_done)
            .setContentTitle(localized.getString(R.string.briefing_notif_title))
            .setContentText(localized.getString(R.string.briefing_notif_text, articleCount))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "daily_briefing_channel"
        const val NOTIFICATION_ID = 2001
    }
}

object BriefingScheduler {
    private const val WORK_NAME = "daily_briefing_fetch"

    /** Schedules (or cancels) the daily news download at [BriefingConfig.autoUpdateHour]. */
    fun apply(context: Context, config: BriefingConfig) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!config.autoUpdateEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, config.autoUpdateHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delayMs = next.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<BriefingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
