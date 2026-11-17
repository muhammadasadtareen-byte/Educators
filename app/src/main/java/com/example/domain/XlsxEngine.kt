package com.example.domain

import android.util.Xml
import com.example.data.model.AcademicSession
import com.example.data.model.ClassPerformanceStats
import com.example.data.model.ClassSection
import com.example.data.model.ExamComponent
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
    data class Percent(val ratio: Double) : CellValue // e.g. 0.325 displays as 32.5%
}

data class XlsxSheetData(
    val name: String,
    val titleRow: String? = null,
    val subtitleRow: String? = null,
    val extraBannerRows: List<String> = emptyList(),
    val groupHeaders: List<String>? = null,
    val groupMergeSpans: List<Pair<Int, Int>> = emptyList(),
    val verticalMergeCols: List<Int> = emptyList(),
    val headers: List<String>,
    val rows: List<List<CellValue>>,
    val footerRows: List<List<CellValue>> = emptyList()
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

    // --- EXPORTS ---

    fun exportCompleteStudentList(
        students: List<Student>,
        classesById: Map<String, SchoolClass>,
        sectionsById: Map<String, ClassSection>,
        sessionsById: Map<String, AcademicSession>,
        schoolName: String = "THE EDUCATORS",
        campusName: String = "ABBOTTABAD MAIN CAMPUS"
    ): ByteArray {
        val headers = listOf(
            "Roll Number", "Admission Number", "Student Name", "Father's Name",
            "Class", "Section", "Academic Session"
        )
        val rows = students.sortedWith(
            compareBy<Student> { classesById[it.classId]?.sortOrder ?: 99L }
                .thenBy { it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.name }
        ).map { s ->
            listOf(
                CellValue.Text(s.rollNumber),
                CellValue.Text(s.admissionNumber),
                CellValue.Text(s.name),
                CellValue.Text(s.fatherName.takeIf { it != "-" }.orEmpty()),
                CellValue.Text(classesById[s.classId]?.name ?: ""),
                CellValue.Text(sectionsById[s.sectionId]?.name ?: ""),
                CellValue.Text(sessionsById[s.sessionId]?.name ?: "")
            )
        }
        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = "Students",
                    titleRow = "$schoolName - $campusName",
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
        summaries: List<StudentResultSummary>,
        schoolName: String = "THE EDUCATORS",
        campusName: String = "ABBOTTABAD MAIN CAMPUS"
    ): ByteArray {
        val headers = listOf(
            "Roll No", "Admission No", "Student Name", "Father's Name",
            "Subject", "Max Marks", "Passing Marks", "Obtained Marks", "Percentage", "Grade", "Status"
        )
        val rows = summaries.sortedWith(
            compareBy<StudentResultSummary> { it.student.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.student.name }
        ).map { summary ->
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
                CellValue.Text(summary.student.fatherName.takeIf { it != "-" }.orEmpty()),
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
                    titleRow = "$schoolName - $campusName",
                    subtitleRow = "$examName ($sessionName) | $className - $sectionName | Subject: ${subject.name}",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    fun exportIndividualStudentResult(
        summary: StudentResultSummary,
        schoolName: String = "THE EDUCATORS",
        campusName: String = "ABBOTTABAD MAIN CAMPUS",
        address: String = "",
        contact: String = ""
    ): ByteArray {
        val components = summary.examComponents
        val headers = buildList {
            add("Subjects")
            components.forEach { comp ->
                add("${comp.name} (${ResultCalculator.formatMarks(comp.maxMarks)})")
            }
            add("Total Marks Obtained")
            add("Highest Marks in Class")
            add("Percentage")
            add("Grade")
        }

        val rows = summary.subjectResults.map { sr ->
            buildList {
                add(CellValue.Text(sr.subject.name + if (sr.passed == false && sr.subject.compulsory) " *" else ""))
                components.forEach { comp ->
                    val compVal = sr.markEntry?.componentMarks?.get(comp.id)
                    if (compVal != null) {
                        add(CellValue.Number(compVal))
                    } else {
                        add(CellValue.Text("-"))
                    }
                }
                if (sr.obtainedMarks != null) {
                    add(CellValue.Number(sr.obtainedMarks))
                } else {
                    add(CellValue.Text("-"))
                }
                if (sr.highestMarksInClass != null) {
                    add(CellValue.Number(sr.highestMarksInClass))
                } else {
                    add(CellValue.Text("-"))
                }
                if (sr.percentage != null) {
                    add(CellValue.Percent(sr.percentage / 100.0))
                } else {
                    add(CellValue.Text("-"))
                }
                add(CellValue.Text(sr.grade))
            }
        } + listOf(
            buildList {
                add(CellValue.Text("GRAND TOTAL (${ResultCalculator.formatMarks(summary.totalMaxMarks)})"))
                repeat(components.size) { add(CellValue.Text("")) }
                add(CellValue.Number(summary.totalObtainedMarks))
                add(CellValue.Text(""))
                add(CellValue.Percent(summary.overallPercentage / 100.0))
                add(CellValue.Text(summary.overallGrade))
            }
        )

        val particularsLine1 = buildList {
            add("Student Name: ${summary.student.name}")
            summary.student.fatherName.takeIf { it.isNotBlank() && it != "-" }?.let { add("Father's Name: $it") }
            add("Class - Section: ${summary.className} - ${summary.sectionName}")
            add("Session: ${summary.sessionName}")
        }.joinToString("   |   ")

        val particularsLine2 = buildList {
            summary.student.rollNumber.takeIf { it.isNotBlank() }?.let { add("Roll Number: $it") }
            summary.student.admissionNumber.takeIf { it.isNotBlank() }?.let { add("Admission No: $it") }
            if (address.isNotBlank()) add("Address: $address")
            if (contact.isNotBlank()) add("Contact: $contact")
        }.joinToString("   |   ")

        val extraBanners = buildList {
            add(particularsLine1)
            if (particularsLine2.isNotBlank()) add(particularsLine2)
        }

        val rules = summary.gradingRulesUsed.ifEmpty { ResultCalculator.defaultGradingTemplates() }
            .sortedByDescending { it.minPercentage }

        val footerRows = buildList {
            add(listOf(CellValue.Text("")))
            add(listOf(CellValue.Text("Teacher's Remarks"), CellValue.Text(summary.teacherRemarks)))
            add(listOf(CellValue.Text("")))
            add(listOf(CellValue.Text("Class Teacher's Signature"), CellValue.Text(""), CellValue.Text("Dated"), CellValue.Text("")))
            add(listOf(CellValue.Text("Principal's Signature"), CellValue.Text(""), CellValue.Text("Dated"), CellValue.Text("")))
            add(listOf(CellValue.Text("Parent/Guardian's Signature"), CellValue.Text(""), CellValue.Text("Dated"), CellValue.Text("")))
            add(listOf(CellValue.Text("")))
            add(listOf(CellValue.Text("GRADING KEY")))
            add(rules.map { CellValue.Text("Grade ${it.grade}") })
            add(rules.map { CellValue.Text(formatRuleBand(it.minPercentage, it.maxPercentage)) })
        }

        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = sanitizeSheetName("${summary.student.rollNumber.ifBlank { "Student" }}_${summary.student.name}"),
                    titleRow = "PROGRESS REPORT — ${schoolName.uppercase()}",
                    subtitleRow = "${summary.examName.uppercase()} — ${campusName.uppercase()}",
                    extraBannerRows = extraBanners,
                    headers = headers,
                    rows = rows,
                    footerRows = footerRows
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
        summaries: List<StudentResultSummary>,
        schoolName: String = "THE EDUCATORS",
        campusName: String = "ABBOTTABAD MAIN CAMPUS",
        address: String = "",
        contact: String = "",
        examComponents: List<ExamComponent> = emptyList()
    ): ByteArray {
        val sortedSubjects = subjects.sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name })
        val totalMax = sortedSubjects.sumOf { it.maxMarks }

        val groupHeaders: List<String>?
        val headers: List<String>
        val groupMergeSpans: List<Pair<Int, Int>>
        val verticalMergeCols: List<Int>

        if (examComponents.isNotEmpty() && sortedSubjects.isNotEmpty()) {
            val topList = mutableListOf("Name")
            val subList = mutableListOf("")
            val spans = mutableListOf<Pair<Int, Int>>()
            var colCursor = 1

            sortedSubjects.forEach { sub ->
                val startCol = colCursor
                examComponents.forEachIndexed { idx, comp ->
                    topList.add(if (idx == 0) sub.name else "")
                    subList.add("${comp.name} (${ResultCalculator.formatMarks(comp.maxMarks)})")
                    colCursor++
                }
                if (examComponents.size > 1) {
                    spans.add(startCol to (colCursor - 1))
                }
            }
            val obtCol = colCursor
            topList.add("Obt. (${ResultCalculator.formatMarks(totalMax)})")
            subList.add("")
            val pctCol = colCursor + 1
            topList.add("% age")
            subList.add("")
            val gradeCol = colCursor + 2
            topList.add("Grade")
            subList.add("")

            groupHeaders = topList
            headers = subList
            groupMergeSpans = spans
            verticalMergeCols = listOf(0, obtCol, pctCol, gradeCol)
        } else {
            groupHeaders = null
            groupMergeSpans = emptyList()
            verticalMergeCols = emptyList()
            headers = buildList {
                add("Name")
                sortedSubjects.forEach { add("${it.name} (${ResultCalculator.formatMarks(it.maxMarks)})") }
                add("Obt. (${ResultCalculator.formatMarks(totalMax)})")
                add("% age")
                add("Grade")
            }
        }

        val rows = summaries.map { summary ->
            buildList {
                add(CellValue.Text(summary.student.name))
                if (examComponents.isNotEmpty()) {
                    sortedSubjects.forEach { sub ->
                        val sr = summary.subjectResults.firstOrNull { it.subject.id == sub.id }
                        examComponents.forEach { comp ->
                            val compMark = sr?.markEntry?.componentMarks?.get(comp.id)
                            if (compMark != null) {
                                add(CellValue.Number(compMark))
                            } else {
                                add(CellValue.Text("-"))
                            }
                        }
                    }
                } else {
                    sortedSubjects.forEach { sub ->
                        val sr = summary.subjectResults.firstOrNull { it.subject.id == sub.id }
                        if (sr?.obtainedMarks != null) {
                            add(CellValue.Number(sr.obtainedMarks))
                        } else {
                            add(CellValue.Text("-"))
                        }
                    }
                }
                add(CellValue.Number(summary.totalObtainedMarks))
                add(CellValue.Percent(summary.overallPercentage / 100.0))
                add(CellValue.Text(summary.overallGrade))
            }
        }

        val extraBanners = buildList {
            if (address.isNotBlank()) add("Address: $address")
            if (contact.isNotBlank()) add("Contact: $contact")
            add("Consolidated Sheet")
            add("$className - $sectionLabel *** $examName ($sessionName)")
        }

        return writeWorkbook(
            listOf(
                XlsxSheetData(
                    name = sanitizeSheetName("${className}_Consolidated"),
                    titleRow = schoolName.uppercase(),
                    subtitleRow = campusName.uppercase(),
                    extraBannerRows = extraBanners,
                    groupHeaders = groupHeaders,
                    groupMergeSpans = groupMergeSpans,
                    verticalMergeCols = verticalMergeCols,
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    fun exportClassPerformanceSummary(
        examName: String,
        sessionName: String,
        stats: List<ClassPerformanceStats>,
        schoolName: String = "THE EDUCATORS",
        campusName: String = "ABBOTTABAD MAIN CAMPUS"
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
                    titleRow = "$schoolName - $campusName",
                    subtitleRow = "Class-wise Performance Summary | $examName | $sessionName",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    private fun formatRuleBand(minPct: Double, maxPct: Double): String {
        val minInt = minPct.toInt()
        val upperInt = if (maxPct >= 99.9) 100 else kotlin.math.ceil(maxPct).toInt().let {
            if (maxPct % 1.0 > 0.5) (maxPct.toInt() + 1) else maxPct.toInt()
        }
        return "$minInt% - $upperInt%"
    }

    // --- 3 TEMPLATE HELPERS FOR CLEAN IMPORT ---

    fun generateStudentImportTemplate(): ByteArray = writeWorkbook(
        listOf(
            XlsxSheetData(
                name = "Students_Import",
                headers = listOf("Student Name", "Father's Name (Optional)", "Roll Number (Optional)", "Admission Number (Optional)", "Class", "Section", "Academic Session"),
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
                listOf(ImportRowError(1, "Missing required header 'Student Name'. Expected columns: Student Name, Class (Father's Name, Roll Number, Admission Number, Section, Session are optional)."))
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

        if (nameCol < 0 || classCol < 0) {
            return ImportPreviewResult(
                emptyList(),
                listOf(ImportRowError(headerIdx + 1, "Required columns missing: Student Name, Class."))
            )
        }

        val valid = mutableListOf<ImportedStudentDraft>()
        val errors = mutableListOf<ImportRowError>()
        val seenKeys = existingStudents.map { st ->
            if (st.rollNumber.isNotBlank()) {
                "${st.classId}_${st.sectionId}_${st.sessionId}_roll_${st.rollNumber.lowercase()}"
            } else {
                "${st.classId}_${st.sectionId}_${st.sessionId}_name_${st.name.lowercase()}_${st.fatherName.lowercase()}"
            }
        }.toMutableSet()

        for (r in (headerIdx + 1) until rawRows.size) {
            val row = rawRows[r]
            if (row.all { it.isBlank() }) continue
            val rowNum = r + 1
            val name = row.getOrNull(nameCol)?.trim().orEmpty()
            val father = if (fatherCol >= 0) row.getOrNull(fatherCol)?.trim().takeIf { it != "-" }.orEmpty() else ""
            val roll = if (rollCol >= 0) row.getOrNull(rollCol)?.trim().orEmpty() else ""
            val adm = if (admCol >= 0) row.getOrNull(admCol)?.trim().orEmpty() else ""
            val classText = row.getOrNull(classCol)?.trim().orEmpty()
            val sectionText = if (sectionCol >= 0) row.getOrNull(sectionCol)?.trim().orEmpty().ifBlank { "A" } else "A"
            val sessionText = if (sessionCol >= 0) row.getOrNull(sessionCol)?.trim().orEmpty() else ""

            if (name.isBlank()) { errors.add(ImportRowError(rowNum, "Student Name is required.")); continue }

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

            val dedupeKey = if (roll.isNotBlank()) {
                "${matchedClass.id}_${matchedSection.id}_${matchedSession.id}_roll_${roll.lowercase()}"
            } else {
                "${matchedClass.id}_${matchedSection.id}_${matchedSession.id}_name_${name.lowercase()}_${father.lowercase()}"
            }
            if (!seenKeys.add(dedupeKey)) {
                val desc = if (roll.isNotBlank()) "Roll Number '$roll'" else "Student '$name'"
                errors.add(ImportRowError(rowNum, "Duplicate $desc in ${matchedClass.name}-${matchedSection.name}."))
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
        val totalCols = maxOf(
            sheet.headers.size,
            sheet.groupHeaders?.size ?: 0,
            sheet.rows.maxOfOrNull { it.size } ?: 0
        ).coerceAtLeast(1)

        sb.append("<cols>")
        (0 until totalCols).forEach { idx ->
            val colNum = idx + 1
            val width = if (idx == 0) 24 else 14
            sb.append("""<col min="$colNum" max="$colNum" width="$width" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        val mergeRefs = mutableListOf<String>()
        var rowIndex = 1
        if (!sheet.titleRow.isNullOrBlank()) {
            sb.append("""<row r="$rowIndex"><c r="A$rowIndex" t="inlineStr" s="1"><is><t>${xmlEscape(sheet.titleRow)}</t></is></c></row>""")
            rowIndex++
        }
        if (!sheet.subtitleRow.isNullOrBlank()) {
            sb.append("""<row r="$rowIndex"><c r="A$rowIndex" t="inlineStr" s="2"><is><t>${xmlEscape(sheet.subtitleRow)}</t></is></c></row>""")
            rowIndex++
        }
        sheet.extraBannerRows.forEach { banner ->
            if (banner.isNotBlank()) {
                sb.append("""<row r="$rowIndex"><c r="A$rowIndex" t="inlineStr" s="2"><is><t>${xmlEscape(banner)}</t></is></c></row>""")
                rowIndex++
            }
        }

        // Optional Top Grouped Header Row (style 3 = blue fill, white bold)
        val groupHeaders = sheet.groupHeaders
        if (groupHeaders != null) {
            val groupRowIdx = rowIndex
            sb.append("""<row r="$groupRowIdx">""")
            groupHeaders.forEachIndexed { colIdx, header ->
                val ref = "${colLetter(colIdx)}$groupRowIdx"
                sb.append("""<c r="$ref" t="inlineStr" s="3"><is><t>${xmlEscape(header)}</t></is></c>""")
            }
            sb.append("</row>")
            rowIndex++

            sheet.groupMergeSpans.forEach { (startCol, endCol) ->
                if (endCol > startCol) {
                    mergeRefs.add("${colLetter(startCol)}$groupRowIdx:${colLetter(endCol)}$groupRowIdx")
                }
            }
            sheet.verticalMergeCols.forEach { col ->
                mergeRefs.add("${colLetter(col)}$groupRowIdx:${colLetter(col)}${groupRowIdx + 1}")
            }
        }

        // Header row (style 3 = blue fill, white bold)
        sb.append("""<row r="$rowIndex">""")
        sheet.headers.forEachIndexed { colIdx, header ->
            val ref = "${colLetter(colIdx)}$rowIndex"
            sb.append("""<c r="$ref" t="inlineStr" s="3"><is><t>${xmlEscape(header)}</t></is></c>""")
        }
        sb.append("</row>")
        rowIndex++

        fun appendDataRow(row: List<CellValue>) {
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

        sheet.rows.forEach { appendDataRow(it) }
        sheet.footerRows.forEach { appendDataRow(it) }

        sb.append("</sheetData>")
        if (mergeRefs.isNotEmpty()) {
            sb.append("""<mergeCells count="${mergeRefs.size}">""")
            mergeRefs.forEach { ref ->
                sb.append("""<mergeCell ref="$ref"/>""")
            }
            sb.append("</mergeCells>")
        }
        sb.append("</worksheet>")
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
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.relationships+xml"/>
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

    // Style 0: default, 1: Title bold, 2: Subtitle, 3: Header blue fill + white bold, 4: Percentage 0.0%
    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="0.0%"/>
  </numFmts>
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
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
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
        for (i in 0 until minOf(rows.size, 10)) {
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
