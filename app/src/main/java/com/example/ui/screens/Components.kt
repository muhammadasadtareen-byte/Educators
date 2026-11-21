package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.AcademicSession
import com.example.data.model.ExamComponent
import com.example.data.model.ExamComponentPresets
import com.example.data.model.ExamType
import com.example.data.model.Examination
import com.example.data.model.StaffRole
import com.example.domain.ResultCalculator
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
import com.example.ui.theme.AmberPendingContainer
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.CrimsonFailContainer
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsBlueSurface
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import com.example.ui.theme.EmeraldPassContainer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@Composable
fun <T> SelectorDropdown(
    label: String,
    selectedText: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    footerActions: List<Pair<String, () -> Unit>> = emptyList()
) {
    var expanded by remember { mutableStateOf(false) }
    val hasMenuEntries = items.isNotEmpty() || footerActions.isNotEmpty()
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { if (enabled && hasMenuEntries) expanded = true },
            enabled = enabled && hasMenuEntries,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = selectedText.ifBlank { "Select" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select $label"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    }
                )
            }
            if (footerActions.isNotEmpty()) {
                if (items.isNotEmpty()) {
                    HorizontalDivider()
                }
                footerActions.forEach { (actionLabel, action) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = EducatorsBlue
                            )
                        },
                        onClick = {
                            expanded = false
                            action()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GradePill(grade: String, passed: Boolean?, incomplete: Boolean = false) {
    val (bg, fg) = when {
        incomplete -> AmberPendingContainer to AmberPending
        passed == true -> EmeraldPassContainer to EmeraldPass
        passed == false -> CrimsonFailContainer to CrimsonFail
        else -> EducatorsBlueSurface to EducatorsNavy
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = grade,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusPill(text: String, isPositive: Boolean, isWarning: Boolean = false) {
    val (bg, fg) = when {
        isWarning -> AmberPendingContainer to AmberPending
        isPositive -> EmeraldPassContainer to EmeraldPass
        else -> CrimsonFailContainer to CrimsonFail
    }
    Surface(shape = CircleShape, color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(EducatorsBlueSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = EducatorsBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

data class LegendBadge(
    val label: String,
    val dotColor: Color,
    val description: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabLegendCard(
    tabTitle: String,
    summary: String,
    items: List<LegendBadge>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tab_legend_${tabTitle.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Legend",
                        tint = EducatorsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Legend • $tabTitle",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide legend" else "Show legend",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (items.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items.forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(badge.dotColor)
                                        )
                                        Text(
                                            text = "${badge.label}: ${badge.description}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
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
}

@Composable
fun CampusContextBar(
    snapshot: SchoolSnapshot,
    onSelectSession: (String) -> Unit,
    onSelectExam: (String) -> Unit,
    onEditSelectedExam: ((Examination) -> Unit)? = null,
    onCreateExam: (() -> Unit)? = null
) {
    val selectedExam = snapshot.selectedExam
    val isAdmin = snapshot.effectiveRole == StaffRole.ADMIN
    val examFooterActions = remember(selectedExam, isAdmin, onEditSelectedExam, onCreateExam) {
        buildList {
            if (isAdmin) {
                if (selectedExam != null && onEditSelectedExam != null) {
                    add("✎ Edit '${selectedExam.name}'" to { onEditSelectedExam(selectedExam) })
                }
                if (onCreateExam != null) {
                    add("+ Create New Examination" to onCreateExam)
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectorDropdown(
            label = "Academic Session",
            selectedText = snapshot.selectedSession?.name ?: "Select Session",
            items = snapshot.sessions,
            itemLabel = { it.name },
            onSelect = { onSelectSession(it.id) },
            modifier = Modifier.weight(1f)
        )
        SelectorDropdown(
            label = "Examination",
            selectedText = selectedExam?.name ?: "Select Exam",
            items = snapshot.examinations,
            itemLabel = { exam ->
                if (exam.components.isEmpty()) exam.name
                else "${exam.name} (${exam.components.size} parts)"
            },
            onSelect = { onSelectExam(it.id) },
            footerActions = examFooterActions,
            modifier = Modifier.weight(1.1f)
        )
        if (onEditSelectedExam != null && isAdmin && selectedExam != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.heightIn(min = 52.dp)
            ) {
                IconButton(
                    onClick = { onEditSelectedExam(selectedExam) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_selected_exam_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Examination",
                        tint = EducatorsBlue
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamEditorDialog(
    existing: Examination?,
    sessions: List<AcademicSession>,
    defaultSessionId: String,
    defaultSortOrder: Long,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        examType: ExamType,
        sessionId: String,
        useCustomGrading: Boolean,
        sortOrder: Long,
        components: List<ExamComponent>
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    availableExams: List<Examination> = emptyList()
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var examType by remember(existing) { mutableStateOf(existing?.parsedExamType() ?: ExamType.MIDTERM) }
    var sessionId by remember(existing, defaultSessionId) {
        mutableStateOf(existing?.sessionId?.takeIf { it.isNotBlank() } ?: defaultSessionId)
    }
    var customGrading by remember(existing) { mutableStateOf(existing?.useCustomGrading ?: false) }
    var sortOrderText by remember(existing, defaultSortOrder) {
        mutableStateOf((existing?.sortOrder ?: defaultSortOrder).toString())
    }
    val components = remember(existing) {
        mutableStateListOf<ExamComponent>().apply {
            existing?.components?.let { addAll(it) }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Examination" else "Edit Examination") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Exam Name *") },
                    placeholder = { Text("e.g. ASSESSMENT TASK-I (TERM II), Mid Year Examination") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_name_input")
                )
                SelectorDropdown(
                    label = "Exam Category",
                    selectedText = examType.label,
                    items = ExamType.entries,
                    itemLabel = { it.label },
                    onSelect = {
                        examType = it
                        if (name.isBlank()) name = it.label
                    }
                )
                if (sessions.isNotEmpty()) {
                    SelectorDropdown(
                        label = "Academic Session",
                        selectedText = sessions.firstOrNull { it.id == sessionId }?.name ?: "Select Session",
                        items = sessions,
                        itemLabel = { it.name },
                        onSelect = { sessionId = it.id }
                    )
                }
                OutlinedTextField(
                    value = sortOrderText,
                    onValueChange = { sortOrderText = it },
                    label = { Text("Display Order (1, 2, 3...)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Custom Grading Rules", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Overrides default campus grading scale",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = customGrading, onCheckedChange = { customGrading = it })
                }

                HorizontalDivider()

                // Assessment Components & Linked Terms Section
                val linkableExams = remember(availableExams, existing?.id, sessionId) {
                    availableExams.filter {
                        it.id != existing?.id && (sessionId.isBlank() || it.sessionId == sessionId)
                    }.sortedBy { it.sortOrder }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assessments, Quizzes & Linked Term Weights",
                        style = MaterialTheme.typography.titleSmall,
                        color = EducatorsNavy
                    )
                    Text(
                        text = "Join any quizzes/assessments with different total marks, or link earlier terms/assessments so a student's percentage automatically carries forward into this exam. Passing is 40%.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = components.isEmpty(),
                            onClick = { components.clear() },
                            label = { Text("Single Total") }
                        )
                        FilterChip(
                            selected = components.size == 2 && components.firstOrNull()?.id == "task_01",
                            onClick = {
                                components.clear()
                                components.addAll(ExamComponentPresets.consolidatedTaskPreset())
                            },
                            label = { Text("Task (30) + Quiz (20)") }
                        )
                        FilterChip(
                            selected = components.size == 4 && components.firstOrNull()?.id == "classwork",
                            onClick = {
                                components.clear()
                                components.addAll(ExamComponentPresets.midYearTermPreset())
                            },
                            label = { Text("Mid Year (10+10+15+25)") }
                        )
                    }

                    components.forEachIndexed { idx, comp ->
                        val isLinked = comp.isCarryForward()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLinked) EmeraldPassContainer.copy(alpha = 0.55f) else EducatorsBlueSurface.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (isLinked) EmeraldPass.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = if (isLinked) "Auto Carry-Forward from Term" else "Manual Assessment / Quiz",
                                        isPositive = isLinked,
                                        isWarning = !isLinked
                                    )
                                    IconButton(
                                        onClick = { components.removeAt(idx) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove component", tint = CrimsonFail)
                                    }
                                }
                                if (isLinked && linkableExams.isNotEmpty()) {
                                    val pickedSource = linkableExams.firstOrNull { it.id == comp.sourceExamId }
                                    SelectorDropdown(
                                        label = "Source Term / Assessment",
                                        selectedText = pickedSource?.name ?: "Select Term / Assessment",
                                        items = linkableExams,
                                        itemLabel = { it.name },
                                        onSelect = { src ->
                                            val defaultLbl = "${src.name} (%)"
                                            components[idx] = comp.copy(
                                                sourceExamId = src.id,
                                                name = if (comp.name.isBlank() || comp.name.endsWith("(%)")) defaultLbl else comp.name
                                            )
                                            errorMsg = null
                                        }
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = comp.name,
                                        onValueChange = { newName ->
                                            components[idx] = comp.copy(name = newName)
                                            errorMsg = null
                                        },
                                        label = { Text(if (isLinked) "Column Label" else "Assessment / Quiz Name") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.45f)
                                    )
                                    OutlinedTextField(
                                        value = if (comp.maxMarks == 0.0) "" else ResultCalculator.formatMarks(comp.maxMarks),
                                        onValueChange = { rawMax ->
                                            val parsed = rawMax.trim().toDoubleOrNull() ?: 0.0
                                            components[idx] = comp.copy(
                                                maxMarks = parsed,
                                                weightPercentage = if (isLinked) parsed else 0.0
                                            )
                                            errorMsg = null
                                        },
                                        label = { Text(if (isLinked) "Weight / Marks" else "Total Marks") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.85f)
                                    )
                                }
                                if (isLinked) {
                                    val srcName = linkableExams.firstOrNull { it.id == comp.sourceExamId }?.name ?: "source term"
                                    val sample80 = ResultCalculator.formatMarks(comp.maxMarks * 0.8)
                                    Text(
                                        text = "Auto-calculates student's % in $srcName × ${ResultCalculator.formatMarks(comp.maxMarks)} (e.g. 80% → $sample80/${ResultCalculator.formatMarks(comp.maxMarks)}).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EducatorsNavy
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                components.add(
                                    ExamComponent(
                                        id = "comp_${System.nanoTime()}",
                                        name = "Quiz / Assessment ${components.size + 1}",
                                        maxMarks = 10.0
                                    )
                                )
                            },
                            modifier = Modifier.heightIn(min = 42.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Assessment / Quiz")
                        }
                        if (linkableExams.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val defaultSrc = linkableExams.firstOrNull { src ->
                                        components.none { it.sourceExamId == src.id }
                                    } ?: linkableExams.first()
                                    components.add(
                                        ExamComponent(
                                            id = "term_${System.nanoTime()}",
                                            name = "${defaultSrc.name} (%)",
                                            maxMarks = 20.0,
                                            sourceExamId = defaultSrc.id,
                                            weightPercentage = 20.0
                                        )
                                    )
                                },
                                modifier = Modifier.heightIn(min = 42.dp).testTag("add_linked_term_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Previous Term (%)")
                            }
                        }
                    }
                    if (components.isNotEmpty()) {
                        val totalSum = components.sumOf { it.maxMarks }
                        val passAt40 = totalSum * 0.40
                        Text(
                            text = "Exam Total: ${ResultCalculator.formatMarks(totalSum)} marks  •  Passing (40%): ${ResultCalculator.formatMarks(passAt40)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = EducatorsNavy
                        )
                    }
                }

                if (existing != null && canDelete && onDelete != null) {
                    if (!confirmDelete) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonFail),
                            border = BorderStroke(1.dp, CrimsonFail.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Examination")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CrimsonFailContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Confirm delete?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CrimsonFail
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { confirmDelete = false }) { Text("No") }
                                    Button(
                                        onClick = onDelete,
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonFail)
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
                if (errorMsg != null) {
                    Text(errorMsg ?: "", color = CrimsonFail, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val parsedOrder = sortOrderText.trim().toLongOrNull() ?: defaultSortOrder
                    val invalidComponent = components.any { it.name.trim().isBlank() || it.maxMarks <= 0.0 }
                    when {
                        trimmedName.isBlank() -> errorMsg = "Exam Name is required."
                        sessionId.isBlank() -> errorMsg = "Please select an Academic Session."
                        invalidComponent -> errorMsg = "Every assessment component needs a name and Max Marks > 0."
                        else -> {
                            val cleanedComponents = components.mapIndexed { idx, c ->
                                ExamComponent(
                                    id = c.id.ifBlank { "comp_${idx + 1}" },
                                    name = c.name.trim(),
                                    maxMarks = c.maxMarks,
                                    sourceExamId = c.sourceExamId.trim(),
                                    weightPercentage = if (c.isCarryForward()) c.maxMarks else 0.0
                                )
                            }
                            onSave(
                                trimmedName,
                                examType,
                                sessionId,
                                customGrading,
                                parsedOrder.coerceIn(0L, 1000L),
                                cleanedComponents
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("save_exam_btn")
            ) {
                Text(if (existing == null) "Create" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SchoolLogoBadge(
    logoBase64: String?,
    sizeDp: Int = 44,
    paddingDp: Int = 3
) {
    val bmp = remember(logoBase64) {
        if (logoBase64.isNullOrBlank()) null
        else try {
            val bytes = Base64.decode(logoBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
    Surface(
        shape = CircleShape,
        color = Color.White,
        modifier = Modifier.size(sizeDp.dp)
    ) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "School logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingDp.dp)
                    .clip(CircleShape)
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_school_crest),
                contentDescription = "School crest",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingDp.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun StudentAvatar(photoBase64: String?, name: String, sizeDp: Int = 44) {
    val bmp = remember(photoBase64) {
        if (photoBase64.isNullOrBlank()) null
        else try {
            val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "$name photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
        )
    } else {
        val initials = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "S" }
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(EducatorsBlueSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = EducatorsNavy
            )
        }
    }
}

object FileExportHelper {
    fun writeBytesToCache(context: Context, fileName: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun viewPdfFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            shareFile(context, file, "application/pdf", "Share / Open PDF")
        }
    }

    fun uriToCompressedBase64(context: Context, uri: Uri, maxPx: Int = 256): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return null
                val scale = minOf(maxPx.toFloat() / raw.width, maxPx.toFloat() / raw.height, 1f)
                val targetW = (raw.width * scale).toInt().coerceAtLeast(1)
                val targetH = (raw.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 76, baos)
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }
}
