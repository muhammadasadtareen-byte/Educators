package com.example

import com.example.data.model.AcademicSession
import com.example.data.model.AttendanceRecord
import com.example.data.model.Campus
import com.example.data.model.ClassSection
import com.example.data.model.Examination
import com.example.data.model.MarkEntry
import com.example.data.model.SchoolClass
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.domain.AppUpdateChecker
import com.example.domain.DriveBackupEngine
import com.example.ui.SchoolSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class BackupAndUpdateUnitTest {

    @Test
    fun `version comparison accurately detects newer GitHub releases`() {
        assertTrue(AppUpdateChecker.isNewerVersion("2.10", "2.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("v3.0", "2.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("2.9.1", "2.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("2.9", "2.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("v2.9", "2.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("2.8", "2.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("invalid", "2.9"))
    }

    @Test
    fun `drive backup builds valid 6-sheet Excel workbook and JSON snapshot with all school records`() {
        val campus = Campus(
            id = "camp_1",
            ownerId = "admin_1",
            memberUids = listOf("admin_1"),
            schoolName = "THE EDUCATORS",
            campusName = "ABBOTTABAD MAIN CAMPUS",
            coordinatorWhatsApp = "0300-9999999",
            activeSessionId = "sess_1",
            activeExamId = "exam_1"
        )
        val session = AcademicSession(id = "sess_1", name = "2026-2027", current = true)
        val cls = SchoolClass(id = "cls_8", name = "Class 8", sortOrder = 8)
        val sec = ClassSection(id = "sec_v", classId = "cls_8", name = "Venus")
        val sub = Subject(id = "sub_eng", classId = "cls_8", name = "English", maxMarks = 50.0, passingMarks = 20.0)
        val exam = Examination(id = "exam_1", sessionId = "sess_1", name = "Midterm")
        val stu = Student(
            id = "stu_1",
            name = "Abdul Moiz Khan",
            rollNumber = "01",
            classId = "cls_8",
            sectionId = "sec_v",
            sessionId = "sess_1",
            parentWhatsApp = "0312-1234567"
        )
        val mark = MarkEntry(
            id = "m_1",
            sessionId = "sess_1",
            examId = "exam_1",
            classId = "cls_8",
            sectionId = "sec_v",
            studentId = "stu_1",
            subjectId = "sub_eng",
            obtainedMarks = 42.0
        )
        val att = AttendanceRecord(
            id = "att_2026-11-23_cls_8_sec_v",
            sessionId = "sess_1",
            classId = "cls_8",
            sectionId = "sec_v",
            dateKey = "2026-11-23",
            records = mapOf("stu_1" to "PRESENT"),
            note = "All present",
            markedByUid = "admin_1",
            markedByName = "Asad"
        )
        val staff = StaffMember(
            id = "admin_1",
            userUid = "admin_1",
            displayName = "Asad",
            email = "admin@example.com",
            role = StaffRole.ADMIN.name
        )

        val snapshot = SchoolSnapshot(
            campus = campus,
            staff = listOf(staff),
            sessions = listOf(session),
            examinations = listOf(exam),
            classes = listOf(cls),
            sections = listOf(sec),
            subjects = listOf(sub),
            students = listOf(stu),
            marks = listOf(mark),
            gradingRules = emptyList(),
            resultRecords = emptyList(),
            effectiveRole = StaffRole.ADMIN,
            isOwnerOrAdmin = true,
            assignedClassIds = emptyList(),
            selectedSessionId = "sess_1",
            selectedExamId = "exam_1",
            attendanceRecords = listOf(att)
        )

        // Verify Excel multi-sheet workbook
        val xlsxBytes = DriveBackupEngine.buildFullBackupExcelBytes(snapshot)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        val workbookXml = entries["xl/workbook.xml"].orEmpty()
        assertTrue(workbookXml.contains("Campus_Info"))
        assertTrue(workbookXml.contains("Students"))
        assertTrue(workbookXml.contains("Classes_Subjects"))
        assertTrue(workbookXml.contains("Marks"))
        assertTrue(workbookXml.contains("Daily_Attendance"))
        assertTrue(workbookXml.contains("Staff"))

        val allSheetXml = entries.filterKeys { it.startsWith("xl/worksheets/") }.values.joinToString("\n")
        assertTrue(allSheetXml.contains("Abdul Moiz Khan"))
        assertTrue(allSheetXml.contains("0312-1234567"))
        assertTrue(allSheetXml.contains("0300-9999999"))
        assertTrue(allSheetXml.contains("2026-11-23"))

        // Verify JSON backup
        val json = DriveBackupEngine.buildFullBackupJson(snapshot)
        assertTrue(json.contains("\"coordinatorWhatsApp\": \"0300-9999999\""))
        assertTrue(json.contains("\"parentWhatsApp\": \"0312-1234567\""))
        assertTrue(json.contains("\"dateKey\": \"2026-11-23\""))

        // Verify filename sanitization
        val fname = DriveBackupEngine.buildBackupFileName("ABBOTTABAD MAIN CAMPUS", "xlsx")
        assertTrue(fname.startsWith("Educators_Backup_ABBOTTABAD_MAIN_CAMPUS_"))
        assertEquals(true, fname.endsWith(".xlsx"))
    }
}
