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
        // but weighted overall % MUST be (85 + 25) / (100 + 50) * 100 = 110 / 150 * 100 = 73.333% (Grade B+)
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
        assertEquals("B+", s.overallGrade)
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
        assertEquals("", candidates[0].admissionNumber)

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

        assertEquals("", candidates[2].rollNumber)
        assertEquals("Bilal Ahmed", candidates[2].name)
        assertEquals("", candidates[2].fatherName)
        assertEquals("", candidates[2].admissionNumber)
    }

    @Test
    fun `parseStudentCandidates strips consolidated sheet marks percentages and trailing dots`() {
        val consolidatedOcr = """
            THE EDUCATORS
            Consolidated Sheet
            VIII - Mars *** ASSESSMENT TASK-I (TERM II)
            Farhan Khalid . 27.00 16.00 20.00 14.00 297.75 66.2% B
            Muhammad Bilal Tareen . 22.00 17.00 319.75 71.1% B+
            Idrees Khalid 30.00 11.00 21.00 361.75 80.4% A
        """.trimIndent()

        val candidates = StudentDocumentExtractor.parseStudentCandidates(consolidatedOcr, startingRoll = 1)
        assertEquals(3, candidates.size)
        assertEquals("Farhan Khalid", candidates[0].name)
        assertEquals("Muhammad Bilal Tareen", candidates[1].name)
        assertEquals("Idrees Khalid", candidates[2].name)
    }

    @Test
    fun `component exam computes highest marks in class and exports merged headers and grading key`() {
        val compExam = defaultExam.copy(
            id = "exam_comp",
            name = "MID YEAR EXAMINATION",
            components = listOf(
                com.example.data.model.ExamComponent("classwork", "Classwork Average", 10.0),
                com.example.data.model.ExamComponent("quiz", "Quiz", 10.0),
                com.example.data.model.ExamComponent("assess_1", "Assessment-I", 15.0),
                com.example.data.model.ExamComponent("assess_2", "Assessment-II", 25.0)
            )
        )
        val eng = Subject(id = "sub_eng", classId = "cls_5", name = "English", maxMarks = 60.0, passingMarks = 24.0, sortOrder = 1)
        val stu1 = Student(id = "s1", name = "Abiha Fatima", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val stu2 = Student(id = "s2", name = "Hina Tariq", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")

        val marks = listOf(
            MarkEntry(
                id = "m1", classId = "cls_5", studentId = "s1", subjectId = "sub_eng", obtainedMarks = 59.0,
                componentMarks = mapOf("classwork" to 9.0, "quiz" to 10.0, "assess_1" to 15.0, "assess_2" to 25.0)
            ),
            MarkEntry(
                id = "m2", classId = "cls_5", studentId = "s2", subjectId = "sub_eng", obtainedMarks = 60.0,
                componentMarks = mapOf("classwork" to 10.0, "quiz" to 10.0, "assess_1" to 15.0, "assess_2" to 25.0)
            )
        )

        val summaries = ResultCalculator.computeClassResults(
            students = listOf(stu1, stu2),
            subjects = listOf(eng),
            marks = marks,
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = compExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates()
        )
        val abiha = summaries.first { it.student.id == "s1" }
        assertEquals(60.0, abiha.subjectResults.first().highestMarksInClass ?: 0.0, 0.001)
        assertEquals(98.33, abiha.overallPercentage, 0.05)
        assertEquals("A+", abiha.overallGrade)

        val consolidatedXlsx = XlsxEngine.exportCompleteClassResultSheet(
            className = "Class 5",
            sectionLabel = "A",
            examName = compExam.name,
            sessionName = session.name,
            subjects = listOf(eng),
            summaries = summaries,
            examComponents = compExam.components
        )
        val consolidatedXml = readSheet1Xml(consolidatedXlsx)
        assertTrue(consolidatedXml.contains("<mergeCells"))
        assertTrue(consolidatedXml.contains("Consolidated Sheet"))

        val individualXlsx = XlsxEngine.exportIndividualStudentResult(abiha)
        val individualXml = readSheet1Xml(individualXlsx)
        assertTrue(individualXml.contains("GRADING KEY"))
        assertTrue(individualXml.contains("GRAND TOTAL"))
        assertTrue(individualXml.contains("Highest Marks in Class"))
    }

    @Test
    fun `parseAndValidateStudents accepts minimal columns with optional fields omitted`() {
        val minimalWorkbook = XlsxEngine.writeWorkbook(
            listOf(
                com.example.domain.XlsxSheetData(
                    name = "Students",
                    headers = listOf("Student Name", "Class"),
                    rows = listOf(
                        listOf(
                            com.example.domain.CellValue.Text("Zoya Malik"),
                            com.example.domain.CellValue.Text("Class 5")
                        )
                    )
                )
            )
        )
        val preview = XlsxEngine.parseAndValidateStudents(
            inputStream = ByteArrayInputStream(minimalWorkbook),
            classes = listOf(schoolClass),
            sections = listOf(sectionA),
            sessions = listOf(session),
            existingStudents = emptyList(),
            defaultSessionId = session.id
        )
        assertEquals(1, preview.validRows.size)
        assertTrue(preview.errors.isEmpty())
        assertEquals("Zoya Malik", preview.validRows.first().name)
        assertEquals("", preview.validRows.first().fatherName)
        assertEquals("", preview.validRows.first().rollNumber)
        assertEquals("", preview.validRows.first().admissionNumber)
    }

    @Test
    fun `term with joined quizzes and different total marks enforces 40 percent passing threshold`() {
        // Subject default max is 100, but Term 1 Assessment joins Quiz (10) + Assessment-I (15) = 25 marks!
        // 40% of 25 = 10.0 marks -> 10.0 PASS, 9.5 (38%) FAIL.
        val term1 = Examination(
            id = "exam_term1",
            sessionId = "sess_1",
            name = "Term 1 Assessment",
            examType = ExamType.MONTHLY_TEST.name,
            components = listOf(
                com.example.data.model.ExamComponent("quiz", "Quiz", 10.0),
                com.example.data.model.ExamComponent("assess1", "Assessment-I", 15.0)
            )
        )
        val stuPass = Student(id = "st_pass", name = "Bilal", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val stuFail = Student(id = "st_fail", name = "Omar", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")

        val marks = listOf(
            MarkEntry(
                id = "m_p", examId = "exam_term1", classId = "cls_5", studentId = "st_pass", subjectId = "sub_math",
                obtainedMarks = 10.0, componentMarks = mapOf("quiz" to 4.0, "assess1" to 6.0)
            ),
            MarkEntry(
                id = "m_f", examId = "exam_term1", classId = "cls_5", studentId = "st_fail", subjectId = "sub_math",
                obtainedMarks = 9.5, componentMarks = mapOf("quiz" to 3.5, "assess1" to 6.0)
            )
        )

        val summaries = ResultCalculator.computeClassResults(
            students = listOf(stuPass, stuFail),
            subjects = listOf(math),
            marks = marks,
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = term1,
            allGradingRules = ResultCalculator.defaultGradingTemplates(),
            allExaminations = listOf(term1),
            allSessionMarks = marks
        ).associateBy { it.student.id }

        val passRes = summaries["st_pass"]!!
        assertEquals(25.0, passRes.totalMaxMarks, 0.001)
        assertEquals(10.0, passRes.totalObtainedMarks, 0.001)
        assertEquals(40.0, passRes.overallPercentage, 0.01)
        assertTrue(passRes.passed)
        assertEquals("D", passRes.overallGrade)

        val failRes = summaries["st_fail"]!!
        assertEquals(25.0, failRes.totalMaxMarks, 0.001)
        assertEquals(38.0, failRes.overallPercentage, 0.01)
        assertFalse(failRes.passed)
        assertEquals("F", failRes.overallGrade)
    }

    @Test
    fun `final exam automatically carries forward percentages from previous terms and adds manual paper marks`() {
        // Monthly Test (Quiz 10 + Assess 15 = 25): student scores 20/25 = 80%
        // Midterm (Max 50): student scores 45/50 = 90%
        // Final Exam (100): Monthly Test (10% -> 8.0) + Midterm (30% -> 27.0) + Final Paper (60 -> 45.0) = 80.0/100 (80% Grade A)
        val monthly = Examination(
            id = "exam_monthly",
            sessionId = "sess_1",
            name = "Monthly Test",
            examType = ExamType.MONTHLY_TEST.name,
            components = listOf(
                com.example.data.model.ExamComponent("q1", "Quiz", 10.0),
                com.example.data.model.ExamComponent("a1", "Assessment", 15.0)
            )
        )
        val midterm = Examination(
            id = "exam_midterm",
            sessionId = "sess_1",
            name = "Midterm",
            examType = ExamType.MIDTERM.name,
            components = listOf(
                com.example.data.model.ExamComponent("mid_paper", "Midterm Paper", 50.0)
            )
        )
        val finalExam = Examination(
            id = "exam_final",
            sessionId = "sess_1",
            name = "Final Exam",
            examType = ExamType.FINAL_TERM.name,
            components = listOf(
                com.example.data.model.ExamComponent(
                    id = "cf_monthly",
                    name = "Monthly (10%)",
                    maxMarks = 10.0,
                    sourceExamId = "exam_monthly",
                    weightPercentage = 10.0
                ),
                com.example.data.model.ExamComponent(
                    id = "cf_midterm",
                    name = "Midterm (30%)",
                    maxMarks = 30.0,
                    sourceExamId = "exam_midterm",
                    weightPercentage = 30.0
                ),
                com.example.data.model.ExamComponent(
                    id = "final_paper",
                    name = "Final Paper",
                    maxMarks = 60.0
                )
            )
        )

        val stu = Student(id = "s1", name = "Ayesha", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val allMarks = listOf(
            MarkEntry(
                id = "m_mon", examId = "exam_monthly", classId = "cls_5", studentId = "s1", subjectId = "sub_math",
                obtainedMarks = 20.0, componentMarks = mapOf("q1" to 8.0, "a1" to 12.0)
            ),
            MarkEntry(
                id = "m_mid", examId = "exam_midterm", classId = "cls_5", studentId = "s1", subjectId = "sub_math",
                obtainedMarks = 45.0, componentMarks = mapOf("mid_paper" to 45.0)
            ),
            MarkEntry(
                id = "m_fin", examId = "exam_final", classId = "cls_5", studentId = "s1", subjectId = "sub_math",
                obtainedMarks = 80.0, componentMarks = mapOf("final_paper" to 45.0)
            )
        )

        val summary = ResultCalculator.computeClassResults(
            students = listOf(stu),
            subjects = listOf(math),
            marks = allMarks.filter { it.examId == "exam_final" },
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = finalExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates(),
            allExaminations = listOf(monthly, midterm, finalExam),
            allSessionMarks = allMarks
        ).first()

        val mathRes = summary.subjectResults.first()
        assertEquals(100.0, mathRes.effectiveMaxMarks, 0.001)
        assertEquals(8.0, mathRes.resolvedComponentMarks["cf_monthly"] ?: -1.0, 0.001)
        assertEquals(27.0, mathRes.resolvedComponentMarks["cf_midterm"] ?: -1.0, 0.001)
        assertEquals(45.0, mathRes.resolvedComponentMarks["final_paper"] ?: -1.0, 0.001)
        assertEquals(80.0, mathRes.obtainedMarks ?: -1.0, 0.001)
        assertEquals(80.0, summary.overallPercentage, 0.01)
        assertEquals("A", summary.overallGrade)
        assertTrue(summary.passed)

        // Pure composite exam (no manual entry needed): 50% Monthly (80% -> 40) + 50% Midterm (90% -> 45) = 85/100
        val compositeAnnual = Examination(
            id = "exam_annual",
            sessionId = "sess_1",
            name = "Annual Combined",
            examType = ExamType.FINAL_TERM.name,
            components = listOf(
                com.example.data.model.ExamComponent("c1", "Monthly 50%", 50.0, sourceExamId = "exam_monthly", weightPercentage = 50.0),
                com.example.data.model.ExamComponent("c2", "Midterm 50%", 50.0, sourceExamId = "exam_midterm", weightPercentage = 50.0)
            )
        )
        val compositeSummary = ResultCalculator.computeClassResults(
            students = listOf(stu),
            subjects = listOf(math),
            marks = emptyList(),
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = compositeAnnual,
            allGradingRules = ResultCalculator.defaultGradingTemplates(),
            allExaminations = listOf(monthly, midterm, compositeAnnual),
            allSessionMarks = allMarks
        ).first()
        assertEquals(ResultStatus.COMPLETE, compositeSummary.status)
        assertEquals(85.0, compositeSummary.totalObtainedMarks, 0.001)
        assertEquals(85.0, compositeSummary.overallPercentage, 0.01)
        assertEquals("A", compositeSummary.overallGrade)
    }

    @Test
    fun `teacher per-subject total marks and component overrides recalculate percentages 40 percent pass and final exam carry-forward`() {
        // Case 1: Single-mark exam where Teacher sets Math total = 50 and Islamiat total = 25 (defaults were 100 and 50)
        val stu = Student(id = "s_ov", name = "Zainab", classId = "cls_5", sectionId = "sec_a", sessionId = "sess_1")
        val singleConfigs = listOf(
            com.example.data.model.ExamSubjectConfig(
                id = "exam_mid_sub_math",
                examId = "exam_mid",
                classId = "cls_5",
                subjectId = "sub_math",
                totalMaxMarks = 50.0
            ),
            com.example.data.model.ExamSubjectConfig(
                id = "exam_mid_sub_isl",
                examId = "exam_mid",
                classId = "cls_5",
                subjectId = "sub_isl",
                totalMaxMarks = 25.0
            )
        )
        // Math: 20/50 = 40% (PASS), Islamiat: 20/25 = 80% (PASS) -> Total 40/75 = 53.33% (Grade C)
        val singleMarks = listOf(
            MarkEntry(id = "m1", examId = "exam_mid", classId = "cls_5", studentId = "s_ov", subjectId = "sub_math", obtainedMarks = 20.0),
            MarkEntry(id = "m2", examId = "exam_mid", classId = "cls_5", studentId = "s_ov", subjectId = "sub_isl", obtainedMarks = 20.0)
        )
        val singleSummary = ResultCalculator.computeClassResults(
            students = listOf(stu),
            subjects = listOf(math, islamiat),
            marks = singleMarks,
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = defaultExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates(),
            allExaminations = listOf(defaultExam),
            allSessionMarks = singleMarks,
            examSubjectConfigs = singleConfigs
        ).first()

        assertEquals(75.0, singleSummary.totalMaxMarks, 0.001)
        assertEquals(40.0, singleSummary.totalObtainedMarks, 0.001)
        assertEquals(53.33, singleSummary.overallPercentage, 0.05)
        assertTrue(singleSummary.passed)
        assertEquals(20.0, singleSummary.subjectResults.first { it.subject.id == "sub_math" }.effectivePassingMarks, 0.001)
        assertEquals(10.0, singleSummary.subjectResults.first { it.subject.id == "sub_isl" }.effectivePassingMarks, 0.001)

        // Case 2: Multi-component Term 1 where Teacher customizes Math Quiz(15)+Assess(25)=40,
        // while Islamiat keeps Quiz(10)+Assess(15)=25, AND Final Exam carries 20% of Term 1!
        val term1 = Examination(
            id = "exam_t1",
            sessionId = "sess_1",
            name = "Term 1",
            examType = ExamType.MONTHLY_TEST.name,
            components = listOf(
                com.example.data.model.ExamComponent("q", "Quiz", 10.0),
                com.example.data.model.ExamComponent("a", "Assessment", 15.0)
            )
        )
        val finalExam = Examination(
            id = "exam_fin",
            sessionId = "sess_1",
            name = "Final Exam",
            examType = ExamType.FINAL_TERM.name,
            components = listOf(
                com.example.data.model.ExamComponent("cf_t1", "Term 1 (20%)", 20.0, sourceExamId = "exam_t1", weightPercentage = 20.0),
                com.example.data.model.ExamComponent("paper", "Final Paper", 80.0)
            )
        )
        val compConfigs = listOf(
            com.example.data.model.ExamSubjectConfig(
                id = "exam_t1_sub_math",
                examId = "exam_t1",
                classId = "cls_5",
                subjectId = "sub_math",
                totalMaxMarks = 40.0,
                componentMaxMarks = mapOf("q" to 15.0, "a" to 25.0)
            )
        )
        // Term 1 Math: 30/40 = 75% -> 20% carried into Final = 15.0 marks + Paper 65/80 = 80/100 (Grade A)
        // Term 1 Islamiat: 20/25 = 80% -> 20% carried into Final = 16.0 marks + Paper 64/80 = 80/100 (Grade A)
        val sessionMarks = listOf(
            MarkEntry(id = "mt1_m", examId = "exam_t1", classId = "cls_5", studentId = "s_ov", subjectId = "sub_math", obtainedMarks = 30.0, componentMarks = mapOf("q" to 10.0, "a" to 20.0)),
            MarkEntry(id = "mt1_i", examId = "exam_t1", classId = "cls_5", studentId = "s_ov", subjectId = "sub_isl", obtainedMarks = 20.0, componentMarks = mapOf("q" to 8.0, "a" to 12.0)),
            MarkEntry(id = "mf_m", examId = "exam_fin", classId = "cls_5", studentId = "s_ov", subjectId = "sub_math", obtainedMarks = 80.0, componentMarks = mapOf("paper" to 65.0)),
            MarkEntry(id = "mf_i", examId = "exam_fin", classId = "cls_5", studentId = "s_ov", subjectId = "sub_isl", obtainedMarks = 80.0, componentMarks = mapOf("paper" to 64.0))
        )
        val finalSummary = ResultCalculator.computeClassResults(
            students = listOf(stu),
            subjects = listOf(math, islamiat),
            marks = sessionMarks.filter { it.examId == "exam_fin" },
            resultRecords = emptyList(),
            classesById = mapOf(schoolClass.id to schoolClass),
            sectionsById = mapOf(sectionA.id to sectionA),
            sessionsById = mapOf(session.id to session),
            examination = finalExam,
            allGradingRules = ResultCalculator.defaultGradingTemplates(),
            allExaminations = listOf(term1, finalExam),
            allSessionMarks = sessionMarks,
            examSubjectConfigs = compConfigs
        ).first()

        val mathFin = finalSummary.subjectResults.first { it.subject.id == "sub_math" }
        val islFin = finalSummary.subjectResults.first { it.subject.id == "sub_isl" }
        assertEquals(15.0, mathFin.resolvedComponentMarks["cf_t1"] ?: -1.0, 0.001)
        assertEquals(16.0, islFin.resolvedComponentMarks["cf_t1"] ?: -1.0, 0.001)
        assertEquals(80.0, mathFin.obtainedMarks ?: -1.0, 0.001)
        assertEquals(80.0, islFin.obtainedMarks ?: -1.0, 0.001)
        assertEquals(80.0, finalSummary.overallPercentage, 0.01)
        assertEquals("A", finalSummary.overallGrade)
    }

    private fun readSheet1Xml(xlsxBytes: ByteArray): String {
        java.util.zip.ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        return ""
    }
}
