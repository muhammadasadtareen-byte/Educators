package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.example.AppRoute
import com.example.data.model.StaffRole
import com.example.data.model.Student
import com.example.data.model.StudentEntryField
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass

@Composable
fun StudentsScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val isAdmin = snapshot.effectiveRole == StaffRole.ADMIN
    var searchQuery by remember { mutableStateOf("") }
    var filterClassId by remember { mutableStateOf("ALL") }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Student?>(null) }

    val filteredStudents = remember(snapshot.visibleStudents, searchQuery, filterClassId) {
        snapshot.visibleStudents.filter { st ->
            val matchesClass = filterClassId == "ALL" || st.classId == filterClassId
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                st.name.lowercase().contains(q) ||
                st.fatherName.lowercase().contains(q) ||
                st.rollNumber.lowercase().contains(q) ||
                st.admissionNumber.lowercase().contains(q)
            matchesClass && matchesQuery
        }
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin && snapshot.visibleClasses.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        editingStudent = null
                        showAddEditDialog = true
                    },
                    containerColor = EducatorsNavy,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_student_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Student")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            TabLegendCard(
                tabTitle = "Student Management",
                summary = "Manage enrolled students, extract names from Image/PDF/Word, or import via Excel.",
                items = listOf(
                    LegendBadge("Extract", EducatorsBlue, "OCR/read from Photo, PDF, or Word"),
                    LegendBadge("Edit / Delete", EmeraldPass, "Update or remove student profile"),
                    LegendBadge("+ Add", EducatorsNavy, "Enroll single student")
                )
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search name, father, or roll no...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_search_input")
            )

            if (isAdmin && snapshot.visibleClasses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showExtractDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EducatorsBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("extract_students_btn")
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = "Extract from Image, PDF, or Word", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Extract (Img/PDF/Word)", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onNavigate(AppRoute.EXCEL_CENTER) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 48.dp)
                            .testTag("import_students_shortcut_btn")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Excel", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Excel Import", maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val classFilterItems = listOf("ALL" to "All Classes") + snapshot.visibleClasses.map { it.id to it.name }
                SelectorDropdown(
                    label = "Filter Class",
                    selectedText = classFilterItems.firstOrNull { it.first == filterClassId }?.second ?: "All Classes",
                    items = classFilterItems,
                    itemLabel = { it.second },
                    onSelect = { filterClassId = it.first },
                    modifier = Modifier.weight(1f)
                )
                SelectorDropdown(
                    label = "Session",
                    selectedText = snapshot.selectedSession?.name ?: "Session",
                    items = snapshot.sessions,
                    itemLabel = { it.name },
                    onSelect = { viewModel.selectSession(it.id) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "${filteredStudents.size} student(s) found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            if (snapshot.visibleClasses.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.People,
                    title = "Create Classes First",
                    description = "Before adding students, create classes and sections in Class Management.",
                    actionLabel = if (isAdmin) "Open Class Management" else null,
                    onAction = if (isAdmin) ({ onNavigate(AppRoute.CLASSES) }) else null
                )
            } else if (filteredStudents.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.People,
                    title = "No Students Found",
                    description = "Tap + to add a new student or import your student roster from an Excel (.xlsx) spreadsheet.",
                    actionLabel = if (isAdmin) "Add Student" else null,
                    onAction = if (isAdmin) ({
                        editingStudent = null
                        showAddEditDialog = true
                    }) else null
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { st ->
                        val clsName = snapshot.classesById[st.classId]?.name ?: "-"
                        val secName = snapshot.sectionsById[st.sectionId]?.name ?: "-"
                        val sesName = snapshot.sessionsById[st.sessionId]?.name ?: "-"
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudentAvatar(photoBase64 = st.photoBase64, name = st.name, sizeDp = 48)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = st.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val cleanFather = st.fatherName.trim().takeIf { it.isNotBlank() && it != "-" }
                                    if (cleanFather != null) {
                                        Text(
                                            text = "S/O $cleanFather",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val metaParts = buildList {
                                        st.rollNumber.trim().takeIf { it.isNotBlank() }?.let { add("Roll: $it") }
                                        st.admissionNumber.trim().takeIf { it.isNotBlank() }?.let { add("Adm: $it") }
                                        add("$clsName-$secName ($sesName)")
                                    }
                                    Text(
                                        text = metaParts.joinToString("  •  "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EducatorsBlue
                                    )
                                }
                                if (isAdmin) {
                                    IconButton(
                                        onClick = {
                                            editingStudent = st
                                            showAddEditDialog = true
                                        },
                                        modifier = Modifier.testTag("edit_student_${st.rollNumber.ifBlank { st.id }}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${st.name}", tint = EducatorsBlue)
                                    }
                                    IconButton(
                                        onClick = { deleteCandidate = st },
                                        modifier = Modifier.testTag("delete_student_${st.rollNumber.ifBlank { st.id }}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete ${st.name}", tint = CrimsonFail)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExtractDialog) {
        StudentExtractImportDialog(
            snapshot = snapshot,
            viewModel = viewModel,
            onDismiss = { showExtractDialog = false }
        )
    }

    if (showAddEditDialog) {
        StudentFormDialog(
            snapshot = snapshot,
            existing = editingStudent,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, father, roll, adm, classId, sectionId, sessionId, photoBase64 ->
                viewModel.saveStudent(
                    campus = snapshot.campus,
                    studentId = editingStudent?.id,
                    name = name,
                    fatherName = father,
                    rollNumber = roll,
                    admissionNumber = adm,
                    classId = classId,
                    sectionId = sectionId,
                    sessionId = sessionId,
                    photoBase64 = photoBase64
                )
                showAddEditDialog = false
            }
        )
    }

    deleteCandidate?.let { stu ->
        val rollSuffix = stu.rollNumber.takeIf { it.isNotBlank() }?.let { " (Roll $it)" } ?: ""
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Student") },
            text = { Text("Remove ${stu.name}$rollSuffix from the campus directory?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStudent(snapshot.campus.id, stu.id)
                        deleteCandidate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonFail)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentFormDialog(
    snapshot: SchoolSnapshot,
    existing: Student?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var fatherName by remember { mutableStateOf(existing?.fatherName?.takeIf { it != "-" } ?: "") }
    var rollNumber by remember { mutableStateOf(existing?.rollNumber ?: "") }
    var admissionNumber by remember { mutableStateOf(existing?.admissionNumber ?: "") }
    var selectedClassId by remember {
        mutableStateOf(existing?.classId ?: snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val availableSections = remember(selectedClassId, snapshot.sections) {
        snapshot.sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(selectedClassId, availableSections) {
        mutableStateOf(
            existing?.sectionId?.takeIf { id -> availableSections.any { it.id == id } }
                ?: availableSections.firstOrNull()?.id.orEmpty()
        )
    }
    var selectedSessionId by remember {
        mutableStateOf(existing?.sessionId ?: snapshot.selectedSessionId)
    }
    var photoBase64 by remember { mutableStateOf(existing?.photoBase64) }

    var enabledFields by remember(existing, snapshot.campus.studentEntryFields) {
        val base = snapshot.campus.studentEntryFields.toMutableSet()
        if (existing != null) {
            if (existing.fatherName.isNotBlank() && existing.fatherName != "-") base.add(StudentEntryField.FATHER_NAME)
            if (existing.rollNumber.isNotBlank()) base.add(StudentEntryField.ROLL_NUMBER)
            if (existing.admissionNumber.isNotBlank()) base.add(StudentEntryField.ADMISSION_NUMBER)
            if (!existing.photoBase64.isNullOrBlank()) base.add(StudentEntryField.PHOTO)
        }
        mutableStateOf(base.toSet())
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoBase64 = FileExportHelper.uriToCompressedBase64(context, uri, maxPx = 220)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add New Student" else "Edit Student") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Which entries do you want to add?",
                    style = MaterialTheme.typography.labelLarge,
                    color = EducatorsNavy
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("Name (Required)") }
                    )
                    listOf(
                        StudentEntryField.FATHER_NAME to "Father's Name",
                        StudentEntryField.ROLL_NUMBER to "Roll No",
                        StudentEntryField.ADMISSION_NUMBER to "Admission No",
                        StudentEntryField.PHOTO to "Photo"
                    ).forEach { (key, label) ->
                        val active = enabledFields.contains(key)
                        FilterChip(
                            selected = active,
                            onClick = {
                                enabledFields = if (active) enabledFields - key else enabledFields + key
                            },
                            label = { Text(label) }
                        )
                    }
                }

                if (enabledFields.contains(StudentEntryField.PHOTO)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudentAvatar(photoBase64 = photoBase64, name = name.ifBlank { "S" }, sizeDp = 56)
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (photoBase64 == null) "Select Photo (Optional)" else "Change Photo")
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Student Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_name_input")
                )
                if (enabledFields.contains(StudentEntryField.FATHER_NAME)) {
                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text("Father's Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_father_input")
                    )
                }
                val showRoll = enabledFields.contains(StudentEntryField.ROLL_NUMBER)
                val showAdm = enabledFields.contains(StudentEntryField.ADMISSION_NUMBER)
                if (showRoll || showAdm) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showRoll) {
                            OutlinedTextField(
                                value = rollNumber,
                                onValueChange = { rollNumber = it },
                                label = { Text("Roll No (Optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("student_roll_input")
                            )
                        }
                        if (showAdm) {
                            OutlinedTextField(
                                value = admissionNumber,
                                onValueChange = { admissionNumber = it },
                                label = { Text("Admission No (Optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("student_adm_input")
                            )
                        }
                    }
                }

                SelectorDropdown(
                    label = "Class",
                    selectedText = snapshot.classesById[selectedClassId]?.name ?: "Select Class",
                    items = snapshot.visibleClasses,
                    itemLabel = { it.name },
                    onSelect = { selectedClassId = it.id }
                )

                SelectorDropdown(
                    label = "Section",
                    selectedText = availableSections.firstOrNull { it.id == selectedSectionId }?.name ?: "No Sections",
                    items = availableSections,
                    itemLabel = { it.name },
                    onSelect = { selectedSectionId = it.id }
                )

                SelectorDropdown(
                    label = "Academic Session",
                    selectedText = snapshot.sessionsById[selectedSessionId]?.name ?: "Select Session",
                    items = snapshot.sessions,
                    itemLabel = { it.name },
                    onSelect = { selectedSessionId = it.id }
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = CrimsonFail,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        name.isBlank() -> errorMsg = "Student Name is required."
                        selectedClassId.isBlank() -> errorMsg = "Please select a Class."
                        selectedSectionId.isBlank() -> errorMsg = "Please add a Section to this class first."
                        selectedSessionId.isBlank() -> errorMsg = "Please select an Academic Session."
                        else -> onSave(
                            name.trim(),
                            if (enabledFields.contains(StudentEntryField.FATHER_NAME)) fatherName.trim() else "",
                            if (enabledFields.contains(StudentEntryField.ROLL_NUMBER)) rollNumber.trim() else "",
                            if (enabledFields.contains(StudentEntryField.ADMISSION_NUMBER)) admissionNumber.trim() else "",
                            selectedClassId,
                            selectedSectionId,
                            selectedSessionId,
                            if (enabledFields.contains(StudentEntryField.PHOTO)) photoBase64 else null
                        )
                    }
                },
                modifier = Modifier.testTag("save_student_btn")
            ) {
                Text("Save Student")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
