package com.example.domain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.AttendanceStatus
import com.example.data.model.Student
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure domain utilities for Daily Attendance:
 * - Working weekday calendar logic (Saturday & Sunday are school weekends / off)
 * - WhatsApp phone number normalization (local 03XX -> 923XX & international numbers)
 * - Structured WhatsApp message builders for School Coordinator (whole class) & Parents (individual child)
 * - Zero-permission WhatsApp dispatch via https://wa.me/ deep link
 */
object AttendanceHelper {

    private const val DATE_PATTERN = "yyyy-MM-dd"

    private fun localDateFormat(pattern: String = DATE_PATTERN): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US)

    private fun utcDateFormat(pattern: String = DATE_PATTERN): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun todayCalendar(): Calendar = Calendar.getInstance()

    fun todayDateKey(now: Calendar = Calendar.getInstance()): String =
        formatDateKey(now)

    fun formatDateKey(cal: Calendar): String = localDateFormat(DATE_PATTERN).format(cal.time)

    fun parseDateKey(dateKey: String): Calendar? {
        return try {
            val parsed: Date = localDateFormat(DATE_PATTERN).apply { isLenient = false }.parse(dateKey.trim())
                ?: return null
            Calendar.getInstance().apply { time = parsed }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts a Material 3 DatePicker UTC epoch millis value into a yyyy-MM-dd dateKey.
     */
    fun utcMillisToDateKey(utcMillis: Long): String =
        utcDateFormat(DATE_PATTERN).format(Date(utcMillis))

    /**
     * Converts a yyyy-MM-dd dateKey into UTC epoch millis suitable for Material 3 DatePicker initialSelectedDateMillis.
     */
    fun dateKeyToUtcMillis(dateKey: String): Long? {
        return try {
            utcDateFormat(DATE_PATTERN).apply { isLenient = false }.parse(dateKey.trim())?.time
        } catch (_: Exception) {
            null
        }
    }

    fun isWeekendCalendar(cal: Calendar): Boolean {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    fun isWeekendUtcMillis(utcMillis: Long): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return isWeekendCalendar(cal)
    }

    fun isWeekend(dateKey: String): Boolean {
        val cal = parseDateKey(dateKey) ?: return false
        return isWeekendCalendar(cal)
    }

    fun dayOfWeekLabel(dateKey: String): String {
        val cal = parseDateKey(dateKey) ?: return ""
        return SimpleDateFormat("EEEE", Locale.US).format(cal.time)
    }

    /**
     * Returns today's dateKey if Mon-Fri, or rolls back to Friday if today is Saturday or Sunday.
     */
    fun lastWorkingDayKey(from: Calendar = Calendar.getInstance()): String {
        val copy = from.clone() as Calendar
        while (isWeekendCalendar(copy)) {
            copy.add(Calendar.DAY_OF_MONTH, -1)
        }
        return formatDateKey(copy)
    }

    /**
     * Steps backward by 1 working weekday (Monday steps back to Friday, skipping Sunday & Saturday).
     */
    fun previousWeekday(dateKey: String): String {
        val cal = parseDateKey(dateKey) ?: return lastWorkingDayKey()
        do {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        } while (isWeekendCalendar(cal))
        return formatDateKey(cal)
    }

    /**
     * Steps forward by 1 working weekday (Friday steps forward to Monday, skipping Saturday & Sunday).
     */
    fun nextWeekday(dateKey: String): String {
        val cal = parseDateKey(dateKey) ?: return lastWorkingDayKey()
        do {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        } while (isWeekendCalendar(cal))
        return formatDateKey(cal)
    }

    /**
     * Human-readable weekday display date, e.g. "Mon, 23 Nov 2026".
     */
    fun formatDisplayDate(dateKey: String): String {
        val cal = parseDateKey(dateKey) ?: return dateKey
        return SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(cal.time)
    }

    /**
     * Builds a deterministic Firestore document ID for a class/section/date attendance sheet.
     */
    fun attendanceDocId(dateKey: String, classId: String, sectionId: String): String {
        val safeDate = dateKey.trim().replace(Regex("[^0-9\\-]"), "")
        val safeClass = classId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val safeSection = sectionId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return "att_${safeDate}_${safeClass}_${safeSection}".take(120)
    }

    /**
     * Normalizes a WhatsApp phone number into pure international digits required by https://wa.me/<digits>:
     * - Strips spaces, dashes, parentheses, and dots
     * - "+92 300 1234567" -> "923001234567"
     * - "0092 300 1234567" -> "923001234567"
     * - Local Pakistan mobile "0300-1234567" (11 digits starting with 03) -> "923001234567"
     * - 10-digit Pakistan mobile "3001234567" (starts with 3) -> "923001234567"
     * - Other international numbers with country code are kept intact.
     */
    fun normalizeWhatsAppNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val hasExplicitPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        if (hasExplicitPlus) return digits
        if (digits.startsWith("00") && digits.length > 4) {
            return digits.drop(2)
        }
        if (digits.startsWith("03") && digits.length == 11) {
            return "92" + digits.drop(1)
        }
        if (digits.startsWith("3") && digits.length == 10) {
            return "92$digits"
        }
        return digits
    }

    fun isValidWhatsAppNumber(raw: String): Boolean {
        val normalized = normalizeWhatsAppNumber(raw)
        return normalized.length in 10..15
    }

    /**
     * Builds the whole-class Daily Attendance report message for the School Coordinator.
     */
    fun buildCoordinatorMessage(
        schoolName: String,
        campusName: String,
        className: String,
        sectionName: String,
        dateKey: String,
        students: List<Student>,
        statuses: Map<String, AttendanceStatus>,
        customNote: String = ""
    ): String {
        val displayDate = formatDisplayDate(dateKey)
        val presentStudents = mutableListOf<Student>()
        val absentStudents = mutableListOf<Student>()
        val leaveStudents = mutableListOf<Student>()

        students.forEach { st ->
            when (statuses[st.id] ?: AttendanceStatus.PRESENT) {
                AttendanceStatus.PRESENT -> presentStudents.add(st)
                AttendanceStatus.ABSENT -> absentStudents.add(st)
                AttendanceStatus.LEAVE -> leaveStudents.add(st)
            }
        }

        val sb = StringBuilder()
        sb.appendLine("*${schoolName.ifBlank { "THE EDUCATORS" }}*")
        if (campusName.isNotBlank()) {
            sb.appendLine(campusName)
        }
        sb.appendLine("*Daily Class Attendance Report*")
        sb.appendLine("Date: $displayDate")
        sb.appendLine("Class: $className - Section $sectionName")
        sb.appendLine("")
        sb.appendLine("Summary:")
        sb.appendLine("- Total Students: ${students.size}")
        sb.appendLine("- Present: ${presentStudents.size}")
        sb.appendLine("- Absent: ${absentStudents.size}")
        sb.appendLine("- On Leave: ${leaveStudents.size}")

        if (absentStudents.isNotEmpty()) {
            sb.appendLine("")
            sb.appendLine("*Absent Students (${absentStudents.size}):*")
            absentStudents.forEachIndexed { index, st ->
                val roll = st.rollNumber.trim().takeIf { it.isNotBlank() }?.let { " (Roll $it)" } ?: ""
                sb.appendLine("${index + 1}. ${st.name}$roll")
            }
        }

        if (leaveStudents.isNotEmpty()) {
            sb.appendLine("")
            sb.appendLine("*On Leave (${leaveStudents.size}):*")
            leaveStudents.forEachIndexed { index, st ->
                val roll = st.rollNumber.trim().takeIf { it.isNotBlank() }?.let { " (Roll $it)" } ?: ""
                sb.appendLine("${index + 1}. ${st.name}$roll")
            }
        }

        if (presentStudents.isNotEmpty()) {
            sb.appendLine("")
            sb.appendLine("*Present Students (${presentStudents.size}):*")
            presentStudents.forEachIndexed { index, st ->
                val roll = st.rollNumber.trim().takeIf { it.isNotBlank() }?.let { " (Roll $it)" } ?: ""
                sb.appendLine("${index + 1}. ${st.name}$roll")
            }
        }

        if (customNote.isNotBlank()) {
            sb.appendLine("")
            sb.appendLine("Note: ${customNote.trim()}")
        }
        return sb.toString().trim()
    }

    /**
     * Builds an individual attendance message strictly for one student's parent (son/daughter ONLY).
     */
    fun buildParentMessage(
        schoolName: String,
        campusName: String,
        student: Student,
        className: String,
        sectionName: String,
        dateKey: String,
        status: AttendanceStatus,
        customNote: String = ""
    ): String {
        val displayDate = formatDisplayDate(dateKey)
        val statusText = when (status) {
            AttendanceStatus.PRESENT -> "PRESENT"
            AttendanceStatus.ABSENT -> "ABSENT"
            AttendanceStatus.LEAVE -> "ON LEAVE"
        }
        val statusDetail = when (status) {
            AttendanceStatus.PRESENT -> "We are pleased to inform you that your child *${student.name}* is *Present* at school today."
            AttendanceStatus.ABSENT -> "This is to inform you that your child *${student.name}* is *Absent* from school today. Please inform the school office if needed."
            AttendanceStatus.LEAVE -> "This is to confirm that your child *${student.name}* is marked *On Leave* today."
        }

        val sb = StringBuilder()
        sb.appendLine("*${schoolName.ifBlank { "THE EDUCATORS" }}*")
        if (campusName.isNotBlank()) {
            sb.appendLine(campusName)
        }
        sb.appendLine("")
        sb.appendLine("Dear Parent,")
        sb.appendLine(statusDetail)
        sb.appendLine("")
        sb.appendLine("Student Name: *${student.name}*")
        if (student.rollNumber.isNotBlank()) {
            sb.appendLine("Roll No: ${student.rollNumber.trim()}")
        }
        sb.appendLine("Class: $className - $sectionName")
        sb.appendLine("Date: $displayDate")
        sb.appendLine("Attendance: *$statusText*")
        if (customNote.isNotBlank()) {
            sb.appendLine("Message: ${customNote.trim()}")
        }
        sb.appendLine("")
        sb.appendLine("Regards,")
        sb.append("${schoolName.ifBlank { "School" }} Administration")
        return sb.toString().trim()
    }

    /**
     * Opens WhatsApp directly to [rawPhoneNumber] with a pre-filled [message] via https://wa.me/ deep link.
     * Uses zero dangerous Android permissions (Play Policy compliant).
     * Returns true if an activity was successfully launched, or false if the number is invalid or launch failed.
     */
    fun openWhatsApp(
        context: Context,
        rawPhoneNumber: String,
        message: String
    ): Boolean {
        val normalized = normalizeWhatsAppNumber(rawPhoneNumber)
        if (normalized.length < 10) return false
        val encodedText = Uri.encode(message)
        val uri = Uri.parse("https://wa.me/$normalized?text=$encodedText")
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(viewIntent)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Send Attendance via WhatsApp"))
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
