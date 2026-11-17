package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.MarkEntry
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.domain.ResultCalculator
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
import com.example.ui.theme.AmberPendingContainer
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsBlueSurface
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import com.example.ui.theme.EmeraldPassContainer
import kotlinx.coroutines.launch

@Composable
fun MarksEntryScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val sectionsForClass = remember(selectedClassId, snapshot.sections) {
        snapshot.sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(selectedClassId, sectionsForClass) {
        mutableStateOf(sectionsForClass.firstOrNull()?.id.orEmpty())
    }
    val subjectsForClass = remember(selectedClassId, snapshot.subjects) {
        snapshot.subjects.filter { it.classId == selectedClassId }
    }
    var selectedSubjectId by remember(selectedClassId, subjectsForClass) {
        mutableStateOf(subjectsForClass.firstOrNull()?.id.orEmpty())
    }

    val selectedSubject: Subject? = subjectsForClass.firstOrNull { it.id == selectedSubjectId }
    val studentsInSection = remember(
        snapshot.visibleStudents, selectedClassId, selectedSectionId, snapshot.selectedSessionId
    ) {
        snapshot.visibleStudents
            .filter {
                it.classId == selectedClassId &&
                    it.sectionId == selectedSectionId &&
                    (snapshot.selectedSessionId.isBlank() || it.sessionId == snapshot.selectedSessionId)
            }
            .sortedWith(compareBy<Student> { it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.rollNumber })
    }

    val marksByStudentId = remember(snapshot.activeExamMarks, selectedSubjectId) {
        snapshot.activeExamMarks
            .filter { it.subjectId == selectedSubjectId }
            .associateBy { it.studentId }
    }

    var isTableMode by remember { mutableStateOf(false) }
    var currentIndex by remember(selectedClassId, selectedSectionId, selectedSubjectId) { mutableIntStateOf(0) }
    val clampedIndex = currentIndex.coerceIn(0, (studentsInSection.size - 1).coerceAtLeast(0))
    val currentStudent = studentsInSection.getOrNull(clampedIndex)
    val existingMark = currentStudent?.let { marksByStudentId[it.id] }

    var marksInput by remember(currentStudent?.id, selectedSubjectId, existingMark?.obtainedMarks) {
        mutableStateOf(existingMark?.let { ResultCalculator.formatMarks(it.obtainedMarks) } ?: "")
    }
    var isEditingExisting by remember(currentStudent?.id, selectedSubjectId, existingMark?.id) {
        mutableStateOf(existingMark == null)
    }
    var validationError by remember(currentStudent?.id, selectedSubjectId) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val savedText = existingMark?.let { ResultCalculator.formatMarks(it.obtainedMarks) } ?: ""
    val hasUnsavedChanges = !isTableMode && currentStudent != null && isEditingExisting &&
        marksInput.trim() != savedText && marksInput.isNotBlank()

    var pendingNavigationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun guardUnsaved(action: () -> Unit) {
        if (hasUnsavedChanges) {
            pendingNavigationAction = action
        } else {
            action()
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        pendingNavigationAction = onNavigateBack
    }

    val activeRules = remember(snapshot.gradingRules, snapshot.selectedExam) {
        ResultCalculator.resolveActiveGradingRules(snapshot.gradingRules, snapshot.selectedExam)
    }

    val completedCount = studentsInSection.count { marksByStudentId.containsKey(it.id) }
    val allEntered = studentsInSection.isNotEmpty() && selectedSubject != null && completedCount == studentsInSection.size

    fun validateAndParseMarks(raw: String, subject: Subject): Double? {
        val trimmed = raw.trim()
        val parsed = trimmed.toDoubleOrNull()
        if (parsed == null) {
            validationError = "Please enter a valid number."
            return null
        }
        if (parsed < 0.0) {
            validationError = "Marks cannot be negative."
            return null
        }
        if (parsed > subject.maxMarks) {
            validationError = "Marks cannot exceed Maximum Marks (${ResultCalculator.formatMarks(subject.maxMarks)})."
            return null
        }
        if (!snapshot.campus.allowDecimalMarks && parsed % 1.0 != 0.0) {
            validationError = "Decimal marks are disabled in Settings."
            return null
        }
        validationError = null
        return parsed
    }

    fun performSave(advanceToNext: Boolean, afterComplete: (() -> Unit)? = null) {
        val stu = currentStudent ?: return
        val sub = selectedSubject ?: return
        val parsed = validateAndParseMarks(marksInput, sub) ?: return
        scope.launch {
            isSaving = true
            val res = viewModel.saveMarkEntrySuspend(
                campus = snapshot.campus,
                sessionId = snapshot.selectedSessionId,
                examId = snapshot.selectedExamId,
                student = stu,
                subject = sub,
                obtainedMarks = parsed,
                existingMark = existingMark
            )
            isSaving = false
            res.onSuccess {
                isEditingExisting = false
                if (advanceToNext) {
                    if (clampedIndex < studentsInSection.lastIndex) {
                        currentIndex = clampedIndex + 1
                        viewModel.postMessage("Saved ${stu.name} (${ResultCalculator.formatMarks(parsed)}). Loaded next student.")
                    } else {
                        viewModel.postMessage("Saved ${stu.name}. All students' marks have been entered successfully.")
                    }
                } else {
                    viewModel.postMessage("Saved marks for ${stu.name}.")
                }
                afterComplete?.invoke()
            }.onFailure { err ->
                validationError = err.message ?: "Failed to save marks."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 5-Step Selector Panel
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampusContextBar(
                    snapshot = snapshot,
                    onSelectSession = { guardUnsaved { viewModel.selectSession(it) } },
                    onSelectExam = { guardUnsaved { viewModel.selectExam(it) } }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorDropdown(
                        label = "Class",
                        selectedText = snapshot.classesById[selectedClassId]?.name ?: "Select Class",
                        items = snapshot.visibleClasses,
                        itemLabel = { it.name },
                        onSelect = { guardUnsaved { selectedClassId = it.id } },
                        modifier = Modifier.weight(1f)
                    )
                    SelectorDropdown(
                        label = "Section",
                        selectedText = sectionsForClass.firstOrNull { it.id == selectedSectionId }?.name ?: "Section",
                        items = sectionsForClass,
                        itemLabel = { it.name },
                        onSelect = { guardUnsaved { selectedSectionId = it.id } },
                        modifier = Modifier.weight(1f)
                    )
                }
                SelectorDropdown(
                    label = "Subject",
                    selectedText = selectedSubject?.let {
                        "${it.name} (Max: ${ResultCalculator.formatMarks(it.maxMarks)})"
                    } ?: "No Subjects",
                    items = subjectsForClass,
                    itemLabel = { "${it.name} (Max: ${ResultCalculator.formatMarks(it.maxMarks)})" },
                    onSelect = { guardUnsaved { selectedSubjectId = it.id } }
                )
            }
        }

        // Mode Toggle: Sequential vs Table
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !isTableMode,
                onClick = { guardUnsaved { isTableMode = false } },
                label = { Text("Sequential Mode") },
                leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f).testTag("sequential_mode_chip")
            )
            FilterChip(
                selected = isTableMode,
                onClick = { guardUnsaved { isTableMode = true } },
                label = { Text("Table Entry Mode") },
                leadingIcon = { Icon(Icons.Default.TableRows, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f).testTag("table_mode_chip")
            )
        }

        if (selectedSubject == null || studentsInSection.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Edit,
                title = if (selectedSubject == null) "No Subject Configured" else "No Students in Section",
                description = if (selectedSubject == null) {
                    "Add subjects for this class in Subject Management before entering marks."
                } else {
                    "Add students to this class and section in Student Management to begin marks entry."
                }
            )
        } else {
            // Progress Bar
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = EducatorsBlueSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$completedCount/${studentsInSection.size} students completed",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = EducatorsNavy,
                            modifier = Modifier.testTag("marks_progress_text")
                        )
                        Text(
                            text = "${(completedCount * 100) / studentsInSection.size}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EducatorsBlue
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { completedCount.toFloat() / studentsInSection.size.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = EducatorsBlue,
                        trackColor = Color.White
                    )
                }
            }

            // Completion Banner
            AnimatedVisibility(visible = allEntered) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldPassContainer),
                    border = BorderStroke(1.dp, EmeraldPass.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPass)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "All students' marks have been entered successfully.",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldPass,
                            modifier = Modifier.testTag("all_marks_completed_banner")
                        )
                    }
                }
            }

            if (!isTableMode && currentStudent != null) {
                // Quick Student Selector Strip
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(studentsInSection) { index, st ->
                        val hasMark = marksByStudentId.containsKey(st.id)
                        val isCurrent = index == clampedIndex
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isCurrent -> EducatorsNavy
                                hasMark -> EmeraldPassContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) EducatorsNavy else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clickable { guardUnsaved { currentIndex = index } }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "#${st.rollNumber}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        isCurrent -> Color.White
                                        hasMark -> EmeraldPass
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }

                // Sequential Student Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, EducatorsBlue.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Student ${clampedIndex + 1} of ${studentsInSection.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = EducatorsBlue
                            )
                            if (existingMark != null) {
                                StatusPill(
                                    text = if (isEditingExisting) "Editing Saved" else "Already Entered",
                                    isPositive = !isEditingExisting,
                                    isWarning = isEditingExisting
                                )
                            } else {
                                StatusPill(text = "Pending", isPositive = false, isWarning = true)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StudentAvatar(photoBase64 = currentStudent.photoBase64, name = currentStudent.name, sizeDp = 54)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentStudent.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = EducatorsNavy,
                                    modifier = Modifier.testTag("seq_student_name")
                                )
                                Text(
                                    text = "Roll Number: ${currentStudent.rollNumber}  •  Father: ${currentStudent.fatherName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EducatorsBlueSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Subject", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(selectedSubject.name, style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Maximum / Passing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${ResultCalculator.formatMarks(selectedSubject.maxMarks)} / ${ResultCalculator.formatMarks(selectedSubject.passingMarks)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EducatorsBlue
                                    )
                                }
                            }
                        }

                        // Obtained Marks Input
                        OutlinedTextField(
                            value = marksInput,
                            onValueChange = {
                                marksInput = it
                                validationError = null
                            },
                            enabled = isEditingExisting,
                            label = { Text("Enter Obtained Marks (0 - ${ResultCalculator.formatMarks(selectedSubject.maxMarks)})") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (snapshot.campus.allowDecimalMarks) KeyboardType.Decimal else KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { performSave(advanceToNext = true) }),
                            singleLine = true,
                            isError = validationError != null,
                            supportingText = validationError?.let { err -> { Text(err) } },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seq_marks_input")
                        )

                        // Live Preview Calculation
                        val previewVal = marksInput.trim().toDoubleOrNull()
                        if (previewVal != null && previewVal in 0.0..selectedSubject.maxMarks) {
                            val previewResult = ResultCalculator.calculateSubjectResult(
                                selectedSubject,
                                MarkEntry(obtainedMarks = previewVal),
                                activeRules
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live Percentage: ${ResultCalculator.formatPercentage(previewResult.percentage ?: 0.0)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EducatorsNavy
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GradePill(grade = previewResult.grade, passed = previewResult.passed)
                                    StatusPill(
                                        text = if (previewResult.passed == true) "PASS" else "FAIL",
                                        isPositive = previewResult.passed == true
                                    )
                                }
                            }
                        }

                        // Primary Actions: Save & Next, Save, Edit Marks
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { performSave(advanceToNext = true) },
                                enabled = !isSaving && isEditingExisting,
                                colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .heightIn(min = 50.dp)
                                    .testTag("save_and_next_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("SAVE & NEXT")
                            }

                            OutlinedButton(
                                onClick = { performSave(advanceToNext = false) },
                                enabled = !isSaving && isEditingExisting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .heightIn(min = 50.dp)
                                    .testTag("save_only_btn")
                            ) {
                                Text("Save")
                            }
                        }

                        if (!isEditingExisting) {
                            OutlinedButton(
                                onClick = { isEditingExisting = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("edit_marks_btn")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Marks")
                            }
                        }

                        // Navigation Row: Previous, Skip, Next
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (clampedIndex > 0) guardUnsaved { currentIndex = clampedIndex - 1 }
                                },
                                enabled = clampedIndex > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("prev_student_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Prev")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (clampedIndex < studentsInSection.lastIndex) {
                                        guardUnsaved { currentIndex = clampedIndex + 1 }
                                    }
                                },
                                enabled = clampedIndex < studentsInSection.lastIndex,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("skip_student_btn")
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Skip")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (clampedIndex < studentsInSection.lastIndex) {
                                        guardUnsaved { currentIndex = clampedIndex + 1 }
                                    }
                                },
                                enabled = clampedIndex < studentsInSection.lastIndex,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("next_student_btn")
                            ) {
                                Text("Next")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else if (isTableMode) {
                // Table-Based Marks Entry Mode
                TableMarksEntrySection(
                    snapshot = snapshot,
                    students = studentsInSection,
                    subject = selectedSubject,
                    marksByStudentId = marksByStudentId,
                    viewModel = viewModel
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    pendingNavigationAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingNavigationAction = null },
            title = { Text("Unsaved Marks Entry") },
            text = { Text("You have unsaved marks for ${currentStudent?.name ?: "this student"}. Save before leaving?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingNavigationAction = null
                        performSave(advanceToNext = false) { action() }
                    }
                ) { Text("Save & Continue") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingNavigationAction = null
                            marksInput = savedText
                            action()
                        }
                    ) { Text("Discard", color = CrimsonFail) }
                    TextButton(onClick = { pendingNavigationAction = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun TableMarksEntrySection(
    snapshot: SchoolSnapshot,
    students: List<Student>,
    subject: Subject,
    marksByStudentId: Map<String, MarkEntry>,
    viewModel: MainViewModel
) {
    val scope = rememberCoroutineScope()
    val draftValues = remember(students, subject.id, marksByStudentId) {
        mutableStateMapOf<String, String>().apply {
            students.forEach { st ->
                put(st.id, marksByStudentId[st.id]?.let { ResultCalculator.formatMarks(it.obtainedMarks) } ?: "")
            }
        }
    }
    val activeRules = remember(snapshot.gradingRules, snapshot.selectedExam) {
        ResultCalculator.resolveActiveGradingRules(snapshot.gradingRules, snapshot.selectedExam)
    }
    var isSavingAll by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                scope.launch {
                    isSavingAll = true
                    var savedCount = 0
                    var errorCount = 0
                    students.forEach { st ->
                        val raw = draftValues[st.id]?.trim().orEmpty()
                        if (raw.isNotEmpty()) {
                            val num = raw.toDoubleOrNull()
                            val validDecimal = snapshot.campus.allowDecimalMarks || (num != null && num % 1.0 == 0.0)
                            if (num != null && num in 0.0..subject.maxMarks && validDecimal) {
                                val res = viewModel.saveMarkEntrySuspend(
                                    campus = snapshot.campus,
                                    sessionId = snapshot.selectedSessionId,
                                    examId = snapshot.selectedExamId,
                                    student = st,
                                    subject = subject,
                                    obtainedMarks = num,
                                    existingMark = marksByStudentId[st.id]
                                )
                                if (res.isSuccess) savedCount++ else errorCount++
                            } else {
                                errorCount++
                            }
                        }
                    }
                    isSavingAll = false
                    viewModel.postMessage(
                        if (errorCount == 0) "Saved $savedCount student marks."
                        else "Saved $savedCount marks; $errorCount invalid entries skipped."
                    )
                }
            },
            enabled = !isSavingAll,
            colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("save_all_table_marks_btn")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isSavingAll) "Saving All..." else "Save All Entered Marks (${subject.name})")
        }

        students.forEach { st ->
            val raw = draftValues[st.id].orEmpty()
            val num = raw.trim().toDoubleOrNull()
            val invalid = raw.isNotBlank() && (num == null || num < 0.0 || num > subject.maxMarks ||
                (!snapshot.campus.allowDecimalMarks && num % 1.0 != 0.0))
            val preview = if (!invalid && num != null) {
                ResultCalculator.calculateSubjectResult(subject, MarkEntry(obtainedMarks = num), activeRules)
            } else null

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (invalid) CrimsonFail else MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "${st.rollNumber}. ${st.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = EducatorsNavy
                        )
                        Text(
                            text = if (preview != null) {
                                "${ResultCalculator.formatPercentage(preview.percentage ?: 0.0)} • ${preview.grade}"
                            } else if (invalid) {
                                "Max: ${ResultCalculator.formatMarks(subject.maxMarks)}"
                            } else {
                                "Pending"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (invalid) CrimsonFail else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { draftValues[st.id] = it },
                        singleLine = true,
                        isError = invalid,
                        placeholder = { Text("/${ResultCalculator.formatMarks(subject.maxMarks)}") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (snapshot.campus.allowDecimalMarks) KeyboardType.Decimal else KeyboardType.Number
                        ),
                        modifier = Modifier.width(105.dp)
                    )
                }
            }
        }
    }
}
