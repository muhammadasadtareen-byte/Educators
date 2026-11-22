package com.example.domain

import com.example.data.model.AttendanceStatus
import com.example.ui.SchoolSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates complete, self-contained school backup packages (Multi-sheet Excel .xlsx and structured JSON)
 * suitable for saving directly into Google Drive / Google Sheets.
 */
object DriveBackupEngine {

    fun buildBackupFileName(campusName: String, extension: String, now: Date = Date()): String {
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val slug = campusName
            .trim()
            .replace(Regex("[^a-zA-Z0-9]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "Campus" }
        return "Educators_Backup_${slug}_${dateStamp}.$extension"
    }

    /**
     * Builds a comprehensive multi-sheet Excel (.xlsx) backup workbook containing:
     * 1. Campus_Info
     * 2. Students (with Parent WhatsApp)
     * 3. Classes_Subjects
     * 4. Marks
     * 5. Daily_Attendance
     * 6. Staff
     */
    fun buildFullBackupExcelBytes(snapshot: SchoolSnapshot, now: Date = Date()): ByteArray {
        val exportedAt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(now)
        val campus = snapshot.campus
        val classesById = snapshot.classesById
        val sectionsById = snapshot.sectionsById
        val sessionsById = snapshot.sessionsById
        val examsById = snapshot.examinations.associateBy { it.id }
        val subjectsById = snapshot.subjects.associateBy { it.id }
        val studentsById = snapshot.students.associateBy { it.id }

        val visibleClassIds = snapshot.visibleClasses.map { it.id }.toSet()
        val scopedStudents = snapshot.students.filter { it.classId in visibleClassIds }
        val scopedSubjects = snapshot.subjects.filter { it.classId in visibleClassIds }
        val scopedMarks = snapshot.marks.filter { it.classId in visibleClassIds }
        val scopedAttendance = snapshot.attendanceRecords.filter { it.classId in visibleClassIds }

        // Sheet 1: Campus Info
        val campusSheet = XlsxSheetData(
            name = "Campus_Info",
            titleRow = "${campus.schoolName} - ${campus.campusName}",
            subtitleRow = "Full Google Drive Backup | Generated: $exportedAt",
            headers = listOf("Field", "Value"),
            rows = listOf(
                listOf(CellValue.Text("School Name"), CellValue.Text(campus.schoolName)),
                listOf(CellValue.Text("Campus Name"), CellValue.Text(campus.campusName)),
                listOf(CellValue.Text("Address"), CellValue.Text(campus.address)),
                listOf(CellValue.Text("Contact"), CellValue.Text(campus.contact)),
                listOf(CellValue.Text("School Coordinator WhatsApp"), CellValue.Text(campus.coordinatorWhatsApp)),
                listOf(CellValue.Text("Active Session"), CellValue.Text(snapshot.selectedSession?.name.orEmpty())),
                listOf(CellValue.Text("Active Examination"), CellValue.Text(snapshot.selectedExam?.name.orEmpty())),
                listOf(CellValue.Text("Total Students Backed Up"), CellValue.Number(scopedStudents.size.toDouble())),
                listOf(CellValue.Text("Total Mark Entries Backed Up"), CellValue.Number(scopedMarks.size.toDouble())),
                listOf(CellValue.Text("Total Attendance Days Backed Up"), CellValue.Number(scopedAttendance.size.toDouble())),
                listOf(CellValue.Text("Backup Date"), CellValue.Text(exportedAt))
            )
        )

        // Sheet 2: Students
        val studentRows = scopedStudents
            .sortedWith(
                compareBy<com.example.data.model.Student> { classesById[it.classId]?.sortOrder ?: 999L }
                    .thenBy { sectionsById[it.sectionId]?.name.orEmpty() }
                    .then(ResultCalculator.studentEntryOrderComparator)
            )
            .map { st ->
                listOf(
                    CellValue.Text(st.rollNumber),
                    CellValue.Text(st.admissionNumber),
                    CellValue.Text(st.name),
                    CellValue.Text(st.fatherName.takeIf { it != "-" }.orEmpty()),
                    CellValue.Text(classesById[st.classId]?.name.orEmpty()),
                    CellValue.Text(sectionsById[st.sectionId]?.name.orEmpty()),
                    CellValue.Text(sessionsById[st.sessionId]?.name.orEmpty()),
                    CellValue.Text(st.parentWhatsApp)
                )
            }
        val studentsSheet = XlsxSheetData(
            name = "Students",
            titleRow = "${campus.schoolName} - Student Directory Backup",
            subtitleRow = "Total Students: ${scopedStudents.size}",
            headers = listOf(
                "Roll Number", "Admission No", "Student Name", "Father's Name",
                "Class", "Section", "Session", "Parent WhatsApp"
            ),
            rows = studentRows
        )

        // Sheet 3: Classes & Subjects
        val classSubjectRows = scopedSubjects
            .sortedWith(compareBy<com.example.data.model.Subject> { classesById[it.classId]?.sortOrder ?: 999L }.thenBy { it.sortOrder }.thenBy { it.name })
            .map { sub ->
                val secNames = snapshot.sections.filter { it.classId == sub.classId }.joinToString(", ") { it.name }
                listOf(
                    CellValue.Text(classesById[sub.classId]?.name.orEmpty()),
                    CellValue.Text(secNames),
                    CellValue.Text(sub.name),
                    CellValue.Number(sub.maxMarks),
                    CellValue.Number(sub.passingMarks),
                    CellValue.Text(if (sub.compulsory) "Yes" else "Optional")
                )
            }
        val subjectsSheet = XlsxSheetData(
            name = "Classes_Subjects",
            titleRow = "${campus.schoolName} - Classes & Subjects",
            subtitleRow = "Total Subjects: ${scopedSubjects.size}",
            headers = listOf("Class", "Sections", "Subject", "Max Marks", "Passing Marks", "Compulsory"),
            rows = classSubjectRows
        )

        // Sheet 4: Marks
        val markRows = scopedMarks
            .sortedWith(
                compareBy<com.example.data.model.MarkEntry> { sessionsById[it.sessionId]?.name.orEmpty() }
                    .thenBy { examsById[it.examId]?.name.orEmpty() }
                    .thenBy { classesById[it.classId]?.sortOrder ?: 999L }
                    .thenBy { studentsById[it.studentId]?.name.orEmpty() }
                    .thenBy { subjectsById[it.subjectId]?.sortOrder ?: 999L }
            )
            .map { m ->
                val stu = studentsById[m.studentId]
                val compSummary = m.componentMarks.entries.joinToString(", ") { (k, v) -> "$k=$v" }
                listOf(
                    CellValue.Text(sessionsById[m.sessionId]?.name.orEmpty()),
                    CellValue.Text(examsById[m.examId]?.name.orEmpty()),
                    CellValue.Text(classesById[m.classId]?.name.orEmpty()),
                    CellValue.Text(sectionsById[m.sectionId]?.name.orEmpty()),
                    CellValue.Text(stu?.rollNumber.orEmpty()),
                    CellValue.Text(stu?.name ?: m.studentId),
                    CellValue.Text(subjectsById[m.subjectId]?.name ?: m.subjectId),
                    CellValue.Number(m.obtainedMarks),
                    CellValue.Text(compSummary)
                )
            }
        val marksSheet = XlsxSheetData(
            name = "Marks",
            titleRow = "${campus.schoolName} - Marks Records Backup",
            subtitleRow = "Total Mark Entries: ${scopedMarks.size}",
            headers = listOf(
                "Session", "Examination", "Class", "Section",
                "Roll No", "Student Name", "Subject", "Obtained Marks", "Component Breakdown"
            ),
            rows = markRows
        )

        // Sheet 5: Daily Attendance
        val attendanceRows = mutableListOf<List<CellValue>>()
        scopedAttendance.sortedByDescending { it.dateKey }.forEach { rec ->
            val clsName = classesById[rec.classId]?.name.orEmpty()
            val secName = sectionsById[rec.sectionId]?.name.orEmpty()
            val dayName = AttendanceHelper.dayOfWeekLabel(rec.dateKey)
            rec.records.entries.forEach { (studentId, rawStatus) ->
                val st = studentsById[studentId]
                val statusLabel = AttendanceStatus.fromRaw(rawStatus).label
                attendanceRows.add(
                    listOf(
                        CellValue.Text(rec.dateKey),
                        CellValue.Text(dayName),
                        CellValue.Text(clsName),
                        CellValue.Text(secName),
                        CellValue.Text(st?.name ?: studentId),
                        CellValue.Text(statusLabel),
                        CellValue.Text(rec.note),
                        CellValue.Text(rec.markedByName)
                    )
                )
            }
        }
        val attendanceSheet = XlsxSheetData(
            name = "Daily_Attendance",
            titleRow = "${campus.schoolName} - Daily Attendance Backup",
            subtitleRow = "Saved Attendance Days: ${scopedAttendance.size}",
            headers = listOf("Date", "Day", "Class", "Section", "Student Name", "Status", "Note", "Marked By"),
            rows = attendanceRows
        )

        // Sheet 6: Staff
        val staffRows = snapshot.staff.map { staff ->
            val assignedNames = staff.assignedClassIds.mapNotNull { classesById[it]?.name }.joinToString(", ")
            listOf(
                CellValue.Text(staff.displayName),
                CellValue.Text(staff.email),
                CellValue.Text(staff.role),
                CellValue.Text(assignedNames),
                CellValue.Text(if (staff.active) "Active" else "Inactive")
            )
        }
        val staffSheet = XlsxSheetData(
            name = "Staff",
            titleRow = "${campus.schoolName} - Staff Directory",
            subtitleRow = "Total Staff: ${snapshot.staff.size}",
            headers = listOf("Name", "Email", "Role", "Assigned Classes", "Status"),
            rows = staffRows
        )

        return XlsxEngine.writeWorkbook(
            listOf(campusSheet, studentsSheet, subjectsSheet, marksSheet, attendanceSheet, staffSheet)
        )
    }

    /**
     * Builds a complete JSON archive snapshot of all campus records for disaster-recovery storage in Google Drive.
     */
    fun buildFullBackupJson(snapshot: SchoolSnapshot, now: Date = Date()): String {
        val visibleClassIds = snapshot.visibleClasses.map { it.id }.toSet()
        val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(now)

        val root = linkedMapOf<String, Any?>(
            "backupFormatVersion" to 1,
            "exportedAt" to exportedAt,
            "campus" to linkedMapOf(
                "id" to snapshot.campus.id,
                "schoolName" to snapshot.campus.schoolName,
                "campusName" to snapshot.campus.campusName,
                "address" to snapshot.campus.address,
                "contact" to snapshot.campus.contact,
                "coordinatorWhatsApp" to snapshot.campus.coordinatorWhatsApp,
                "activeSessionId" to snapshot.selectedSessionId,
                "activeExamId" to snapshot.selectedExamId,
                "allowDecimalMarks" to snapshot.campus.allowDecimalMarks
            ),
            "sessions" to snapshot.sessions.map { s ->
                linkedMapOf(
                    "id" to s.id,
                    "name" to s.name,
                    "current" to s.current
                )
            },
            "classes" to snapshot.visibleClasses.map { c ->
                linkedMapOf(
                    "id" to c.id,
                    "name" to c.name,
                    "sortOrder" to c.sortOrder
                )
            },
            "sections" to snapshot.sections.filter { it.classId in visibleClassIds }.map { sec ->
                linkedMapOf(
                    "id" to sec.id,
                    "classId" to sec.classId,
                    "name" to sec.name
                )
            },
            "subjects" to snapshot.subjects.filter { it.classId in visibleClassIds }.map { sub ->
                linkedMapOf(
                    "id" to sub.id,
                    "classId" to sub.classId,
                    "name" to sub.name,
                    "maxMarks" to sub.maxMarks,
                    "passingMarks" to sub.passingMarks,
                    "compulsory" to sub.compulsory,
                    "sortOrder" to sub.sortOrder
                )
            },
            "students" to snapshot.students.filter { it.classId in visibleClassIds }.map { st ->
                linkedMapOf(
                    "id" to st.id,
                    "name" to st.name,
                    "fatherName" to st.fatherName,
                    "rollNumber" to st.rollNumber,
                    "admissionNumber" to st.admissionNumber,
                    "classId" to st.classId,
                    "sectionId" to st.sectionId,
                    "sessionId" to st.sessionId,
                    "parentWhatsApp" to st.parentWhatsApp
                )
            },
            "examinations" to snapshot.examinations.map { ex ->
                linkedMapOf(
                    "id" to ex.id,
                    "sessionId" to ex.sessionId,
                    "name" to ex.name,
                    "examType" to ex.examType,
                    "components" to ex.components.map { comp ->
                        linkedMapOf(
                            "id" to comp.id,
                            "name" to comp.name,
                            "maxMarks" to comp.maxMarks,
                            "sourceExamId" to comp.sourceExamId,
                            "weightPercentage" to comp.weightPercentage
                        )
                    }
                )
            },
            "marks" to snapshot.marks.filter { it.classId in visibleClassIds }.map { m ->
                linkedMapOf(
                    "id" to m.id,
                    "sessionId" to m.sessionId,
                    "examId" to m.examId,
                    "classId" to m.classId,
                    "sectionId" to m.sectionId,
                    "studentId" to m.studentId,
                    "subjectId" to m.subjectId,
                    "obtainedMarks" to m.obtainedMarks,
                    "componentMarks" to m.componentMarks
                )
            },
            "attendance" to snapshot.attendanceRecords.filter { it.classId in visibleClassIds }.map { att ->
                linkedMapOf(
                    "id" to att.id,
                    "sessionId" to att.sessionId,
                    "classId" to att.classId,
                    "sectionId" to att.sectionId,
                    "dateKey" to att.dateKey,
                    "note" to att.note,
                    "markedByName" to att.markedByName,
                    "records" to att.records
                )
            }
        )

        val sb = StringBuilder(4096)
        appendJsonValue(sb, root, 0)
        return sb.toString()
    }

    private fun appendJsonValue(sb: StringBuilder, value: Any?, indent: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> appendJsonString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long -> sb.append(value)
            is Double -> {
                if (value.isNaN() || value.isInfinite()) sb.append("0")
                else if (value % 1.0 == 0.0) sb.append(value.toLong())
                else sb.append(value)
            }
            is Number -> sb.append(value)
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                val entries = value.entries.toList()
                val childPad = " ".repeat(indent + 2)
                entries.forEachIndexed { idx, (k, v) ->
                    sb.append(childPad)
                    appendJsonString(sb, k.toString())
                    sb.append(": ")
                    appendJsonValue(sb, v, indent + 2)
                    if (idx < entries.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                sb.append(" ".repeat(indent)).append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                val childPad = " ".repeat(indent + 2)
                items.forEachIndexed { idx, item ->
                    sb.append(childPad)
                    appendJsonValue(sb, item, indent + 2)
                    if (idx < items.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                sb.append(" ".repeat(indent)).append(']')
            }
            else -> appendJsonString(sb, value.toString())
        }
    }

    private fun appendJsonString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }
}
