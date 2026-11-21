package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Examination
import com.example.data.model.ResultStatus
import com.example.data.model.StudentResultSummary
import com.example.data.model.Subject
import com.example.domain.PdfReportGenerator
import com.example.domain.ResultCalculator
import com.example.domain.XlsxEngine
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EmeraldPass
import java.io.File

private val SheetTeal = Color(0xFF0B4F6C)

private enum class ResultSort(val label: String) {
    ROLL("Roll / Name"),
    POSITION("Position"),
    PERCENTAGE("Percentage")
}

private enum class ResultFilter(val label: String) {
    ALL("All"),
    PASS("Pass"),
    FAIL("Fail"),
    INCOMPLETE("Pending")
}

@Composable
fun ClassResultsScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val sectionsForClass = remember(selectedClassId, snapshot.sections) {
        snapshot.sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(selectedClassId) { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(ResultSort.POSITION) }
    var filterBy by remember { mutableStateOf(ResultFilter.ALL) }

    val classSubjects = remember(snapshot.subjects, selectedClassId) {
        snapshot.subjects
            .filter { it.classId == selectedClassId }
            .sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name })
    }
    val examComponents = snapshot.selectedExam?.components.orEmpty()

    val filteredSummaries = remember(
        snapshot.allStudentSummaries,
        selectedClassId,
        selectedSectionId,
        searchQuery,
        sortBy,
        filterBy
    ) {
        val base = snapshot.allStudentSummaries.filter { s ->
            val matchClass = selectedClassId.isBlank() || s.student.classId == selectedClassId
            val matchSection = selectedSectionId == "ALL" || s.student.sectionId == selectedSectionId
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                s.student.name.lowercase().contains(q) ||
                s.student.rollNumber.lowercase().contains(q)
            val matchFilter = when (filterBy) {
                ResultFilter.ALL -> true
                ResultFilter.PASS -> s.status == ResultStatus.COMPLETE && s.passed
                ResultFilter.FAIL -> s.status == ResultStatus.COMPLETE && !s.passed
                ResultFilter.INCOMPLETE -> s.status == ResultStatus.INCOMPLETE
            }
            matchClass && matchSection && matchSearch && matchFilter
        }
        when (sortBy) {
            ResultSort.ROLL -> base.sortedWith(
                compareBy<StudentResultSummary> { it.student.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.student.name }
            )
            ResultSort.PERCENTAGE -> base.sortedByDescending { it.overallPercentage }
            ResultSort.POSITION -> base.sortedWith(
                compareBy<StudentResultSummary> { it.position ?: Int.MAX_VALUE }
                    .thenByDescending { it.overallPercentage }
                    .thenBy { it.student.name }
            )
        }
    }

    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var editingExam by remember { mutableStateOf<Examination?>(null) }
    var showCreateExamDialog by remember { mutableStateOf(false) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            viewModel.postMessage("Consolidated Sheet PDF saved.")
        }
        pendingSaveFile = null
    }
    val saveXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            viewModel.postMessage("Consolidated Sheet Excel saved.")
        }
        pendingSaveFile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        TabLegendCard(
            tabTitle = "Consolidated Sheet",
            summary = "Official class Consolidated Sheet matching PDF & Excel formats. Swipe table sideways to view all subjects & components.",
            items = listOf(
                LegendBadge("Pass", EmeraldPass, "Cleared all compulsory subjects"),
                LegendBadge("Fail / Red", CrimsonFail, "Failed subject or overall"),
                LegendBadge("Pending", AmberPending, "Awaiting remaining marks"),
                LegendBadge("Edit Exam", EducatorsBlue, "Tap pencil or dropdown to edit components")
            )
        )
        Spacer(Modifier.height(8.dp))
        CampusContextBar(
            snapshot = snapshot,
            onSelectSession = { viewModel.selectSession(it) },
            onSelectExam = { viewModel.selectExam(it) },
            onEditSelectedExam = { editingExam = it },
            onCreateExam = { showCreateExamDialog = true }
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorDropdown(
                label = "Class",
                selectedText = snapshot.classesById[selectedClassId]?.name ?: "Select Class",
                items = snapshot.visibleClasses,
                itemLabel = { it.name },
                onSelect = {
                    selectedClassId = it.id
                    selectedSectionId = "ALL"
                },
                modifier = Modifier.weight(1f)
            )
            val secOptions = listOf("ALL" to "All Sections") + sectionsForClass.map { it.id to it.name }
            SelectorDropdown(
                label = "Section",
                selectedText = secOptions.firstOrNull { it.first == selectedSectionId }?.second ?: "All Sections",
                items = secOptions,
                itemLabel = { it.second },
                onSelect = { selectedSectionId = it.first },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search student name or roll no...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ResultSort.entries.forEach { s ->
                FilterChip(
                    selected = sortBy == s,
                    onClick = { sortBy = s },
                    label = { Text("Sort: ${s.label}") }
                )
            }
            ResultFilter.entries.forEach { f ->
                FilterChip(
                    selected = filterBy == f,
                    onClick = { filterBy = f },
                    label = { Text(f.label) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val className = snapshot.classesById[selectedClassId]?.name ?: "Class"
        val sectionLabel = if (selectedSectionId == "ALL") {
            "All Sections"
        } else {
            snapshot.sectionsById[selectedSectionId]?.name ?: "Section"
        }
        val examName = snapshot.selectedExam?.name ?: "Examination"

        if (filteredSummaries.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val bytes = XlsxEngine.exportCompleteClassResultSheet(
                            className = className,
                            sectionLabel = sectionLabel,
                            examName = examName,
                            sessionName = snapshot.selectedSession?.name ?: "Session",
                            subjects = classSubjects,
                            summaries = filteredSummaries,
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            address = snapshot.campus.address,
                            contact = snapshot.campus.contact,
                            examComponents = examComponents
                        )
                        val file = FileExportHelper.writeBytesToCache(context, "Consolidated_Sheet_${className}.xlsx", bytes)
                        pendingSaveFile = file
                        saveXlsxLauncher.launch(file.name)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_class_excel_btn")
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Excel Sheet")
                }
                OutlinedButton(
                    onClick = {
                        val file = PdfReportGenerator.generateClassResultSheetPdf(
                            context = context,
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            schoolLogoBase64 = snapshot.campus.logoBase64,
                            className = className,
                            sectionLabel = sectionLabel,
                            examName = examName,
                            sessionName = snapshot.selectedSession?.name ?: "Session",
                            subjects = classSubjects,
                            summaries = filteredSummaries,
                            address = snapshot.campus.address,
                            contact = snapshot.campus.contact,
                            examComponents = examComponents
                        )
                        pendingSaveFile = file
                        savePdfLauncher.launch(file.name)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_class_pdf_btn")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF Sheet")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (filteredSummaries.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Assessment,
                title = "No Class Results Available",
                description = "No matching students found for this filter. Add students and enter marks to view the Consolidated Sheet."
            )
        } else {
            val hScroll = rememberScrollState()
            val useComponents = examComponents.isNotEmpty() && classSubjects.isNotEmpty()
            val totalMax = filteredSummaries.firstOrNull()?.totalMaxMarks
                ?: if (useComponents) {
                    classSubjects.size * examComponents.sumOf { it.maxMarks }
                } else {
                    classSubjects.sumOf { it.maxMarks }
                }

            val nameColW = 138.dp
            val compColW = 82.dp
            val singleSubW = 86.dp
            val obtColW = 72.dp
            val pctColW = 60.dp
            val gradeColW = 52.dp
            val posColW = 48.dp

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, SheetTeal),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Consolidated Sheet Document Header Banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = snapshot.campus.schoolName.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = SheetTeal,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = snapshot.campus.campusName.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = SheetTeal,
                            textAlign = TextAlign.Center
                        )
                        if (snapshot.campus.address.isNotBlank()) {
                            Text(
                                text = "Address: ${snapshot.campus.address}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SheetTeal,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (snapshot.campus.contact.isNotBlank()) {
                            Text(
                                text = "Contact: ${snapshot.campus.contact}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SheetTeal,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Consolidated Sheet",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            ),
                            color = SheetTeal,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "$className - $sectionLabel *** ${examName.uppercase()}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SheetTeal,
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider(color = SheetTeal, thickness = 1.dp)

                    // 2-Row Grouped Table Header (matching Consolidated Sheet PDF & Excel)
                    Row(
                        modifier = Modifier
                            .horizontalScroll(hScroll)
                            .height(IntrinsicSize.Min)
                            .background(SheetTeal)
                    ) {
                        if (useComponents) {
                            SheetHeaderBox("Name", nameColW, Modifier.fillMaxHeight(), Alignment.CenterStart)
                            classSubjects.forEach { sub ->
                                val groupW = compColW * examComponents.size
                                Column(
                                    modifier = Modifier
                                        .width(groupW)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.6f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.5f))
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = sub.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        examComponents.forEach { comp ->
                                            Box(
                                                modifier = Modifier
                                                    .width(compColW)
                                                    .height(34.dp)
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.5f))
                                                    .padding(horizontal = 3.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${comp.name} (${ResultCalculator.formatMarks(comp.maxMarks)})",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
                                                    color = Color.White,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            SheetHeaderBox("Obt. (${ResultCalculator.formatMarks(totalMax)})", obtColW, Modifier.fillMaxHeight(), Alignment.Center)
                            SheetHeaderBox("% age", pctColW, Modifier.fillMaxHeight(), Alignment.Center)
                            SheetHeaderBox("Grade", gradeColW, Modifier.fillMaxHeight(), Alignment.Center)
                            SheetHeaderBox("Pos", posColW, Modifier.fillMaxHeight(), Alignment.Center)
                        } else {
                            SheetHeaderBox("Name", nameColW, Modifier.height(42.dp), Alignment.CenterStart)
                            classSubjects.forEach { sub ->
                                SheetHeaderBox(
                                    "${sub.name} (${ResultCalculator.formatMarks(sub.maxMarks)})",
                                    singleSubW,
                                    Modifier.height(42.dp),
                                    Alignment.Center
                                )
                            }
                            SheetHeaderBox("Obt. (${ResultCalculator.formatMarks(totalMax)})", obtColW, Modifier.height(42.dp), Alignment.Center)
                            SheetHeaderBox("% age", pctColW, Modifier.height(42.dp), Alignment.Center)
                            SheetHeaderBox("Grade", gradeColW, Modifier.height(42.dp), Alignment.Center)
                            SheetHeaderBox("Pos", posColW, Modifier.height(42.dp), Alignment.Center)
                        }
                    }

                    // Data Rows
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredSummaries, key = { _, s -> s.student.id }) { idx, s ->
                            val rowBg = if (idx % 2 == 1) Color(0xFFF8FAFC) else Color.White
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(hScroll)
                                    .background(rowBg)
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SheetBodyBox(
                                    text = s.student.name,
                                    width = nameColW,
                                    alignment = Alignment.CenterStart,
                                    bold = true
                                )
                                if (useComponents) {
                                    classSubjects.forEach { sub ->
                                        val sr = s.subjectResults.firstOrNull { it.subject.id == sub.id }
                                        examComponents.forEach { comp ->
                                            val compVal = sr?.resolvedComponentMarks?.get(comp.id)
                                            SheetBodyBox(
                                                text = compVal?.let { ResultCalculator.formatMarks(it) } ?: "-",
                                                width = compColW,
                                                alignment = Alignment.Center,
                                                color = if (sr?.passed == false) CrimsonFail else Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                } else {
                                    classSubjects.forEach { sub ->
                                        val sr = s.subjectResults.firstOrNull { it.subject.id == sub.id }
                                        SheetBodyBox(
                                            text = sr?.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "-",
                                            width = singleSubW,
                                            alignment = Alignment.Center,
                                            color = if (sr?.passed == false) CrimsonFail else Color(0xFF0F172A)
                                        )
                                    }
                                }
                                SheetBodyBox(
                                    text = ResultCalculator.formatMarks(s.totalObtainedMarks),
                                    width = obtColW,
                                    alignment = Alignment.Center,
                                    bold = true
                                )
                                SheetBodyBox(
                                    text = ResultCalculator.formatPercentage(s.overallPercentage),
                                    width = pctColW,
                                    alignment = Alignment.Center
                                )
                                val gradeColor = when {
                                    s.status == ResultStatus.INCOMPLETE -> AmberPending
                                    s.passed -> EmeraldPass
                                    else -> CrimsonFail
                                }
                                SheetBodyBox(
                                    text = s.overallGrade,
                                    width = gradeColW,
                                    alignment = Alignment.Center,
                                    bold = true,
                                    color = gradeColor
                                )
                                SheetBodyBox(
                                    text = ResultCalculator.formatOrdinal(s.position),
                                    width = posColW,
                                    alignment = Alignment.Center,
                                    bold = true,
                                    color = EducatorsBlue
                                )
                            }
                            HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 0.6.dp)
                        }
                    }
                }
            }
        }
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

@Composable
private fun SheetHeaderBox(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center
) {
    Box(
        modifier = modifier
            .width(width)
            .border(0.5.dp, Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignment == Alignment.CenterStart) TextAlign.Start else TextAlign.Center
        )
    }
}

@Composable
private fun SheetBodyBox(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    alignment: Alignment = Alignment.Center,
    bold: Boolean = false,
    color: Color = Color(0xFF0F172A)
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(38.dp)
            .border(0.5.dp, Color(0xFFE2E8F0))
            .padding(horizontal = 6.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignment == Alignment.CenterStart) TextAlign.Start else TextAlign.Center
        )
    }
}
