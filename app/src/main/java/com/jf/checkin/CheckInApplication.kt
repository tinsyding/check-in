package com.jf.checkin

import android.app.Application
import com.jf.checkin.data.repository.HistoryRepository
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository

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
    }
}
