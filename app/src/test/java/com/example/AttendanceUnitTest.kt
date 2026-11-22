package com.example

import com.example.data.model.AttendanceStatus
import com.example.data.model.Student
import com.example.domain.AttendanceHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceUnitTest {

    @Test
    fun `daily attendance enforces Saturday and Sunday off and skips weekends cleanly`() {
        // 2026-11-20 = Friday, 2026-11-21 = Saturday, 2026-11-22 = Sunday, 2026-11-23 = Monday
        assertFalse(AttendanceHelper.isWeekend("2026-11-20"))
        assertTrue(AttendanceHelper.isWeekend("2026-11-21"))
        assertTrue(AttendanceHelper.isWeekend("2026-11-22"))
        assertFalse(AttendanceHelper.isWeekend("2026-11-23"))

        assertEquals("2026-11-23", AttendanceHelper.nextWeekday("2026-11-20"))
        assertEquals("2026-11-20", AttendanceHelper.previousWeekday("2026-11-23"))

        val satCal = AttendanceHelper.parseDateKey("2026-11-21")!!
        val sunCal = AttendanceHelper.parseDateKey("2026-11-22")!!
        assertEquals("2026-11-20", AttendanceHelper.lastWorkingDayKey(satCal))
        assertEquals("2026-11-20", AttendanceHelper.lastWorkingDayKey(sunCal))
    }

    @Test
    fun `whatsapp phone normalization and coordinator vs parent messages separate whole class from individual child`() {
        assertEquals("923001234567", AttendanceHelper.normalizeWhatsAppNumber("0300-1234567"))
        assertEquals("923121234567", AttendanceHelper.normalizeWhatsAppNumber("+92 312 1234567"))
        assertEquals("923331234567", AttendanceHelper.normalizeWhatsAppNumber("0092 333 1234567"))
        assertEquals("923451234567", AttendanceHelper.normalizeWhatsAppNumber("3451234567"))
        assertTrue(AttendanceHelper.isValidWhatsAppNumber("0300-1234567"))
        assertFalse(AttendanceHelper.isValidWhatsAppNumber("12345"))

        val s1 = Student(id = "s1", name = "Abdul Moiz Khan", rollNumber = "01", parentWhatsApp = "0300-1111111")
        val s2 = Student(id = "s2", name = "Ayan Bilal", rollNumber = "02", parentWhatsApp = "0300-2222222")
        val s3 = Student(id = "s3", name = "Farhan Khalid", rollNumber = "03", parentWhatsApp = "0300-3333333")
        val statuses = mapOf(
            "s1" to AttendanceStatus.PRESENT,
            "s2" to AttendanceStatus.ABSENT,
            "s3" to AttendanceStatus.LEAVE
        )

        val coordMsg = AttendanceHelper.buildCoordinatorMessage(
            schoolName = "THE EDUCATORS",
            campusName = "ABBOTTABAD MAIN CAMPUS",
            className = "Class 8",
            sectionName = "Venus",
            dateKey = "2026-11-23",
            students = listOf(s1, s2, s3),
            statuses = statuses,
            customNote = "Morning assembly completed."
        )
        assertTrue(coordMsg.contains("Class 8 - Section Venus"))
        assertTrue(coordMsg.contains("Total Students: 3"))
        assertTrue(coordMsg.contains("Present: 1"))
        assertTrue(coordMsg.contains("Absent: 1"))
        assertTrue(coordMsg.contains("On Leave: 1"))
        assertTrue(coordMsg.contains("Abdul Moiz Khan"))
        assertTrue(coordMsg.contains("Ayan Bilal"))
        assertTrue(coordMsg.contains("Farhan Khalid"))
        assertTrue(coordMsg.contains("Morning assembly completed."))

        val parentMsg = AttendanceHelper.buildParentMessage(
            schoolName = "THE EDUCATORS",
            campusName = "ABBOTTABAD MAIN CAMPUS",
            student = s2,
            className = "Class 8",
            sectionName = "Venus",
            dateKey = "2026-11-23",
            status = AttendanceStatus.ABSENT,
            customNote = "Please send a leave note."
        )
        assertTrue(parentMsg.contains("Ayan Bilal"))
        assertTrue(parentMsg.contains("Class 8 - Venus"))
        assertTrue(parentMsg.contains("ABSENT"))
        assertTrue(parentMsg.contains("Please send a leave note."))
        assertFalse(parentMsg.contains("Abdul Moiz Khan"))
        assertFalse(parentMsg.contains("Farhan Khalid"))
    }
}
