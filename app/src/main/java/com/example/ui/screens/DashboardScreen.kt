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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
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
    onSelectSession: (String) -> Unit,
    onSelectExam: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
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
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(50.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_school_crest),
                                    contentDescription = "School crest",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                )
                            }
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
                            if (snapshot.effectiveRole == StaffRole.ADMIN) {
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
                                    Text("Students")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Session & Exam Context Selector
        item {
            CampusContextBar(
                snapshot = snapshot,
                onSelectSession = onSelectSession,
                onSelectExam = onSelectExam
            )
        }

        // 5 Core KPI Metrics
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        title = "Total Students",
                        value = snapshot.visibleStudents.size.toString(),
                        subtitle = "Active session",
                        icon = Icons.Default.People,
                        accentColor = EducatorsBlue,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppRoute.STUDENTS) }
                    )
                    StatMetricCard(
                        title = "Total Classes",
                        value = snapshot.visibleClasses.size.toString(),
                        subtitle = "${snapshot.sections.size} sections",
                        icon = Icons.Default.Class,
                        accentColor = EducatorsNavy,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppRoute.CLASSES) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        title = "Total Subjects",
                        value = snapshot.subjects.size.toString(),
                        subtitle = "Configured",
                        icon = Icons.Default.MenuBook,
                        accentColor = EducatorsBlue,
                        containerColor = EducatorsBlueSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppRoute.SUBJECTS) }
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
                add(Triple("Marks Entry", Icons.Default.EditNote, AppRoute.MARKS_ENTRY))
                add(Triple("Result Cards", Icons.Default.Badge, AppRoute.RESULT_CARDS))
                add(Triple("Class Results", Icons.Default.Assessment, AppRoute.CLASS_RESULTS))
                if (snapshot.effectiveRole == StaffRole.ADMIN) {
                    add(Triple("Students", Icons.Default.People, AppRoute.STUDENTS))
                    add(Triple("Classes", Icons.Default.Class, AppRoute.CLASSES))
                    add(Triple("Subjects", Icons.Default.MenuBook, AppRoute.SUBJECTS))
                }
                add(Triple("Excel Export", Icons.Default.TableChart, AppRoute.EXCEL_CENTER))
                add(Triple("PDF Export", Icons.Default.PictureAsPdf, AppRoute.PDF_CENTER))
                if (snapshot.effectiveRole == StaffRole.ADMIN || snapshot.isOwnerOrAdmin) {
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
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = "No Classes Configured Yet",
                    description = "Create classes (Playgroup through Class 10) and subjects to start enrolling students and entering marks.",
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
