package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Examination
import com.example.data.model.ImportPreviewResult
import com.example.data.model.StaffRole
import com.example.domain.ImportedMarkDraft
import com.example.domain.ImportedStudentDraft
import com.example.domain.ImportedSubjectDraft
import com.example.domain.PdfReportGenerator
import com.example.domain.XlsxEngine
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import java.io.File

private sealed interface PendingImportState {
    data class Students(val preview: ImportPreviewResult<ImportedStudentDraft>) : PendingImportState
    data class Marks(val preview: ImportPreviewResult<ImportedMarkDraft>) : PendingImportState
    data class Subjects(val preview: ImportPreviewResult<ImportedSubjectDraft>) : PendingImportState
}

@Composable
fun ExcelCenterScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val isAdmin = snapshot.effectiveRole == StaffRole.ADMIN

    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val classSections = remember(selectedClassId, snapshot.sections) {
        snapshot.sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(selectedClassId, classSections) {
        mutableStateOf(classSections.firstOrNull()?.id.orEmpty())
    }
    val classSubjects = remember(selectedClassId, snapshot.subjects) {
        snapshot.subjects.filter { it.classId == selectedClassId }
    }
    var selectedSubjectId by remember(selectedClassId, classSubjects) {
        mutableStateOf(classSubjects.firstOrNull()?.id.orEmpty())
    }
    val classSummaries = remember(snapshot.allStudentSummaries, selectedClassId) {
        snapshot.allStudentSummaries.filter { it.student.classId == selectedClassId }
    }
    var selectedStudentId by remember(classSummaries) {
        mutableStateOf(classSummaries.firstOrNull()?.student?.id.orEmpty())
    }

    var pendingXlsxFile by remember { mutableStateOf<File?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImportState?>(null) }
    var activeImportKind by remember { mutableStateOf("STUDENTS") }
    var editingExam by remember { mutableStateOf<Examination?>(null) }
    var showExtractDialog by remember { mutableStateOf(false) }

    val saveXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val src = pendingXlsxFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            viewModel.postMessage("Excel workbook exported successfully.")
        }
        pendingXlsxFile = null
    }

    val openXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    pendingImport = when (activeImportKind) {
                        "STUDENTS" -> PendingImportState.Students(
                            XlsxEngine.parseAndValidateStudents(
                                inputStream = stream,
                                classes = snapshot.classes,
                                sections = snapshot.sections,
                                sessions = snapshot.sessions,
                                existingStudents = snapshot.students,
                                defaultSessionId = snapshot.selectedSessionId
                            )
                        )
                        "MARKS" -> PendingImportState.Marks(
                            XlsxEngine.parseAndValidateMarks(
                                inputStream = stream,
                                classes = snapshot.classes,
                                sections = snapshot.sections,
                                students = snapshot.visibleStudents,
                                subjects = snapshot.subjects,
                                allowDecimalMarks = snapshot.campus.allowDecimalMarks
                            )
                        )
                        else -> PendingImportState.Subjects(
                            XlsxEngine.parseAndValidateSubjects(stream, snapshot.classes)
                        )
                    }
                }
            } catch (e: Exception) {
                viewModel.postMessage("Failed to read .xlsx file: ${e.message}")
            }
        }
    }

    fun exportAndSave(fileName: String, bytes: ByteArray) {
        val file = FileExportHelper.writeBytesToCache(context, fileName, bytes)
        pendingXlsxFile = file
        saveXlsxLauncher.launch(fileName)
    }

    fun exportAndShare(fileName: String, bytes: ByteArray) {
        val file = FileExportHelper.writeBytesToCache(context, fileName, bytes)
        FileExportHelper.shareFile(
            context,
            file,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "Share Excel Report"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TabLegendCard(
                tabTitle = "Excel Export & Import",
                summary = "Export formatted .xlsx workbooks, import spreadsheets, or extract student names from Image/PDF/Word.",
                items = listOf(
                    LegendBadge("Save / Share", EmeraldPass, "Download or send .xlsx sheets"),
                    LegendBadge("Import / Extract", EducatorsBlue, "Validate Excel or Photo/PDF/Word"),
                    LegendBadge("Edit Exam", EducatorsNavy, "Tap pencil next to Examination")
                )
            )
        }

        item {
            CampusContextBar(
                snapshot = snapshot,
                onSelectSession = { viewModel.selectSession(it) },
                onSelectExam = { viewModel.selectExam(it) },
                onEditSelectedExam = { editingExam = it }
            )
        }

        // Scope selectors
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Report Filter Scope", style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectorDropdown(
                            label = "Class",
                            selectedText = snapshot.classesById[selectedClassId]?.name ?: "Select Class",
                            items = snapshot.visibleClasses,
                            itemLabel = { it.name },
                            onSelect = { selectedClassId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                        SelectorDropdown(
                            label = "Section",
                            selectedText = classSections.firstOrNull { it.id == selectedSectionId }?.name ?: "Section",
                            items = classSections,
                            itemLabel = { it.name },
                            onSelect = { selectedSectionId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectorDropdown(
                            label = "Subject",
                            selectedText = classSubjects.firstOrNull { it.id == selectedSubjectId }?.name ?: "Subject",
                            items = classSubjects,
                            itemLabel = { it.name },
                            onSelect = { selectedSubjectId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                        val stu = classSummaries.firstOrNull { it.student.id == selectedStudentId } ?: classSummaries.firstOrNull()
                        SelectorDropdown(
                            label = "Student",
                            selectedText = stu?.student?.name ?: "Student",
                            items = classSummaries,
                            itemLabel = { "${it.student.rollNumber} - ${it.student.name}" },
                            onSelect = { selectedStudentId = it.student.id },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Text("Excel (.xlsx) Exports", style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
        }

        // 1. Complete Student List
        item {
            ExportActionCard(
                title = "1. Complete Student List",
                description = "All enrolled students with roll numbers, admission numbers, parents, classes & sections.",
                enabled = snapshot.visibleStudents.isNotEmpty(),
                onSave = {
                    val bytes = XlsxEngine.exportCompleteStudentList(
                        snapshot.visibleStudents, snapshot.classesById, snapshot.sectionsById, snapshot.sessionsById
                    )
                    exportAndSave("Educators_Students_List.xlsx", bytes)
                },
                onShare = {
                    val bytes = XlsxEngine.exportCompleteStudentList(
                        snapshot.visibleStudents, snapshot.classesById, snapshot.sectionsById, snapshot.sessionsById
                    )
                    exportAndShare("Educators_Students_List.xlsx", bytes)
                }
            )
        }

        // 2. Subject-wise Marks
        item {
            val sub = classSubjects.firstOrNull { it.id == selectedSubjectId }
            val secSummaries = classSummaries.filter { selectedSectionId.isBlank() || it.student.sectionId == selectedSectionId }
            ExportActionCard(
                title = "2. Subject-wise Marks Sheet",
                description = "Marks, percentage, grade & pass/fail for ${sub?.name ?: "selected subject"}.",
                enabled = sub != null && secSummaries.isNotEmpty(),
                onSave = {
                    sub?.let {
                        val bytes = XlsxEngine.exportSubjectWiseMarks(
                            className = snapshot.classesById[selectedClassId]?.name ?: "Class",
                            sectionName = snapshot.sectionsById[selectedSectionId]?.name ?: "All",
                            examName = snapshot.selectedExam?.name ?: "Exam",
                            sessionName = snapshot.selectedSession?.name ?: "Session",
                            subject = it,
                            summaries = secSummaries
                        )
                        exportAndSave("SubjectMarks_${it.name}.xlsx", bytes)
                    }
                },
                onShare = {
                    sub?.let {
                        val bytes = XlsxEngine.exportSubjectWiseMarks(
                            className = snapshot.classesById[selectedClassId]?.name ?: "Class",
                            sectionName = snapshot.sectionsById[selectedSectionId]?.name ?: "All",
                            examName = snapshot.selectedExam?.name ?: "Exam",
                            sessionName = snapshot.selectedSession?.name ?: "Session",
                            subject = it,
                            summaries = secSummaries
                        )
                        exportAndShare("SubjectMarks_${it.name}.xlsx", bytes)
                    }
                }
            )
        }

        // 3. Individual Student Results
        item {
            val stuSum = classSummaries.firstOrNull { it.student.id == selectedStudentId } ?: classSummaries.firstOrNull()
            ExportActionCard(
                title = "3. Individual Student Result",
                description = "Detailed subject breakdown & overall result for ${stuSum?.student?.name ?: "selected student"}.",
                enabled = stuSum != null,
                onSave = {
                    stuSum?.let {
                        exportAndSave("Result_${it.student.rollNumber}.xlsx", XlsxEngine.exportIndividualStudentResult(it))
                    }
                },
                onShare = {
                    stuSum?.let {
                        exportAndShare("Result_${it.student.rollNumber}.xlsx", XlsxEngine.exportIndividualStudentResult(it))
                    }
                }
            )
        }

        // 4. Complete Class Result Sheet
        item {
            val className = snapshot.classesById[selectedClassId]?.name ?: "Class"
            ExportActionCard(
                title = "4. Complete Class Result Sheet",
                description = "Full class tabulation with all subject marks, total, percentage, grade & position.",
                enabled = classSummaries.isNotEmpty(),
                onSave = {
                    val bytes = XlsxEngine.exportCompleteClassResultSheet(
                        className = className,
                        sectionLabel = "All Sections",
                        examName = snapshot.selectedExam?.name ?: "Exam",
                        sessionName = snapshot.selectedSession?.name ?: "Session",
                        subjects = classSubjects,
                        summaries = classSummaries
                    )
                    exportAndSave("ClassResult_${className}.xlsx", bytes)
                },
                onShare = {
                    val bytes = XlsxEngine.exportCompleteClassResultSheet(
                        className = className,
                        sectionLabel = "All Sections",
                        examName = snapshot.selectedExam?.name ?: "Exam",
                        sessionName = snapshot.selectedSession?.name ?: "Session",
                        subjects = classSubjects,
                        summaries = classSummaries
                    )
                    exportAndShare("ClassResult_${className}.xlsx", bytes)
                }
            )
        }

        // 5. Class-wise Performance Summary
        item {
            ExportActionCard(
                title = "5. Class-wise Performance Summary",
                description = "Campus overview of pass rates, averages, completed & pending counts per class.",
                enabled = snapshot.classStats.isNotEmpty(),
                onSave = {
                    val bytes = XlsxEngine.exportClassPerformanceSummary(
                        examName = snapshot.selectedExam?.name ?: "Exam",
                        sessionName = snapshot.selectedSession?.name ?: "Session",
                        stats = snapshot.classStats
                    )
                    exportAndSave("Class_Performance_Summary.xlsx", bytes)
                },
                onShare = {
                    val bytes = XlsxEngine.exportClassPerformanceSummary(
                        examName = snapshot.selectedExam?.name ?: "Exam",
                        sessionName = snapshot.selectedSession?.name ?: "Session",
                        stats = snapshot.classStats
                    )
                    exportAndShare("Class_Performance_Summary.xlsx", bytes)
                }
            )
        }

        // Imports Section
        item {
            Spacer(Modifier.height(6.dp))
            Text("Excel (.xlsx) Validated Imports", style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
        }

        if (isAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Extract Student Names (Image / PDF / Word)", style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
                        Text(
                            "Scan or read student rosters directly from photos, PDF lists, or Word (.docx/.doc) files and review before saving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showExtractDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("excel_tab_extract_students_btn")
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Extract from Image, PDF, or Word")
                        }
                    }
                }
            }
            item {
                ImportActionCard(
                    title = "Import Student Records (.xlsx)",
                    columnsHint = "Columns: Student Name, Father's Name, Roll Number, Admission Number, Class, Section, Academic Session",
                    onImport = {
                        activeImportKind = "STUDENTS"
                        openXlsxLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    },
                    onTemplate = {
                        exportAndSave("Template_Students.xlsx", XlsxEngine.generateStudentImportTemplate())
                    }
                )
            }
            item {
                ImportActionCard(
                    title = "Import Subject Lists (.xlsx)",
                    columnsHint = "Columns: Class, Subject Name, Maximum Marks, Passing Marks, Compulsory (Yes/No)",
                    onImport = {
                        activeImportKind = "SUBJECTS"
                        openXlsxLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    },
                    onTemplate = {
                        exportAndSave("Template_Subjects.xlsx", XlsxEngine.generateSubjectsImportTemplate())
                    }
                )
            }
        }

        item {
            ImportActionCard(
                title = "Import Student Marks (.xlsx)",
                columnsHint = "Columns: Roll Number, Class, Section, Subject, Obtained Marks",
                onImport = {
                    activeImportKind = "MARKS"
                    openXlsxLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                },
                onTemplate = {
                    exportAndSave("Template_Marks.xlsx", XlsxEngine.generateMarksImportTemplate())
                }
            )
        }
    }

    // Validation Preview Dialog before saving imported Excel data
    pendingImport?.let { state ->
        val (validCount, errors) = when (state) {
            is PendingImportState.Students -> state.preview.validRows.size to state.preview.errors
            is PendingImportState.Marks -> state.preview.validRows.size to state.preview.errors
            is PendingImportState.Subjects -> state.preview.validRows.size to state.preview.errors
        }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Excel Import Validation") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Valid rows ready to save: $validCount",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldPass
                    )
                    if (errors.isNotEmpty()) {
                        Text(
                            text = "Validation Errors (${errors.size}):",
                            style = MaterialTheme.typography.titleSmall,
                            color = CrimsonFail
                        )
                        errors.take(25).forEach { err ->
                            Text(
                                text = "Row ${err.rowNumber}: ${err.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CrimsonFail
                            )
                        }
                    } else {
                        Text("All rows passed validation with zero errors.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (state) {
                            is PendingImportState.Students -> viewModel.importStudents(snapshot.campus, state.preview.validRows)
                            is PendingImportState.Marks -> viewModel.importMarks(
                                snapshot.campus, snapshot.selectedSessionId, snapshot.selectedExamId,
                                state.preview.validRows, snapshot.activeExamMarks
                            )
                            is PendingImportState.Subjects -> viewModel.importSubjects(snapshot.campus, state.preview.validRows)
                        }
                        pendingImport = null
                    },
                    enabled = validCount > 0
                ) { Text("Save $validCount Valid Rows") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            }
        )
    }

    if (showExtractDialog) {
        StudentExtractImportDialog(
            snapshot = snapshot,
            viewModel = viewModel,
            onDismiss = { showExtractDialog = false }
        )
    }

    editingExam?.let { target ->
        ExamEditorDialog(
            existing = target,
            sessions = snapshot.sessions,
            defaultSessionId = snapshot.selectedSessionId,
            defaultSortOrder = target.sortOrder,
            canDelete = snapshot.examinations.size > 1,
            onDismiss = { editingExam = null },
            onSave = { name, examType, sessionId, useCustomGrading, sortOrder ->
                viewModel.updateExamination(
                    campusId = snapshot.campus.id,
                    examId = target.id,
                    sessionId = sessionId,
                    name = name,
                    examType = examType,
                    useCustomGrading = useCustomGrading,
                    sortOrder = sortOrder
                )
                editingExam = null
            },
            onDelete = {
                viewModel.deleteExamination(snapshot.campus.id, target.id)
                editingExam = null
            }
        )
    }
}

@Composable
fun PdfCenterScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val classSections = remember(selectedClassId, snapshot.sections) {
        snapshot.sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(selectedClassId, classSections) {
        mutableStateOf(classSections.firstOrNull()?.id.orEmpty())
    }
    val classSubjects = remember(selectedClassId, snapshot.subjects) {
        snapshot.subjects.filter { it.classId == selectedClassId }
    }
    var selectedSubjectId by remember(selectedClassId, classSubjects) {
        mutableStateOf(classSubjects.firstOrNull()?.id.orEmpty())
    }
    val classSummaries = remember(snapshot.allStudentSummaries, selectedClassId) {
        snapshot.allStudentSummaries.filter { it.student.classId == selectedClassId }
    }
    var selectedStudentId by remember(classSummaries) {
        mutableStateOf(classSummaries.firstOrNull()?.student?.id.orEmpty())
    }

    var pendingPdfFile by remember { mutableStateOf<File?>(null) }
    var editingExam by remember { mutableStateOf<Examination?>(null) }
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingPdfFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            viewModel.postMessage("PDF report saved.")
        }
        pendingPdfFile = null
    }

    fun triggerSave(file: File) {
        pendingPdfFile = file
        savePdfLauncher.launch(file.name)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TabLegendCard(
                tabTitle = "PDF Export",
                summary = "Generate official print-ready A4 PDFs for result cards, class registers & summaries.",
                items = listOf(
                    LegendBadge("Save", EducatorsNavy, "Download A4 PDF to device"),
                    LegendBadge("Print / Share", CrimsonFail, "Print directly or share PDF"),
                    LegendBadge("Edit Exam", EducatorsBlue, "Tap pencil next to Examination")
                )
            )
        }

        item {
            CampusContextBar(
                snapshot = snapshot,
                onSelectSession = { viewModel.selectSession(it) },
                onSelectExam = { viewModel.selectExam(it) },
                onEditSelectedExam = { editingExam = it }
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PDF Report Scope (A4 Format)", style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectorDropdown(
                            label = "Class",
                            selectedText = snapshot.classesById[selectedClassId]?.name ?: "Class",
                            items = snapshot.visibleClasses,
                            itemLabel = { it.name },
                            onSelect = { selectedClassId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                        SelectorDropdown(
                            label = "Section",
                            selectedText = classSections.firstOrNull { it.id == selectedSectionId }?.name ?: "Section",
                            items = classSections,
                            itemLabel = { it.name },
                            onSelect = { selectedSectionId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectorDropdown(
                            label = "Subject",
                            selectedText = classSubjects.firstOrNull { it.id == selectedSubjectId }?.name ?: "Subject",
                            items = classSubjects,
                            itemLabel = { it.name },
                            onSelect = { selectedSubjectId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                        val stu = classSummaries.firstOrNull { it.student.id == selectedStudentId } ?: classSummaries.firstOrNull()
                        SelectorDropdown(
                            label = "Student",
                            selectedText = stu?.student?.name ?: "Student",
                            items = classSummaries,
                            itemLabel = { "${it.student.rollNumber} - ${it.student.name}" },
                            onSelect = { selectedStudentId = it.student.id },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 1. Individual Result Card PDF
        item {
            val stuSum = classSummaries.firstOrNull { it.student.id == selectedStudentId } ?: classSummaries.firstOrNull()
            PdfActionCard(
                title = "1. Individual Student Result Card (A4)",
                description = "Official A4 result card with school logo, marks table, positions & signature areas.",
                enabled = stuSum != null,
                onSave = {
                    stuSum?.let {
                        triggerSave(
                            PdfReportGenerator.generateResultCardsPdf(
                                context, listOf(it), snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64
                            )
                        )
                    }
                },
                onPrint = {
                    stuSum?.let {
                        val f = PdfReportGenerator.generateResultCardsPdf(
                            context, listOf(it), snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64
                        )
                        PdfReportGenerator.printPdfFile(context, f, "ResultCard_${it.student.name}")
                    }
                },
                onShare = {
                    stuSum?.let {
                        val f = PdfReportGenerator.generateResultCardsPdf(
                            context, listOf(it), snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64
                        )
                        FileExportHelper.shareFile(context, f, "application/pdf", "Share Result Card PDF")
                    }
                }
            )
        }

        // 2. Batch All Student Result Cards PDF
        item {
            PdfActionCard(
                title = "2. All Student Result Cards (Batch A4 PDF)",
                description = "Generates all ${classSummaries.size} student result cards in one multi-page A4 document.",
                enabled = classSummaries.isNotEmpty(),
                onSave = {
                    triggerSave(
                        PdfReportGenerator.generateResultCardsPdf(
                            context, classSummaries, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64
                        )
                    )
                },
                onPrint = {
                    val f = PdfReportGenerator.generateResultCardsPdf(
                        context, classSummaries, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64
                    )
                    PdfReportGenerator.printPdfFile(context, f, "All_Result_Cards")
                },
                onShare = {
                    val f = PdfReportGenerator.generateResultCardsPdf(
                        context, classSummaries, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64
                    )
                    FileExportHelper.shareFile(context, f, "application/pdf", "Share All Result Cards PDF")
                }
            )
        }

        // 3. Complete Class Result Sheet PDF
        item {
            val className = snapshot.classesById[selectedClassId]?.name ?: "Class"
            PdfActionCard(
                title = "3. Complete Class Result Sheet (A4)",
                description = "Full multi-page class result register with subject marks, totals, percentages & ranks.",
                enabled = classSummaries.isNotEmpty(),
                onSave = {
                    triggerSave(
                        PdfReportGenerator.generateClassResultSheetPdf(
                            context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                            className, "All Sections", snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                            classSubjects, classSummaries
                        )
                    )
                },
                onPrint = {
                    val f = PdfReportGenerator.generateClassResultSheetPdf(
                        context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                        className, "All Sections", snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                        classSubjects, classSummaries
                    )
                    PdfReportGenerator.printPdfFile(context, f, "ClassResult_$className")
                },
                onShare = {
                    val f = PdfReportGenerator.generateClassResultSheetPdf(
                        context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                        className, "All Sections", snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                        classSubjects, classSummaries
                    )
                    FileExportHelper.shareFile(context, f, "application/pdf", "Share Class Result PDF")
                }
            )
        }

        // 4. Subject-wise Marks Sheet PDF
        item {
            val sub = classSubjects.firstOrNull { it.id == selectedSubjectId }
            val secSummaries = classSummaries.filter { selectedSectionId.isBlank() || it.student.sectionId == selectedSectionId }
            PdfActionCard(
                title = "4. Subject-wise Marks Sheet (A4)",
                description = "Marks list for ${sub?.name ?: "selected subject"} with percentages and grades.",
                enabled = sub != null && secSummaries.isNotEmpty(),
                onSave = {
                    sub?.let {
                        triggerSave(
                            PdfReportGenerator.generateSubjectMarksPdf(
                                context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                                snapshot.classesById[selectedClassId]?.name ?: "Class",
                                snapshot.sectionsById[selectedSectionId]?.name ?: "All",
                                snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                                it, secSummaries
                            )
                        )
                    }
                },
                onPrint = {
                    sub?.let {
                        val f = PdfReportGenerator.generateSubjectMarksPdf(
                            context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                            snapshot.classesById[selectedClassId]?.name ?: "Class",
                            snapshot.sectionsById[selectedSectionId]?.name ?: "All",
                            snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                            it, secSummaries
                        )
                        PdfReportGenerator.printPdfFile(context, f, "SubjectMarks_${it.name}")
                    }
                },
                onShare = {
                    sub?.let {
                        val f = PdfReportGenerator.generateSubjectMarksPdf(
                            context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                            snapshot.classesById[selectedClassId]?.name ?: "Class",
                            snapshot.sectionsById[selectedSectionId]?.name ?: "All",
                            snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                            it, secSummaries
                        )
                        FileExportHelper.shareFile(context, f, "application/pdf", "Share Subject Marks PDF")
                    }
                }
            )
        }

        // 5. Class Performance & Student Result Summaries PDF
        item {
            PdfActionCard(
                title = "5. Class Performance Report (A4)",
                description = "Campus-wide pass rate, completion counts, and average percentage per class.",
                enabled = snapshot.classStats.isNotEmpty(),
                onSave = {
                    triggerSave(
                        PdfReportGenerator.generatePerformanceReportPdf(
                            context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                            snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                            snapshot.classStats
                        )
                    )
                },
                onPrint = {
                    val f = PdfReportGenerator.generatePerformanceReportPdf(
                        context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                        snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                        snapshot.classStats
                    )
                    PdfReportGenerator.printPdfFile(context, f, "Class_Performance_Report")
                },
                onShare = {
                    val f = PdfReportGenerator.generatePerformanceReportPdf(
                        context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                        snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                        snapshot.classStats
                    )
                    FileExportHelper.shareFile(context, f, "application/pdf", "Share Performance Report PDF")
                }
            )
        }

        item {
            PdfActionCard(
                title = "6. Student Result Summaries (A4)",
                description = "Compact summary roster of all students across classes with overall grades and status.",
                enabled = snapshot.allStudentSummaries.isNotEmpty(),
                onSave = {
                    triggerSave(
                        PdfReportGenerator.generateStudentSummaryListPdf(
                            context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                            snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                            snapshot.allStudentSummaries
                        )
                    )
                },
                onPrint = {
                    val f = PdfReportGenerator.generateStudentSummaryListPdf(
                        context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                        snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                        snapshot.allStudentSummaries
                    )
                    PdfReportGenerator.printPdfFile(context, f, "Student_Result_Summaries")
                },
                onShare = {
                    val f = PdfReportGenerator.generateStudentSummaryListPdf(
                        context, snapshot.campus.schoolName, snapshot.campus.campusName, snapshot.campus.logoBase64,
                        snapshot.selectedExam?.name ?: "Exam", snapshot.selectedSession?.name ?: "Session",
                        snapshot.allStudentSummaries
                    )
                    FileExportHelper.shareFile(context, f, "application/pdf", "Share Student Summaries PDF")
                }
            )
        }
    }

    editingExam?.let { target ->
        ExamEditorDialog(
            existing = target,
            sessions = snapshot.sessions,
            defaultSessionId = snapshot.selectedSessionId,
            defaultSortOrder = target.sortOrder,
            canDelete = snapshot.examinations.size > 1,
            onDismiss = { editingExam = null },
            onSave = { name, examType, sessionId, useCustomGrading, sortOrder ->
                viewModel.updateExamination(
                    campusId = snapshot.campus.id,
                    examId = target.id,
                    sessionId = sessionId,
                    name = name,
                    examType = examType,
                    useCustomGrading = useCustomGrading,
                    sortOrder = sortOrder
                )
                editingExam = null
            },
            onDelete = {
                viewModel.deleteExamination(snapshot.campus.id, target.id)
                editingExam = null
            }
        )
    }
}

@Composable
private fun ExportActionCard(
    title: String,
    description: String,
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TableChart, contentDescription = null, tint = EmeraldPass)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save .xlsx")
                }
                OutlinedButton(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun ImportActionCard(
    title: String,
    columnsHint: String,
    onImport: () -> Unit,
    onTemplate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
            Text(columnsHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = EducatorsBlue),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Select .xlsx")
                }
                OutlinedButton(
                    onClick = onTemplate,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Template")
                }
            }
        }
    }
}

@Composable
private fun PdfActionCard(
    title: String,
    description: String,
    enabled: Boolean,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CrimsonFail)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onSave,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
                OutlinedButton(
                    onClick = onPrint,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print")
                }
                OutlinedButton(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}
