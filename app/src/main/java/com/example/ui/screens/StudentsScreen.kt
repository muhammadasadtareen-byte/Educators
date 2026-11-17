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
import com.example.AppRoute
import com.example.data.model.StaffRole
import com.example.data.model.Student
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsNavy

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name or roll no...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("student_search_input")
                )
                if (isAdmin) {
                    OutlinedButton(
                        onClick = { onNavigate(AppRoute.EXCEL_CENTER) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("import_students_shortcut_btn")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Excel", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import")
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
                                    Text(
                                        text = "S/O ${st.fatherName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Roll: ${st.rollNumber}  •  Adm: ${st.admissionNumber}  •  $clsName-$secName ($sesName)",
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
                                        modifier = Modifier.testTag("edit_student_${st.rollNumber}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${st.name}", tint = EducatorsBlue)
                                    }
                                    IconButton(
                                        onClick = { deleteCandidate = st },
                                        modifier = Modifier.testTag("delete_student_${st.rollNumber}")
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
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Student") },
            text = { Text("Remove ${stu.name} (Roll ${stu.rollNumber}) from the campus directory?") },
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

@Composable
private fun StudentFormDialog(
    snapshot: SchoolSnapshot,
    existing: Student?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var fatherName by remember { mutableStateOf(existing?.fatherName ?: "") }
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_name_input")
                )
                OutlinedTextField(
                    value = fatherName,
                    onValueChange = { fatherName = it },
                    label = { Text("Father's Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_father_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rollNumber,
                        onValueChange = { rollNumber = it },
                        label = { Text("Roll No *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("student_roll_input")
                    )
                    OutlinedTextField(
                        value = admissionNumber,
                        onValueChange = { admissionNumber = it },
                        label = { Text("Admission No *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("student_adm_input")
                    )
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
                        fatherName.isBlank() -> errorMsg = "Father's Name is required."
                        rollNumber.isBlank() -> errorMsg = "Roll Number is required."
                        admissionNumber.isBlank() -> errorMsg = "Admission Number is required."
                        selectedClassId.isBlank() -> errorMsg = "Please select a Class."
                        selectedSectionId.isBlank() -> errorMsg = "Please add a Section to this class first."
                        selectedSessionId.isBlank() -> errorMsg = "Please select an Academic Session."
                        else -> onSave(
                            name, fatherName, rollNumber, admissionNumber,
                            selectedClassId, selectedSectionId, selectedSessionId, photoBase64
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
