package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceStatus
import com.example.data.model.StaffRole
import com.example.data.model.Student
import com.example.domain.AttendanceHelper
import com.example.ui.MainViewModel
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

private enum class AttendanceStep(val title: String) {
    MARK("1. Mark Attendance"),
    DISPATCH("2. Send WhatsApp")
}

private enum class ParentFilter(val label: String) {
    ABSENT_AND_LEAVE("Absent & Leave"),
    ALL("All Students"),
    PRESENT("Present")
}

private val WhatsAppGreen = Color(0xFF168A45)
private val WhatsAppGreenSurface = Color(0xFFE7F7ED)

private const val PREF_ATTENDANCE = "educators_attendance_prefs"
private const val KEY_LOCAL_COORDINATOR_WA = "local_coordinator_whatsapp_"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AttendanceScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val isAdmin = snapshot.effectiveRole == StaffRole.ADMIN

    // Weekend awareness: Saturday & Sunday are off
    val todayCal = remember { AttendanceHelper.todayCalendar() }
    val todayIsWeekend = remember(todayCal) { AttendanceHelper.isWeekendCalendar(todayCal) }
    val todayDayName = remember(todayCal) { AttendanceHelper.dayOfWeekLabel(AttendanceHelper.formatDateKey(todayCal)) }

    var selectedDateKey by remember { mutableStateOf(AttendanceHelper.lastWorkingDayKey(todayCal)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(AttendanceStep.MARK) }

    // Class & Section selection (scoped strictly to visibleClasses)
    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    if (selectedClassId.isBlank() || snapshot.visibleClasses.none { it.id == selectedClassId }) {
        selectedClassId = snapshot.visibleClasses.firstOrNull()?.id.orEmpty()
    }
    val sectionsForClass = remember(selectedClassId, snapshot.sections) {
        snapshot.sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(selectedClassId, sectionsForClass) {
        mutableStateOf(sectionsForClass.firstOrNull()?.id.orEmpty())
    }
    if (selectedSectionId.isBlank() || sectionsForClass.none { it.id == selectedSectionId }) {
        selectedSectionId = sectionsForClass.firstOrNull()?.id.orEmpty()
    }

    val className = snapshot.classesById[selectedClassId]?.name ?: "Class"
    val sectionName = snapshot.sectionsById[selectedSectionId]?.name ?: "Section"

    // Students in this class & section (already in Entry Order from snapshot.visibleStudents)
    val classStudents = remember(snapshot.visibleStudents, selectedClassId, selectedSectionId) {
        snapshot.visibleStudents.filter {
            it.classId == selectedClassId && it.sectionId == selectedSectionId
        }
    }

    // Existing saved Firestore attendance record for (dateKey, classId, sectionId)
    val expectedDocId = remember(selectedDateKey, selectedClassId, selectedSectionId) {
        AttendanceHelper.attendanceDocId(selectedDateKey, selectedClassId, selectedSectionId)
    }
    val savedRecord = remember(snapshot.attendanceRecords, expectedDocId, selectedDateKey, selectedClassId, selectedSectionId) {
        snapshot.attendanceRecords.firstOrNull {
            it.id == expectedDocId ||
                (it.dateKey == selectedDateKey && it.classId == selectedClassId && it.sectionId == selectedSectionId)
        }
    }

    // Saved history for selected class & section
    val classHistory = remember(snapshot.attendanceRecords, selectedClassId, selectedSectionId) {
        snapshot.attendanceRecords
            .filter { it.classId == selectedClassId && it.sectionId == selectedSectionId }
            .sortedByDescending { it.dateKey }
            .take(10)
    }

    // Working attendance map for current date/class/section
    val statusMap = remember { mutableStateMapOf<String, AttendanceStatus>() }
    var dailyNote by remember { mutableStateOf("") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    // Track which parents have been opened/notified in this session for current date+section
    val notifiedParents = remember(selectedDateKey, selectedClassId, selectedSectionId) {
        mutableStateMapOf<String, Boolean>()
    }
    var parentFilter by remember { mutableStateOf(ParentFilter.ABSENT_AND_LEAVE) }
    var showCoordinatorPreview by remember { mutableStateOf(false) }

    // Coordinator WhatsApp Number: Campus Firestore value + local override for non-admin convenience
    val prefs = remember(context) { context.getSharedPreferences(PREF_ATTENDANCE, Context.MODE_PRIVATE) }
    var localCoordinatorOverride by remember(snapshot.campus.id) {
        mutableStateOf(prefs.getString(KEY_LOCAL_COORDINATOR_WA + snapshot.campus.id, "").orEmpty())
    }
    val effectiveCoordinatorNumber = snapshot.campus.coordinatorWhatsApp.ifBlank { localCoordinatorOverride }

    // Dialog states
    var showEditCoordinatorDialog by remember { mutableStateOf(false) }
    var editingParentStudent by remember { mutableStateOf<Student?>(null) }
    var openWhatsAppAfterParentSave by remember { mutableStateOf(false) }

    // Sync local editable map whenever date / class / section / savedRecord changes
    LaunchedEffect(selectedDateKey, selectedClassId, selectedSectionId, savedRecord?.id, savedRecord?.updatedAt, classStudents.size) {
        statusMap.clear()
        classStudents.forEach { st ->
            val savedStatus = savedRecord?.records?.get(st.id)
            statusMap[st.id] = if (savedStatus != null) {
                AttendanceStatus.fromRaw(savedStatus)
            } else {
                AttendanceStatus.PRESENT
            }
        }
        dailyNote = savedRecord?.note.orEmpty()
        hasUnsavedChanges = false
    }

    // Live counts
    val totalCount = classStudents.size
    val presentCount = classStudents.count { (statusMap[it.id] ?: AttendanceStatus.PRESENT) == AttendanceStatus.PRESENT }
    val absentCount = classStudents.count { statusMap[it.id] == AttendanceStatus.ABSENT }
    val leaveCount = classStudents.count { statusMap[it.id] == AttendanceStatus.LEAVE }

    fun persistCurrentAttendance() {
        if (selectedClassId.isBlank() || selectedSectionId.isBlank()) {
            viewModel.postMessage("Please select a Class and Section first.")
            return
        }
        if (classStudents.isEmpty()) {
            viewModel.postMessage("No students found in $className - $sectionName.")
            return
        }
        if (AttendanceHelper.isWeekend(selectedDateKey)) {
            viewModel.postMessage("Saturday and Sunday are off. Please select a weekday (Mon - Fri).")
            return
        }
        val payload = classStudents.associate { st ->
            st.id to (statusMap[st.id] ?: AttendanceStatus.PRESENT)
        }
        viewModel.saveAttendance(
            campus = snapshot.campus,
            sessionId = snapshot.selectedSessionId,
            classId = selectedClassId,
            sectionId = selectedSectionId,
            dateKey = selectedDateKey,
            statuses = payload,
            note = dailyNote
        )
        hasUnsavedChanges = false
    }

    fun dispatchParentWhatsApp(student: Student, phoneNumber: String) {
        val status = statusMap[student.id] ?: AttendanceStatus.PRESENT
        val message = AttendanceHelper.buildParentMessage(
            schoolName = snapshot.campus.schoolName,
            campusName = snapshot.campus.campusName,
            student = student,
            className = className,
            sectionName = sectionName,
            dateKey = selectedDateKey,
            status = status,
            customNote = dailyNote
        )
        val launched = AttendanceHelper.openWhatsApp(context, phoneNumber, message)
        if (launched) {
            notifiedParents[student.id] = true
        } else {
            viewModel.postMessage("Invalid WhatsApp number for ${student.name}'s parent. Tap Edit to update it.")
        }
    }

    fun dispatchCoordinatorWhatsApp(phoneNumber: String) {
        val statuses = classStudents.associate { it.id to (statusMap[it.id] ?: AttendanceStatus.PRESENT) }
        val message = AttendanceHelper.buildCoordinatorMessage(
            schoolName = snapshot.campus.schoolName,
            campusName = snapshot.campus.campusName,
            className = className,
            sectionName = sectionName,
            dateKey = selectedDateKey,
            students = classStudents,
            statuses = statuses,
            customNote = dailyNote
        )
        val launched = AttendanceHelper.openWhatsApp(context, phoneNumber, message)
        if (!launched) {
            viewModel.postMessage("Please enter a valid School Coordinator WhatsApp number (e.g. 0300-1234567).")
        }
    }

    if (snapshot.visibleClasses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Default.EventAvailable,
                title = if (isAdmin) "No Classes Configured" else "No Classes Assigned Yet",
                description = if (isAdmin) {
                    "Create classes, sections, and add students before marking daily attendance."
                } else {
                    "Once your campus administrator assigns your class, you can mark daily attendance and send updates on WhatsApp here."
                }
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Weekend Off Banner if today is Saturday or Sunday
        if (todayIsWeekend) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberPendingContainer),
                    border = BorderStroke(1.dp, AmberPending.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = "Weekend Off",
                            tint = AmberPending,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Weekend Off ($todayDayName)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = EducatorsNavy
                            )
                            Text(
                                text = "Saturday & Sunday are school holidays. Showing the last working day (${AttendanceHelper.formatDisplayDate(selectedDateKey)}). You can select any Mon–Fri date below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EducatorsNavy.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Class, Section & Weekday Date Selector Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = EducatorsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Daily Attendance",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = EducatorsNavy
                            )
                        }
                        StatusPill(
                            text = when {
                                savedRecord != null && !hasUnsavedChanges -> "Saved"
                                savedRecord != null && hasUnsavedChanges -> "Edited"
                                else -> "Not Saved"
                            },
                            isPositive = savedRecord != null && !hasUnsavedChanges,
                            isWarning = savedRecord == null || hasUnsavedChanges
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectorDropdown(
                            label = "Class",
                            selectedText = className,
                            items = snapshot.visibleClasses,
                            itemLabel = { it.name },
                            onSelect = { selectedClassId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                        SelectorDropdown(
                            label = "Section",
                            selectedText = sectionName,
                            items = sectionsForClass,
                            itemLabel = { it.name },
                            onSelect = { selectedSectionId = it.id },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Weekday Date Bar (Mon-Fri only; Sat/Sun automatically skipped)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EducatorsBlueSurface,
                        border = BorderStroke(1.dp, EducatorsBlue.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { selectedDateKey = AttendanceHelper.previousWeekday(selectedDateKey) },
                                modifier = Modifier.testTag("attendance_prev_weekday_btn")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous working day",
                                    tint = EducatorsNavy
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showDatePicker = true }
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                                    .testTag("attendance_date_picker_btn"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Choose weekday date",
                                    tint = EducatorsBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = AttendanceHelper.formatDisplayDate(selectedDateKey),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = EducatorsNavy
                                    )
                                    Text(
                                        text = "Mon – Fri Only (Sat & Sun Off)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EducatorsBlue
                                    )
                                }
                            }
                            IconButton(
                                onClick = { selectedDateKey = AttendanceHelper.nextWeekday(selectedDateKey) },
                                modifier = Modifier.testTag("attendance_next_weekday_btn")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next working day",
                                    tint = EducatorsNavy
                                )
                            }
                        }
                    }

                    if (savedRecord != null && savedRecord.markedByName.isNotBlank()) {
                        Text(
                            text = "Last saved by ${savedRecord.markedByName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Step Switcher: 1. Mark Attendance | 2. Send WhatsApp
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceStep.entries.forEach { step ->
                    val isSelected = currentStep == step
                    Surface(
                        onClick = { currentStep = step },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) EducatorsNavy else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isSelected) EducatorsNavy else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("attendance_step_${step.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (step == AttendanceStep.MARK) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else EducatorsNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else EducatorsNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Live Attendance Summary Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceStatChip("Total", totalCount.toString(), EducatorsNavy, EducatorsBlueSurface, Modifier.weight(1f))
                AttendanceStatChip("Present", presentCount.toString(), EmeraldPass, EmeraldPassContainer, Modifier.weight(1f))
                AttendanceStatChip("Absent", absentCount.toString(), CrimsonFail, CrimsonFailContainer, Modifier.weight(1f))
                AttendanceStatChip("Leave", leaveCount.toString(), AmberPending, AmberPendingContainer, Modifier.weight(1f))
            }
        }

        if (classStudents.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.People,
                    title = "No Students in $className - $sectionName",
                    description = "Add students to this class and section in Student Management first."
                )
            }
        } else if (currentStep == AttendanceStep.MARK) {
            // STEP 1: MARK & SAVE ATTENDANCE
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Students ($totalCount) • Entry Order",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = EducatorsNavy
                    )
                    OutlinedButton(
                        onClick = {
                            classStudents.forEach { st -> statusMap[st.id] = AttendanceStatus.PRESENT }
                            hasUnsavedChanges = true
                        },
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("mark_all_present_btn")
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("All Present")
                    }
                }
            }

            itemsIndexed(classStudents, key = { _, st -> st.id }) { index, student ->
                val currentStatus = statusMap[student.id] ?: AttendanceStatus.PRESENT
                StudentAttendanceMarkCard(
                    index = index + 1,
                    student = student,
                    status = currentStatus,
                    onSelectStatus = { newStatus ->
                        statusMap[student.id] = newStatus
                        hasUnsavedChanges = true
                    },
                    onEditParentPhone = {
                        editingParentStudent = student
                        openWhatsAppAfterParentSave = false
                    },
                    onQuickSendParent = {
                        if (AttendanceHelper.isValidWhatsAppNumber(student.parentWhatsApp)) {
                            dispatchParentWhatsApp(student, student.parentWhatsApp)
                        } else {
                            editingParentStudent = student
                            openWhatsAppAfterParentSave = true
                        }
                    }
                )
            }

            // Optional Message / Daily Note + Save & Proceed Buttons
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dailyNote,
                            onValueChange = {
                                dailyNote = it.take(300)
                                hasUnsavedChanges = true
                            },
                            label = { Text("Message / Note (Optional)") },
                            placeholder = { Text("Included in Coordinator & Parent WhatsApp messages") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("attendance_note_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { persistCurrentAttendance() },
                                colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("save_attendance_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Attendance", maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    if (hasUnsavedChanges || savedRecord == null) {
                                        persistCurrentAttendance()
                                    }
                                    currentStep = AttendanceStep.DISPATCH
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("go_to_send_whatsapp_btn")
                            ) {
                                Text("Send WhatsApp", maxLines = 1)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // STEP 2: SEND ATTENDANCE VIA WHATSAPP (COORDINATOR + INDIVIDUAL PARENTS)

            // 2A. School Coordinator Whole-Class Report Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, WhatsAppGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppGreenSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SupervisorAccount,
                                        contentDescription = null,
                                        tint = WhatsAppGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "School Coordinator (Whole Class)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = EducatorsNavy
                                    )
                                    Text(
                                        text = "Sends complete $className - $sectionName report (Present, Absent & Leave)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Coordinator WhatsApp Number Row with Enter / Edit Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (effectiveCoordinatorNumber.isNotBlank()) WhatsAppGreen else AmberPending,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Coordinator WhatsApp",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = effectiveCoordinatorNumber.ifBlank { "Not entered yet — tap Add" },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (effectiveCoordinatorNumber.isNotBlank()) EducatorsNavy else AmberPending
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showEditCoordinatorDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 42.dp)
                                        .testTag("edit_coordinator_whatsapp_btn")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (effectiveCoordinatorNumber.isBlank()) "Add" else "Edit")
                                }
                            }
                        }

                        // Editable Note/Message before sending
                        OutlinedTextField(
                            value = dailyNote,
                            onValueChange = { dailyNote = it.take(300) },
                            label = { Text("Custom Message (Optional)") },
                            placeholder = { Text("Optional remark added to coordinator & parent messages") },
                            singleLine = false,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (AttendanceHelper.isValidWhatsAppNumber(effectiveCoordinatorNumber)) {
                                    dispatchCoordinatorWhatsApp(effectiveCoordinatorNumber)
                                } else {
                                    showEditCoordinatorDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("send_coordinator_whatsapp_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Send Whole Class Attendance to Coordinator",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        TextButton(
                            onClick = { showCoordinatorPreview = !showCoordinatorPreview },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (showCoordinatorPreview) "Hide Report Preview" else "Preview Whole Class Message")
                        }

                        if (showCoordinatorPreview) {
                            val previewStatuses = classStudents.associate { it.id to (statusMap[it.id] ?: AttendanceStatus.PRESENT) }
                            val previewText = AttendanceHelper.buildCoordinatorMessage(
                                schoolName = snapshot.campus.schoolName,
                                campusName = snapshot.campus.campusName,
                                className = className,
                                sectionName = sectionName,
                                dateKey = selectedDateKey,
                                students = classStudents,
                                statuses = previewStatuses,
                                customNote = dailyNote
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = WhatsAppGreenSurface,
                                border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EducatorsNavy,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2B. Send Individual Student Attendance to Parents Card Header + Filter
            item {
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(EducatorsBlueSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = EducatorsBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Send Attendance to Parents (Individual)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EducatorsNavy
                                )
                                Text(
                                    text = "Sends ONLY their son/daughter's name, class, attendance status & message. Tap Edit anytime if a parent changes their number.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val absentLeaveCount = absentCount + leaveCount
                            FilterChip(
                                selected = parentFilter == ParentFilter.ABSENT_AND_LEAVE,
                                onClick = { parentFilter = ParentFilter.ABSENT_AND_LEAVE },
                                label = { Text("Absent & Leave ($absentLeaveCount)") },
                                modifier = Modifier.testTag("parent_filter_absent_leave")
                            )
                            FilterChip(
                                selected = parentFilter == ParentFilter.ALL,
                                onClick = { parentFilter = ParentFilter.ALL },
                                label = { Text("All Students ($totalCount)") },
                                modifier = Modifier.testTag("parent_filter_all")
                            )
                            FilterChip(
                                selected = parentFilter == ParentFilter.PRESENT,
                                onClick = { parentFilter = ParentFilter.PRESENT },
                                label = { Text("Present ($presentCount)") },
                                modifier = Modifier.testTag("parent_filter_present")
                            )
                        }
                    }
                }
            }

            val filteredParents = classStudents.filter { st ->
                val status = statusMap[st.id] ?: AttendanceStatus.PRESENT
                when (parentFilter) {
                    ParentFilter.ABSENT_AND_LEAVE -> status == AttendanceStatus.ABSENT || status == AttendanceStatus.LEAVE
                    ParentFilter.ALL -> true
                    ParentFilter.PRESENT -> status == AttendanceStatus.PRESENT
                }
            }

            if (filteredParents.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = EmeraldPassContainer),
                        border = BorderStroke(1.dp, EmeraldPass.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (parentFilter == ParentFilter.ABSENT_AND_LEAVE) {
                                    "All students are Present today!"
                                } else {
                                    "No students match this filter."
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldPass
                            )
                            Text(
                                text = "Switch to 'All Students' above if you want to send Present confirmation messages to parents as well.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EducatorsNavy
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filteredParents, key = { _, st -> "parent_${st.id}" }) { index, student ->
                    val status = statusMap[student.id] ?: AttendanceStatus.PRESENT
                    val alreadySent = notifiedParents[student.id] == true
                    ParentWhatsAppDispatchCard(
                        index = index + 1,
                        student = student,
                        classLabel = "$className - $sectionName",
                        status = status,
                        sentInSession = alreadySent,
                        onEditPhone = {
                            editingParentStudent = student
                            openWhatsAppAfterParentSave = false
                        },
                        onSendWhatsApp = {
                            if (AttendanceHelper.isValidWhatsAppNumber(student.parentWhatsApp)) {
                                dispatchParentWhatsApp(student, student.parentWhatsApp)
                            } else {
                                editingParentStudent = student
                                openWhatsAppAfterParentSave = true
                            }
                        }
                    )
                }
            }
        }

        // Saved Attendance History for Selected Class & Section
        if (classHistory.isNotEmpty()) {
            item {
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
                        Text(
                            text = "Saved Dates ($className - $sectionName)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = EducatorsNavy
                        )
                        classHistory.forEachIndexed { idx, rec ->
                            val pCount = rec.records.values.count { it == AttendanceStatus.PRESENT.name }
                            val aCount = rec.records.values.count { it == AttendanceStatus.ABSENT.name }
                            val lCount = rec.records.values.count { it == AttendanceStatus.LEAVE.name }
                            val isCurrent = rec.dateKey == selectedDateKey
                            Surface(
                                onClick = { selectedDateKey = rec.dateKey },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) EducatorsBlueSurface else MaterialTheme.colorScheme.background,
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) EducatorsBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = AttendanceHelper.formatDisplayDate(rec.dateKey),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = EducatorsNavy
                                        )
                                        if (rec.markedByName.isNotBlank()) {
                                            Text(
                                                text = "By ${rec.markedByName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "P:$pCount  •  A:$aCount  •  L:$lCount",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (aCount > 0) CrimsonFail else EmeraldPass
                                    )
                                }
                            }
                            if (idx < classHistory.lastIndex) {
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // Material 3 DatePickerDialog disabling Saturday & Sunday
    if (showDatePicker) {
        val initialMillis = remember(selectedDateKey) {
            AttendanceHelper.dateKeyToUtcMillis(selectedDateKey)
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return !AttendanceHelper.isWeekendUtcMillis(utcTimeMillis)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = datePickerState.selectedDateMillis
                        if (picked != null && !AttendanceHelper.isWeekendUtcMillis(picked)) {
                            selectedDateKey = AttendanceHelper.utcMillisToDateKey(picked)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select Weekday")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "Select Attendance Date (Sat & Sun Off)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }

    // Enter / Edit School Coordinator WhatsApp Dialog
    if (showEditCoordinatorDialog) {
        var phoneInput by remember(effectiveCoordinatorNumber) { mutableStateOf(effectiveCoordinatorNumber) }
        var phoneError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditCoordinatorDialog = false },
            title = { Text("School Coordinator WhatsApp") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isAdmin) {
                            "Enter or update the School Coordinator's WhatsApp number. This syncs to all teachers automatically."
                        } else {
                            "Enter or edit the School Coordinator's WhatsApp number to send whole-class daily attendance."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it.take(30)
                            phoneError = null
                        },
                        label = { Text("WhatsApp Number *") },
                        placeholder = { Text("e.g. 0300-1234567 or +923001234567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coordinator_whatsapp_input")
                    )
                    if (phoneError != null) {
                        Text(
                            text = phoneError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonFail
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = phoneInput.trim()
                        if (cleaned.isNotEmpty() && !AttendanceHelper.isValidWhatsAppNumber(cleaned)) {
                            phoneError = "Enter a valid 10–15 digit WhatsApp number (e.g. 0300-1234567)."
                            return@Button
                        }
                        if (isAdmin) {
                            viewModel.updateCampusCoordinatorWhatsApp(snapshot.campus.id, cleaned)
                        } else {
                            prefs.edit().putString(KEY_LOCAL_COORDINATOR_WA + snapshot.campus.id, cleaned).apply()
                            localCoordinatorOverride = cleaned
                            viewModel.postMessage("Coordinator WhatsApp number updated.")
                        }
                        showEditCoordinatorDialog = false
                    },
                    modifier = Modifier.testTag("save_coordinator_whatsapp_btn")
                ) {
                    Text("Save Number")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCoordinatorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Enter / Edit Parent WhatsApp Number Dialog (works for both Admin & Assigned Teachers)
    editingParentStudent?.let { stu ->
        var phoneInput by remember(stu.id, stu.parentWhatsApp) { mutableStateOf(stu.parentWhatsApp) }
        var phoneError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                editingParentStudent = null
                openWhatsAppAfterParentSave = false
            },
            title = { Text("Parent WhatsApp Number") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Student: ${stu.name} ($className - $sectionName)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = EducatorsNavy
                    )
                    Text(
                        text = "Enter or update the parent's WhatsApp number. Changes save to cloud immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it.take(30)
                            phoneError = null
                        },
                        label = { Text("Parent WhatsApp Number") },
                        placeholder = { Text("e.g. 0300-1234567 or +923001234567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parent_whatsapp_input")
                    )
                    if (phoneError != null) {
                        Text(
                            text = phoneError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonFail
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = phoneInput.trim()
                        if (cleaned.isNotEmpty() && !AttendanceHelper.isValidWhatsAppNumber(cleaned)) {
                            phoneError = "Enter a valid 10–15 digit WhatsApp number (e.g. 0300-1234567)."
                            return@Button
                        }
                        if (openWhatsAppAfterParentSave && cleaned.isEmpty()) {
                            phoneError = "Please enter the parent's WhatsApp number to send."
                            return@Button
                        }
                        viewModel.updateStudentParentWhatsApp(
                            campusId = snapshot.campus.id,
                            studentId = stu.id,
                            studentName = stu.name,
                            parentWhatsApp = cleaned
                        )
                        val shouldLaunch = openWhatsAppAfterParentSave && cleaned.isNotEmpty()
                        editingParentStudent = null
                        openWhatsAppAfterParentSave = false
                        if (shouldLaunch) {
                            dispatchParentWhatsApp(stu.copy(parentWhatsApp = cleaned), cleaned)
                        }
                    },
                    modifier = Modifier.testTag("save_parent_whatsapp_btn")
                ) {
                    Text(if (openWhatsAppAfterParentSave) "Save & Open WhatsApp" else "Save Number")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingParentStudent = null
                        openWhatsAppAfterParentSave = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AttendanceStatChip(
    label: String,
    value: String,
    accent: Color,
    container: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = EducatorsNavy
            )
        }
    }
}

@Composable
private fun StudentAttendanceMarkCard(
    index: Int,
    student: Student,
    status: AttendanceStatus,
    onSelectStatus: (AttendanceStatus) -> Unit,
    onEditParentPhone: () -> Unit,
    onQuickSendParent: () -> Unit
) {
    val borderColor = when (status) {
        AttendanceStatus.PRESENT -> EmeraldPass.copy(alpha = 0.35f)
        AttendanceStatus.ABSENT -> CrimsonFail.copy(alpha = 0.55f)
        AttendanceStatus.LEAVE -> AmberPending.copy(alpha = 0.55f)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_student_card_${student.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(EducatorsBlueSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = EducatorsNavy
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = EducatorsNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subMeta = buildList {
                            student.rollNumber.trim().takeIf { it.isNotBlank() }?.let { add("Roll $it") }
                            student.fatherName.trim().takeIf { it.isNotBlank() && it != "-" }?.let { add("S/O $it") }
                        }.joinToString(" • ")
                        if (subMeta.isNotBlank()) {
                            Text(
                                text = subMeta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // P / A / L Segmented Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttendanceStatusPill(
                        label = "P",
                        selected = status == AttendanceStatus.PRESENT,
                        activeColor = EmeraldPass,
                        onClick = { onSelectStatus(AttendanceStatus.PRESENT) },
                        testTag = "att_p_${student.id}"
                    )
                    AttendanceStatusPill(
                        label = "A",
                        selected = status == AttendanceStatus.ABSENT,
                        activeColor = CrimsonFail,
                        onClick = { onSelectStatus(AttendanceStatus.ABSENT) },
                        testTag = "att_a_${student.id}"
                    )
                    AttendanceStatusPill(
                        label = "L",
                        selected = status == AttendanceStatus.LEAVE,
                        activeColor = AmberPending,
                        onClick = { onSelectStatus(AttendanceStatus.LEAVE) },
                        testTag = "att_l_${student.id}"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // Parent WhatsApp Number Row + Edit + Quick Send
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEditParentPhone() }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Parent WhatsApp",
                        tint = if (student.parentWhatsApp.isNotBlank()) WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (student.parentWhatsApp.isNotBlank()) {
                            "Parent: ${student.parentWhatsApp}"
                        } else {
                            "Add Parent WhatsApp No."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (student.parentWhatsApp.isNotBlank()) EducatorsNavy else EducatorsBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit parent number",
                        tint = EducatorsBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }

                TextButton(
                    onClick = onQuickSendParent,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send to ${student.name}'s parent",
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Parent WA",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = WhatsAppGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceStatusPill(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor else MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, if (selected) activeColor else MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(width = 40.dp, height = 40.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ParentWhatsAppDispatchCard(
    index: Int,
    student: Student,
    classLabel: String,
    status: AttendanceStatus,
    sentInSession: Boolean,
    onEditPhone: () -> Unit,
    onSendWhatsApp: () -> Unit
) {
    val (statusBg, statusColor) = when (status) {
        AttendanceStatus.PRESENT -> EmeraldPassContainer to EmeraldPass
        AttendanceStatus.ABSENT -> CrimsonFailContainer to CrimsonFail
        AttendanceStatus.LEAVE -> AmberPendingContainer to AmberPending
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (sentInSession) WhatsAppGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("parent_dispatch_card_${student.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$index. ${student.name}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = EducatorsNavy
                    )
                    Text(
                        text = classLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (sentInSession) {
                        Surface(shape = CircleShape, color = WhatsAppGreenSurface) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = WhatsAppGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "Sent",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = WhatsAppGreen
                                )
                            }
                        }
                    }
                    Surface(shape = CircleShape, color = statusBg) {
                        Text(
                            text = status.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onEditPhone,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("edit_parent_phone_${student.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = if (student.parentWhatsApp.isNotBlank()) WhatsAppGreen else AmberPending,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = student.parentWhatsApp.ifBlank { "Add Parent Number" },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (student.parentWhatsApp.isNotBlank()) EducatorsNavy else AmberPending,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit parent number",
                            tint = EducatorsBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Button(
                    onClick = onSendWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("send_parent_wa_${student.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Send to Parent", maxLines = 1)
                }
            }
        }
    }
}
