package com.example.auralocalai.data.monitor

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
import com.example.auralocalai.AppSecurity
import com.example.auralocalai.MainActivity
import com.example.auralocalai.R
import com.example.auralocalai.data.briefing.NewsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/** Stores the watched pages in the app's private files. */
class MonitorRepository(filesDir: File) {
    private val file = File(File(filesDir, "monitor").apply { mkdirs() }, "pages.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<WatchedPage> =
        runCatching { json.decodeFromString(ListSerializer(WatchedPage.serializer()), file.readText()) }.getOrDefault(emptyList())

    fun save(pages: List<WatchedPage>) {
        file.writeText(json.encodeToString(ListSerializer(WatchedPage.serializer()), pages))
    }

    /** Checks every enabled page once. Returns the names of pages with a real content change. */
    suspend fun checkAll(fetcher: NewsFetcher = NewsFetcher()): List<String> = withContext(Dispatchers.IO) {
        val changed = mutableListOf<String>()
        val updated = load().map { page ->
            if (!page.enabled) return@map page
            val text = runCatching { fetcher.fetchArticle(page.url, maxChars = PageMonitor.MAX_TEXT)?.text }.getOrNull()
            if (text == null) {
                page.copy(lastChecked = System.currentTimeMillis(), lastError = "fetch failed")
            } else {
                val (result, real) = PageMonitor.applyCheck(page, text, System.currentTimeMillis())
                if (real) changed += page.name
                result
            }
        }
        save(updated)
        changed
    }
}

/** Daily background check of watched guideline pages (light work: no model). */
class MonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (AppSecurity.isPrivacyMode(applicationContext)) return Result.success()
        val changed = MonitorRepository(applicationContext.filesDir).checkAll()
        if (changed.isNotEmpty()) notify(changed)
        return Result.success()
    }

    private fun notify(changed: List<String>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val localized = AppLocale.localized(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, localized.getString(R.string.monitor_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_BRIEFING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_done)
            .setContentTitle(localized.getString(R.string.monitor_notif_title))
            .setContentText(changed.joinToString(", "))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "guideline_monitor"
        const val NOTIFICATION_ID = 2201
        private const val WORK_NAME = "guideline_monitor"

        /** Daily check while there are watched pages; cancelled when the list is empty. */
        fun schedule(context: Context, hasPages: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!hasPages) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<MonitorWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
