package com.example

import com.example.data.model.AcademicSession
import com.example.data.model.ClassSection
import com.example.data.model.ExamType
import com.example.data.model.Examination
import com.example.data.model.GradingRule
import com.example.data.model.MarkEntry
import com.example.data.model.ResultStatus
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.domain.ResultCalculator
import com.example.domain.StudentDocumentExtractor
import com.example.domain.XlsxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResultSystemUnitTest {

    private val session = AcademicSession(id = "sess_1", name = "2026-2027", current = true)
    private val schoolClass = SchoolClass(id = "cls_5", name = "Class 5", sortOrder = 8)
    private val sectionA = ClassSection(id = "sec_a", classId = "cls_5", name = "A")
    private val defaultExam = Examination(
        id = "exam_mid",
        sessionId = "sess_1",
        name = "Midterm",
        examType = ExamType.MIDTERM.name,
        useCustomGrading = false
    )

    private val math = Subject(
        id = "sub_math",
        classId = "cls_5",
        name = "Mathematics",
        maxMarks = 100.0,
        passingMarks = 40.0,
        compulsory = true,
        sortOrder = 1
    )
    private val islamiat = Subject(
        id = "sub_isl",
        classId = "cls_5",
        name = "Islamiat",
        maxMarks = 50.0,
        passingMarks = 20.0,
        compulsory = true,
        sortOrder = 2
    )
    private val computer = Subject(
        id = "sub_comp",
        classId = "cls_5",
        name = "Computer",
        maxMarks = 50.0,
        passingMarks = 20.0,
        compulsory = false,
        sortOrder = 3
    )

    @Test
    fun `overall percentage uses total obtained divided by total max marks`() {
        // Math: 85/100 (85%), Islamiat: 25/50 (50%) -> unweighted average of % would be 67.5%,
        // but weighted overall % MUST be (85 + 25) / (100 + 50) * 100 = 110 / 150 * 100 = 73.333% (Grade B)
        val stu = Student(
            id = "stu_1",
            name = "Ali Ahmed",
            fatherName = "Tariq Ahmed",
            rollNumber = "01",
            admissionNumber = "ADM-01",
            classId = "cls_5",
            sectionId = "sec_a",
            sessionId = "sess_1"
        )
        val marks = listOf(
            MarkEntry(id = "m1", sessionId = "sess_1", examId = "exam_mid", classId = "cls_5", sectionId = "sec_a", studentId = "stu_1", subjectId = "sub_math", obtainedMarks = 85.0),
            MarkEntry(id = "m2", sessionId = "sess_1", examId = "exam_mid", classId = "cls_5", sectionId = "sec_a", studentId = "stu_1", subjectId = "sub_isl", obtainedMarks = 25.0)
        )

        val summaries = ResultCalculator.computeClassResults(
            students = listOf(stu),
            subjects = listOf(math, islamiat),
            marks = marks,
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = defaultExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates()
        )

        assertEquals(1, summaries.size)
        val s = summaries.first()
        assertEquals(ResultStatus.COMPLETE, s.status)
        assertEquals(150.0, s.totalMaxMarks, 0.001)
        assertEquals(110.0, s.totalObtainedMarks, 0.001)
        assertEquals(73.333, s.overallPercentage, 0.01)
        assertEquals("B", s.overallGrade)
        assertTrue(s.passed)
        assertEquals(1, s.position)
    }

    @Test
    fun `incomplete results are not treated as zero and are excluded from rank`() {
        val stuComplete1 = Student(id = "s1", name = "Amina", fatherName = "Kamran", rollNumber = "01", admissionNumber = "A1", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val stuComplete2 = Student(id = "s2", name = "Bilal", fatherName = "Zafar", rollNumber = "02", admissionNumber = "A2", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val stuCompleteTie = Student(id = "s3", name = "Hira", fatherName = "Sajid", rollNumber = "03", admissionNumber = "A3", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val stuIncomplete = Student(id = "s4", name = "Zain", fatherName = "Nadeem", rollNumber = "04", admissionNumber = "A4", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")

        val marks = listOf(
            // s1: 90 + 45 = 135
            MarkEntry(id = "m1", studentId = "s1", subjectId = "sub_math", obtainedMarks = 90.0),
            MarkEntry(id = "m2", studentId = "s1", subjectId = "sub_isl", obtainedMarks = 45.0),
            // s2: 80 + 40 = 120
            MarkEntry(id = "m3", studentId = "s2", subjectId = "sub_math", obtainedMarks = 80.0),
            MarkEntry(id = "m4", studentId = "s2", subjectId = "sub_isl", obtainedMarks = 40.0),
            // s3: 90 + 45 = 135 (ties s1 for 1st; s2 should be 3rd)
            MarkEntry(id = "m5", studentId = "s3", subjectId = "sub_math", obtainedMarks = 90.0),
            MarkEntry(id = "m6", studentId = "s3", subjectId = "sub_isl", obtainedMarks = 45.0),
            // s4: missing Islamiat
            MarkEntry(id = "m7", studentId = "s4", subjectId = "sub_math", obtainedMarks = 95.0)
        )

        val summaries = ResultCalculator.computeClassResults(
            students = listOf(stuComplete1, stuComplete2, stuCompleteTie, stuIncomplete),
            subjects = listOf(math, islamiat),
            marks = marks,
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = defaultExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates()
        ).associateBy { it.student.id }

        assertEquals(1, summaries["s1"]?.position)
        assertEquals(1, summaries["s3"]?.position)
        assertEquals(3, summaries["s2"]?.position)
        assertEquals(ResultStatus.INCOMPLETE, summaries["s4"]?.status)
        assertEquals("Pending", summaries["s4"]?.overallGrade)
        assertNull(summaries["s4"]?.position)
    }

    @Test
    fun `failing compulsory subject fails overall result even if overall percentage is high`() {
        val stu = Student(id = "s1", name = "Hamza", fatherName = "Imran", rollNumber = "05", admissionNumber = "A5", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        // Math: 95/100 (Pass), Islamiat (compulsory, min 20): 15/50 (Fail). Total = 110/150 = 73.3%
        val marks = listOf(
            MarkEntry(id = "m1", studentId = "s1", subjectId = "sub_math", obtainedMarks = 95.0),
            MarkEntry(id = "m2", studentId = "s1", subjectId = "sub_isl", obtainedMarks = 15.0)
        )

        val summary = ResultCalculator.computeClassResults(
            students = listOf(stu),
            subjects = listOf(math, islamiat),
            marks = marks,
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = defaultExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates()
        ).first()

        assertFalse(summary.passed)
        assertEquals("F*", summary.overallGrade)
        assertTrue(summary.failedCompulsorySubjects.contains("Islamiat"))
    }

    @Test
    fun `custom grading rules override default grading when enabled on examination`() {
        val customExam = defaultExam.copy(id = "exam_custom", useCustomGrading = true)
        val rules = ResultCalculator.defaultGradingTemplates() + listOf(
            GradingRule(id = "c1", examId = "exam_custom", minPercentage = 85.0, maxPercentage = 100.0, grade = "DIST", remarks = "Distinction", passing = true, sortOrder = 1),
            GradingRule(id = "c2", examId = "exam_custom", minPercentage = 0.0, maxPercentage = 84.99, grade = "PASS", remarks = "Cleared", passing = true, sortOrder = 2)
        )
        val stu = Student(id = "s1", name = "Sana", fatherName = "Riaz", rollNumber = "06", admissionNumber = "A6", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val marks = listOf(MarkEntry(id = "m1", studentId = "s1", subjectId = "sub_math", obtainedMarks = 86.0))

        val summary = ResultCalculator.computeClassResults(
            students = listOf(stu),
            subjects = listOf(math),
            marks = marks,
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = customExam,
            allGradingRules = rules
        ).first()

        assertEquals("DIST", summary.overallGrade)
    }

    @Test
    fun `xlsx round-trip export and import validates students marks and subjects`() {
        // 1. Students export -> parse round-trip
        val existingStudent = Student(
            id = "stu_existing",
            name = "Ayesha Khan",
            fatherName = "Farooq Khan",
            rollNumber = "10",
            admissionNumber = "ADM-10",
            classId = "cls_5",
            sectionId = "sec_a",
            sessionId = "sess_1"
        )
        val studentsXlsx = XlsxEngine.exportCompleteStudentList(
            students = listOf(existingStudent),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session)
        )
        // Parse against empty existingStudents should accept row; duplicate check against existingStudent should flag row
        val parsedNew = XlsxEngine.parseAndValidateStudents(
            inputStream = ByteArrayInputStream(studentsXlsx),
            classes = listOf(schoolClass),
            sections = listOf(sectionA),
            sessions = listOf(session),
            existingStudents = emptyList(),
            defaultSessionId = session.id
        )
        assertEquals(1, parsedNew.validRows.size)
        assertTrue(parsedNew.errors.isEmpty())
        assertEquals("Ayesha Khan", parsedNew.validRows.first().name)

        val parsedDup = XlsxEngine.parseAndValidateStudents(
            inputStream = ByteArrayInputStream(studentsXlsx),
            classes = listOf(schoolClass),
            sections = listOf(sectionA),
            sessions = listOf(session),
            existingStudents = listOf(existingStudent),
            defaultSessionId = session.id
        )
        assertEquals(0, parsedDup.validRows.size)
        assertEquals(1, parsedDup.errors.size)

        // 2. Marks export -> parse round-trip (validates decimal rule & max marks)
        val summary = ResultCalculator.computeClassResults(
            students = listOf(existingStudent),
            subjects = listOf(math),
            marks = listOf(MarkEntry(id = "m1", studentId = "stu_existing", subjectId = "sub_math", obtainedMarks = 87.5)),
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = defaultExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates()
        ).first()

        val marksXlsx = XlsxEngine.exportSubjectWiseMarks(
            className = schoolClass.name,
            sectionName = sectionA.name,
            examName = defaultExam.name,
            sessionName = session.name,
            subject = math,
            summaries = listOf(summary)
        )
        val parsedMarksDecimalAllowed = XlsxEngine.parseAndValidateMarks(
            inputStream = ByteArrayInputStream(marksXlsx),
            classes = listOf(schoolClass),
            sections = listOf(sectionA),
            students = listOf(existingStudent),
            subjects = listOf(math, computer),
            allowDecimalMarks = true
        )
        // Note: exportSubjectWiseMarks doesn't have a "Class" column (it has Class in subtitle), so test template-based marks workbook:
        assertTrue(parsedMarksDecimalAllowed.errors.isNotEmpty())

        val marksTemplateWorkbook = XlsxEngine.writeWorkbook(
            listOf(
                com.example.domain.XlsxSheetData(
                    name = "Marks_Import",
                    headers = listOf("Roll Number", "Class", "Section", "Subject", "Obtained Marks"),
                    rows = listOf(
                        listOf(
                            com.example.domain.CellValue.Text("10"),
                            com.example.domain.CellValue.Text("Class 5"),
                            com.example.domain.CellValue.Text("A"),
                            com.example.domain.CellValue.Text("Mathematics"),
                            com.example.domain.CellValue.Number(87.5)
                        ),
                        listOf(
                            com.example.domain.CellValue.Text("10"),
                            com.example.domain.CellValue.Text("Class 5"),
                            com.example.domain.CellValue.Text("A"),
                            com.example.domain.CellValue.Text("Computer"),
                            com.example.domain.CellValue.Number(65.0) // exceeds maxMarks 50 -> error
                        )
                    )
                )
            )
        )
        val marksResult = XlsxEngine.parseAndValidateMarks(
            inputStream = ByteArrayInputStream(marksTemplateWorkbook),
            classes = listOf(schoolClass),
            sections = listOf(sectionA),
            students = listOf(existingStudent),
            subjects = listOf(math, computer),
            allowDecimalMarks = true
        )
        assertEquals(1, marksResult.validRows.size)
        assertEquals(87.5, marksResult.validRows.first().obtainedMarks, 0.001)
        assertEquals(1, marksResult.errors.size)

        val marksNoDecimals = XlsxEngine.parseAndValidateMarks(
            inputStream = ByteArrayInputStream(marksTemplateWorkbook),
            classes = listOf(schoolClass),
            sections = listOf(sectionA),
            students = listOf(existingStudent),
            subjects = listOf(math, computer),
            allowDecimalMarks = false
        )
        assertEquals(0, marksNoDecimals.validRows.size)
        assertEquals(2, marksNoDecimals.errors.size)
    }

    @Test
    fun `extractTextFromDocxStream reads paragraphs and tables into student candidates`() {
        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>THE EDUCATORS ABBOTTABAD</w:t></w:r></w:p>
                <w:p><w:r><w:t>Student List</w:t></w:r></w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Roll No</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Student Name</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Father's Name</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>101</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Hamza Tariq</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Tariq Mehmood</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>102</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Ayesha Siddiqui</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Salman Siddiqui</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val rawText = StudentDocumentExtractor.extractTextFromWordBytes(baos.toByteArray())
        val candidates = StudentDocumentExtractor.parseStudentCandidates(rawText, startingRoll = 1)

        assertEquals(2, candidates.size)
        assertEquals("101", candidates[0].rollNumber)
        assertEquals("Hamza Tariq", candidates[0].name)
        assertEquals("Tariq Mehmood", candidates[0].fatherName)
        assertEquals("ADM-101", candidates[0].admissionNumber)

        assertEquals("102", candidates[1].rollNumber)
        assertEquals("Ayesha Siddiqui", candidates[1].name)
        assertEquals("Salman Siddiqui", candidates[1].fatherName)
    }

    @Test
    fun `parseStudentCandidates handles numbered lists and S_O D_O lines while filtering noise`() {
        val ocrSample = """
            THE EDUCATORS
            Class: Class 5   Page 1
            1. Muhammad Usman S/O Khalid Mehmood
            2) Zainab Bibi D/O Shafqat Ali
            Bilal Ahmed
            0312-1234567
        """.trimIndent()

        val candidates = StudentDocumentExtractor.parseStudentCandidates(ocrSample, startingRoll = 5)
        assertEquals(3, candidates.size)

        assertEquals("1", candidates[0].rollNumber)
        assertEquals("Muhammad Usman", candidates[0].name)
        assertEquals("Khalid Mehmood", candidates[0].fatherName)

        assertEquals("2", candidates[1].rollNumber)
        assertEquals("Zainab Bibi", candidates[1].name)
        assertEquals("Shafqat Ali", candidates[1].fatherName)

        assertEquals("5", candidates[2].rollNumber)
        assertEquals("Bilal Ahmed", candidates[2].name)
        assertEquals("-", candidates[2].fatherName)
        assertEquals("ADM-5", candidates[2].admissionNumber)
    }
}
