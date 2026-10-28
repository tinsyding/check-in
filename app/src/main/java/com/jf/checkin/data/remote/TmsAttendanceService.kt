package com.jf.checkin.data.remote

import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.round

/** 校管家（TMS）自动考勤使用的账号密码。只保存在本机 SharedPreferences 中。 */
data class TmsCredentials(val username: String, val password: String)

data class TmsCourseSummary(
    val id: String,
    val className: String,
    val startTime: String,
    val endTime: String,
    val finished: Int,
    val attendanceCount: Int,
    val lastAttendTime: String,
    val studentCount: Int,
    val unitCode: Int,
    val durationMinutes: Int,
    val minutesToTimes: Int,
    val standardMinutes: Double,
    val consumeAmount: Double,
    val isDynamicConsume: Boolean
)

data class TmsStudentMatch(
    val appStudentId: String,
    val appName: String,
    val appStatus: AttendanceStatus,
    val tmsStudentId: String,
    val tmsName: String
)

data class TmsCoursePlan(
    val className: String,
    val classFormat: String,
    val classTime: String,
    val courseId: String?,
    val startTime: String,
    val endTime: String,
    val alreadyPointed: Boolean,
    val matches: List<TmsStudentMatch>,
    val unmatchedAppStudents: List<Student>,
    val tmsOnlyStudentNames: List<String>,
    val message: String?,
    val attendanceDetailJson: String?,
    val costAmount: Double,
    val unitCode: Int
) {
    val displayTime: String
        get() = TmsAttendanceMapper.displayTime(startTime, endTime)

    val presentCount: Int
        get() = matches.count {
            it.appStatus == AttendanceStatus.PRESENT ||
                it.appStatus == AttendanceStatus.LATE ||
                it.appStatus == AttendanceStatus.ONLINE
        }

    val lateCount: Int get() = matches.count { it.appStatus == AttendanceStatus.LATE }

    val leaveCount: Int get() = matches.count { it.appStatus == AttendanceStatus.LEAVE }

    val absentCount: Int get() = matches.count { it.appStatus == AttendanceStatus.UNCHECKED }

    /**
     * 只要课程存在、未点过名、学员全部匹配且课消有效就可以提交。
     * 未到和请假也会按出勤提交并计费，因此课消金额必须大于 0。
     */
    val canSubmit: Boolean
        get() = courseId != null &&
            !alreadyPointed &&
            message == null &&
            matches.isNotEmpty() &&
            costAmount > 0.0

    /**
     * 解析本节课的开始日期时间。
     * 优先使用校管家 StartTime，若空则尝试本地课表 classTime。
     */
    fun getStartDateTime(date: LocalDate): LocalDateTime? {
        val raw = TmsAttendanceMapper.tmsTime(startTime).ifBlank {
            TmsAttendanceMapper.parseClassTime(classTime)?.start.orEmpty()
        }
        if (raw.isBlank()) return null
        val normalized = TmsAttendanceMapper.normalizeTime(raw)
        val localTime = runCatching { LocalTime.parse(normalized) }.getOrNull() ?: return null
        return date.atTime(localTime)
    }
}

data class TmsSyncPreview(
    val date: String,
    val plans: List<TmsCoursePlan>,
    val warnings: List<String>
) {
    val submittableCount: Int get() = plans.count { it.canSubmit }
    val canSubmit: Boolean get() = submittableCount > 0

    /**
     * 如果存在尚未开始的课程，返回最早的上课开始时间；
     * 若所有可提交课程均已开始或已过上课时间，则返回 null（表示可立即提交）。
     */
    fun earliestFutureStartTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val dateParsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        return plans.filter { it.canSubmit }
            .mapNotNull { it.getStartDateTime(dateParsed) }
            .filter { it.isAfter(now) }
            .minOrNull()
    }
}

data class TmsCourseResult(
    val className: String,
    val success: Boolean,
    val message: String
)

/**
 * 校管家自动考勤：登录 TMS、按“班级号+班型”匹配当天课程、读取课程学员，
 * 并把本地点名状态转换为校管家 SetAttendance 接口需要的字段。
 *
 * 注意：校管家的登录跳转会在 URL query 中携带密码，这里强制使用 HTTPS，
 * 并且所有异常信息都不会包含完整 URL。
 */
class TmsAttendanceService {

    @Volatile private var session: TmsSession? = null
    @Volatile private var schoolHost: String = ""
    @Volatile private var campusId: String = ""
    @Volatile private var cachedUsername: String = ""
    @Volatile private var cachedPassword: String = ""

    suspend fun preview(
        students: List<Student>,
        classCode: String,
        date: LocalDate,
        credentials: TmsCredentials
    ): TmsSyncPreview = withContext(Dispatchers.IO) {
        val classStudents = students.filter { it.classCode == classCode }
        if (classStudents.isEmpty()) {
            throw IllegalStateException("当前班级暂无学生数据")
        }

        // α1/β1 线上课程和 β2/β3 线下课程都同步到校管家。
        // 应用内线上学员默认是 ONLINE，同步时会按“已到并计费”处理；未到和请假学员同样按出勤计费提交。
        val syncGroups = classStudents
            .filter {
                it.classFormat != MANUAL_FORMAT &&
                    TmsAttendanceMapper.isSyncableFormat(it.classFormat)
            }
            .groupBy { it.classFormat }
            .toSortedMap()

        // “手动调入”的学生没有固定的校管家班级；如果校管家某节课的学员名单里恰好有他们，
        // 则按学号/姓名匹配进该课程，否则在预览里提示需要手工处理。
        val manualStudents = classStudents.filter { it.isManual || it.classFormat == MANUAL_FORMAT }

        if (syncGroups.isEmpty() && manualStudents.isEmpty()) {
            throw IllegalStateException("当前班级没有需要同步的学生（α1/β1/β2/β3）")
        }

        val activeSession = ensureSession(credentials)
        val courses = queryCourses(activeSession, date)
        val plans = mutableListOf<TmsCoursePlan>()
        val warnings = mutableListOf<String>()
        val matchedManualIds = mutableSetOf<String>()

        for ((format, groupStudents) in syncGroups) {
            val className = classCode + format
            val normalizedClassName = TmsAttendanceMapper.normalizeClassName(className)
            val classTime = groupStudents.firstOrNull()?.classTime ?: ""
            val candidates = courses.filter {
                TmsAttendanceMapper.normalizeClassName(it.className)
                    .equals(normalizedClassName, ignoreCase = true)
            }

            when {
                candidates.isEmpty() -> {
                    plans += emptyPlan(
                        className = className,
                        format = format,
                        classTime = classTime,
                        unmatched = groupStudents,
                        message = "校管家在 $date 没有找到该班级课程"
                    )
                }

                candidates.size > 1 -> {
                    val times = TmsAttendanceMapper.parseClassTime(classTime)
                    val timed = if (times == null) emptyList() else candidates.filter {
                        TmsAttendanceMapper.timeMatches(it.startTime, it.endTime, times)
                    }
                    if (timed.size == 1) {
                        plans += buildPlan(
                            session = activeSession,
                            course = timed.first(),
                            format = format,
                            classTime = classTime,
                            groupStudents = groupStudents,
                            manualStudents = manualStudents,
                            matchedManualIds = matchedManualIds,
                            warnings = warnings
                        )
                    } else {
                        plans += emptyPlan(
                            className = className,
                            format = format,
                            classTime = classTime,
                            unmatched = groupStudents,
                            message = "校管家找到多节同名课程，无法确定要同步哪一节"
                        )
                    }
                }

                else -> {
                    val course = candidates.first()
                    val times = TmsAttendanceMapper.parseClassTime(classTime)
                    if (times != null && !TmsAttendanceMapper.timeMatches(course.startTime, course.endTime, times)) {
                        warnings += "$className 校管家课程时间为 ${TmsAttendanceMapper.displayTime(course.startTime, course.endTime)}，与本地课表 $classTime 不完全一致，请核对"
                    }
                    plans += buildPlan(
                        session = activeSession,
                        course = course,
                        format = format,
                        classTime = classTime,
                        groupStudents = groupStudents,
                        manualStudents = manualStudents,
                        matchedManualIds = matchedManualIds,
                        warnings = warnings
                    )
                }
            }
        }

        val unmatchedManual = manualStudents.filter { it.studentId !in matchedManualIds }
        if (unmatchedManual.isNotEmpty()) {
            warnings += "以下手动调入学生未参与本次同步（未匹配或课程已跳过）：${
                unmatchedManual.joinToString("、") { it.name }
            }"
        }
        if (courses.isEmpty()) {
            warnings += "$date 校管家中没有课程，请确认同步日期是否正确"
        }

        TmsSyncPreview(date.toString(), plans, warnings)
    }

    suspend fun submit(
        preview: TmsSyncPreview,
        credentials: TmsCredentials
    ): List<TmsCourseResult> = withContext(Dispatchers.IO) {
        val activeSession = ensureSession(credentials)
        val results = mutableListOf<TmsCourseResult>()
        for (plan in preview.plans) {
            if (!plan.canSubmit) continue
            results += submitPlan(activeSession, plan)
        }
        results
    }

    private fun emptyPlan(
        className: String,
        format: String,
        classTime: String,
        unmatched: List<Student>,
        message: String
    ): TmsCoursePlan = TmsCoursePlan(
        className = className,
        classFormat = format,
        classTime = classTime,
        courseId = null,
        startTime = "",
        endTime = "",
        alreadyPointed = false,
        matches = emptyList(),
        unmatchedAppStudents = unmatched,
        tmsOnlyStudentNames = emptyList(),
        message = message,
        attendanceDetailJson = null,
        costAmount = 0.0,
        unitCode = 2
    )

    private fun alreadyPointedPlan(
        course: TmsCourseSummary,
        format: String,
        classTime: String,
        message: String
    ): TmsCoursePlan = TmsCoursePlan(
        className = course.className,
        classFormat = format,
        classTime = classTime,
        courseId = course.id,
        startTime = course.startTime,
        endTime = course.endTime,
        alreadyPointed = true,
        matches = emptyList(),
        unmatchedAppStudents = emptyList(),
        tmsOnlyStudentNames = emptyList(),
        message = message,
        attendanceDetailJson = null,
        costAmount = 0.0,
        unitCode = course.unitCode
    )

    private fun buildPlan(
        session: TmsSession,
        course: TmsCourseSummary,
        format: String,
        classTime: String,
        groupStudents: List<Student>,
        manualStudents: List<Student>,
        matchedManualIds: MutableSet<String>,
        warnings: MutableList<String>
    ): TmsCoursePlan {
        // 已经点过名的课程跳过；课程“已结束”但尚未点名时仍允许补点名。
        val everPointed = course.attendanceCount > 0 ||
            TmsAttendanceMapper.hasAttendanceTime(course.lastAttendTime)
        if (everPointed) {
            return alreadyPointedPlan(course, format, classTime, "校管家已点名，自动跳过")
        }

        val detail = fetchAttendance(session, course.id)
        val tmsStudents = detail.optJSONArray("students") ?: JSONArray()
        if (tmsStudents.length() == 0) {
            return TmsCoursePlan(
                className = course.className,
                classFormat = format,
                classTime = classTime,
                courseId = course.id,
                startTime = course.startTime,
                endTime = course.endTime,
                alreadyPointed = false,
                matches = emptyList(),
                unmatchedAppStudents = groupStudents,
                tmsOnlyStudentNames = emptyList(),
                message = "校管家课程中没有学员",
                attendanceDetailJson = detail.toString(),
                costAmount = TmsAttendanceMapper.computeCost(course),
                unitCode = course.unitCode
            )
        }

        // AttendanceCount 可能只统计出勤人数。若已有计费或请假记录，也不能覆盖。
        val hasExistingRecord = (0 until tmsStudents.length()).any { index ->
            val tmsStudent = tmsStudents.optJSONObject(index) ?: return@any false
            tmsStudent.optInt("IsAttend", 0) != 0 ||
                tmsStudent.optDouble("IsCost", 0.0) > 0.0 ||
                tmsStudent.optInt("IsAttendStauts", 0) == 1
        }
        if (hasExistingRecord) {
            return alreadyPointedPlan(
                course,
                format,
                classTime,
                "校管家已有部分点名/计费记录，自动跳过"
            )
        }
        if (course.finished == 1) {
            warnings += "${course.className} 课程已结束，本次将按补点名提交，请确认日期无误"
        }

        val usedTmsIds = mutableSetOf<String>()
        val matches = mutableListOf<TmsStudentMatch>()
        val unmatched = mutableListOf<Student>()

        for (appStudent in groupStudents) {
            val tmsStudent = findTmsStudent(tmsStudents, appStudent, usedTmsIds)
            if (tmsStudent == null) {
                unmatched += appStudent
            } else {
                val tmsId = tmsStudent.optString("ID")
                usedTmsIds += tmsId
                matches += TmsStudentMatch(
                    appStudentId = appStudent.studentId,
                    appName = appStudent.name,
                    appStatus = appStudent.status,
                    tmsStudentId = tmsId,
                    tmsName = tmsStudent.optString("Name")
                )
            }
        }

        // 手动调入学生：只在当前课程的校管家名单里找，找到才同步。
        for (manual in manualStudents) {
            if (manual.studentId in matchedManualIds) continue
            val tmsStudent = findTmsStudent(tmsStudents, manual, usedTmsIds) ?: continue
            val tmsId = tmsStudent.optString("ID")
            usedTmsIds += tmsId
            matchedManualIds += manual.studentId
            matches += TmsStudentMatch(
                appStudentId = manual.studentId,
                appName = manual.name,
                appStatus = manual.status,
                tmsStudentId = tmsId,
                tmsName = tmsStudent.optString("Name")
            )
        }

        val tmsOnlyNames = (0 until tmsStudents.length()).mapNotNull { index ->
            val item = tmsStudents.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("ID") in usedTmsIds) null else item.optString("Name")
        }

        val cost = TmsAttendanceMapper.computeCost(course)
        val presentMatches = matches.filter {
            it.appStatus == AttendanceStatus.PRESENT ||
                it.appStatus == AttendanceStatus.LATE ||
                it.appStatus == AttendanceStatus.ONLINE
        }
        var message: String? = null
        if (matches.isEmpty()) {
            message = "没有匹配到校管家学员"
        } else if (unmatched.isNotEmpty()) {
            message = "有 ${unmatched.size} 名学员未匹配到校管家名单，请先核对：${
                unmatched.joinToString("、") { it.name }
            }"
        } else if (cost <= 0.0) {
            message = "本课程课消数量为 0，无法自动提交，请手动处理"
        } else {
            val attendIds = matches.map { it.tmsStudentId }
            val conflicts = checkConflict(session, course.id, attendIds)
            if (conflicts.isNotEmpty()) {
                message = "检测到上课时间冲突：${conflicts.joinToString("、")}，请在网页端手动确认"
            }
        }

        if (matches.isNotEmpty() && presentMatches.isEmpty()) {
            warnings += "${course.className} 本次没有已到学生，未到/请假学员也将按出勤提交并计费"
        }

        if (tmsOnlyNames.isNotEmpty()) {
            warnings += "${course.className} 校管家名单中有 ${tmsOnlyNames.size} 人未在本地点名名单中，将保持原状：${
                tmsOnlyNames.joinToString("、")
            }"
        }

        return TmsCoursePlan(
            className = course.className,
            classFormat = format,
            classTime = classTime,
            courseId = course.id,
            startTime = course.startTime,
            endTime = course.endTime,
            alreadyPointed = false,
            matches = matches,
            unmatchedAppStudents = unmatched,
            tmsOnlyStudentNames = tmsOnlyNames,
            message = message,
            attendanceDetailJson = detail.toString(),
            costAmount = cost,
            unitCode = course.unitCode
        )
    }

    private fun submitPlan(session: TmsSession, plan: TmsCoursePlan): TmsCourseResult {
        return try {
            val detailJson = plan.attendanceDetailJson
                ?: return TmsCourseResult(plan.className, false, "缺少校管家课程数据")
            val detail = JSONObject(detailJson)
            val students = detail.optJSONArray("students") ?: JSONArray()
            val matchById = plan.matches.associateBy { it.tmsStudentId }

            for (index in 0 until students.length()) {
                val tmsStudent = students.optJSONObject(index) ?: continue
                val match = matchById[tmsStudent.optString("ID")] ?: continue
                applyStatus(tmsStudent, match.appStatus, plan.costAmount)
            }

            val cleanedStudents = JSONArray()
            for (index in 0 until students.length()) {
                val tmsStudent = students.optJSONObject(index) ?: continue
                cleanStudent(tmsStudent)
                cleanedStudents.put(tmsStudent)
            }

            val payload = buildPayload(detail, plan, cleanedStudents)
            val responseBody = session.postForm(
                "https://$schoolHost/api/Course/SetAttendance",
                linkedMapOf("data" to payload.toString()),
                mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
            val response = JSONObject(responseBody)
            val code = response.optInt("ErrorCode", -1)
            if (code != 200) {
                val message = friendlyError(
                    code,
                    response.optString("ErrorMsg"),
                    "校管家返回错误码 $code"
                )
                TmsCourseResult(plan.className, false, message)
            } else {
                val data = response.optJSONObject("Data")
                val exceptions = namesOf(data?.optJSONArray("ExceptionList"))
                val approvals = namesOf(data?.optJSONArray("ApprovalList"))
                val message = buildString {
                    append("同步成功")
                    if (exceptions.isNotEmpty()) {
                        append("；欠费未计费：").append(exceptions.joinToString("、"))
                    }
                    if (approvals.isNotEmpty()) {
                        append("；审批中未点名：").append(approvals.joinToString("、"))
                    }
                }
                TmsCourseResult(plan.className, true, message)
            }
        } catch (e: TmsHttpException) {
            TmsCourseResult(plan.className, false, e.friendlyMessage())
        } catch (e: Exception) {
            TmsCourseResult(plan.className, false, e.localizedMessage ?: "同步失败")
        }
    }

    private fun applyStatus(student: JSONObject, status: AttendanceStatus, cost: Double) {
        // 校管家同步统一按出勤处理：未到、请假也提交为已出勤并计费。
        when (status) {
            AttendanceStatus.PRESENT,
            AttendanceStatus.LATE,
            AttendanceStatus.ONLINE,
            AttendanceStatus.LEAVE,
            AttendanceStatus.UNCHECKED -> {
                student.put("IsAttend", 1)
                student.put("IsCost", cost)
                student.put("IsAttendStauts", 0)
                student.put("AbsentCauseID", "")
                student.put("AbsentCauseName", "")
            }
        }
        // 按天计费已经扣过费的学员，校管家网页端会把 IsCost 置 0，这里保持一致。
        if (student.optInt("IsDayFeeCost", 0) == 1) {
            student.put("IsCost", 0)
        }
        // 试听学员不扣课消。
        if (student.optInt("IsTry", 0) == 1) {
            student.put("IsCost", 0)
        }
        // 补课学员如果原课程已经计费，不重复计费。
        if (student.optInt("IsMend", 0) > 0 && student.optInt("AbsentIsCost", 0) == 1) {
            student.put("IsCost", 0)
        }
    }

    private fun cleanStudent(student: JSONObject) {
        for (key in DISPLAY_ONLY_STUDENT_KEYS) {
            student.remove(key)
        }
    }

    private fun buildPayload(
        detail: JSONObject,
        plan: TmsCoursePlan,
        students: JSONArray
    ): JSONObject {
        val teacher = findMainTeacher(detail)
        val teacherId = teacher?.optString("ID").orEmpty().ifBlank { EMPTY_GUID }
        val teacherCommissionIds = teacher?.optString("TeacherCommissionIDs").orEmpty()
        val shiftAmount = detail.optDouble("shiftAmount", 0.0).let {
            if (it.isNaN()) 0.0 else it
        }

        return JSONObject().apply {
            put("Students", students)
            put("CourseID", plan.courseId ?: EMPTY_GUID)
            put("Describe", detail.optString("describe"))
            put("Content", detail.optString("content"))
            put("shiftAmount", shiftAmount)
            put("ShiftScheduleID", findShiftScheduleId(detail, shiftAmount))
            put("UpdateTime", detail.optString("updateTime"))
            put("Action", 1)
            // 自动同步不发送微信/短信通知。
            put("Weixin", 0)
            put("LastClasstime", TmsAttendanceMapper.lastClassTime(plan.startTime, plan.endTime))
            put("ComeFrom", 1)
            put("Agnet", 0)
            put("IsEditTeacher", if (teacher != null && teacherId != EMPTY_GUID) 0 else 1)
            put("TeacherID", teacherId)
            put("TeacherCommissionIDs", teacherCommissionIds)
        }
    }

    private fun findMainTeacher(detail: JSONObject): JSONObject? {
        val teachers = detail.optJSONArray("Teacher") ?: return null
        for (index in 0 until teachers.length()) {
            val teacher = teachers.optJSONObject(index) ?: continue
            if (teacher.optInt("Role", 0) == 1) return teacher
        }
        return null
    }

    private fun findShiftScheduleId(detail: JSONObject, shiftAmount: Double): String {
        val list = detail.optJSONArray("shiftScheduleList") ?: return EMPTY_GUID
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val amount = item.optDouble("ShiftAmount", 0.0)
            if (amount == shiftAmount) {
                return item.optString("ID").ifBlank { EMPTY_GUID }
            }
        }
        return EMPTY_GUID
    }

    private fun findTmsStudent(
        tmsStudents: JSONArray,
        appStudent: Student,
        usedTmsIds: Set<String>
    ): JSONObject? {
        val appId = appStudent.studentId.trim().removePrefix("'")
        val appIdNumeric = appId.toLongOrNull()
        val appName = TmsAttendanceMapper.normalizeName(appStudent.name)

        // 先按学号精确匹配（支持去掉前导 0 后的数字比较）。
        for (index in 0 until tmsStudents.length()) {
            val candidate = tmsStudents.optJSONObject(index) ?: continue
            val candidateId = candidate.optString("ID")
            if (candidateId in usedTmsIds) continue

            val (tmsNumber, _) = TmsAttendanceMapper.studentNumberAndName(candidate.optString("Name"))
            if (appId.isNotBlank() && tmsNumber == appId) return candidate
            if (appIdNumeric != null && tmsNumber.toLongOrNull() == appIdNumeric) return candidate
        }

        // 学号匹配不上时才按姓名兜底；同名多人时不猜，交给老师手工处理。
        if (appName.isBlank()) return null
        val nameMatches = mutableListOf<JSONObject>()
        for (index in 0 until tmsStudents.length()) {
            val candidate = tmsStudents.optJSONObject(index) ?: continue
            val candidateId = candidate.optString("ID")
            if (candidateId in usedTmsIds) continue

            val rawName = candidate.optString("Name")
            val (_, tmsName) = TmsAttendanceMapper.studentNumberAndName(rawName)
            if (tmsName == appName || TmsAttendanceMapper.normalizeName(rawName) == appName) {
                nameMatches += candidate
            }
        }
        return nameMatches.singleOrNull()
    }

    private fun invalidateSession() {
        session = null
        schoolHost = ""
        campusId = ""
        cachedUsername = ""
        cachedPassword = ""
    }

    private fun friendlyError(code: Int, message: String, fallback: String): String {
        if (code == 302 || code == 401 || code == 403 || code == 407) {
            return "校管家登录已过期，请重新同步"
        }
        return message.ifBlank { fallback }
    }

    private fun queryCourses(session: TmsSession, date: LocalDate): List<TmsCourseSummary> {
        val params = linkedMapOf(
            "Campus" to campusId,
            "PageSize" to "10000",
            "PageIndex" to "1",
            "startDate" to date.toString(),
            "endDate" to date.toString(),
            "finished" to "-1",
            "shiftType" to "7",
            "byterm" to "1",
            "sort" to "StartTime",
            "desc" to "0",
            "Forenoon" to "1",
            "Afternoon" to "1",
            "Nightnoon" to "1",
            "IsContainFinished" to "0",
            "courseFlag" to ""
        )
        val response = try {
            session.getJson(
                "https://$schoolHost/api/Course/query?" + TmsAttendanceMapper.encodeForm(params)
            )
        } catch (e: Exception) {
            invalidateSession()
            throw e
        }
        val courseQueryCode = response.optInt("ErrorCode", -1)
        if (courseQueryCode != 200) {
            invalidateSession()
            throw IllegalStateException(
                friendlyError(
                    courseQueryCode,
                    response.optString("ErrorMsg"),
                    "读取校管家课程失败"
                )
            )
        }
        val array = response.optJSONArray("Data") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val course = array.optJSONObject(index) ?: return@mapNotNull null
            val id = course.optString("ID")
            val className = course.optString("ClassName")
            if (id.isBlank() || className.isBlank()) return@mapNotNull null
            TmsCourseSummary(
                id = id,
                className = className,
                startTime = course.optString("StartTime"),
                endTime = course.optString("EndTime"),
                finished = course.optInt("Finished", 0),
                attendanceCount = course.optInt("AttendanceCount", 0),
                lastAttendTime = course.optString("LastAttendTime"),
                studentCount = course.optInt("StudentCount", 0),
                unitCode = course.optInt("Unit", 2),
                durationMinutes = TmsAttendanceMapper.durationMinutes(course.optString("Duration")),
                minutesToTimes = course.optInt("MinutesToTimes", 0),
                standardMinutes = course.optDouble("StandardMinutes", 0.0).let {
                    if (it.isNaN()) 0.0 else it
                },
                consumeAmount = course.optDouble("ConsumeAmount", 1.0).let {
                    if (it.isNaN()) 1.0 else it
                },
                isDynamicConsume = course.optInt("IsDynamicConsume", 0) == 1
            )
        }
    }

    private fun fetchAttendance(session: TmsSession, courseId: String): JSONObject {
        val url = "https://$schoolHost/api/Course/GetAttendance?" +
            TmsAttendanceMapper.encodeForm(linkedMapOf("id" to courseId))
        val response = try {
            session.getJson(url)
        } catch (e: Exception) {
            invalidateSession()
            throw e
        }
        val attendanceCode = response.optInt("ErrorCode", -1)
        if (attendanceCode != 200) {
            invalidateSession()
            throw IllegalStateException(
                friendlyError(
                    attendanceCode,
                    response.optString("ErrorMsg"),
                    "读取校管家课程学员失败"
                )
            )
        }
        return response.optJSONObject("Data") ?: run {
            invalidateSession()
            throw IllegalStateException("校管家没有返回课程学员数据")
        }
    }

    private fun checkConflict(
        session: TmsSession,
        courseId: String,
        presentStudentIds: List<String>
    ): List<String> {
        if (presentStudentIds.isEmpty()) return emptyList()
        return try {
            val body = session.postForm(
                "https://$schoolHost/api/course/CheckStudentAttendanceConflict",
                linkedMapOf(
                    "CourseID" to courseId,
                    "StudentIDs" to presentStudentIds.joinToString(",")
                ),
                mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
            val response = JSONObject(body)
            if (response.optInt("ErrorCode", -1) != 200) {
                emptyList()
            } else {
                val data = response.optJSONObject("Data")
                val result = data?.opt("ConflictResult")
                val array = when (result) {
                    is JSONArray -> result
                    is JSONObject -> JSONArray().put(result)
                    else -> return emptyList()
                }
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)
                        ?.optString("StudentName")
                        ?.takeIf { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            // 冲突检查失败不阻断预览；真正的提交仍由校管家服务端校验。
            emptyList()
        }
    }

    private fun namesOf(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.optString("Name")?.takeIf { it.isNotBlank() }
        }
    }

    private fun ensureSession(credentials: TmsCredentials): TmsSession {
        val existing = session
        if (existing != null &&
            cachedUsername == credentials.username &&
            cachedPassword == credentials.password
        ) {
            return existing
        }

        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            throw IllegalStateException("请先在设置中填写校管家账号和密码")
        }

        session = null
        val newSession = TmsSession()

        // 先取登录页的 ASP.NET_SessionId，再调用登录接口。
        newSession.get(
            "https://tms.xiaogj.com/login.aspx",
            mapOf("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        )

        val loginBody = newSession.postForm(
            "https://tms.xiaogj.com/api/login/LoginGate",
            linkedMapOf(
                "name" to credentials.username,
                "pwd" to credentials.password,
                "isTel" to "false",
                "phone" to "",
                "yzm" to "",
                "isSevenDayLoginFree" to "false",
                "ssx" to "0"
            ),
            mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://tms.xiaogj.com/login.aspx"
            )
        )
        val loginResponse = try {
            JSONObject(loginBody)
        } catch (_: Exception) {
            throw IllegalStateException("校管家登录失败，请检查账号密码或网络")
        }
        if (loginResponse.optInt("ErrorCode", -1) != 200) {
            throw IllegalStateException(
                loginResponse.optString("ErrorMsg").ifBlank { "校管家登录失败，请检查账号密码" }
            )
        }

        val host = loginResponse.optString("ErrorMsg")
            .trim()
            .lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        if (!host.matches(HOST_PATTERN)) {
            throw IllegalStateException("校管家登录成功，但返回的学校地址无效")
        }

        // 校管家官方登录流程会把密码放在这个 URL 的 query 中；必须使用 HTTPS，
        // 并且这里不记录、不打印完整 URL。
        val gateUrl = "https://$host/api/login/GateReturnUrl?" +
            TmsAttendanceMapper.encodeForm(
                linkedMapOf(
                    "name" to credentials.username,
                    "pwd" to credentials.password,
                    "url" to host,
                    "ssx" to "0"
                )
            )
        try {
            newSession.get(
                gateUrl,
                mapOf(
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to "https://tms.xiaogj.com/login.aspx"
                )
            )
        } catch (_: Exception) {
            throw IllegalStateException("校管家登录跳转失败，请检查网络")
        }

        val whoami = newSession.getJson("https://$host/api/user/whoami")
        if (whoami.optInt("ErrorCode", -1) != 200) {
            throw IllegalStateException(
                whoami.optString("ErrorMsg").ifBlank { "校管家登录状态校验失败" }
            )
        }
        val data = whoami.optJSONObject("Data")
            ?: throw IllegalStateException("校管家没有返回用户信息")
        val campus = data.optJSONArray("CampusList")?.optJSONObject(0)
            ?: throw IllegalStateException("校管家账号没有可用校区")
        val id = campus.optString("ID")
        if (id.isBlank()) {
            throw IllegalStateException("校管家账号没有可用校区")
        }

        schoolHost = host
        campusId = id
        cachedUsername = credentials.username
        cachedPassword = credentials.password
        session = newSession
        return newSession
    }

    companion object {
        private const val MANUAL_FORMAT = "手动"
        private const val EMPTY_GUID = "00000000-0000-0000-0000-000000000000"
        private val HOST_PATTERN = Regex("^[a-z0-9.-]+\\.xiaogj\\.com$")

        private val DISPLAY_ONLY_STUDENT_KEYS = listOf(
            "canCost", "remainAmount", "MachineChecked", "canAttend", "highlight",
            "FreezeCount", "OutAmount", "RemainAmount", "AbsentCauseCount",
            "CanUseShiftRelation", "HasShiftRelationRemain", "hasShiftRelationRemain",
            "IsShiftRelation", "DefaultAttend", "FaceCreateTime", "IsSign",
            "DelayLimit", "DelayLimitList", "CustomerStudentID", "EndDate", "Unit",
            "IsDynamicConsume", "FeeCusTypesName", "IsTwins", "IsConfirmed",
            "SMSTel", "StudentStatus", "StudentType", "IsSource", "AwawType",
            "CreateTime", "Photo", "StudentPhoto", "CostDisabledByArrears",
            "AvailableReceipts", "consumeShiftNames", "consumeExceptionText",
            "hasInsufficientReceipts", "selectedReceipts"
        )
    }
}

/** 纯函数集合，便于单元测试，不包含任何网络请求。 */
internal object TmsAttendanceMapper {

    data class ClassTime(val start: String, val end: String)

    /** 校管家需要同步的班型：线上 α1/β1 和线下 β2/β3 都要点名。 */
    private val SYNC_FORMATS = setOf("α1", "β1", "β2", "β3")

    fun isSyncableFormat(format: String): Boolean = format in SYNC_FORMATS

    private val APP_TIME_PATTERN = Regex("(\\d{1,2}:\\d{2})\\s*[~～\\-]\\s*(\\d{1,2}:\\d{2})")
    private val TIME_PATTERN = Regex("\\d{1,2}:\\d{2}")
    private val STUDENT_PATTERN = Regex("^\\s*(\\d{4,})\\s*(.*)$")

    fun parseClassTime(raw: String): ClassTime? {
        val match = APP_TIME_PATTERN.find(raw) ?: return null
        return ClassTime(normalizeTime(match.groupValues[1]), normalizeTime(match.groupValues[2]))
    }

    fun timeMatches(tmsStart: String, tmsEnd: String, classTime: ClassTime): Boolean {
        return tmsTime(tmsStart) == classTime.start && tmsTime(tmsEnd) == classTime.end
    }

    fun tmsTime(raw: String): String {
        return try {
            LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            // 兼容 "HH:mm"、带空格的日期时间，以及 ISO 字符串。
            val match = TIME_PATTERN.find(raw) ?: return ""
            normalizeTime(match.value)
        }
    }

    fun displayTime(start: String, end: String): String {
        val startText = tmsTime(start)
        val endText = tmsTime(end)
        return if (startText.isNotBlank() && endText.isNotBlank()) "$startText-$endText" else ""
    }

    fun durationMinutes(raw: String): Int {
        val parts = raw.split(':')
        if (parts.size < 2) return 0
        val hours = parts[0].toIntOrNull() ?: return 0
        val minutes = parts[1].toIntOrNull() ?: return 0
        return hours * 60 + minutes
    }

    /**
     * 复刻网页端 classCheckForm.js 的课消计算：
     * 按小时 -> 上课分钟数；按次且按时长 -> 分钟数/标准分钟；
     * 普通按次 -> 1 或课程固定课消；按天/按月等特殊计费返回 0 以阻止自动提交。
     */
    fun computeCost(course: TmsCourseSummary): Double {
        val duration = course.durationMinutes.toDouble()
        return when {
            course.unitCode == 1 -> duration
            course.unitCode != 2 -> 0.0 // 按天/按月等特殊计费不在自动同步范围内，直接阻止提交
            course.minutesToTimes == 1 ->
                if (course.standardMinutes > 0.0) {
                    round(duration / course.standardMinutes * 100000.0) / 100000.0
                } else {
                    0.0
                }
            course.isDynamicConsume -> course.consumeAmount
            else -> 1.0
        }
    }

    fun normalizeTime(raw: String): String {
        val parts = raw.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return raw
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return raw
        return "%02d:%02d".format(Locale.ROOT, hour, minute)
    }

    fun normalizeName(raw: String): String {
        return raw.replace("\\s".toRegex(), "").trim()
    }

    fun normalizeClassName(raw: String): String {
        return raw.replace("\\s".toRegex(), "")
    }

    /** 用于本机同步记录比较：忽略空白和大小写差异。 */
    fun classNameKey(raw: String): String {
        return normalizeClassName(raw).lowercase(Locale.ROOT)
    }

    /** 判断课程是否真正点过名；兼容 .NET 的 DateTime.MinValue 和 JSON null。 */
    fun hasAttendanceTime(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return false
        if (value.startsWith("0001-01-01")) return false
        if (value.contains("-62135596800000")) return false
        return true
    }

    fun studentNumberAndName(raw: String): Pair<String, String> {
        val match = STUDENT_PATTERN.find(raw)
        return if (match == null) {
            "" to normalizeName(raw)
        } else {
            match.groupValues[1] to normalizeName(match.groupValues[2])
        }
    }

    fun lastClassTime(start: String, end: String): String {
        val date = start.take(10)
        val startText = tmsTime(start)
        val endText = tmsTime(end)
        val weekday = try {
            when (LocalDateTime.parse(start).dayOfWeek.value) {
                1 -> "周一"
                2 -> "周二"
                3 -> "周三"
                4 -> "周四"
                5 -> "周五"
                6 -> "周六"
                else -> "周日"
            }
        } catch (_: Exception) {
            ""
        }
        return "$date$startText-$endText[$weekday]"
    }

    fun encodeForm(values: Map<String, String>): String {
        return values.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

private class TmsSession {

    private val cookies = mutableMapOf<String, MutableMap<String, String>>()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        return request(
            method = "GET",
            url = url,
            body = null,
            contentType = null,
            headers = headers,
            followRedirects = true
        )
    }

    fun getJson(url: String): JSONObject {
        val body = get(
            url,
            mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            )
        )
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            throw IllegalStateException("校管家返回了无法解析的数据，可能登录已过期")
        }
    }

    fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String {
        return request(
            method = "POST",
            url = url,
            body = TmsAttendanceMapper.encodeForm(form),
            contentType = "application/x-www-form-urlencoded; charset=UTF-8",
            headers = headers,
            followRedirects = false
        )
    }

    private fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
        followRedirects: Boolean
    ): String {
        var currentUrl = url
        var currentMethod = method
        var currentBody = body
        var currentContentType = contentType
        var redirectCount = 0

        while (true) {
            val uri = try {
                URI(currentUrl)
            } catch (_: Exception) {
                throw IllegalStateException("校管家地址无效")
            }
            val connection = try {
                uri.toURL().openConnection() as HttpURLConnection
            } catch (_: Exception) {
                throw IllegalStateException("无法连接校管家，请检查网络")
            }

            try {
                connection.requestMethod = currentMethod
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Referer", "${uri.scheme}://${uri.host}/")
                for ((key, value) in headers) {
                    connection.setRequestProperty(key, value)
                }
                val cookieHeader = cookieHeader(uri.host)
                if (cookieHeader.isNotBlank()) {
                    connection.setRequestProperty("Cookie", cookieHeader)
                }
                val bodyToWrite = currentBody
                if (bodyToWrite != null && currentMethod == "POST") {
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Type",
                        currentContentType ?: "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                    connection.outputStream.use {
                        it.write(bodyToWrite.toByteArray(Charsets.UTF_8))
                    }
                }

                val code = connection.responseCode
                storeCookies(uri.host, connection)

                if (followRedirects && code in REDIRECT_CODES && redirectCount < 5) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = uri.resolve(location).toString()
                        if (code == 303 || (code in setOf(301, 302) && currentMethod == "POST")) {
                            currentMethod = "GET"
                            currentBody = null
                            currentContentType = null
                        }
                        redirectCount++
                        continue
                    }
                }

                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val bytes = readBytes(connection, stream)
                val text = bytes.toString(Charsets.UTF_8)
                if (code !in 200..299) {
                    throw TmsHttpException(code, text)
                }
                return text
            } catch (e: TmsHttpException) {
                throw e
            } catch (e: IOException) {
                throw IllegalStateException("无法连接校管家，请检查网络", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readBytes(connection: HttpURLConnection, stream: InputStream?): ByteArray {
        if (stream == null) return ByteArray(0)
        val encoding = connection.contentEncoding.orEmpty()
        return if (encoding.contains("gzip", ignoreCase = true)) {
            GZIPInputStream(stream).use { it.readBytes() }
        } else {
            stream.use { it.readBytes() }
        }
    }

    private fun storeCookies(host: String, connection: HttpURLConnection) {
        val hostCookies = cookies.getOrPut(host) { mutableMapOf() }
        for ((key, values) in connection.headerFields) {
            if (!"Set-Cookie".equals(key, ignoreCase = true)) continue
            values?.forEach { raw ->
                val firstPart = raw.substringBefore(';')
                val separator = firstPart.indexOf('=')
                if (separator > 0) {
                    val name = firstPart.substring(0, separator).trim()
                    val value = firstPart.substring(separator + 1).trim()
                    if (name.isNotEmpty()) hostCookies[name] = value
                }
            }
        }
    }

    private fun cookieHeader(host: String): String {
        return cookies[host]?.entries?.joinToString("; ") { "${it.key}=${it.value}" }.orEmpty()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

private class TmsHttpException(val code: Int, val body: String) :
    Exception("校管家请求失败（HTTP $code）") {
    fun friendlyMessage(): String {
        if (code == 302 || code == 401 || code == 403 || code == 407) {
            return "校管家登录已过期，请关闭后重新同步"
        }
        return try {
            JSONObject(body).optString("ErrorMsg").ifBlank { "HTTP $code" }
        } catch (_: Exception) {
            "HTTP $code"
        }
    }
}
