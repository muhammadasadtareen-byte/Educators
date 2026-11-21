package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AppRoute
import com.example.R
import com.example.data.model.ExamComponent
import com.example.data.model.ExamType
import com.example.data.model.Examination
import com.example.data.model.StaffRole
import com.example.domain.ResultCalculator
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
import com.example.ui.theme.AmberPendingContainer
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsBlueSurface
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import com.example.ui.theme.EmeraldPassContainer

@Composable
fun DashboardScreen(
    snapshot: SchoolSnapshot,
    currentUserId: String = "",
    onSelectSession: (String) -> Unit,
    onSelectExam: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onSwitchCampus: (campusId: String, label: String) -> Unit = { _, _ -> },
    onUpdateExam: (examId: String, sessionId: String, name: String, examType: ExamType, useCustomGrading: Boolean, sortOrder: Long, components: List<ExamComponent>) -> Unit = { _, _, _, _, _, _, _ -> },
    onCreateExam: (sessionId: String, name: String, examType: ExamType, useCustomGrading: Boolean, sortOrder: Long, components: List<ExamComponent>) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteExam: (examId: String) -> Unit = {}
) {
    var editingExam by remember { mutableStateOf<Examination?>(null) }
    var showCreateExamDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // School Hero Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_campus_banner),
                        contentDescription = "Campus header artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        EducatorsNavy.copy(alpha = 0.90f),
                                        EducatorsBlue.copy(alpha = 0.80f)
                                    )
                                )
                            )
                    )
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SchoolLogoBadge(
                                logoBase64 = snapshot.campus.logoBase64,
                                sizeDp = 50,
                                paddingDp = 4
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = snapshot.campus.schoolName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = snapshot.campus.campusName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = snapshot.effectiveRole.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onNavigate(AppRoute.MARKS_ENTRY) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = EducatorsNavy
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("dashboard_marks_entry_btn")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Smart Marks Entry")
                            }
                            OutlinedButton(
                                onClick = { onNavigate(AppRoute.STUDENTS) },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("dashboard_students_btn")
                            ) {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (snapshot.effectiveRole == StaffRole.ADMIN) "Students" else "My Students")
                            }
                        }
                    }
                }
            }
        }

        // Shared School Portal Banner: ONLY show when user is currently in their personal campus
        // and has been invited to a shared school campus. Never show "Personal Workspace Available"
        // (Admin portal) to a Teacher on their shared school portal.
        val isOnPersonalCampus = snapshot.campus.ownerId == currentUserId
        val invitedSharedCampus = if (isOnPersonalCampus) {
            snapshot.accessibleCampuses.firstOrNull { it.id != snapshot.campus.id && it.ownerId != currentUserId }
        } else {
            null
        }
        if (invitedSharedCampus != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldPassContainer),
                    border = BorderStroke(1.dp, EmeraldPass.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shared School Portal Available",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = EducatorsNavy
                            )
                            Text(
                                text = "${invitedSharedCampus.schoolName} — ${invitedSharedCampus.campusName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSwitchCampus(
                                    invitedSharedCampus.id,
                                    "${invitedSharedCampus.schoolName} (${invitedSharedCampus.campusName})"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPass),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("dashboard_switch_campus_btn")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Switch")
                        }
                    }
                }
            }
        }

        // Tab Legend
        item {
            TabLegendCard(
                tabTitle = "Dashboard",
                summary = "Live campus overview for the selected Academic Session & Examination.",
                items = listOf(
                    LegendBadge("Total", EducatorsBlue, "Active students, classes & subjects"),
                    LegendBadge("Completed", EmeraldPass, "All subject marks entered"),
                    LegendBadge("Pending", AmberPending, "Missing marks in 1+ subjects")
                )
            )
        }

        // Session & Exam Context Selector
        item {
            CampusContextBar(
                snapshot = snapshot,
                onSelectSession = onSelectSession,
                onSelectExam = onSelectExam,
                onEditSelectedExam = { editingExam = it },
                onCreateExam = { showCreateExamDialog = true }
            )
        }

        // 5 Core KPI Metrics
        val isAdminRole = snapshot.effectiveRole == StaffRole.ADMIN
        val visibleSubjectCount = if (isAdminRole) {
            snapshot.subjects.size
        } else {
            snapshot.subjects.count { it.classId in snapshot.assignedClassIds }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        title = if (isAdminRole) "Total Students" else "My Students",
                        value = snapshot.visibleStudents.size.toString(),
                        subtitle = if (isAdminRole) "Active session" else "Assigned class(es)",
                        icon = Icons.Default.People,
                        accentColor = EducatorsBlue,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppRoute.STUDENTS) }
                    )
                    StatMetricCard(
                        title = if (isAdminRole) "Total Classes" else "Assigned Classes",
                        value = snapshot.visibleClasses.size.toString(),
                        subtitle = if (isAdminRole) "${snapshot.sections.size} sections" else "Allotted to you",
                        icon = Icons.Default.Class,
                        accentColor = EducatorsNavy,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f),
                        onClick = if (isAdminRole) ({ onNavigate(AppRoute.CLASSES) }) else ({ onNavigate(AppRoute.STUDENTS) })
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        title = if (isAdminRole) "Total Subjects" else "Assigned Subjects",
                        value = visibleSubjectCount.toString(),
                        subtitle = if (isAdminRole) "Configured" else "Editable total marks",
                        icon = Icons.Default.MenuBook,
                        accentColor = EducatorsBlue,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f),
                        onClick = if (isAdminRole) ({ onNavigate(AppRoute.SUBJECTS) }) else ({ onNavigate(AppRoute.MARKS_ENTRY) })
                    )
                    StatMetricCard(
                        title = "Results Completed",
                        value = snapshot.completedResultCount.toString(),
                        subtitle = snapshot.selectedExam?.name ?: "Exam",
                        icon = Icons.Default.CheckCircle,
                        accentColor = EmeraldPass,
                        containerColor = EmeraldPassContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppRoute.CLASS_RESULTS) }
                    )
                }
                StatMetricCard(
                    title = "Results Pending",
                    value = snapshot.pendingResultCount.toString(),
                    subtitle = "Students awaiting remaining subject marks",
                    icon = Icons.Default.PendingActions,
                    accentColor = AmberPending,
                    containerColor = AmberPendingContainer,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate(AppRoute.MARKS_ENTRY) }
                )
            }
        }

        // Quick Navigation Grid
        item {
            Text(
                text = "Quick Modules",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            val navItems = buildList {
                add(Triple("Students", Icons.Default.People, AppRoute.STUDENTS))
                add(Triple("Marks Entry", Icons.Default.EditNote, AppRoute.MARKS_ENTRY))
                add(Triple("Result Cards", Icons.Default.Badge, AppRoute.RESULT_CARDS))
                add(Triple("Class Results", Icons.Default.Assessment, AppRoute.CLASS_RESULTS))
                if (isAdminRole) {
                    add(Triple("Classes", Icons.Default.Class, AppRoute.CLASSES))
                    add(Triple("Subjects", Icons.Default.MenuBook, AppRoute.SUBJECTS))
                    add(Triple("Activity Log", Icons.Default.History, AppRoute.TEACHER_ACTIVITY))
                }
                add(Triple("Excel Export", Icons.Default.TableChart, AppRoute.EXCEL_CENTER))
                add(Triple("PDF Export", Icons.Default.PictureAsPdf, AppRoute.PDF_CENTER))
                if (isAdminRole) {
                    add(Triple("Settings", Icons.Default.Settings, AppRoute.SETTINGS))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                navItems.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (label, icon, route) ->
                            QuickNavTile(
                                label = label,
                                icon = icon,
                                onClick = { onNavigate(route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Admin-only Recent Teacher Activity Preview
        if (isAdminRole) {
            item {
                val recentLogs = snapshot.activityLogs.take(4)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = EducatorsBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Recent Teacher Activity",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EducatorsNavy
                                )
                            }
                            StatusPill(
                                text = "${snapshot.activityLogs.size} Logged",
                                isPositive = snapshot.activityLogs.isNotEmpty(),
                                isWarning = snapshot.activityLogs.isEmpty()
                            )
                        }

                        if (recentLogs.isEmpty()) {
                            Text(
                                text = "No teacher activity recorded yet. Marks entered and subject total marks edited by teachers will appear here live.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            recentLogs.forEach { log ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${log.actorName.ifBlank { "Staff" }} (${log.actorRole})",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = EducatorsNavy
                                            )
                                            Text(
                                                text = formatActivityTime(log),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = log.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onNavigate(AppRoute.TEACHER_ACTIVITY) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("dashboard_teacher_activity_btn")
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Teacher Activity Monitor")
                        }
                    }
                }
            }
        }

        // Class-wise Student Statistics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Class-wise Student Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${snapshot.classStats.size} Classes",
                    style = MaterialTheme.typography.labelMedium,
                    color = EducatorsBlue
                )
            }
        }

        if (snapshot.classStats.isEmpty()) {
            item {
                val isTeacherRole = snapshot.effectiveRole == StaffRole.TEACHER
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = if (isTeacherRole) "No Classes Assigned Yet" else "No Classes Configured Yet",
                    description = if (isTeacherRole) {
                        "Your school administrator has not allotted a class to your account yet. Once assigned, your class students, exams, and marks will appear here automatically."
                    } else {
                        "Create classes (Playgroup through Class 10) and subjects to start enrolling students and entering marks."
                    },
                    actionLabel = if (snapshot.effectiveRole == StaffRole.ADMIN) "Go to Class Management" else null,
                    onAction = if (snapshot.effectiveRole == StaffRole.ADMIN) {
                        { onNavigate(AppRoute.CLASSES) }
                    } else null
                )
            }
        } else {
            items(snapshot.classStats, key = { it.schoolClass.id }) { st ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = st.schoolClass.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = EducatorsNavy
                            )
                            StatusPill(
                                text = "${st.totalStudents} Students",
                                isPositive = st.totalStudents > 0
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        val completionRatio = if (st.totalStudents > 0) {
                            st.completedResults.toFloat() / st.totalStudents.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { completionRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = EducatorsBlue,
                            trackColor = EducatorsBlueSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Completed: ${st.completedResults}  •  Pending: ${st.pendingResults}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Avg: ${ResultCalculator.formatPercentage(st.averagePercentage)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = EducatorsBlue
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
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
                onUpdateExam(target.id, sessionId, name, examType, useCustomGrading, sortOrder, components)
                editingExam = null
            },
            onDelete = {
                onDeleteExam(target.id)
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
                onCreateExam(sessionId, name, examType, useCustomGrading, sortOrder, components)
                showCreateExamDialog = false
            }
        )
    }
}

@Composable
private fun QuickNavTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .heightIn(min = 74.dp)
            .testTag("quick_nav_${label.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = EducatorsBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
