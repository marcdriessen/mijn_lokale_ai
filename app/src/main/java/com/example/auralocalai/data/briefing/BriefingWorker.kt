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
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import android.content.pm.ServiceInfo
import com.example.auralocalai.data.translate.MlKitNewsTranslator
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.auralocalai.AppLocale
import com.example.auralocalai.AppSecurity
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
        val profileId = inputData.getString(KEY_PROFILE_ID)
        val profile = config.profiles.firstOrNull { it.id == profileId } ?: return Result.success()
        if (!profile.scheduleEnabled) return Result.success()
        val now = Calendar.getInstance()
        if (profile.startDateMillis > 0 && now.timeInMillis < profile.startDateMillis) return Result.success()
        if (profile.schedule == BriefingFrequency.WEEKDAYS) {
            val day = now.get(Calendar.DAY_OF_WEEK)
            if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) return Result.success()
        }
        if (profile.topics.none { it.enabled }) return Result.success()
        if (AppSecurity.isPrivacyMode(applicationContext)) return Result.success()

        val started = System.currentTimeMillis()
        // Keep the job alive longer than the normal 10 minutes when Android allows it.
        try {
            setForeground(foregroundInfo(profile))
        } catch (_: Exception) {
            // Not allowed right now (e.g. background start restrictions): continue within the normal limit.
        }

        val appLanguage = AppLocale.effectiveLanguage(applicationContext)
        val languageCode = BriefingGenerator.languageFor(profile, appLanguage)
        val languageName = AppLocale.languageNameForModel(languageCode) ?: "English"
        val translator = MlKitNewsTranslator()
        val model = BackgroundModel(applicationContext)
        try {
            // Background runs use the news feeds (web search needs the encrypted key and the UI).
            val modelLoaded = try { model.load() } catch (_: Exception) { false }
            val briefing = BriefingGenerator(repository, NewsFetcher(), translator).build(
                profile = profile,
                config = config,
                languageCode = languageCode,
                languageName = languageName,
                forceRefresh = true,
                model = if (modelLoaded) BriefingGenerator.ModelCall { system, prompt -> model.run(system, prompt) } else null,
                modelDeadline = started + MODEL_TIME_BUDGET_MS
            )
            if (briefing.articles.isEmpty()) {
                return if (runAttemptCount < 2) Result.retry() else Result.success()
            }
            repository.saveBriefing(briefing)
            runCatching { BriefingWidgetProvider.refresh(applicationContext) }
            if (profile.notify) showReadyNotification(briefing.articles.size, profile)
            return Result.success()
        } finally {
            translator.close()
            try { model.unload() } catch (_: Exception) {}
        }
    }

    private fun foregroundInfo(profile: BriefingProfile): ForegroundInfo {
        val context = applicationContext
        val localized = AppLocale.localized(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(PROGRESS_CHANNEL_ID, localized.getString(R.string.briefing_notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(profile.name)
            .setContentText(localized.getString(R.string.briefing_preparing))
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun showReadyNotification(articleCount: Int, profile: BriefingProfile) {
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
            putExtra(MainActivity.EXTRA_BRIEFING_PROFILE, profile.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + profile.id.hashCode().and(0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_done)
            .setContentTitle(profile.name.ifBlank { localized.getString(R.string.briefing_notif_title) })
            .setContentText(localized.getString(R.string.briefing_notif_text, articleCount))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID + profile.id.hashCode().and(0xFFFF), notification)
    }

    companion object {
        const val CHANNEL_ID = "daily_briefing_channel"
        const val NOTIFICATION_ID = 2001
        const val KEY_PROFILE_ID = "profile_id"
        const val PROGRESS_CHANNEL_ID = "daily_briefing_progress"
        const val PROGRESS_NOTIFICATION_ID = 2101
        /** Stop asking the model after this long, so the job finishes within Android's limits. */
        const val MODEL_TIME_BUDGET_MS = 8L * 60L * 1000L
        /** The background job starts this long before the chosen time, so the briefing is ready on time. */
        const val LEAD_TIME_MS = 30L * 60L * 1000L
    }
}

object BriefingScheduler {
    private const val TAG = "briefing_profile"
    private const val LEGACY_WORK_NAME = "daily_briefing_fetch"

    /** (Re)schedules a background news download for every profile with a schedule. */
    fun apply(context: Context, config: BriefingConfig) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        workManager.cancelAllWorkByTag(TAG)
        config.profiles.filter { it.scheduleEnabled }.forEach { profile ->
            val periodDays = if (profile.schedule == BriefingFrequency.WEEKLY) 7L else 1L
            // Start early so the briefing is finished (and the notification arrives) around the chosen time.
            val delayMs = nextRunAt(profile, Calendar.getInstance()) - BriefingWorker.LEAD_TIME_MS - System.currentTimeMillis()
            val request = PeriodicWorkRequestBuilder<BriefingWorker>(periodDays, TimeUnit.DAYS)
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(BriefingWorker.KEY_PROFILE_ID, profile.id).build())
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork("briefing_${profile.id}", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    /** Next moment the profile should run, on or after its start date. */
    fun nextRunAt(profile: BriefingProfile, now: Calendar): Long {
        val next = (now.clone() as Calendar).apply {
            if (profile.startDateMillis > timeInMillis) timeInMillis = profile.startDateMillis
            set(Calendar.HOUR_OF_DAY, profile.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, profile.minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (!next.after(now) || (profile.schedule == BriefingFrequency.WEEKLY && next.get(Calendar.DAY_OF_WEEK) != profile.weekday)) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }
}
