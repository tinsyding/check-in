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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jf.checkin.R
import com.jf.checkin.data.remote.TmsAttendanceService
import com.jf.checkin.data.remote.TmsCredentials
import com.jf.checkin.data.repository.ScheduledTmsSync
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository
import com.jf.checkin.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 老师在上课前完成点名并点击考勤时，后台等待到上课开始时间自动执行校管家（TMS）同步。
 */
class TmsScheduledSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val classCode = inputData.getString(KEY_CLASS_CODE) ?: return Result.failure()
        val dateStr = inputData.getString(KEY_DATE) ?: return Result.failure()
        val targetTime = inputData.getString(KEY_TARGET_TIME) ?: ""
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: LocalDate.now()

        val prefRepo = UserPrefRepository(applicationContext)
        val username = prefRepo.tmsUsername
        val password = prefRepo.tmsPassword
        if (username.isBlank() || password.isBlank()) {
            TmsScheduledSyncNotifier.showFailure(
                applicationContext,
                classCode,
                "未配置校管家账号密码，自动考勤未执行"
            )
            prefRepo.removeScheduledTmsSync(classCode)
            return Result.failure()
        }

        val studentRepo = StudentRepository(prefRepo)
        val students = studentRepo.students.value.filter { it.classCode == classCode }
        if (students.isEmpty()) {
            TmsScheduledSyncNotifier.showFailure(
                applicationContext,
                classCode,
                "未找到班级学生数据，自动考勤未执行"
            )
            prefRepo.removeScheduledTmsSync(classCode)
            return Result.failure()
        }

        val tmsService = TmsAttendanceService()
        return try {
            val preview = tmsService.preview(
                students = students,
                classCode = classCode,
                date = date,
                credentials = TmsCredentials(username, password)
            )

            if (!preview.canSubmit) {
                val allPointed = preview.plans.isNotEmpty() && preview.plans.all { it.alreadyPointed }
                if (allPointed) {
                    val syncedNames = preview.plans.map { it.className }
                    prefRepo.markTmsSynced(date, syncedNames)
                    TmsScheduledSyncNotifier.showSkipped(
                        applicationContext,
                        classCode,
                        "课程已在校管家完成点名，无需重复提交"
                    )
                } else {
                    val errorMsg = preview.plans.firstOrNull { it.message != null }?.message
                        ?: "没有可提交的课程"
                    TmsScheduledSyncNotifier.showFailure(applicationContext, classCode, errorMsg)
                }
                prefRepo.removeScheduledTmsSync(classCode)
                return Result.success()
            }

            val results = tmsService.submit(preview, TmsCredentials(username, password))
            val syncedNames = results.filter { it.success }.map { it.className } +
                preview.plans.filter { it.alreadyPointed }.map { it.className }
            prefRepo.markTmsSynced(date, syncedNames)

            val allSuccess = results.isNotEmpty() && results.all { it.success }
            if (allSuccess) {
                val timeDesc = if (targetTime.isNotBlank()) " ($targetTime)" else ""
                TmsScheduledSyncNotifier.showSuccess(
                    applicationContext,
                    classCode,
                    "已自动完成班级 $classCode$timeDesc 考勤同步（${results.size} 个班型全部成功）"
                )
            } else {
                val failedNames = results.filter { !it.success }.joinToString("、") { it.className }
                TmsScheduledSyncNotifier.showFailure(
                    applicationContext,
                    classCode,
                    "部分班型考勤失败（$failedNames），请打开应用核对"
                )
            }
            prefRepo.removeScheduledTmsSync(classCode)
            Result.success()
        } catch (e: Exception) {
            TmsScheduledSyncNotifier.showFailure(
                applicationContext,
                classCode,
                "自动考勤异常: ${e.localizedMessage ?: "网络或接口错误"}"
            )
            prefRepo.removeScheduledTmsSync(classCode)
            Result.failure()
        }
    }

    companion object {
        const val KEY_CLASS_CODE = "class_code"
        const val KEY_DATE = "date"
        const val KEY_TARGET_TIME = "target_time"
    }
}

object TmsScheduledSyncScheduler {

    fun schedule(
        context: Context,
        classCode: String,
        date: LocalDate,
        targetDateTime: LocalDateTime
    ) {
        val now = LocalDateTime.now()
        val delayMillis = Duration.between(now, targetDateTime).toMillis().coerceAtLeast(0L)
        val workName = "tms_scheduled_sync_$classCode"
        val timeStr = targetDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

        val data = Data.Builder()
            .putString(TmsScheduledSyncWorker.KEY_CLASS_CODE, classCode)
            .putString(TmsScheduledSyncWorker.KEY_DATE, date.toString())
            .putString(TmsScheduledSyncWorker.KEY_TARGET_TIME, timeStr)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<TmsScheduledSyncWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(data)
            .addTag("tms_scheduled_sync")
            .addTag("class_$classCode")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )

        val prefRepo = UserPrefRepository(context)
        val targetEpoch = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        prefRepo.saveScheduledTmsSync(
            ScheduledTmsSync(
                classCode = classCode,
                date = date.toString(),
                targetTime = timeStr,
                targetEpochMillis = targetEpoch
            )
        )
    }

    fun cancel(context: Context, classCode: String) {
        val workName = "tms_scheduled_sync_$classCode"
        WorkManager.getInstance(context).cancelUniqueWork(workName)
        UserPrefRepository(context).removeScheduledTmsSync(classCode)
    }
}

object TmsScheduledSyncNotifier {

    private const val CHANNEL_ID = "tms_scheduled_attendance"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "校管家定时考勤",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "上课时间到达后的自动考勤结果提醒"
        }
        manager.createNotificationChannel(channel)
    }

    fun showSuccess(context: Context, classCode: String, message: String) {
        show(context, classCode, "校管家考勤同步成功", message)
    }

    fun showSkipped(context: Context, classCode: String, message: String) {
        show(context, classCode, "校管家考勤已跳过", message)
    }

    fun showFailure(context: Context, classCode: String, message: String) {
        show(context, classCode, "校管家考勤自动提交失败", message)
    }

    private fun show(context: Context, classCode: String, title: String, message: String) {
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = 2000 + (classCode.hashCode() and 0x7FFF)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_tms)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
