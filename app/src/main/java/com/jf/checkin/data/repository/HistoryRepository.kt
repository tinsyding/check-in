package com.jf.checkin.data.repository

import android.content.Context
import com.jf.checkin.data.model.AttendanceRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryRepository(private val context: Context) {

    private val historyFile: File = File(context.filesDir, "attendance_history.json")
    private val _records = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val records: StateFlow<List<AttendanceRecord>> = _records.asStateFlow()

    init {
        loadRecords()
    }

    private fun loadRecords() {
        if (!historyFile.exists()) return
        try {
            val jsonStr = historyFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<AttendanceRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val snapshotObj = obj.optJSONObject("statusSnapshot")
                val snapshot = mutableMapOf<String, String>()
                if (snapshotObj != null) {
                    snapshotObj.keys().forEach { key ->
                        val v = snapshotObj.optString(key, "")
                        if (v.isNotBlank()) snapshot[key] = v
                    }
                }
                list.add(
                    AttendanceRecord(
                        id = obj.optString("id"),
                        classCode = obj.optString("classCode"),
                        classTime = obj.optString("classTime"),
                        teacherName = obj.optString("teacherName"),
                        recordTime = obj.optString("recordTime"),
                        totalCount = obj.optInt("totalCount"),
                        presentCount = obj.optInt("presentCount"),
                        lateCount = obj.optInt("lateCount"),
                        leaveCount = obj.optInt("leaveCount"),
                        absentCount = obj.optInt("absentCount"),
                        imagePath = obj.optString("imagePath"),
                        statusSnapshot = snapshot
                    )
                )
            }
            _records.value = list
        } catch (_: Exception) {
            // 容错处理
        }
    }

    private fun persistRecords(list: List<AttendanceRecord>) {
        try {
            val array = JSONArray()
            for (r in list) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("classCode", r.classCode)
                    put("classTime", r.classTime)
                    put("teacherName", r.teacherName)
                    put("recordTime", r.recordTime)
                    put("totalCount", r.totalCount)
                    put("presentCount", r.presentCount)
                    put("lateCount", r.lateCount)
                    put("leaveCount", r.leaveCount)
                    put("absentCount", r.absentCount)
                    put("imagePath", r.imagePath)
                    put("statusSnapshot", JSONObject().apply {
                        for ((k, v) in r.statusSnapshot) {
                            if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                        }
                    })
                }
                array.put(obj)
            }
            historyFile.writeText(array.toString())
        } catch (_: Exception) {
        }
    }

    fun addRecord(record: AttendanceRecord) {
        // 新记录插入到最前
        val updated = listOf(record) + _records.value
        _records.value = updated
        persistRecords(updated)
    }

    fun deleteRecord(id: String) {
        val updated = _records.value.filter { it.id != id }
        _records.value = updated
        persistRecords(updated)
    }
}
