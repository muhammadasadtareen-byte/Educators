package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Examination
import com.example.data.model.ResultStatus
import com.example.data.model.StudentResultSummary
import com.example.domain.PdfReportGenerator
import com.example.domain.ResultCalculator
import com.example.domain.XlsxEngine
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import java.io.File
import kotlin.math.ceil

private val ReportTeal = Color(0xFF0B4F6C)
private val RibbonRose = Color(0xFFC83E6B)
private val LightHeaderBg = Color(0xFFD8DEE9)
private val SubRowAltBg = Color(0xFFF8FAFC)
private val GridBorderColor = Color(0xFF64748B)
private val ChartPalette = listOf(
    Color(0xFF1D4ED8), // Blue
    Color(0xFF0891B2), // Cyan
    Color(0xFF7C3AED), // Purple
    Color(0xFFD97706), // Amber
    Color(0xFF0F766E), // Teal (Obtained Marks)
    Color(0xFF0F172A)  // Slate Navy (Highest in Class)
)

@Composable
fun ResultCardsScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val classSummaries = remember(snapshot.allStudentSummaries, selectedClassId) {
        snapshot.allStudentSummaries.filter { selectedClassId.isBlank() || it.student.classId == selectedClassId }
    }
    var selectedStudentId by remember(classSummaries) {
        mutableStateOf(classSummaries.firstOrNull()?.student?.id.orEmpty())
    }
    val selectedSummary: StudentResultSummary? = classSummaries.firstOrNull { it.student.id == selectedStudentId }
        ?: classSummaries.firstOrNull()

    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var editingExam by remember { mutableStateOf<Examination?>(null) }
    var showCreateExamDialog by remember { mutableStateOf(false) }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            viewModel.postMessage("Progress Report PDF saved.")
        }
        pendingSaveFile = null
    }
    val createXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            viewModel.postMessage("Progress Report Excel exported.")
        }
        pendingSaveFile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TabLegendCard(
            tabTitle = "Progress Reports",
            summary = "Preview A4 Progress Reports with subject breakdowns, bar chart & grading key. Matches exported PDF & Excel formats.",
            items = listOf(
                LegendBadge("PASS", EmeraldPass, "Cleared all compulsory subjects"),
                LegendBadge("* / FAIL", CrimsonFail, "Failed a compulsory subject"),
                LegendBadge("Pending", AmberPending, "Missing subject marks"),
                LegendBadge("Edit Exam", EducatorsBlue, "Tap pencil or dropdown to edit components")
            )
        )

        CampusContextBar(
            snapshot = snapshot,
            onSelectSession = { viewModel.selectSession(it) },
            onSelectExam = { viewModel.selectExam(it) },
            onEditSelectedExam = { editingExam = it },
            onCreateExam = { showCreateExamDialog = true }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorDropdown(
                label = "Class",
                selectedText = snapshot.classesById[selectedClassId]?.name ?: "All Classes",
                items = snapshot.visibleClasses,
                itemLabel = { it.name },
                onSelect = { selectedClassId = it.id },
                modifier = Modifier.weight(1f)
            )
            SelectorDropdown(
                label = "Student",
                selectedText = selectedSummary?.let { s ->
                    if (s.student.rollNumber.isNotBlank()) "${s.student.rollNumber} - ${s.student.name}" else s.student.name
                } ?: "No Students",
                items = classSummaries,
                itemLabel = { s ->
                    if (s.student.rollNumber.isNotBlank()) "${s.student.rollNumber} - ${s.student.name}" else s.student.name
                },
                onSelect = { selectedStudentId = it.student.id },
                modifier = Modifier.weight(1.3f)
            )
        }

        if (classSummaries.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val file = PdfReportGenerator.generateResultCardsPdf(
                        context = context,
                        summaries = classSummaries,
                        schoolName = snapshot.campus.schoolName,
                        campusName = snapshot.campus.campusName,
                        schoolLogoBase64 = snapshot.campus.logoBase64,
                        address = snapshot.campus.address,
                        contact = snapshot.campus.contact
                    )
                    pendingSaveFile = file
                    createPdfLauncher.launch("All_Progress_Reports_${snapshot.classesById[selectedClassId]?.name ?: "Class"}.pdf")
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("download_all_cards_pdf_btn")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download All ${classSummaries.size} Progress Reports (PDF)")
            }
        }

        if (selectedSummary == null) {
            EmptyStateCard(
                icon = Icons.Default.Badge,
                title = "No Student Selected",
                description = "Add students and enter marks to preview and print formal A4 Progress Reports."
            )
        } else {
            ProgressReportPreview(snapshot = snapshot, summary = selectedSummary)

            // Teacher's Remarks Editor
            var remarksDraft by remember(selectedSummary.student.id, selectedSummary.teacherRemarks) {
                mutableStateOf(selectedSummary.teacherRemarks)
            }
            val existingRecord = snapshot.activeResultRecords.firstOrNull { it.studentId == selectedSummary.student.id }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Teacher's Remarks", style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
                    OutlinedTextField(
                        value = remarksDraft,
                        onValueChange = { remarksDraft = it },
                        label = { Text("Remarks for Progress Report") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.saveTeacherRemarks(
                                campus = snapshot.campus,
                                sessionId = snapshot.selectedSessionId,
                                examId = snapshot.selectedExamId,
                                summary = selectedSummary,
                                remarks = remarksDraft,
                                existingRecord = existingRecord
                            )
                        },
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Remarks to Cloud")
                    }
                }
            }

            // Export & Print Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val file = PdfReportGenerator.generateResultCardsPdf(
                            context = context,
                            summaries = listOf(selectedSummary),
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            schoolLogoBase64 = snapshot.campus.logoBase64,
                            address = snapshot.campus.address,
                            contact = snapshot.campus.contact
                        )
                        pendingSaveFile = file
                        createPdfLauncher.launch(file.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("download_card_pdf_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save PDF")
                }

                OutlinedButton(
                    onClick = {
                        val file = PdfReportGenerator.generateResultCardsPdf(
                            context = context,
                            summaries = listOf(selectedSummary),
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            schoolLogoBase64 = snapshot.campus.logoBase64,
                            address = snapshot.campus.address,
                            contact = snapshot.campus.contact
                        )
                        PdfReportGenerator.printPdfFile(context, file, "ProgressReport_${selectedSummary.student.name}")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("print_card_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print A4")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val file = PdfReportGenerator.generateResultCardsPdf(
                            context = context,
                            summaries = listOf(selectedSummary),
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            schoolLogoBase64 = snapshot.campus.logoBase64,
                            address = snapshot.campus.address,
                            contact = snapshot.campus.contact
                        )
                        FileExportHelper.shareFile(context, file, "application/pdf", "Share Progress Report PDF")
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share PDF")
                }

                OutlinedButton(
                    onClick = {
                        val bytes = XlsxEngine.exportIndividualStudentResult(
                            summary = selectedSummary,
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            address = snapshot.campus.address,
                            contact = snapshot.campus.contact
                        )
                        val nameSlug = selectedSummary.student.rollNumber.takeIf { it.isNotBlank() }
                            ?: selectedSummary.student.name.replace(" ", "_")
                        val file = FileExportHelper.writeBytesToCache(
                            context,
                            "ProgressReport_$nameSlug.xlsx",
                            bytes
                        )
                        pendingSaveFile = file
                        createXlsxLauncher.launch(file.name)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save Excel")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    editingExam?.let { target ->
        ExamEditorDialog(
            existing = target,
            sessions = snapshot.sessions,
            availableExams = snapshot.examinations,
            defaultSessionId = snapshot.selectedSessionId,
            defaultSortOrder = target.sortOrder,
            canDelete = snapshot.examinations.size > 1,
            onDismiss = { editingExam = null },
            onSave = { name, examType, sessionId, useCustomGrading, sortOrder, components ->
                viewModel.updateExamination(
                    campusId = snapshot.campus.id,
                    examId = target.id,
                    sessionId = sessionId,
                    name = name,
                    examType = examType,
                    useCustomGrading = useCustomGrading,
                    sortOrder = sortOrder,
                    components = components
                )
                editingExam = null
            },
            onDelete = {
                viewModel.deleteExamination(snapshot.campus.id, target.id)
                editingExam = null
            }
        )
    }

    if (showCreateExamDialog) {
        ExamEditorDialog(
            existing = null,
            sessions = snapshot.sessions,
            availableExams = snapshot.examinations,
            defaultSessionId = snapshot.selectedSessionId,
            defaultSortOrder = (snapshot.examinations.maxOfOrNull { it.sortOrder } ?: 0L) + 1L,
            canDelete = false,
            onDismiss = { showCreateExamDialog = false },
            onSave = { name, examType, sessionId, useCustomGrading, sortOrder, components ->
                viewModel.addExamination(
                    campus = snapshot.campus,
                    sessionId = sessionId,
                    name = name,
                    examType = examType,
                    useCustomGrading = useCustomGrading,
                    sortOrder = sortOrder,
                    components = components
                )
                showCreateExamDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressReportPreview(
    snapshot: SchoolSnapshot,
    summary: StudentResultSummary
) {
    val logoBitmap = remember(snapshot.campus.logoBase64) {
        PdfReportGenerator.decodeBase64Bitmap(snapshot.campus.logoBase64)
    }
    val components = summary.examComponents
    val rules = remember(summary.gradingRulesUsed) {
        (if (summary.gradingRulesUsed.isNotEmpty()) summary.gradingRulesUsed else ResultCalculator.defaultGradingTemplates())
            .sortedByDescending { it.minPercentage }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, ReportTeal),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Crests + Rose "Progress Report" Banner Ribbon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SchoolLogoBadge(logoBase64 = snapshot.campus.logoBase64, sizeDp = 44, paddingDp = 2)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RibbonRose)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Progress Report",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                if (!summary.student.photoBase64.isNullOrBlank()) {
                    StudentAvatar(photoBase64 = summary.student.photoBase64, name = summary.student.name, sizeDp = 44)
                } else {
                    SchoolLogoBadge(logoBase64 = snapshot.campus.logoBase64, sizeDp = 44, paddingDp = 2)
                }
            }

            // 2. Centered School / Examination / Campus Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = snapshot.campus.schoolName.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = ReportTeal,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = summary.examName.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = snapshot.campus.campusName.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ReportTeal,
                    textAlign = TextAlign.Center
                )
                if (snapshot.campus.address.isNotBlank()) {
                    Text(
                        text = snapshot.campus.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 3. Particulars Grid (only shows non-blank optional student fields)
            val particularsPairs = remember(summary) {
                buildList {
                    add("Student Name" to summary.student.name)
                    summary.student.fatherName.takeIf { it.isNotBlank() && it != "-" }?.let {
                        add("Father's Name" to it)
                    }
                    add("Class - Section" to "${summary.className} - ${summary.sectionName}")
                    add("Session" to summary.sessionName)
                    summary.student.rollNumber.takeIf { it.isNotBlank() }?.let {
                        add("Roll Number" to it)
                    }
                    summary.student.admissionNumber.takeIf { it.isNotBlank() }?.let {
                        add("Admission No" to it)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorderColor)
            ) {
                particularsPairs.chunked(2).forEach { pair ->
                    val left = pair[0]
                    val right = pair.getOrNull(1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        ParticularCell(left.first, isHeader = true, modifier = Modifier.weight(0.95f).fillMaxHeight())
                        ParticularCell(left.second, isHeader = false, modifier = Modifier.weight(1.25f).fillMaxHeight())
                        if (right != null) {
                            ParticularCell(right.first, isHeader = true, modifier = Modifier.weight(0.95f).fillMaxHeight())
                            ParticularCell(right.second, isHeader = false, modifier = Modifier.weight(1.25f).fillMaxHeight())
                        } else {
                            ParticularCell("", isHeader = false, modifier = Modifier.weight(2.2f).fillMaxHeight())
                        }
                    }
                }
            }

            // 4. Subjects & Component Marks Table (Horizontally scrollable)
            val hScroll = rememberScrollState()
            val subjectColW = 114.dp
            val compColW = 74.dp
            val totalObtW = 72.dp
            val highestW = 74.dp
            val pctW = 64.dp
            val gradeW = 50.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
                    .border(1.dp, GridBorderColor)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .background(LightHeaderBg)
                        .height(IntrinsicSize.Min)
                ) {
                    ReportCell("Subjects", subjectColW, bold = true, alignment = Alignment.Center, modifier = Modifier.fillMaxHeight())
                    components.forEach { comp ->
                        ReportCell(
                            text = "${comp.name}\n(${ResultCalculator.formatMarks(comp.maxMarks)})",
                            width = compColW,
                            bold = true,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    ReportCell("Total Marks\nObtained", totalObtW, bold = true, alignment = Alignment.Center, modifier = Modifier.fillMaxHeight())
                    ReportCell("Highest Marks\nin Class", highestW, bold = true, alignment = Alignment.Center, modifier = Modifier.fillMaxHeight())
                    ReportCell("Percentage", pctW, bold = true, alignment = Alignment.Center, modifier = Modifier.fillMaxHeight())
                    ReportCell("Grade", gradeW, bold = true, alignment = Alignment.Center, modifier = Modifier.fillMaxHeight())
                }

                // Subject Rows
                summary.subjectResults.forEachIndexed { idx, sr ->
                    val bg = if (idx % 2 == 1) SubRowAltBg else Color.White
                    Row(
                        modifier = Modifier
                            .background(bg)
                            .height(IntrinsicSize.Min)
                    ) {
                        val subName = sr.subject.name + if (sr.passed == false && sr.subject.compulsory) " *" else ""
                        ReportCell(
                            text = subName,
                            width = subjectColW,
                            bold = true,
                            alignment = Alignment.CenterStart,
                            textColor = if (sr.passed == false) CrimsonFail else Color(0xFF0F172A),
                            modifier = Modifier.fillMaxHeight()
                        )
                        components.forEach { comp ->
                            val cMark = sr.resolvedComponentMarks[comp.id]
                            ReportCell(
                                text = cMark?.let { ResultCalculator.formatMarks(it) } ?: "-",
                                width = compColW,
                                alignment = Alignment.Center,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                        ReportCell(
                            text = sr.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "-",
                            width = totalObtW,
                            bold = true,
                            alignment = Alignment.Center,
                            textColor = if (sr.passed == false) CrimsonFail else Color(0xFF0F172A),
                            modifier = Modifier.fillMaxHeight()
                        )
                        ReportCell(
                            text = sr.highestMarksInClass?.let { ResultCalculator.formatMarks(it) } ?: "-",
                            width = highestW,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxHeight()
                        )
                        ReportCell(
                            text = sr.percentage?.let { ResultCalculator.formatPercentNumber(it) } ?: "-",
                            width = pctW,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxHeight()
                        )
                        ReportCell(
                            text = sr.grade,
                            width = gradeW,
                            bold = true,
                            alignment = Alignment.Center,
                            textColor = if (sr.passed == false) CrimsonFail else EducatorsNavy,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                }

                // GRAND TOTAL row
                Row(
                    modifier = Modifier
                        .background(LightHeaderBg.copy(alpha = 0.7f))
                        .height(IntrinsicSize.Min)
                ) {
                    ReportCell(
                        text = "GRAND TOTAL (${ResultCalculator.formatMarks(summary.totalMaxMarks)})",
                        width = subjectColW,
                        bold = true,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxHeight()
                    )
                    components.forEach { _ ->
                        ReportCell("", compColW, alignment = Alignment.Center, modifier = Modifier.fillMaxHeight())
                    }
                    ReportCell(
                        text = ResultCalculator.formatMarks(summary.totalObtainedMarks),
                        width = totalObtW,
                        bold = true,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxHeight()
                    )
                    ReportCell("", highestW, alignment = Alignment.Center, modifier = Modifier.fillMaxHeight())
                    ReportCell(
                        text = ResultCalculator.formatPercentNumber(summary.overallPercentage),
                        width = pctW,
                        bold = true,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxHeight()
                    )
                    ReportCell(
                        text = summary.overallGrade,
                        width = gradeW,
                        bold = true,
                        alignment = Alignment.Center,
                        textColor = if (!summary.passed && summary.status == ResultStatus.COMPLETE) CrimsonFail else EducatorsNavy,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }

            if (summary.failedCompulsorySubjects.isNotEmpty()) {
                Text(
                    text = "* Failed Compulsory Subject(s): ${summary.failedCompulsorySubjects.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CrimsonFail
                )
            }

            // 5. Teacher's Remarks Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightHeaderBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Teacher's Remarks",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                }
                HorizontalDivider(color = GridBorderColor, thickness = 0.8.dp)
                Text(
                    text = summary.teacherRemarks.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(8.dp)
                )
            }

            // 6. Subject Performance Grouped Bar Chart + Legend
            if (summary.subjectResults.isNotEmpty()) {
                ProgressGroupedBarChart(summary = summary)
            }

            // 7. Signature / Dated Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorderColor)
            ) {
                listOf(
                    "Class Teacher's Signature",
                    "Principal's Signature",
                    "Parent/Guardian's Signature"
                ).forEach { sigLabel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.45f)
                                .fillMaxHeight()
                                .background(LightHeaderBg.copy(alpha = 0.45f))
                                .border(0.5.dp, GridBorderColor)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = sigLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.25f)
                                .fillMaxHeight()
                                .border(0.5.dp, GridBorderColor)
                        )
                        Box(
                            modifier = Modifier
                                .weight(0.65f)
                                .fillMaxHeight()
                                .background(LightHeaderBg.copy(alpha = 0.45f))
                                .border(0.5.dp, GridBorderColor)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Dated",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0F172A)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight()
                                .border(0.5.dp, GridBorderColor)
                        )
                    }
                }
            }

            // 8. GRADING KEY Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GridBorderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightHeaderBg)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GRADING KEY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                }
                HorizontalDivider(color = GridBorderColor, thickness = 0.8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    rules.forEach { rule ->
                        val minTxt = "${rule.minPercentage.toInt()}%"
                        val maxTxt = if (rule.maxPercentage >= 99.5) "100%" else "${ceil(rule.maxPercentage).toInt()}%"
                        Column(
                            modifier = Modifier
                                .width(76.dp)
                                .border(0.5.dp, GridBorderColor)
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Grade ${rule.grade}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "$minTxt - $maxTxt",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressGroupedBarChart(summary: StudentResultSummary) {
    val components = summary.examComponents
    val chartSeries = remember(components) {
        buildList {
            components.forEachIndexed { idx, comp ->
                add(comp.name to ChartPalette[idx % 4])
            }
            add("Obtained Marks" to ChartPalette[4])
            add("Highest Marks in Class" to ChartPalette[5])
        }
    }

    val maxScale = remember(summary) {
        val rawMax = maxOf(
            summary.subjectResults.maxOfOrNull { sr ->
                maxOf(sr.obtainedMarks ?: 0.0, sr.highestMarksInClass ?: 0.0, sr.effectiveMaxMarks)
            } ?: 100.0,
            10.0
        )
        (ceil(rawMax / 10.0) * 10.0).coerceAtLeast(10.0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GridBorderColor)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val minChartWidth = (summary.subjectResults.size * 58).coerceAtLeast(280).dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Canvas(
                modifier = Modifier
                    .width(minChartWidth)
                    .height(170.dp)
            ) {
                val leftPad = 34.dp.toPx()
                val rightPad = 8.dp.toPx()
                val topPad = 10.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                val gridColor = Color(0xFFCBD5E1)
                val axisColor = Color(0xFF475569)
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#475569")
                    textSize = 9.dp.toPx()
                    isAntiAlias = true
                }

                // 5 Y-axis steps
                for (step in 0..5) {
                    val ratio = step / 5f
                    val y = topPad + plotH * (1f - ratio)
                    drawLine(
                        color = if (step == 0) axisColor else gridColor,
                        start = Offset(leftPad, y),
                        end = Offset(leftPad + plotW, y),
                        strokeWidth = 1f
                    )
                    labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    val tickVal = (maxScale * ratio).toInt().toString()
                    drawContext.canvas.nativeCanvas.drawText(tickVal, leftPad - 4.dp.toPx(), y + 3.dp.toPx(), labelPaint)
                }
                drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + plotH), strokeWidth = 1f)

                val groupCount = summary.subjectResults.size.coerceAtLeast(1)
                val groupW = plotW / groupCount
                val barsPerGroup = chartSeries.size.coerceAtLeast(1)
                val barW = ((groupW * 0.72f) / barsPerGroup).coerceIn(3f, 14.dp.toPx())
                val groupBarsW = barW * barsPerGroup

                summary.subjectResults.forEachIndexed { gIdx, sr ->
                    val gx = leftPad + gIdx * groupW + (groupW - groupBarsW) / 2f
                    val values = buildList {
                        components.forEach { c ->
                            add(sr.resolvedComponentMarks[c.id] ?: 0.0)
                        }
                        add(sr.obtainedMarks ?: 0.0)
                        add(sr.highestMarksInClass ?: 0.0)
                    }
                    values.forEachIndexed { bIdx, value ->
                        val ratio = (value / maxScale).coerceIn(0.0, 1.0).toFloat()
                        val barH = plotH * ratio
                        val bx = gx + bIdx * barW
                        if (barH > 0.5f) {
                            drawRect(
                                color = chartSeries[bIdx].second,
                                topLeft = Offset(bx, topPad + plotH - barH),
                                size = Size((barW - 1.5f).coerceAtLeast(1.5f), barH)
                            )
                        }
                    }

                    labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                    val subLabel = sr.subject.name.take(8)
                    drawContext.canvas.nativeCanvas.drawText(
                        subLabel,
                        leftPad + gIdx * groupW + groupW / 2f,
                        topPad + plotH + 14.dp.toPx(),
                        labelPaint
                    )
                }
            }
        }

        // Chart Legend
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chartSeries.forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 8.dp)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFF334155)
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticularCell(
    text: String,
    isHeader: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (isHeader) LightHeaderBg.copy(alpha = 0.65f) else Color.White)
            .border(0.5.dp, GridBorderColor)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp
            ),
            color = Color(0xFF0F172A),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReportCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    alignment: Alignment = Alignment.Center,
    textColor: Color = Color(0xFF0F172A)
) {
    Box(
        modifier = modifier
            .width(width)
            .border(0.5.dp, GridBorderColor)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.5.sp,
                lineHeight = 12.sp
            ),
            color = textColor,
            textAlign = if (alignment == Alignment.CenterStart) TextAlign.Start else TextAlign.Center
        )
    }
}
