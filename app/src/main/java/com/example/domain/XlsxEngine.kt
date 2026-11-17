package com.example.domain

import android.util.Xml
import com.example.data.model.AcademicSession
import com.example.data.model.ClassPerformanceStats
import com.example.data.model.ClassSection
import com.example.data.model.ImportPreviewResult
import com.example.data.model.ImportRowError
import com.example.data.model.ResultStatus
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.StudentResultSummary
import com.example.data.model.Subject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed interface CellValue {
    data class Text(val value: String) : CellValue
    data class Number(val value: Double) : CellValue
    data class Percent(val ratio: Double) : CellValue // e.g. 0.85 displays as 85.00%
}

data class XlsxSheetData(
    val name: String,
    val titleRow: String? = null,
    val subtitleRow: String? = null,
    val headers: List<String>,
    val rows: List<List<CellValue>>
)

data class ImportedStudentDraft(
    val name: String,
    val fatherName: String,
    val rollNumber: String,
    val admissionNumber: String,
    val classId: String,
    val className: String,
    val sectionId: String,
    val sectionName: String,
    val sessionId: String,
    val sessionName: String
)

data class ImportedMarkDraft(
    val studentId: String,
    val studentName: String,
    val rollNumber: String,
    val classId: String,
    val sectionId: String,
    val subjectId: String,
    val subjectName: String,
    val obtainedMarks: Double
)

data class ImportedSubjectDraft(
    val classId: String,
    val className: String,
    val name: String,
    val maxMarks: Double,
    val passingMarks: Double,
    val compulsory: Boolean
)

object XlsxEngine {

    // --- 5 REQUIRED EXPORTS ---

    fun exportCompleteStudentList(
        students: List<Student>,
        classesById: Map<String, SchoolClass>,
        sectionsById: Map<String, ClassSection>,
        sessionsById: Map<String, AcademicSession>
    ): ByteArray {
        val headers = listOf(
            "Roll Number", "Admission Number", "Student Name", "Father's Name",
            "Class", "Section", "Academic Session"
        )
        val rows = students.sortedWith(compareBy<Student> { classesById[it.classId]?.sortOrder ?: 99L }.thenBy { it.rollNumber })
            .map { s ->
                listOf(
                    CellValue.Text(s.rollNumber),
                    CellValue.Text(s.admissionNumber),
                    CellValue.Text(s.name),
                    CellValue.Text(s.fatherName),
                    CellValue.Text(classesById[s.classId]?.name ?: ""),
                    CellValue.Text(sectionsById[s.sectionId]?.name ?: ""),
                    CellValue.Text(sessionsById[s.sessionId]?.name ?: "")
                )
            }
        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = "Students",
                    titleRow = "THE EDUCATORS - ABBOTTABAD MAIN CAMPUS",
                    subtitleRow = "Complete Student Directory",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    fun exportSubjectWiseMarks(
        className: String,
        sectionName: String,
        examName: String,
        sessionName: String,
        subject: Subject,
        summaries: List<StudentResultSummary>
    ): ByteArray {
        val headers = listOf(
            "Roll No", "Admission No", "Student Name", "Father's Name",
            "Subject", "Max Marks", "Passing Marks", "Obtained Marks", "Percentage", "Grade", "Status"
        )
        val rows = summaries.sortedBy { it.student.rollNumber }.map { summary ->
            val sr = summary.subjectResults.firstOrNull { it.subject.id == subject.id }
            val obtainedCell = if (sr?.obtainedMarks != null) CellValue.Number(sr.obtainedMarks) else CellValue.Text("Pending")
            val pctCell = if (sr?.percentage != null) CellValue.Percent(sr.percentage / 100.0) else CellValue.Text("-")
            val statusText = when (sr?.passed) {
                true -> "Pass"
                false -> "Fail"
                null -> "Pending"
            }
            listOf(
                CellValue.Text(summary.student.rollNumber),
                CellValue.Text(summary.student.admissionNumber),
                CellValue.Text(summary.student.name),
                CellValue.Text(summary.student.fatherName),
                CellValue.Text(subject.name),
                CellValue.Number(subject.maxMarks),
                CellValue.Number(subject.passingMarks),
                obtainedCell,
                pctCell,
                CellValue.Text(sr?.grade ?: "-"),
                CellValue.Text(statusText)
            )
        }
        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = sanitizeSheetName(subject.name),
                    titleRow = "THE EDUCATORS - ABBOTTABAD MAIN CAMPUS",
                    subtitleRow = "$examName ($sessionName) | $className - $sectionName | Subject: ${subject.name}",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    fun exportIndividualStudentResult(summary: StudentResultSummary): ByteArray {
        val headers = listOf("Subject", "Compulsory", "Total Marks", "Passing Marks", "Obtained Marks", "Percentage", "Grade", "Status")
        val rows = summary.subjectResults.map { sr ->
            listOf(
                CellValue.Text(sr.subject.name),
                CellValue.Text(if (sr.subject.compulsory) "Yes" else "Elective"),
                CellValue.Number(sr.subject.maxMarks),
                CellValue.Number(sr.subject.passingMarks),
                if (sr.obtainedMarks != null) CellValue.Number(sr.obtainedMarks) else CellValue.Text("Pending"),
                if (sr.percentage != null) CellValue.Percent(sr.percentage / 100.0) else CellValue.Text("-"),
                CellValue.Text(sr.grade),
                CellValue.Text(
                    when (sr.passed) {
                        true -> "Pass"
                        false -> "Fail"
                        null -> "Pending"
                    }
                )
            )
        } + listOf(
            listOf(
                CellValue.Text("TOTAL / OVERALL"),
                CellValue.Text(""),
                CellValue.Number(summary.totalMaxMarks),
                CellValue.Text(""),
                CellValue.Number(summary.totalObtainedMarks),
                CellValue.Percent(summary.overallPercentage / 100.0),
                CellValue.Text(summary.overallGrade),
                CellValue.Text(
                    when {
                        summary.status == ResultStatus.INCOMPLETE -> "Incomplete (${summary.enteredSubjectCount}/${summary.totalSubjectCount})"
                        summary.passed -> "PASS (Pos: ${ResultCalculator.formatOrdinal(summary.position)})"
                        else -> "FAIL"
                    }
                )
            )
        )

        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = sanitizeSheetName("${summary.student.rollNumber}_${summary.student.name}"),
                    titleRow = "THE EDUCATORS - ABBOTTABAD MAIN CAMPUS",
                    subtitleRow = "${summary.student.name} (Roll: ${summary.student.rollNumber}) | ${summary.className}-${summary.sectionName} | ${summary.examName} (${summary.sessionName})",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    fun exportCompleteClassResultSheet(
        className: String,
        sectionLabel: String,
        examName: String,
        sessionName: String,
        subjects: List<Subject>,
        summaries: List<StudentResultSummary>
    ): ByteArray {
        val sortedSubjects = subjects.sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name })
        val headers = buildList {
            add("Roll No")
            add("Student Name")
            add("Father's Name")
            sortedSubjects.forEach { add("${it.name} (${ResultCalculator.formatMarks(it.maxMarks)})") }
            add("Total Marks")
            add("Obtained Marks")
            add("Percentage")
            add("Grade")
            add("Position")
            add("Result Status")
        }

        val rows = summaries.map { summary ->
            buildList {
                add(CellValue.Text(summary.student.rollNumber))
                add(CellValue.Text(summary.student.name))
                add(CellValue.Text(summary.student.fatherName))
                sortedSubjects.forEach { sub ->
                    val sr = summary.subjectResults.firstOrNull { it.subject.id == sub.id }
                    if (sr?.obtainedMarks != null) {
                        add(CellValue.Number(sr.obtainedMarks))
                    } else {
                        add(CellValue.Text("Pending"))
                    }
                }
                add(CellValue.Number(summary.totalMaxMarks))
                add(CellValue.Number(summary.totalObtainedMarks))
                add(CellValue.Percent(summary.overallPercentage / 100.0))
                add(CellValue.Text(summary.overallGrade))
                add(CellValue.Text(ResultCalculator.formatOrdinal(summary.position)))
                add(
                    CellValue.Text(
                        when {
                            summary.status == ResultStatus.INCOMPLETE -> "Incomplete"
                            summary.passed -> "Pass"
                            else -> "Fail"
                        }
                    )
                )
            }
        }

        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = sanitizeSheetName("${className}_Results"),
                    titleRow = "THE EDUCATORS - ABBOTTABAD MAIN CAMPUS",
                    subtitleRow = "Class Result Sheet | $className ($sectionLabel) | $examName | $sessionName",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    fun exportClassPerformanceSummary(
        examName: String,
        sessionName: String,
        stats: List<ClassPerformanceStats>
    ): ByteArray {
        val headers = listOf(
            "Class", "Total Students", "Results Completed", "Results Pending",
            "Passed", "Failed", "Pass Rate", "Average Percentage"
        )
        val rows = stats.map { st ->
            val passRate = if (st.completedResults > 0) st.passedCount.toDouble() / st.completedResults.toDouble() else 0.0
            listOf(
                CellValue.Text(st.schoolClass.name),
                CellValue.Number(st.totalStudents.toDouble()),
                CellValue.Number(st.completedResults.toDouble()),
                CellValue.Number(st.pendingResults.toDouble()),
                CellValue.Number(st.passedCount.toDouble()),
                CellValue.Number(st.failedCount.toDouble()),
                CellValue.Percent(passRate),
                CellValue.Percent(st.averagePercentage / 100.0)
            )
        }
        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = "Class_Summary",
                    titleRow = "THE EDUCATORS - ABBOTTABAD MAIN CAMPUS",
                    subtitleRow = "Class-wise Performance Summary | $examName | $sessionName",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    // --- 3 TEMPLATE HELPERS FOR CLEAN IMPORT ---

    fun generateStudentImportTemplate(): ByteArray = writeWorkbook(
        listOf(
            XlsxSheetData(
                name = "Students_Import",
                headers = listOf("Student Name", "Father's Name", "Roll Number", "Admission Number", "Class", "Section", "Academic Session"),
                rows = emptyList()
            )
        )
    )

    fun generateMarksImportTemplate(): ByteArray = writeWorkbook(
        listOf(
            XlsxSheetData(
                name = "Marks_Import",
                headers = listOf("Roll Number", "Class", "Section", "Subject", "Obtained Marks"),
                rows = emptyList()
            )
        )
    )

    fun generateSubjectsImportTemplate(): ByteArray = writeWorkbook(
        listOf(
            XlsxSheetData(
                name = "Subjects_Import",
                headers = listOf("Class", "Subject Name", "Maximum Marks", "Passing Marks", "Compulsory"),
                rows = emptyList()
            )
        )
    )

    // --- 3 VALIDATED IMPORTS ---

    fun parseAndValidateStudents(
        inputStream: InputStream,
        classes: List<SchoolClass>,
        sections: List<ClassSection>,
        sessions: List<AcademicSession>,
        existingStudents: List<Student>,
        defaultSessionId: String
    ): ImportPreviewResult<ImportedStudentDraft> {
        val rawRows = readFirstSheetRows(inputStream)
        if (rawRows.isEmpty()) {
            return ImportPreviewResult(emptyList(), listOf(ImportRowError(1, "Excel file has no rows.")))
        }
        val headerIdx = findHeaderRowIndex(rawRows, listOf("student name", "name"))
        if (headerIdx < 0) {
            return ImportPreviewResult(
                emptyList(),
                listOf(ImportRowError(1, "Missing required header 'Student Name'. Expected columns: Student Name, Father's Name, Roll Number, Admission Number, Class, Section, Academic Session."))
            )
        }

        val headers = rawRows[headerIdx].map { it.trim().lowercase() }
        val nameCol = findCol(headers, "student name", "name")
        val fatherCol = findCol(headers, "father's name", "father name", "father")
        val rollCol = findCol(headers, "roll number", "roll no", "roll")
        val admCol = findCol(headers, "admission number", "admission no", "adm no")
        val classCol = findCol(headers, "class", "grade")
        val sectionCol = findCol(headers, "section", "sec")
        val sessionCol = findCol(headers, "academic session", "session")

        if (nameCol < 0 || fatherCol < 0 || rollCol < 0 || classCol < 0) {
            return ImportPreviewResult(
                emptyList(),
                listOf(ImportRowError(headerIdx + 1, "Required columns missing: Student Name, Father's Name, Roll Number, Class."))
            )
        }

        val valid = mutableListOf<ImportedStudentDraft>()
        val errors = mutableListOf<ImportRowError>()
        val seenKeys = existingStudents.map { "${it.classId}_${it.sectionId}_${it.sessionId}_${it.rollNumber.lowercase()}" }.toMutableSet()

        for (r in (headerIdx + 1) until rawRows.size) {
            val row = rawRows[r]
            if (row.all { it.isBlank() }) continue
            val rowNum = r + 1
            val name = row.getOrNull(nameCol)?.trim().orEmpty()
            val father = row.getOrNull(fatherCol)?.trim().orEmpty()
            val roll = row.getOrNull(rollCol)?.trim().orEmpty()
            val adm = row.getOrNull(admCol)?.trim().orEmpty().ifBlank { "ADM-$roll" }
            val classText = row.getOrNull(classCol)?.trim().orEmpty()
            val sectionText = row.getOrNull(sectionCol)?.trim().orEmpty().ifBlank { "A" }
            val sessionText = row.getOrNull(sessionCol)?.trim().orEmpty()

            if (name.isBlank()) { errors.add(ImportRowError(rowNum, "Student Name is required.")); continue }
            if (father.isBlank()) { errors.add(ImportRowError(rowNum, "Father's Name is required for $name.")); continue }
            if (roll.isBlank()) { errors.add(ImportRowError(rowNum, "Roll Number is required for $name.")); continue }

            val matchedClass = classes.firstOrNull { it.name.equals(classText, ignoreCase = true) }
            if (matchedClass == null) {
                errors.add(ImportRowError(rowNum, "Class '$classText' does not exist. Create it in Class Management first."))
                continue
            }
            val matchedSection = sections.firstOrNull { it.classId == matchedClass.id && it.name.equals(sectionText, ignoreCase = true) }
                ?: sections.firstOrNull { it.classId == matchedClass.id }
            if (matchedSection == null) {
                errors.add(ImportRowError(rowNum, "No section '$sectionText' found for ${matchedClass.name}."))
                continue
            }
            val matchedSession = if (sessionText.isNotBlank()) {
                sessions.firstOrNull { it.name.equals(sessionText, ignoreCase = true) }
            } else {
                sessions.firstOrNull { it.id == defaultSessionId } ?: sessions.firstOrNull()
            }
            if (matchedSession == null) {
                errors.add(ImportRowError(rowNum, "Academic Session '$sessionText' not found."))
                continue
            }

            val dedupeKey = "${matchedClass.id}_${matchedSection.id}_${matchedSession.id}_${roll.lowercase()}"
            if (!seenKeys.add(dedupeKey)) {
                errors.add(ImportRowError(rowNum, "Duplicate Roll Number '$roll' in ${matchedClass.name}-${matchedSection.name}."))
                continue
            }

            valid.add(
                ImportedStudentDraft(
                    name = name,
                    fatherName = father,
                    rollNumber = roll,
                    admissionNumber = adm,
                    classId = matchedClass.id,
                    className = matchedClass.name,
                    sectionId = matchedSection.id,
                    sectionName = matchedSection.name,
                    sessionId = matchedSession.id,
                    sessionName = matchedSession.name
                )
            )
        }
        return ImportPreviewResult(valid, errors)
    }

    fun parseAndValidateMarks(
        inputStream: InputStream,
        classes: List<SchoolClass>,
        sections: List<ClassSection>,
        students: List<Student>,
        subjects: List<Subject>,
        allowDecimalMarks: Boolean
    ): ImportPreviewResult<ImportedMarkDraft> {
        val rawRows = readFirstSheetRows(inputStream)
        if (rawRows.isEmpty()) {
            return ImportPreviewResult(emptyList(), listOf(ImportRowError(1, "Excel file is empty.")))
        }
        val headerIdx = findHeaderRowIndex(rawRows, listOf("roll number", "roll no", "obtained marks"))
        if (headerIdx < 0) {
            return ImportPreviewResult(
                emptyList(),
                listOf(ImportRowError(1, "Expected columns: Roll Number, Class, Section, Subject, Obtained Marks."))
            )
        }
        val headers = rawRows[headerIdx].map { it.trim().lowercase() }
        val rollCol = findCol(headers, "roll number", "roll no", "roll")
        val classCol = findCol(headers, "class", "grade")
        val secCol = findCol(headers, "section", "sec")
        val subCol = findCol(headers, "subject", "subject name")
        val marksCol = findCol(headers, "obtained marks", "marks", "obtained")

        if (rollCol < 0 || classCol < 0 || subCol < 0 || marksCol < 0) {
            return ImportPreviewResult(
                emptyList(),
                listOf(ImportRowError(headerIdx + 1, "Required columns missing: Roll Number, Class, Subject, Obtained Marks."))
            )
        }

        val valid = mutableListOf<ImportedMarkDraft>()
        val errors = mutableListOf<ImportRowError>()

        for (r in (headerIdx + 1) until rawRows.size) {
            val row = rawRows[r]
            if (row.all { it.isBlank() }) continue
            val rowNum = r + 1
            val roll = row.getOrNull(rollCol)?.trim().orEmpty()
            val classText = row.getOrNull(classCol)?.trim().orEmpty()
            val secText = row.getOrNull(secCol)?.trim().orEmpty()
            val subText = row.getOrNull(subCol)?.trim().orEmpty()
            val marksText = row.getOrNull(marksCol)?.trim().orEmpty()

            val matchedClass = classes.firstOrNull { it.name.equals(classText, ignoreCase = true) }
            if (matchedClass == null) {
                errors.add(ImportRowError(rowNum, "Class '$classText' not found."))
                continue
            }
            val matchedSection = if (secText.isNotBlank()) {
                sections.firstOrNull { it.classId == matchedClass.id && it.name.equals(secText, ignoreCase = true) }
            } else null

            val matchedStudent = students.firstOrNull { st ->
                st.classId == matchedClass.id &&
                    (matchedSection == null || st.sectionId == matchedSection.id) &&
                    st.rollNumber.equals(roll, ignoreCase = true)
            }
            if (matchedStudent == null) {
                errors.add(ImportRowError(rowNum, "Student with Roll '$roll' in ${matchedClass.name} not found."))
                continue
            }
            val matchedSubject = subjects.firstOrNull { it.classId == matchedClass.id && it.name.equals(subText, ignoreCase = true) }
            if (matchedSubject == null) {
                errors.add(ImportRowError(rowNum, "Subject '$subText' not configured for ${matchedClass.name}."))
                continue
            }
            val parsedMarks = marksText.toDoubleOrNull()
            if (parsedMarks == null || parsedMarks < 0.0 || parsedMarks > matchedSubject.maxMarks) {
                errors.add(ImportRowError(rowNum, "Marks '$marksText' must be between 0 and ${ResultCalculator.formatMarks(matchedSubject.maxMarks)} for ${matchedSubject.name}."))
                continue
            }
            if (!allowDecimalMarks && parsedMarks % 1.0 != 0.0) {
                errors.add(ImportRowError(rowNum, "Decimal marks ($marksText) are disabled in Settings."))
                continue
            }
            valid.add(
                ImportedMarkDraft(
                    studentId = matchedStudent.id,
                    studentName = matchedStudent.name,
                    rollNumber = matchedStudent.rollNumber,
                    classId = matchedClass.id,
                    sectionId = matchedStudent.sectionId,
                    subjectId = matchedSubject.id,
                    subjectName = matchedSubject.name,
                    obtainedMarks = parsedMarks
                )
            )
        }
        return ImportPreviewResult(valid, errors)
    }

    fun parseAndValidateSubjects(
        inputStream: InputStream,
        classes: List<SchoolClass>
    ): ImportPreviewResult<ImportedSubjectDraft> {
        val rawRows = readFirstSheetRows(inputStream)
        if (rawRows.isEmpty()) {
            return ImportPreviewResult(emptyList(), listOf(ImportRowError(1, "Excel file is empty.")))
        }
        val headerIdx = findHeaderRowIndex(rawRows, listOf("subject name", "subject"))
        if (headerIdx < 0) {
            return ImportPreviewResult(
                emptyList(),
                listOf(ImportRowError(1, "Expected columns: Class, Subject Name, Maximum Marks, Passing Marks, Compulsory."))
            )
        }
        val headers = rawRows[headerIdx].map { it.trim().lowercase() }
        val classCol = findCol(headers, "class", "grade")
        val nameCol = findCol(headers, "subject name", "subject")
        val maxCol = findCol(headers, "maximum marks", "max marks", "total marks")
        val passCol = findCol(headers, "passing marks", "pass marks")
        val compCol = findCol(headers, "compulsory", "required")

        if (classCol < 0 || nameCol < 0 || maxCol < 0) {
            return ImportPreviewResult(
                emptyList(),
                listOf(ImportRowError(headerIdx + 1, "Required columns missing: Class, Subject Name, Maximum Marks."))
            )
        }

        val valid = mutableListOf<ImportedSubjectDraft>()
        val errors = mutableListOf<ImportRowError>()

        for (r in (headerIdx + 1) until rawRows.size) {
            val row = rawRows[r]
            if (row.all { it.isBlank() }) continue
            val rowNum = r + 1
            val classText = row.getOrNull(classCol)?.trim().orEmpty()
            val subName = row.getOrNull(nameCol)?.trim().orEmpty()
            val maxMarks = row.getOrNull(maxCol)?.trim()?.toDoubleOrNull()
            val passMarks = row.getOrNull(passCol)?.trim()?.toDoubleOrNull() ?: ((maxMarks ?: 100.0) * 0.40)
            val compText = row.getOrNull(compCol)?.trim()?.lowercase() ?: "yes"
            val compulsory = compText !in setOf("no", "false", "0", "elective")

            val matchedClass = classes.firstOrNull { it.name.equals(classText, ignoreCase = true) }
            if (matchedClass == null) {
                errors.add(ImportRowError(rowNum, "Class '$classText' not found."))
                continue
            }
            if (subName.isBlank()) {
                errors.add(ImportRowError(rowNum, "Subject Name cannot be blank."))
                continue
            }
            if (maxMarks == null || maxMarks <= 0.0 || maxMarks > 1000.0) {
                errors.add(ImportRowError(rowNum, "Invalid Maximum Marks for $subName."))
                continue
            }
            if (passMarks < 0.0 || passMarks > maxMarks) {
                errors.add(ImportRowError(rowNum, "Passing Marks ($passMarks) must be between 0 and $maxMarks."))
                continue
            }

            valid.add(
                ImportedSubjectDraft(
                    classId = matchedClass.id,
                    className = matchedClass.name,
                    name = subName,
                    maxMarks = maxMarks,
                    passingMarks = passMarks,
                    compulsory = compulsory
                )
            )
        }
        return ImportPreviewResult(valid, errors)
    }

    // --- OOXML XLSX WRITER ---

    fun writeWorkbook(sheets: List<XlsxSheetData>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(sheets.size))
            writeZipEntry(zos, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(sheets))
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
            writeZipEntry(zos, "xl/styles.xml", STYLES_XML)
            sheets.forEachIndexed { idx, sheet ->
                writeZipEntry(zos, "xl/worksheets/sheet${idx + 1}.xml", buildSheetXml(sheet))
            }
        }
        return baos.toByteArray()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildSheetXml(sheet: XlsxSheetData): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<cols>")
        sheet.headers.indices.forEach { idx ->
            val colNum = idx + 1
            val width = if (idx <= 3) 20 else 15
            sb.append("""<col min="$colNum" max="$colNum" width="$width" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        var rowIndex = 1
        if (!sheet.titleRow.isNullOrBlank()) {
            sb.append("""<row r="$rowIndex"><c r="A$rowIndex" t="inlineStr" s="1"><is><t>${xmlEscape(sheet.titleRow)}</t></is></c></row>""")
            rowIndex++
        }
        if (!sheet.subtitleRow.isNullOrBlank()) {
            sb.append("""<row r="$rowIndex"><c r="A$rowIndex" t="inlineStr" s="2"><is><t>${xmlEscape(sheet.subtitleRow)}</t></is></c></row>""")
            rowIndex++
        }

        // Header row (style 3 = blue fill, white bold)
        sb.append("""<row r="$rowIndex">""")
        sheet.headers.forEachIndexed { colIdx, header ->
            val ref = "${colLetter(colIdx)}$rowIndex"
            sb.append("""<c r="$ref" t="inlineStr" s="3"><is><t>${xmlEscape(header)}</t></is></c>""")
        }
        sb.append("</row>")
        rowIndex++

        // Data rows
        sheet.rows.forEach { row ->
            sb.append("""<row r="$rowIndex">""")
            row.forEachIndexed { colIdx, cell ->
                val ref = "${colLetter(colIdx)}$rowIndex"
                when (cell) {
                    is CellValue.Text ->
                        sb.append("""<c r="$ref" t="inlineStr" s="0"><is><t>${xmlEscape(cell.value)}</t></is></c>""")
                    is CellValue.Number ->
                        sb.append("""<c r="$ref" s="0"><v>${cell.value}</v></c>""")
                    is CellValue.Percent ->
                        sb.append("""<c r="$ref" s="4"><v>${cell.ratio}</v></c>""")
                }
            }
            sb.append("</row>")
            rowIndex++
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.append(('A' + (i % 26)))
            i = (i / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun sanitizeSheetName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/?*\\[\\]:]"), "_").trim()
        return cleaned.ifBlank { "Sheet1" }.take(30)
    }

    private fun xmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun buildContentTypesXml(sheetCount: Int): String {
        val overrides = (1..sheetCount).joinToString("") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  $overrides
</Types>"""
    }

    private fun buildWorkbookXml(sheets: List<XlsxSheetData>): String {
        val sheetNodes = sheets.mapIndexed { i, s ->
            """<sheet name="${xmlEscape(sanitizeSheetName(s.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>$sheetNodes</sheets>
</workbook>"""
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String {
        val sheetRels = (1..sheetCount).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  $sheetRels
  <Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    // Style 0: default, 1: Title bold, 2: Subtitle, 3: Header blue fill + white bold, 4: Percentage 0.00%
    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="13"/><color rgb="FF08408A"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF08408A"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>"""

    // --- OOXML XLSX READER ---

    fun readFirstSheetRows(inputStream: InputStream): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }?.value
            ?: return emptyList()
        return parseWorksheet(sheetBytes, sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var inSi = false
        var inT = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; current.clear() }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inSi && inT) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> { inSi = false; result.add(current.toString()) }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var currentRow: MutableList<String>? = null
        var cellType: String? = null
        var colIndex = 0
        var inValue = false
        var inInlineText = false
        val valueBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        val ref = parser.getAttributeValue(null, "r")
                        colIndex = ref?.let { colIndexFromRef(it) } ?: (currentRow?.size ?: 0)
                        valueBuf.clear()
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT -> {
                    if (inValue || inInlineText) valueBuf.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = valueBuf.toString()
                        val text = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                            else -> raw
                        }
                        currentRow?.let { r ->
                            while (r.size <= colIndex) r.add("")
                            r[colIndex] = text
                        }
                    }
                    "row" -> {
                        currentRow?.let { rows.add(it) }
                        currentRow = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun colIndexFromRef(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch.isLetter()) {
                col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else break
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun findHeaderRowIndex(rows: List<List<String>>, keywords: List<String>): Int {
        for (i in 0 until minOf(rows.size, 6)) {
            val joined = rows[i].joinToString("|").lowercase()
            if (keywords.any { joined.contains(it) }) return i
        }
        return -1
    }

    private fun findCol(headers: List<String>, vararg candidates: String): Int {
        candidates.forEach { candidate ->
            val idx = headers.indexOfFirst { it == candidate || it.contains(candidate) }
            if (idx >= 0) return idx
        }
        return -1
    }
}
