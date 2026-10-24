package com.jf.checkin.logic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jf.checkin.R
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository
import com.jf.checkin.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** 每天 18:00 检查本机课表，对尚未同步校管家的班级发本地通知。 */
class TmsReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val prefRepository = UserPrefRepository(applicationContext)
        if (!prefRepository.tmsDailyReminderEnabled || !prefRepository.hasTmsCredentials()) {
            TmsReminderNotifier.cancel(applicationContext)
            return Result.success()
        }

        val students = StudentRepository(prefRepository).students.value
        val today = LocalDate.now()
        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = today,
            syncedClassNames = prefRepository.getTmsSyncedClassNames(today)
        )

        if (pending.isEmpty()) {
            TmsReminderNotifier.cancel(applicationContext)
        } else {
            TmsReminderNotifier.show(applicationContext, pending)
        }
        return Result.success()
    }
}

object TmsReminderScheduler {

    private const val WORK_NAME = "tms_daily_reminder_18"
    private const val REMINDER_HOUR = 18

    fun schedule(context: Context) {
        TmsReminderNotifier.ensureChannel(context)

        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(REMINDER_HOUR, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0L)

        val request = PeriodicWorkRequestBuilder<TmsReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        TmsReminderNotifier.cancel(context)
    }
}

object TmsReminderNotifier {

    private const val CHANNEL_ID = "tms_daily_reminder"
    private const val NOTIFICATION_ID = 1800

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "校管家签到提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每天 18:00 提醒今天尚未同步校管家的班级"
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, pending: List<PendingTmsClass>) {
        if (pending.isEmpty()) {
            cancel(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        ensureChannel(context)

        val names = pending.flatMap { it.classNames }
        val title = "今天还有 ${pending.size} 个班未同步校管家"
        val preview = names.take(4).joinToString("、") +
            if (names.size > 4) " 等 ${names.size} 个班型" else ""
        val fullText = pending.joinToString("\n") { item ->
            "${item.classTime}  ${item.classNames.joinToString("、")}"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_tms)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
