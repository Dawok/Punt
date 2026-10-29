package app.kickpocket.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.kickpocket.MainActivity
import app.kickpocket.PuntApplication
import app.kickpocket.R
import app.kickpocket.data.KickChannel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object LiveAlertPolicy {
    fun session(channel: KickChannel): String = if (!channel.isLive) "offline" else channel.liveSessionId ?: "live"
    fun shouldNotify(previous: String?, current: String): Boolean = previous != null && current != "offline" && previous != current
}

object LiveAlerts {
    private const val workName = "live-channel-alerts"
    private const val channelId = "live-streams"

    fun permitted(context: Context): Boolean = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun schedule(context: Context, restart: Boolean = false) {
        val app = context.applicationContext as PuntApplication
        val work = WorkManager.getInstance(context)
        if (app.preferences.preferences.value.liveAlertChannels.isEmpty() || !permitted(context)) {
            work.cancelUniqueWork(workName)
            return
        }
        val request = PeriodicWorkRequestBuilder<LiveAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        work.enqueueUniquePeriodicWork(workName,
            if (restart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun notify(context: Context, channel: KickChannel) {
        if (!permitted(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Live channels", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://kick.com/${channel.slug}"))
        val pending = PendingIntent.getActivity(context, channel.slug.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${channel.displayName} is live").setContentText(channel.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(channel.title))
            .setContentIntent(pending).setAutoCancel(true).build()
        // Permission is checked immediately above; it may still be revoked concurrently.
        try { manager.notify(channel.slug, 1, notification) } catch (_: SecurityException) { }
    }
}

class LiveAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PuntApplication
        if (!LiveAlerts.permitted(app)) return Result.success()
        val storage = app.getSharedPreferences("live-alert-state", Context.MODE_PRIVATE)
        var failed = false
        for (batch in app.preferences.preferences.value.liveAlertChannels.take(50).chunked(4)) {
            val results = coroutineScope { batch.map { selected -> async {
                try {
                    val channel = app.repository.channel(selected.slug)
                    val current = LiveAlertPolicy.session(channel)
                    val previous = storage.getString(selected.slug, null)
                    if (app.preferences.preferences.value.liveAlertChannels.any { it.slug == selected.slug }) {
                        if (LiveAlertPolicy.shouldNotify(previous, current)) LiveAlerts.notify(app, channel)
                        storage.edit().putString(selected.slug, current).apply()
                    }
                    true
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { false }
            } }.awaitAll() }
            if (results.any { !it }) failed = true
        }
        return if (failed) Result.retry() else Result.success()
    }
}
