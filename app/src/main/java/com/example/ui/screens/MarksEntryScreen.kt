package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Tune
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
import com.example.data.model.ExamComponent
import com.example.data.model.ExamSubjectConfig
import com.example.data.model.Examination
import com.example.data.model.MarkEntry
import com.example.data.model.StaffRole
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.domain.ResultCalculator
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
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
    val allExamsById = remember(snapshot.examinations) { snapshot.examinations.associateBy { it.id } }
    val allSessionMarksByKey = remember(snapshot.sessionMarks) {
        snapshot.sessionMarks.associateBy { "${it.examId}_${it.studentId}_${it.subjectId}" }
    }

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
    val activeSubjectConfig: ExamSubjectConfig? = remember(
        snapshot.selectedExamId, selectedSubjectId, snapshot.examSubjectConfigsByKey
    ) {
        if (snapshot.selectedExamId.isNotBlank() && selectedSubjectId.isNotBlank()) {
            snapshot.examSubjectConfigsByKey["${snapshot.selectedExamId}_${selectedSubjectId}"]
        } else {
            null
        }
    }
    val examComponents = remember(selectedSubject, snapshot.selectedExam, snapshot.examSubjectConfigsByKey) {
        selectedSubject?.let {
            ResultCalculator.effectiveComponentsForSubject(it, snapshot.selectedExam, snapshot.examSubjectConfigsByKey)
        } ?: snapshot.selectedExam?.components.orEmpty()
    }
    val manualComponents = remember(examComponents) { examComponents.filter { !it.isCarryForward() } }
    val linkedComponents = remember(examComponents) { examComponents.filter { it.isCarryForward() } }
    val effectiveMaxMarks = remember(selectedSubject, snapshot.selectedExam, snapshot.examSubjectConfigsByKey) {
        selectedSubject?.let {
            ResultCalculator.effectiveSubjectMaxMarks(it, snapshot.selectedExam, snapshot.examSubjectConfigsByKey)
        } ?: 100.0
    }
    val effectivePassMarks = remember(selectedSubject, snapshot.selectedExam, snapshot.examSubjectConfigsByKey) {
        selectedSubject?.let {
            ResultCalculator.effectiveSubjectPassingMarks(it, snapshot.selectedExam, snapshot.examSubjectConfigsByKey)
        } ?: 40.0
    }
    val studentsInSection = remember(
        snapshot.visibleStudents, selectedClassId, selectedSectionId, snapshot.selectedSessionId
    ) {
        snapshot.visibleStudents
            .filter {
                it.classId == selectedClassId &&
                    it.sectionId == selectedSectionId &&
                    (snapshot.selectedSessionId.isBlank() || it.sessionId == snapshot.selectedSessionId)
            }
            .sortedWith(compareBy<Student> { it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.rollNumber }.thenBy { it.name })
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
    val currentCarryForwardMarks = remember(
        currentStudent?.id, selectedSubject?.id, snapshot.selectedExam, allExamsById, allSessionMarksByKey, snapshot.examSubjectConfigsByKey
    ) {
        if (currentStudent != null && selectedSubject != null && linkedComponents.isNotEmpty()) {
            ResultCalculator.resolveCarryForwardComponentMarks(
                studentId = currentStudent.id,
                subject = selectedSubject,
                examination = snapshot.selectedExam,
                allExaminationsById = allExamsById,
                allSessionMarksByKey = allSessionMarksByKey,
                subjectConfigsByKey = snapshot.examSubjectConfigsByKey
            )
        } else {
            emptyMap()
        }
    }

    var marksInput by remember(currentStudent?.id, selectedSubjectId, existingMark?.obtainedMarks) {
        mutableStateOf(existingMark?.let { ResultCalculator.formatMarks(it.obtainedMarks) } ?: "")
    }
    val componentDrafts = remember(currentStudent?.id, selectedSubjectId, existingMark?.id, manualComponents) {
        mutableStateMapOf<String, String>().apply {
            manualComponents.forEach { comp ->
                val v = existingMark?.componentMarks?.get(comp.id)
                put(comp.id, v?.let { ResultCalculator.formatMarks(it) } ?: "")
            }
        }
    }

    var isEditingExisting by remember(currentStudent?.id, selectedSubjectId, existingMark?.id) {
        mutableStateOf(existingMark == null)
    }
    var validationError by remember(currentStudent?.id, selectedSubjectId, examComponents) {
        mutableStateOf<String?>(null)
    }
    var isSaving by remember { mutableStateOf(false) }

    val savedText = existingMark?.let { ResultCalculator.formatMarks(it.obtainedMarks) } ?: ""
    val hasUnsavedComponentChanges = manualComponents.isNotEmpty() && manualComponents.any { comp ->
        val savedComp = existingMark?.componentMarks?.get(comp.id)?.let { ResultCalculator.formatMarks(it) } ?: ""
        (componentDrafts[comp.id]?.trim().orEmpty()) != savedComp &&
            (componentDrafts[comp.id]?.isNotBlank() == true)
    }
    val hasUnsavedChanges = !isTableMode && currentStudent != null && isEditingExisting && (
        if (examComponents.isEmpty()) marksInput.trim() != savedText && marksInput.isNotBlank()
        else hasUnsavedComponentChanges
        )

    var pendingNavigationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editingExam by remember { mutableStateOf<Examination?>(null) }
    var showCreateExamDialog by remember { mutableStateOf(false) }
    var showEditSubjectTotalDialog by remember { mutableStateOf(false) }

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

    fun validateAndParseSingleMarks(raw: String, maxMarks: Double): Double? {
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
        if (parsed > maxMarks) {
            validationError = "Marks cannot exceed Maximum Marks (${ResultCalculator.formatMarks(maxMarks)})."
            return null
        }
        if (!snapshot.campus.allowDecimalMarks && parsed % 1.0 != 0.0) {
            validationError = "Decimal marks are disabled in Settings."
            return null
        }
        validationError = null
        return parsed
    }

    fun validateAndParseComponentMarks(maxMarks: Double): Pair<Double, Map<String, Double>>? {
        val parsedMap = linkedMapOf<String, Double>()
        for (comp in manualComponents) {
            val raw = componentDrafts[comp.id]?.trim().orEmpty()
            if (raw.isEmpty()) continue
            val v = raw.toDoubleOrNull()
            if (v == null) {
                validationError = "Enter a valid number for ${comp.name}."
                return null
            }
            if (v < 0.0) {
                validationError = "${comp.name} cannot be negative."
                return null
            }
            if (v > comp.maxMarks) {
                validationError = "${comp.name} cannot exceed ${ResultCalculator.formatMarks(comp.maxMarks)}."
                return null
            }
            if (!snapshot.campus.allowDecimalMarks && v % 1.0 != 0.0) {
                validationError = "Decimal marks are disabled in Settings."
                return null
            }
            parsedMap[comp.id] = v
        }
        if (manualComponents.isNotEmpty() && parsedMap.isEmpty()) {
            validationError = "Enter marks for at least one assessment / paper component."
            return null
        }
        val combinedTotal = (parsedMap.values.sum() + currentCarryForwardMarks.values.sum()).coerceAtMost(maxMarks)
        if (parsedMap.values.sum() + currentCarryForwardMarks.values.sum() > maxMarks + 0.01) {
            validationError = "Total marks exceed exam maximum (${ResultCalculator.formatMarks(maxMarks)})."
            return null
        }
        validationError = null
        return combinedTotal to parsedMap
    }

    fun performSave(advanceToNext: Boolean, afterComplete: (() -> Unit)? = null) {
        val stu = currentStudent ?: return
        val sub = selectedSubject ?: return
        val maxAllowed = effectiveMaxMarks
        val (obtained, compMap) = if (examComponents.isEmpty()) {
            val single = validateAndParseSingleMarks(marksInput, maxAllowed) ?: return
            single to emptyMap()
        } else {
            validateAndParseComponentMarks(maxAllowed) ?: return
        }

        scope.launch {
            isSaving = true
            val res = viewModel.saveMarkEntrySuspend(
                campus = snapshot.campus,
                sessionId = snapshot.selectedSessionId,
                examId = snapshot.selectedExamId,
                student = stu,
                subject = sub,
                obtainedMarks = obtained,
                existingMark = existingMark,
                componentMarks = compMap,
                maxAllowedMarks = maxAllowed,
                actorName = snapshot.currentActorName,
                actorEmail = snapshot.currentActorEmail,
                actorRole = snapshot.effectiveRole,
                className = snapshot.classesById[selectedClassId]?.name.orEmpty(),
                examName = snapshot.selectedExam?.name.orEmpty()
            )
            isSaving = false
            res.onSuccess {
                isEditingExisting = false
                if (advanceToNext) {
                    if (clampedIndex < studentsInSection.lastIndex) {
                        currentIndex = clampedIndex + 1
                        viewModel.postMessage("Saved ${stu.name} (${ResultCalculator.formatMarks(obtained)}). Loaded next student.")
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
        TabLegendCard(
            tabTitle = "Marks Entry",
            summary = "Enter marks one student at a time (Sequential) or across the whole class (Table Mode).",
            items = listOf(
                LegendBadge("# / Pos (Dark)", EducatorsNavy, "Selected student"),
                LegendBadge("Green", EmeraldPass, "Marks saved"),
                LegendBadge("Pending", AmberPending, "Marks not yet entered")
            )
        )

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
                    onSelectExam = { guardUnsaved { viewModel.selectExam(it) } },
                    onEditSelectedExam = { guardUnsaved { editingExam = it } },
                    onCreateExam = { guardUnsaved { showCreateExamDialog = true } }
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
                        val effMax = ResultCalculator.effectiveSubjectMaxMarks(it, snapshot.selectedExam, snapshot.examSubjectConfigsByKey)
                        "${it.name} (Total: ${ResultCalculator.formatMarks(effMax)})"
                    } ?: "No Subjects",
                    items = subjectsForClass,
                    itemLabel = {
                        val effMax = ResultCalculator.effectiveSubjectMaxMarks(it, snapshot.selectedExam, snapshot.examSubjectConfigsByKey)
                        "${it.name} (Total: ${ResultCalculator.formatMarks(effMax)})"
                    },
                    onSelect = { guardUnsaved { selectedSubjectId = it.id } }
                )
                if (selectedSubject != null && snapshot.selectedExam != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EducatorsBlueSurface,
                        border = BorderStroke(1.dp, EducatorsBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${selectedSubject.name} • Total: ${ResultCalculator.formatMarks(effectiveMaxMarks)}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = EducatorsNavy
                                        )
                                        if (activeSubjectConfig != null) {
                                            StatusPill(text = "Custom", isPositive = true)
                                        }
                                    }
                                    Text(
                                        text = "Passing (40%): ${ResultCalculator.formatMarks(effectivePassMarks)} • ${snapshot.selectedExam.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showEditSubjectTotalDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 42.dp)
                                        .testTag("edit_subject_total_marks_btn")
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit Total Marks")
                                }
                            }
                            if (examComponents.isNotEmpty()) {
                                val partsSummary = examComponents.joinToString(" + ") { comp ->
                                    if (comp.isCarryForward()) {
                                        "${comp.name} [${ResultCalculator.formatMarks(comp.maxMarks)} Auto]"
                                    } else {
                                        "${comp.name} (${ResultCalculator.formatMarks(comp.maxMarks)})"
                                    }
                                }
                                Text(
                                    text = "Parts: $partsSummary",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = EducatorsBlue
                                )
                            }
                        }
                    }
                }
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

        if (snapshot.visibleClasses.isEmpty()) {
            val sharedCampus = snapshot.accessibleCampuses.firstOrNull {
                it.id != snapshot.campus.id && it.ownerId != viewModel.currentUserId
            }
            EmptyStateCard(
                icon = Icons.Default.Edit,
                title = if (sharedCampus != null) {
                    "Switch to Shared School Portal"
                } else if (snapshot.effectiveRole == StaffRole.TEACHER) {
                    "No Classes Assigned Yet"
                } else {
                    "No Classes Configured"
                },
                description = if (sharedCampus != null) {
                    "You are currently viewing an empty workspace. Switch to ${sharedCampus.schoolName} (${sharedCampus.campusName}) to view your assigned classes and students."
                } else if (snapshot.effectiveRole == StaffRole.TEACHER) {
                    "Ask your campus Admin to open Settings → Staff Accounts, tap Edit next to your name, and assign your classes."
                } else {
                    "Create classes and sections in Class Management first."
                },
                actionLabel = sharedCampus?.let { "Switch to ${it.campusName}" },
                onAction = sharedCampus?.let { camp ->
                    {
                        viewModel.switchCampus(
                            camp.id,
                            "${camp.schoolName} (${camp.campusName})"
                        )
                    }
                }
            )
        } else if (selectedSubject == null || studentsInSection.isEmpty()) {
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
                                    text = st.rollNumber.takeIf { it.isNotBlank() }?.let { "#$it" }
                                        ?: st.name.split(" ").firstOrNull()?.take(8).orEmpty(),
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
                                val stuDetails = buildList {
                                    currentStudent.rollNumber.takeIf { it.isNotBlank() }?.let { add("Roll: $it") }
                                    currentStudent.fatherName.takeIf { it.isNotBlank() && it != "-" }?.let { add("S/O $it") }
                                }
                                if (stuDetails.isNotEmpty()) {
                                    Text(
                                        text = stuDetails.joinToString("  •  "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                    Text("Maximum / Passing (40%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${ResultCalculator.formatMarks(effectiveMaxMarks)} / ${ResultCalculator.formatMarks(effectivePassMarks)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EducatorsBlue
                                    )
                                }
                            }
                        }

                        if (examComponents.isEmpty()) {
                            OutlinedTextField(
                                value = marksInput,
                                onValueChange = {
                                    marksInput = it
                                    validationError = null
                                },
                                enabled = isEditingExisting,
                                label = { Text("Enter Obtained Marks (0 - ${ResultCalculator.formatMarks(effectiveMaxMarks)})") },
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
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (linkedComponents.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = EmeraldPassContainer.copy(alpha = 0.7f),
                                        border = BorderStroke(1.dp, EmeraldPass.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Auto-Added From Previous Terms / Assessments",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = EmeraldPass
                                            )
                                            linkedComponents.forEach { comp ->
                                                val srcName = allExamsById[comp.sourceExamId]?.name ?: "Previous Term"
                                                val carried = currentCarryForwardMarks[comp.id]
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${comp.name} (from $srcName)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = EducatorsNavy
                                                    )
                                                    Text(
                                                        text = if (carried != null) {
                                                            "${ResultCalculator.formatMarks(carried)} / ${ResultCalculator.formatMarks(comp.maxMarks)}"
                                                        } else {
                                                            "Pending / ${ResultCalculator.formatMarks(comp.maxMarks)}"
                                                        },
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (carried != null) EmeraldPass else AmberPending
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (manualComponents.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = EducatorsBlueSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "All components in this exam are carried forward automatically from previous terms. No manual marks entry is required.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EducatorsNavy,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    manualComponents.forEachIndexed { cIdx, comp ->
                                        val isLast = cIdx == manualComponents.lastIndex
                                        OutlinedTextField(
                                            value = componentDrafts[comp.id].orEmpty(),
                                            onValueChange = { txt ->
                                                componentDrafts[comp.id] = txt
                                                validationError = null
                                            },
                                            enabled = isEditingExisting,
                                            label = { Text("${comp.name} (Max ${ResultCalculator.formatMarks(comp.maxMarks)}, blank = -)") },
                                            placeholder = { Text("-") },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = if (snapshot.campus.allowDecimalMarks) KeyboardType.Decimal else KeyboardType.Number,
                                                imeAction = if (isLast) ImeAction.Done else ImeAction.Next
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = { if (isLast) performSave(advanceToNext = true) }
                                            ),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("seq_comp_${comp.id}")
                                        )
                                    }
                                }
                                if (validationError != null) {
                                    Text(
                                        text = validationError ?: "",
                                        color = CrimsonFail,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Live Preview Calculation (includes carry-forward marks + 40% pass rule)
                        val previewVal: Double? = if (examComponents.isEmpty()) {
                            marksInput.trim().toDoubleOrNull()
                        } else {
                            val enteredManual = manualComponents.mapNotNull { componentDrafts[it.id]?.trim()?.toDoubleOrNull() }
                            val carriedSum = currentCarryForwardMarks.values.sum()
                            if (enteredManual.isNotEmpty() || (manualComponents.isEmpty() && currentCarryForwardMarks.isNotEmpty())) {
                                enteredManual.sum() + carriedSum
                            } else {
                                null
                            }
                        }
                        if (previewVal != null && previewVal in 0.0..(effectiveMaxMarks + 0.01)) {
                            val previewResult = ResultCalculator.calculateSubjectResult(
                                subject = selectedSubject,
                                markEntry = MarkEntry(obtainedMarks = previewVal.coerceAtMost(effectiveMaxMarks)),
                                gradingRules = activeRules,
                                effectiveMaxMarks = effectiveMaxMarks
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
                                    text = "Total: ${ResultCalculator.formatMarks(previewVal)}/${ResultCalculator.formatMarks(effectiveMaxMarks)} • ${ResultCalculator.formatPercentage(previewResult.percentage ?: 0.0)}",
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
                        val canSaveManual = examComponents.isEmpty() || manualComponents.isNotEmpty()
                        if (canSaveManual) {
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
                TableMarksEntrySection(
                    snapshot = snapshot,
                    students = studentsInSection,
                    subject = selectedSubject,
                    examComponents = examComponents,
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
            defaultSortOrder = (snapshot.examinations.size + 1).toLong(),
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

    if (showEditSubjectTotalDialog && selectedSubject != null && snapshot.selectedExam != null) {
        SubjectExamMarksEditorDialog(
            subject = selectedSubject,
            className = snapshot.classesById[selectedClassId]?.name.orEmpty(),
            examination = snapshot.selectedExam,
            effectiveComponents = examComponents,
            currentEffectiveMaxMarks = effectiveMaxMarks,
            hasCustomOverride = activeSubjectConfig != null,
            onDismiss = { showEditSubjectTotalDialog = false },
            onSave = { newTotalMax, newCompMap ->
                viewModel.saveExamSubjectConfig(
                    campus = snapshot.campus,
                    examId = snapshot.selectedExam.id,
                    examName = snapshot.selectedExam.name,
                    classId = selectedClassId,
                    className = snapshot.classesById[selectedClassId]?.name.orEmpty(),
                    subject = selectedSubject,
                    totalMaxMarks = newTotalMax,
                    componentMaxMarks = newCompMap,
                    actorName = snapshot.currentActorName,
                    actorEmail = snapshot.currentActorEmail,
                    actorRole = snapshot.effectiveRole
                )
                showEditSubjectTotalDialog = false
            },
            onReset = {
                viewModel.resetExamSubjectConfig(
                    campus = snapshot.campus,
                    examId = snapshot.selectedExam.id,
                    examName = snapshot.selectedExam.name,
                    classId = selectedClassId,
                    className = snapshot.classesById[selectedClassId]?.name.orEmpty(),
                    subject = selectedSubject,
                    actorName = snapshot.currentActorName,
                    actorEmail = snapshot.currentActorEmail,
                    actorRole = snapshot.effectiveRole
                )
                showEditSubjectTotalDialog = false
            }
        )
    }
}

@Composable
private fun SubjectExamMarksEditorDialog(
    subject: Subject,
    className: String,
    examination: Examination,
    effectiveComponents: List<ExamComponent>,
    currentEffectiveMaxMarks: Double,
    hasCustomOverride: Boolean,
    onDismiss: () -> Unit,
    onSave: (totalMaxMarks: Double, componentMaxMarks: Map<String, Double>) -> Unit,
    onReset: () -> Unit
) {
    val manualParts = remember(effectiveComponents) { effectiveComponents.filter { !it.isCarryForward() } }
    val linkedParts = remember(effectiveComponents) { effectiveComponents.filter { it.isCarryForward() } }
    val hasComponents = effectiveComponents.isNotEmpty()

    var totalText by remember(subject.id, examination.id, currentEffectiveMaxMarks) {
        mutableStateOf(ResultCalculator.formatMarks(currentEffectiveMaxMarks))
    }
    val compTexts = remember(subject.id, examination.id, effectiveComponents) {
        mutableStateMapOf<String, String>().apply {
            effectiveComponents.forEach { comp ->
                put(comp.id, ResultCalculator.formatMarks(comp.maxMarks))
            }
        }
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val previewTotal: Double = if (!hasComponents) {
        totalText.trim().toDoubleOrNull() ?: 0.0
    } else {
        effectiveComponents.sumOf { comp ->
            if (comp.isCarryForward()) {
                comp.maxMarks
            } else {
                compTexts[comp.id]?.trim()?.toDoubleOrNull() ?: 0.0
            }
        }
    }
    val previewPass = kotlin.math.round((previewTotal * 0.40) * 100.0) / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Edit Assessment Total Marks", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${subject.name} • $className • ${examination.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EducatorsBlue
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (hasComponents) {
                        "Set custom maximum marks for each assessment / quiz part of ${subject.name} in ${examination.name}. Other subjects remain unaffected."
                    } else {
                        "Set custom total marks for ${subject.name} in ${examination.name} (default: ${ResultCalculator.formatMarks(subject.maxMarks)}). Passing is automatically set to 40%."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasComponents) {
                    OutlinedTextField(
                        value = totalText,
                        onValueChange = {
                            totalText = it
                            errorMsg = null
                        },
                        label = { Text("Total Marks for ${subject.name}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subject_total_marks_input")
                    )
                } else {
                    if (linkedParts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldPassContainer.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Carried Forward From Previous Terms (Locked)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldPass
                                )
                                linkedParts.forEach { comp ->
                                    Text(
                                        text = "${comp.name}: ${ResultCalculator.formatMarks(comp.maxMarks)} marks (auto-calculated)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EducatorsNavy
                                    )
                                }
                            }
                        }
                    }

                    manualParts.forEach { comp ->
                        OutlinedTextField(
                            value = compTexts[comp.id].orEmpty(),
                            onValueChange = {
                                compTexts[comp.id] = it
                                errorMsg = null
                            },
                            label = { Text("${comp.name} — Max Marks") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("subject_comp_max_${comp.id}")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EducatorsBlueSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Subject Total Marks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = ResultCalculator.formatMarks(previewTotal),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = EducatorsNavy
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Passing Marks (40%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = ResultCalculator.formatMarks(previewPass),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldPass
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonFail
                    )
                }

                if (hasCustomOverride) {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .testTag("reset_subject_total_marks_btn")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset to Default Marks")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!hasComponents) {
                        val parsed = totalText.trim().toDoubleOrNull()
                        if (parsed == null || parsed <= 0.0 || parsed > 1000.0) {
                            errorMsg = "Enter a valid total between 1 and 1000."
                            return@Button
                        }
                        onSave(parsed, emptyMap())
                    } else {
                        val outMap = linkedMapOf<String, Double>()
                        linkedParts.forEach { outMap[it.id] = it.maxMarks }
                        for (comp in manualParts) {
                            val parsed = compTexts[comp.id]?.trim()?.toDoubleOrNull()
                            if (parsed == null || parsed <= 0.0 || parsed > 1000.0) {
                                errorMsg = "Enter a valid max marks value for ${comp.name}."
                                return@Button
                            }
                            outMap[comp.id] = parsed
                        }
                        val sumTotal = outMap.values.sum()
                        if (sumTotal <= 0.0 || sumTotal > 1000.0) {
                            errorMsg = "Combined total must be between 1 and 1000."
                            return@Button
                        }
                        onSave(sumTotal, outMap)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                modifier = Modifier.testTag("save_subject_total_marks_btn")
            ) {
                Text("Save Total Marks")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TableMarksEntrySection(
    snapshot: SchoolSnapshot,
    students: List<Student>,
    subject: Subject,
    examComponents: List<ExamComponent>,
    marksByStudentId: Map<String, MarkEntry>,
    viewModel: MainViewModel
) {
    val scope = rememberCoroutineScope()
    val manualComponents = remember(examComponents) { examComponents.filter { !it.isCarryForward() } }
    val linkedComponents = remember(examComponents) { examComponents.filter { it.isCarryForward() } }
    val allExamsById = remember(snapshot.examinations) { snapshot.examinations.associateBy { it.id } }
    val allSessionMarksByKey = remember(snapshot.sessionMarks) {
        snapshot.sessionMarks.associateBy { "${it.examId}_${it.studentId}_${it.subjectId}" }
    }
    val effectiveMaxMarks = remember(subject, snapshot.selectedExam, snapshot.examSubjectConfigsByKey) {
        ResultCalculator.effectiveSubjectMaxMarks(subject, snapshot.selectedExam, snapshot.examSubjectConfigsByKey)
    }

    val draftValues = remember(students, subject.id, marksByStudentId) {
        mutableStateMapOf<String, String>().apply {
            students.forEach { st ->
                put(st.id, marksByStudentId[st.id]?.let { ResultCalculator.formatMarks(it.obtainedMarks) } ?: "")
            }
        }
    }
    val compDraftValues = remember(students, subject.id, marksByStudentId, manualComponents) {
        mutableStateMapOf<String, String>().apply {
            students.forEach { st ->
                val mark = marksByStudentId[st.id]
                manualComponents.forEach { comp ->
                    val key = "${st.id}_${comp.id}"
                    put(key, mark?.componentMarks?.get(comp.id)?.let { ResultCalculator.formatMarks(it) } ?: "")
                }
            }
        }
    }
    val activeRules = remember(snapshot.gradingRules, snapshot.selectedExam) {
        ResultCalculator.resolveActiveGradingRules(snapshot.gradingRules, snapshot.selectedExam)
    }
    var isSavingAll by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (examComponents.isEmpty() || manualComponents.isNotEmpty()) {
            Button(
                onClick = {
                    scope.launch {
                        isSavingAll = true
                        var savedCount = 0
                        var errorCount = 0
                        students.forEach { st ->
                            if (examComponents.isEmpty()) {
                                val raw = draftValues[st.id]?.trim().orEmpty()
                                if (raw.isNotEmpty()) {
                                    val num = raw.toDoubleOrNull()
                                    val validDecimal = snapshot.campus.allowDecimalMarks || (num != null && num % 1.0 == 0.0)
                                    if (num != null && num in 0.0..effectiveMaxMarks && validDecimal) {
                                        val res = viewModel.saveMarkEntrySuspend(
                                            campus = snapshot.campus,
                                            sessionId = snapshot.selectedSessionId,
                                            examId = snapshot.selectedExamId,
                                            student = st,
                                            subject = subject,
                                            obtainedMarks = num,
                                            existingMark = marksByStudentId[st.id],
                                            maxAllowedMarks = effectiveMaxMarks,
                                            actorName = snapshot.currentActorName,
                                            actorEmail = snapshot.currentActorEmail,
                                            actorRole = snapshot.effectiveRole,
                                            className = snapshot.classesById[st.classId]?.name.orEmpty(),
                                            examName = snapshot.selectedExam?.name.orEmpty()
                                        )
                                        if (res.isSuccess) savedCount++ else errorCount++
                                    } else {
                                        errorCount++
                                    }
                                }
                            } else {
                                val parsedMap = linkedMapOf<String, Double>()
                                var rowInvalid = false
                                var anyEntered = false
                                for (comp in manualComponents) {
                                    val raw = compDraftValues["${st.id}_${comp.id}"]?.trim().orEmpty()
                                    if (raw.isNotEmpty()) {
                                        anyEntered = true
                                        val v = raw.toDoubleOrNull()
                                        val validDecimal = snapshot.campus.allowDecimalMarks || (v != null && v % 1.0 == 0.0)
                                        if (v == null || v < 0.0 || v > comp.maxMarks || !validDecimal) {
                                            rowInvalid = true
                                        } else {
                                            parsedMap[comp.id] = v
                                        }
                                    }
                                }
                                if (anyEntered) {
                                    val carriedSum = if (linkedComponents.isNotEmpty()) {
                                        ResultCalculator.resolveCarryForwardComponentMarks(
                                            studentId = st.id,
                                            subject = subject,
                                            examination = snapshot.selectedExam,
                                            allExaminationsById = allExamsById,
                                            allSessionMarksByKey = allSessionMarksByKey,
                                            subjectConfigsByKey = snapshot.examSubjectConfigsByKey
                                        ).values.sum()
                                    } else {
                                        0.0
                                    }
                                    val total = (parsedMap.values.sum() + carriedSum).coerceAtMost(effectiveMaxMarks)
                                    if (!rowInvalid && (parsedMap.values.sum() + carriedSum) <= effectiveMaxMarks + 0.01) {
                                        val res = viewModel.saveMarkEntrySuspend(
                                            campus = snapshot.campus,
                                            sessionId = snapshot.selectedSessionId,
                                            examId = snapshot.selectedExamId,
                                            student = st,
                                            subject = subject,
                                            obtainedMarks = total,
                                            existingMark = marksByStudentId[st.id],
                                            componentMarks = parsedMap,
                                            maxAllowedMarks = effectiveMaxMarks,
                                            actorName = snapshot.currentActorName,
                                            actorEmail = snapshot.currentActorEmail,
                                            actorRole = snapshot.effectiveRole,
                                            className = snapshot.classesById[st.classId]?.name.orEmpty(),
                                            examName = snapshot.selectedExam?.name.orEmpty()
                                        )
                                        if (res.isSuccess) savedCount++ else errorCount++
                                    } else {
                                        errorCount++
                                    }
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
        }

        students.forEach { st ->
            val labelPrefix = st.rollNumber.takeIf { it.isNotBlank() }?.let { "$it. " } ?: ""
            if (examComponents.isEmpty()) {
                val raw = draftValues[st.id].orEmpty()
                val num = raw.trim().toDoubleOrNull()
                val invalid = raw.isNotBlank() && (num == null || num < 0.0 || num > effectiveMaxMarks ||
                    (!snapshot.campus.allowDecimalMarks && num % 1.0 != 0.0))
                val preview = if (!invalid && num != null) {
                    ResultCalculator.calculateSubjectResult(
                        subject = subject,
                        markEntry = MarkEntry(obtainedMarks = num),
                        gradingRules = activeRules,
                        effectiveMaxMarks = effectiveMaxMarks
                    )
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
                                text = "$labelPrefix${st.name}",
                                style = MaterialTheme.typography.titleSmall,
                                color = EducatorsNavy
                            )
                            Text(
                                text = if (preview != null) {
                                    "${ResultCalculator.formatPercentage(preview.percentage ?: 0.0)} • ${preview.grade}"
                                } else if (invalid) {
                                    "Max: ${ResultCalculator.formatMarks(effectiveMaxMarks)}"
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
                            placeholder = { Text("/${ResultCalculator.formatMarks(effectiveMaxMarks)}") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (snapshot.campus.allowDecimalMarks) KeyboardType.Decimal else KeyboardType.Number
                            ),
                            modifier = Modifier.width(105.dp)
                        )
                    }
                }
            } else {
                val stuCarryMap = remember(st.id, subject.id, snapshot.selectedExam, allExamsById, allSessionMarksByKey, snapshot.examSubjectConfigsByKey) {
                    if (linkedComponents.isNotEmpty()) {
                        ResultCalculator.resolveCarryForwardComponentMarks(
                            studentId = st.id,
                            subject = subject,
                            examination = snapshot.selectedExam,
                            allExaminationsById = allExamsById,
                            allSessionMarksByKey = allSessionMarksByKey,
                            subjectConfigsByKey = snapshot.examSubjectConfigsByKey
                        )
                    } else {
                        emptyMap()
                    }
                }
                var rowInvalid = false
                var manualSum = 0.0
                var hasAnyManual = false
                manualComponents.forEach { comp ->
                    val raw = compDraftValues["${st.id}_${comp.id}"].orEmpty()
                    if (raw.isNotBlank()) {
                        hasAnyManual = true
                        val v = raw.trim().toDoubleOrNull()
                        val validDecimal = snapshot.campus.allowDecimalMarks || (v != null && v % 1.0 == 0.0)
                        if (v == null || v < 0.0 || v > comp.maxMarks || !validDecimal) {
                            rowInvalid = true
                        } else {
                            manualSum += v
                        }
                    }
                }
                val totalSum = manualSum + stuCarryMap.values.sum()
                if (totalSum > effectiveMaxMarks + 0.01) rowInvalid = true
                val hasScore = hasAnyManual || (manualComponents.isEmpty() && stuCarryMap.isNotEmpty())
                val preview = if (!rowInvalid && hasScore) {
                    ResultCalculator.calculateSubjectResult(
                        subject = subject,
                        markEntry = MarkEntry(obtainedMarks = totalSum.coerceAtMost(effectiveMaxMarks)),
                        gradingRules = activeRules,
                        effectiveMaxMarks = effectiveMaxMarks
                    )
                } else null

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (rowInvalid) CrimsonFail else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$labelPrefix${st.name}",
                                style = MaterialTheme.typography.titleSmall,
                                color = EducatorsNavy
                            )
                            Text(
                                text = if (preview != null) {
                                    "Total: ${ResultCalculator.formatMarks(totalSum)}/${ResultCalculator.formatMarks(effectiveMaxMarks)} • ${ResultCalculator.formatPercentage(preview.percentage ?: 0.0)} (${preview.grade})"
                                } else if (rowInvalid) {
                                    "Invalid component marks"
                                } else {
                                    "Pending"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (rowInvalid) CrimsonFail else EducatorsBlue
                            )
                        }
                        if (linkedComponents.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                linkedComponents.forEach { comp ->
                                    val carried = stuCarryMap[comp.id]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EmeraldPassContainer.copy(alpha = 0.7f),
                                        border = BorderStroke(1.dp, EmeraldPass.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "${comp.name}: ${carried?.let { ResultCalculator.formatMarks(it) } ?: "-"}/${ResultCalculator.formatMarks(comp.maxMarks)} (Auto)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = EmeraldPass,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (manualComponents.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                manualComponents.forEach { comp ->
                                    val key = "${st.id}_${comp.id}"
                                    OutlinedTextField(
                                        value = compDraftValues[key].orEmpty(),
                                        onValueChange = { compDraftValues[key] = it },
                                        label = { Text("${comp.name} (${ResultCalculator.formatMarks(comp.maxMarks)})", maxLines = 1) },
                                        placeholder = { Text("-") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = if (snapshot.campus.allowDecimalMarks) KeyboardType.Decimal else KeyboardType.Number
                                        ),
                                        modifier = Modifier.width(125.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
