package com.jf.checkin.data.repository

import android.content.Context
import android.content.SharedPreferences

class UserPrefRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jf_checkin_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USERNAME = "jf_username"
        private const val KEY_PASSWORD = "jf_password"
        private const val KEY_LAST_CSV_CONTENT = "jf_last_csv_content"
        private const val KEY_LAST_IMPORT_TIME = "jf_last_import_time"
        private const val KEY_TEACHER_NAME = "jf_teacher_name"
        private const val KEY_PRINTER_IP = "jf_printer_ip"
        private const val KEY_PRINTER_PORT = "jf_printer_port"
        private const val KEY_PRINTER_SIMPLEX = "jf_printer_simplex"
        private const val KEY_PRINTER_PROFILES = "jf_printer_profiles"
        private const val KEY_SELECTED_PRINTER_ID = "jf_selected_printer_id"
        private const val KEY_MANUAL_STUDENTS = "jf_manual_students"
        private const val KEY_STUDENT_REMARKS = "jf_student_remarks"
        private const val KEY_STUDENT_MOVES = "jf_student_moves"
    }

    fun getPrinterProfiles(): List<com.jf.checkin.data.model.PrinterProfile> {
        val jsonStr = prefs.getString(KEY_PRINTER_PROFILES, null)
        if (jsonStr.isNullOrBlank()) {
            val defaultList = listOf(
                com.jf.checkin.data.model.PrinterProfile(
                    id = "default_1",
                    name = "默认打印机",
                    ip = prefs.getString(KEY_PRINTER_IP, "172.16.100.222") ?: "172.16.100.222",
                    port = prefs.getInt(KEY_PRINTER_PORT, 9100)
                )
            )
            savePrinterProfiles(defaultList)
            return defaultList
        }
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.jf.checkin.data.model.PrinterProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.jf.checkin.data.model.PrinterProfile(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "未命名打印机"),
                        ip = obj.optString("ip", "172.16.100.222"),
                        port = obj.optInt("port", 9100)
                    )
                )
            }
            if (list.isEmpty()) {
                listOf(com.jf.checkin.data.model.PrinterProfile(id = "default_1", name = "默认打印机", ip = "172.16.100.222", port = 9100))
            } else list
        } catch (_: Exception) {
            listOf(com.jf.checkin.data.model.PrinterProfile(id = "default_1", name = "默认打印机", ip = "172.16.100.222", port = 9100))
        }
    }

    fun savePrinterProfiles(list: List<com.jf.checkin.data.model.PrinterProfile>) {
        val array = org.json.JSONArray()
        for (profile in list) {
            val obj = org.json.JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("ip", profile.ip)
                put("port", profile.port)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PRINTER_PROFILES, array.toString()).apply()
    }

    var selectedPrinterId: String
        get() = prefs.getString(KEY_SELECTED_PRINTER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_PRINTER_ID, value).apply()

    var printerIp: String
        get() = prefs.getString(KEY_PRINTER_IP, "172.16.100.222") ?: "172.16.100.222"
        set(value) = prefs.edit().putString(KEY_PRINTER_IP, value).apply()

    var printerPort: Int
        get() = prefs.getInt(KEY_PRINTER_PORT, 9100)
        set(value) = prefs.edit().putInt(KEY_PRINTER_PORT, value).apply()

    var printerSimplex: Boolean
        get() = prefs.getBoolean(KEY_PRINTER_SIMPLEX, true)
        set(value) = prefs.edit().putBoolean(KEY_PRINTER_SIMPLEX, value).apply()

    var teacherName: String
        get() = prefs.getString(KEY_TEACHER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TEACHER_NAME, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var lastCsvContent: String
        get() = prefs.getString(KEY_LAST_CSV_CONTENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CSV_CONTENT, value).apply()

    var lastImportTime: String
        get() = prefs.getString(KEY_LAST_IMPORT_TIME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_IMPORT_TIME, value).apply()

    fun saveCredentials(user: String, pass: String) {
        prefs.edit()
            .putString(KEY_USERNAME, user)
            .putString(KEY_PASSWORD, pass)
            .apply()
    }

    // ---- 手动添加的学生（跨班调入等），独立于 CSV 缓存持久化，重导 CSV 不丢失 ----
    fun getManualStudents(): List<com.jf.checkin.data.model.Student> {
        val jsonStr = prefs.getString(KEY_MANUAL_STUDENTS, null)
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.jf.checkin.data.model.Student>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.jf.checkin.data.model.Student(
                        studentId = obj.optString("studentId"),
                        name = obj.optString("name"),
                        classCode = obj.optString("classCode"),
                        classTime = obj.optString("classTime"),
                        classFormat = obj.optString("classFormat", "手动").ifBlank { "手动" },
                        status = try {
                            com.jf.checkin.data.model.AttendanceStatus.valueOf(obj.optString("status", "UNCHECKED"))
                        } catch (_: Exception) {
                            com.jf.checkin.data.model.AttendanceStatus.UNCHECKED
                        },
                        remark = obj.optString("remark", ""),
                        originalClassCode = obj.optString("originalClassCode", ""),
                        isManual = true
                    )
                )
            }
            list.filter { it.studentId.isNotBlank() && it.classCode.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveManualStudents(list: List<com.jf.checkin.data.model.Student>) {
        val array = org.json.JSONArray()
        for (s in list.filter { it.isManual }) {
            array.put(
                org.json.JSONObject().apply {
                    put("studentId", s.studentId)
                    put("name", s.name)
                    put("classCode", s.classCode)
                    put("classTime", s.classTime)
                    put("classFormat", s.classFormat)
                    put("status", s.status.name)
                    put("remark", s.remark)
                    put("originalClassCode", s.originalClassCode)
                }
            )
        }
        prefs.edit().putString(KEY_MANUAL_STUDENTS, array.toString()).apply()
    }

    // ---- 所有学生的备注（studentId -> remark），CSV 重导后仍保留 ----
    fun getStudentRemarks(): Map<String, String> {
        val jsonStr = prefs.getString(KEY_STUDENT_REMARKS, null)
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.optString(key, "") }
            map.filterValues { it.isNotBlank() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveStudentRemarks(map: Map<String, String>) {
        val obj = org.json.JSONObject()
        for ((k, v) in map) {
            if (k.isNotBlank() && v.isNotBlank()) obj.put(k, v)
        }
        prefs.edit().putString(KEY_STUDENT_REMARKS, obj.toString()).apply()
    }

    // ---- 跨班调动记录（studentId -> 目标班级号），CSV 重导后仍保留 ----
    fun getStudentMoves(): Map<String, String> {
        val jsonStr = prefs.getString(KEY_STUDENT_MOVES, null)
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.optString(key, "") }
            map.filterValues { it.isNotBlank() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveStudentMoves(map: Map<String, String>) {
        val obj = org.json.JSONObject()
        for ((k, v) in map) {
            if (k.isNotBlank() && v.isNotBlank()) obj.put(k, v)
        }
        prefs.edit().putString(KEY_STUDENT_MOVES, obj.toString()).apply()
    }
}
