package com.jf.checkin

import android.app.Application
import com.jf.checkin.data.repository.HistoryRepository
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository
import com.jf.checkin.logic.TmsReminderScheduler

class CheckInApplication : Application() {

    lateinit var prefRepository: UserPrefRepository
        private set

    lateinit var studentRepository: StudentRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        prefRepository = UserPrefRepository(this)
        studentRepository = StudentRepository(prefRepository)
        historyRepository = HistoryRepository(this)

        // 每天 18:00 的未同步班级提醒，使用 WorkManager 在本地调度，不需要远程推送服务。
        if (prefRepository.tmsDailyReminderEnabled) {
            TmsReminderScheduler.schedule(this)
        } else {
            TmsReminderScheduler.cancel(this)
        }
    }
}
