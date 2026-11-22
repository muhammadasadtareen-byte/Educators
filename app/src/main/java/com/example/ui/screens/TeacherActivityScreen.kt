package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.data.model.StaffRole
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeacherActivityScreen(
    snapshot: SchoolSnapshot
) {
    if (snapshot.effectiveRole != StaffRole.ADMIN) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Default.Lock,
                title = "Administrator Access Only",
                description = "Only campus Administrators can monitor teacher activity logs."
            )
        }
        return
    }

    var selectedActionFilter by remember { mutableStateOf("ALL") } // ALL, MARKS, TOTALS, STUDENTS, EXAMS
    var selectedTeacherUid by remember { mutableStateOf("ALL") }

    val teachers = remember(snapshot.staff) {
        snapshot.staff.filter { it.parsedRole() == StaffRole.TEACHER }
    }

    val logs = snapshot.activityLogs
    val marksSavedCount = remember(logs) { logs.count { it.actionType == "MARKS_SAVED" || it.actionType == "REMARKS_SAVED" } }
    val totalMarksEditedCount = remember(logs) { logs.count { it.actionType == "TOTAL_MARKS_UPDATED" } }
    val studentsChangedCount = remember(logs) { logs.count { it.actionType.startsWith("STUDENT_") } }
    val examsChangedCount = remember(logs) { logs.count { it.actionType.startsWith("EXAM_") } }
    val attendanceSavedCount = remember(logs) { logs.count { it.actionType == "ATTENDANCE_SAVED" } }
    val activeTeacherUids = remember(logs) {
        logs.filter { it.actorRole == StaffRole.TEACHER.name }.map { it.actorUid }.toSet()
    }

    val filteredLogs = remember(logs, selectedActionFilter, selectedTeacherUid) {
        logs.filter { log ->
            val matchesAction = when (selectedActionFilter) {
                "ALL" -> true
                "MARKS" -> log.actionType == "MARKS_SAVED" || log.actionType == "REMARKS_SAVED"
                "TOTALS" -> log.actionType == "TOTAL_MARKS_UPDATED"
                "STUDENTS" -> log.actionType.startsWith("STUDENT_")
                "EXAMS" -> log.actionType.startsWith("EXAM_")
                "ATTENDANCE" -> log.actionType == "ATTENDANCE_SAVED"
                else -> log.actionType == selectedActionFilter
            }
            val matchesTeacher = selectedTeacherUid == "ALL" || log.actorUid == selectedTeacherUid
            matchesAction && matchesTeacher
        }
    }

    val teacherFilterOptions = remember(teachers, logs) {
        val base = mutableListOf("ALL" to "All Staff & Teachers")
        val addedUids = mutableSetOf<String>()
        teachers.forEach { t ->
            val uid = t.userUid.ifBlank { t.id }
            if (uid.isNotBlank() && addedUids.add(uid)) {
                base.add(uid to "${t.displayName} (Teacher)")
            }
        }
        logs.forEach { log ->
            if (log.actorUid.isNotBlank() && addedUids.add(log.actorUid)) {
                val label = log.actorName.ifBlank { log.actorEmail.ifBlank { "Staff Member" } }
                base.add(log.actorUid to "$label (${log.actorRole})")
            }
        }
        base
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TabLegendCard(
                tabTitle = "Teacher Activity Monitor",
                summary = "Real-time audit log of every student added/edited, examination updated, total marks edited, and student marks entered.",
                items = listOf(
                    LegendBadge("Marks", EmeraldPass, "Student marks saved"),
                    LegendBadge("Total Marks", EducatorsBlue, "Assessment totals edited"),
                    LegendBadge("Students & Exams", AmberPending, "Roster or exam updates")
                )
            )
        }

        // KPI Summary Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        title = "Total Actions",
                        value = logs.size.toString(),
                        subtitle = "Logged activities",
                        icon = Icons.Default.History,
                        accentColor = EducatorsNavy,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Active Teachers",
                        value = "${activeTeacherUids.size}/${teachers.size}",
                        subtitle = "With recorded activity",
                        icon = Icons.Default.People,
                        accentColor = EducatorsBlue,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        title = "Marks Entries",
                        value = marksSavedCount.toString(),
                        subtitle = "Saved / updated",
                        icon = Icons.Default.EditNote,
                        accentColor = EmeraldPass,
                        containerColor = EmeraldPassContainer,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Total Marks Edits",
                        value = totalMarksEditedCount.toString(),
                        subtitle = "Subject assessments",
                        icon = Icons.Default.Tune,
                        accentColor = AmberPending,
                        containerColor = AmberPendingContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Per-Teacher Overview Cards
        if (teachers.isNotEmpty()) {
            item {
                Text(
                    text = "Teachers Overview",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(teachers, key = { "teacher_ov_${it.id}" }) { teacher ->
                val uid = teacher.userUid.ifBlank { teacher.id }
                val tLogs = logs.filter { it.actorUid == uid }
                val tMarks = tLogs.count { it.actionType == "MARKS_SAVED" || it.actionType == "REMARKS_SAVED" }
                val tTotals = tLogs.count { it.actionType == "TOTAL_MARKS_UPDATED" }
                val tStudents = tLogs.count { it.actionType.startsWith("STUDENT_") }
                val tExams = tLogs.count { it.actionType.startsWith("EXAM_") }
                val latestTime = tLogs.firstOrNull()?.let { formatActivityTime(it) } ?: "No activity yet"
                val classNames = teacher.assignedClassIds.mapNotNull { snapshot.classesById[it]?.name }
                val isSelected = selectedTeacherUid == uid

                Card(
                    onClick = {
                        selectedTeacherUid = if (isSelected) "ALL" else uid
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) EducatorsBlueSurface else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) EducatorsBlue else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teacher_overview_card_$uid")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = teacher.displayName.ifBlank { teacher.email },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EducatorsNavy
                                )
                                Text(
                                    text = teacher.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                text = "${tLogs.size} Actions",
                                isPositive = tLogs.isNotEmpty(),
                                isWarning = tLogs.isEmpty()
                            )
                        }
                        Text(
                            text = "Assigned Classes: ${classNames.ifEmpty { listOf("None") }.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = EducatorsBlue
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Marks: $tMarks • Totals: $tTotals • Students: $tStudents • Exams: $tExams",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = latestTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Filter Panel
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Filter Activity Feed",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = EducatorsNavy
                    )
                    SelectorDropdown(
                        label = "Filter by Teacher / Staff",
                        selectedText = teacherFilterOptions.firstOrNull { it.first == selectedTeacherUid }?.second
                            ?: "All Staff & Teachers",
                        items = teacherFilterOptions,
                        itemLabel = { it.second },
                        onSelect = { selectedTeacherUid = it.first }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedActionFilter == "ALL",
                            onClick = { selectedActionFilter = "ALL" },
                            label = { Text("All (${logs.size})") },
                            modifier = Modifier.testTag("activity_filter_all")
                        )
                        FilterChip(
                            selected = selectedActionFilter == "MARKS",
                            onClick = { selectedActionFilter = "MARKS" },
                            label = { Text("Marks ($marksSavedCount)") },
                            modifier = Modifier.testTag("activity_filter_marks")
                        )
                        FilterChip(
                            selected = selectedActionFilter == "TOTALS",
                            onClick = { selectedActionFilter = "TOTALS" },
                            label = { Text("Total Marks ($totalMarksEditedCount)") },
                            modifier = Modifier.testTag("activity_filter_totals")
                        )
                        FilterChip(
                            selected = selectedActionFilter == "STUDENTS",
                            onClick = { selectedActionFilter = "STUDENTS" },
                            label = { Text("Students ($studentsChangedCount)") },
                            modifier = Modifier.testTag("activity_filter_students")
                        )
                        FilterChip(
                            selected = selectedActionFilter == "EXAMS",
                            onClick = { selectedActionFilter = "EXAMS" },
                            label = { Text("Exams ($examsChangedCount)") },
                            modifier = Modifier.testTag("activity_filter_exams")
                        )
                        FilterChip(
                            selected = selectedActionFilter == "ATTENDANCE",
                            onClick = { selectedActionFilter = "ATTENDANCE" },
                            label = { Text("Attendance ($attendanceSavedCount)") },
                            modifier = Modifier.testTag("activity_filter_attendance")
                        )
                    }
                }
            }
        }

        // Activity Feed Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Activity Feed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${filteredLogs.size} records",
                    style = MaterialTheme.typography.labelMedium,
                    color = EducatorsBlue
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.History,
                    title = "No Teacher Activity Recorded Yet",
                    description = "Whenever a teacher enters student marks or edits assessment total marks for a subject, every change will appear here with teacher name, class, subject, and timestamp."
                )
            }
        } else {
            items(filteredLogs, key = { it.id.ifBlank { "${it.actorUid}_${it.summary}_${it.createdAt?.seconds}" } }) { log ->
                ActivityLogCard(log = log)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityLogCard(
    log: ActivityLog,
    modifier: Modifier = Modifier
) {
    val (accent, badgeBg, actionLabel, icon) = when (log.actionType) {
        "TOTAL_MARKS_UPDATED" -> QuadStyle(EducatorsBlue, EducatorsBlueSurface, "Total Marks Edited", Icons.Default.Tune)
        "STUDENT_ADDED" -> QuadStyle(EmeraldPass, EmeraldPassContainer, "Student Added", Icons.Default.People)
        "STUDENT_UPDATED" -> QuadStyle(EducatorsBlue, EducatorsBlueSurface, "Student Updated", Icons.Default.People)
        "STUDENT_DELETED" -> QuadStyle(CrimsonFail, CrimsonFailContainer, "Student Removed", Icons.Default.People)
        "EXAM_CREATED" -> QuadStyle(AmberPending, AmberPendingContainer, "Exam Created", Icons.AutoMirrored.Filled.Assignment)
        "EXAM_UPDATED" -> QuadStyle(EducatorsBlue, EducatorsBlueSurface, "Exam Updated", Icons.AutoMirrored.Filled.Assignment)
        "EXAM_DELETED" -> QuadStyle(CrimsonFail, CrimsonFailContainer, "Exam Deleted", Icons.AutoMirrored.Filled.Assignment)
        "ATTENDANCE_SAVED" -> QuadStyle(EmeraldPass, EmeraldPassContainer, "Attendance Saved", Icons.Default.EventAvailable)
        "REMARKS_SAVED" -> QuadStyle(EmeraldPass, EmeraldPassContainer, "Remarks Saved", Icons.Default.EditNote)
        else -> QuadStyle(EmeraldPass, EmeraldPassContainer, "Marks Saved", Icons.Default.EditNote)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = log.actorName.ifBlank { log.actorEmail.ifBlank { "Staff" } },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = EducatorsNavy
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (log.actorRole == StaffRole.ADMIN.name) EducatorsBlueSurface else AmberPendingContainer
                            ) {
                                Text(
                                    text = log.actorRole,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (log.actorRole == StaffRole.ADMIN.name) EducatorsBlue else AmberPending,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = formatActivityTime(log),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = badgeBg
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = log.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val tags = buildList {
                if (log.className.isNotBlank()) add(log.className)
                if (log.subjectName.isNotBlank()) add(log.subjectName)
                if (log.examName.isNotBlank()) add(log.examName)
            }
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = EducatorsNavy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class QuadStyle(
    val accent: Color,
    val badgeBg: Color,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

fun formatActivityTime(log: ActivityLog): String {
    val date: Date = log.createdAt?.toDate() ?: return "Just now"
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
    return formatter.format(date)
}
