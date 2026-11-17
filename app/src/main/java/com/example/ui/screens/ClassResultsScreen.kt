package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.example.data.model.ResultStatus
import com.example.data.model.StudentResultSummary
import com.example.domain.PdfReportGenerator
import com.example.domain.ResultCalculator
import com.example.domain.XlsxEngine
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import java.io.File

private enum class ResultSort(val label: String) {
    POSITION("Position"),
    PERCENTAGE("Percentage"),
    ROLL("Roll No")
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
        snapshot.subjects.filter { it.classId == selectedClassId }
    }

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
                    .thenBy { it.student.rollNumber }
            )
            ResultSort.PERCENTAGE -> base.sortedByDescending { it.overallPercentage }
            ResultSort.POSITION -> base.sortedWith(
                compareBy<StudentResultSummary> { it.position ?: Int.MAX_VALUE }
                    .thenByDescending { it.overallPercentage }
            )
        }
    }

    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            viewModel.postMessage("Class Result Sheet PDF saved.")
        }
        pendingSaveFile = null
    }
    val saveXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            viewModel.postMessage("Class Result Sheet Excel saved.")
        }
        pendingSaveFile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        CampusContextBar(
            snapshot = snapshot,
            onSelectSession = { viewModel.selectSession(it) },
            onSelectExam = { viewModel.selectExam(it) }
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
            val secOptions = listOf("ALL" to "All Sections") + sectionsForClass.map { it.id to "Section ${it.name}" }
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

        // Sort & Filter Chips
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

        if (filteredSummaries.isNotEmpty()) {
            val className = snapshot.classesById[selectedClassId]?.name ?: "Class"
            val sectionLabel = if (selectedSectionId == "ALL") "All Sections" else "Section ${snapshot.sectionsById[selectedSectionId]?.name ?: ""}"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val bytes = XlsxEngine.exportCompleteClassResultSheet(
                            className = className,
                            sectionLabel = sectionLabel,
                            examName = snapshot.selectedExam?.name ?: "Exam",
                            sessionName = snapshot.selectedSession?.name ?: "Session",
                            subjects = classSubjects,
                            summaries = filteredSummaries
                        )
                        val file = FileExportHelper.writeBytesToCache(context, "ClassResult_${className}.xlsx", bytes)
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
                            examName = snapshot.selectedExam?.name ?: "Exam",
                            sessionName = snapshot.selectedSession?.name ?: "Session",
                            subjects = classSubjects,
                            summaries = filteredSummaries
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
                description = "No matching students found for this filter. Add students and enter marks to view the class result sheet."
            )
        } else {
            // Horizontally scrollable unified class result table + card rows
            val hScroll = rememberScrollState()
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .horizontalScroll(hScroll)
                            .background(EducatorsNavy)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderCell("Roll", 54)
                        HeaderCell("Student Name", 140)
                        classSubjects.forEach { sub ->
                            HeaderCell(sub.name.take(9), 72)
                        }
                        HeaderCell("Total", 66)
                        HeaderCell("Obtained", 74)
                        HeaderCell("%", 68)
                        HeaderCell("Grade", 58)
                        HeaderCell("Pos", 56)
                        HeaderCell("Status", 82)
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredSummaries, key = { it.student.id }) { s ->
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(hScroll)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BodyCell(s.student.rollNumber, 54, bold = true)
                                BodyCell(s.student.name, 140, bold = true)
                                classSubjects.forEach { sub ->
                                    val sr = s.subjectResults.firstOrNull { it.subject.id == sub.id }
                                    BodyCell(
                                        text = sr?.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "-",
                                        widthDp = 72,
                                        color = if (sr?.passed == false) CrimsonFail else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                BodyCell(ResultCalculator.formatMarks(s.totalMaxMarks), 66)
                                BodyCell(ResultCalculator.formatMarks(s.totalObtainedMarks), 74, bold = true)
                                BodyCell(ResultCalculator.formatPercentage(s.overallPercentage), 68)
                                BodyCell(s.overallGrade, 58, bold = true)
                                BodyCell(ResultCalculator.formatOrdinal(s.position), 56, bold = true)
                                val (stLabel, stColor) = when {
                                    s.status == ResultStatus.INCOMPLETE -> "Pending" to AmberPending
                                    s.passed -> "Pass" to EmeraldPass
                                    else -> "Fail" to CrimsonFail
                                }
                                BodyCell(stLabel, 82, bold = true, color = stColor)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, widthDp: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        maxLines = 1,
        modifier = Modifier.width(widthDp.dp)
    )
}

@Composable
private fun BodyCell(
    text: String,
    widthDp: Int,
    bold: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        ),
        color = color,
        maxLines = 1,
        modifier = Modifier.width(widthDp.dp)
    )
}
