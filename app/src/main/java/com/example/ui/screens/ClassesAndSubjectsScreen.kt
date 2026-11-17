package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.SchoolClass
import com.example.data.model.StaffRole
import com.example.data.model.Subject
import com.example.domain.ResultCalculator
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassesScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val isAdmin = snapshot.effectiveRole == StaffRole.ADMIN
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var addSectionTarget by remember { mutableStateOf<SchoolClass?>(null) }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateClassDialog = true },
                    containerColor = EducatorsNavy,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_class_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Class")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isAdmin) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.seedStandardClasses(snapshot.campus, snapshot.classes) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("seed_standard_classes_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Quick-Create Standard Classes (Playgroup - Class 10)")
                    }
                }
            }

            if (snapshot.visibleClasses.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Class,
                        title = "No Classes Added",
                        description = "Create classes from Playgroup to Class 10 and add sections (A, B, C, etc.).",
                        actionLabel = if (isAdmin) "Create Standard Classes" else null,
                        onAction = if (isAdmin) ({ viewModel.seedStandardClasses(snapshot.campus, snapshot.classes) }) else null
                    )
                }
            } else {
                items(snapshot.visibleClasses, key = { it.id }) { cls ->
                    val sections = snapshot.sections.filter { it.classId == cls.id }
                    val stuCount = snapshot.visibleStudents.count { it.classId == cls.id }
                    val subCount = snapshot.subjects.count { it.classId == cls.id }

                    Card(
                        shape = RoundedCornerShape(16.dp),
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
                                Column {
                                    Text(cls.name, style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                                    Text(
                                        "$stuCount Students  •  $subCount Subjects",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isAdmin) {
                                    Row {
                                        TextButton(onClick = { addSectionTarget = cls }) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Section")
                                        }
                                        IconButton(onClick = { viewModel.deleteClass(snapshot.campus.id, cls.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete ${cls.name}", tint = CrimsonFail)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                sections.forEach { sec ->
                                    InputChip(
                                        selected = true,
                                        onClick = {},
                                        label = { Text("Section ${sec.name}") },
                                        trailingIcon = if (isAdmin && sections.size > 1) {
                                            {
                                                IconButton(
                                                    onClick = { viewModel.deleteSection(snapshot.campus.id, sec.id) },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove Section ${sec.name}", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateClassDialog) {
        var name by remember { mutableStateOf("") }
        var sectionsCsv by remember { mutableStateOf("A, B") }
        AlertDialog(
            onDismissRequest = { showCreateClassDialog = false },
            title = { Text("Create Class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Class Name (e.g. Class 5, Prep)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_class_name_input")
                    )
                    OutlinedTextField(
                        value = sectionsCsv,
                        onValueChange = { sectionsCsv = it },
                        label = { Text("Sections (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addClass(snapshot.campus, name, (snapshot.classes.size + 1).toLong(), sectionsCsv)
                            showCreateClassDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_class_btn")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateClassDialog = false }) { Text("Cancel") }
            }
        )
    }

    addSectionTarget?.let { targetClass ->
        var secName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addSectionTarget = null },
            title = { Text("Add Section to ${targetClass.name}") },
            text = {
                OutlinedTextField(
                    value = secName,
                    onValueChange = { secName = it },
                    label = { Text("Section Name (e.g. C, Blue, Iqbal)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (secName.isNotBlank()) {
                            viewModel.addSection(snapshot.campus, targetClass.id, secName)
                            addSectionTarget = null
                        }
                    }
                ) { Text("Add Section") }
            },
            dismissButton = {
                TextButton(onClick = { addSectionTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SubjectsScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val isAdmin = snapshot.effectiveRole == StaffRole.ADMIN
    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var showSubjectDialog by remember { mutableStateOf(false) }

    val classSubjects = remember(snapshot.subjects, selectedClassId) {
        snapshot.subjects.filter { it.classId == selectedClassId }
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin && selectedClassId.isNotBlank()) {
                FloatingActionButton(
                    onClick = {
                        editingSubject = null
                        showSubjectDialog = true
                    },
                    containerColor = EducatorsNavy,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_subject_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Subject")
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
            SelectorDropdown(
                label = "Select Class",
                selectedText = snapshot.classesById[selectedClassId]?.name ?: "No Classes Available",
                items = snapshot.visibleClasses,
                itemLabel = { it.name },
                onSelect = { selectedClassId = it.id }
            )
            Spacer(Modifier.height(10.dp))

            if (isAdmin && selectedClassId.isNotBlank()) {
                OutlinedButton(
                    onClick = { viewModel.addSampleSubjects(snapshot.campus, selectedClassId, classSubjects) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("seed_subjects_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Load Standard Subjects (English, Urdu, Math, Science...)")
                }
                Spacer(Modifier.height(10.dp))
            }

            if (selectedClassId.isBlank()) {
                EmptyStateCard(
                    icon = Icons.Default.MenuBook,
                    title = "No Class Selected",
                    description = "Create a class in Class Management before configuring subjects."
                )
            } else if (classSubjects.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.MenuBook,
                    title = "No Subjects Configured",
                    description = "Add subjects with custom maximum marks and passing marks for ${snapshot.classesById[selectedClassId]?.name ?: "this class"}.",
                    actionLabel = if (isAdmin) "Add Subject" else null,
                    onAction = if (isAdmin) ({
                        editingSubject = null
                        showSubjectDialog = true
                    }) else null
                )
            } else {
                val totalMax = classSubjects.sumOf { it.maxMarks }
                Text(
                    text = "${classSubjects.size} Subjects  •  Total Max Marks: ${ResultCalculator.formatMarks(totalMax)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = EducatorsBlue
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(classSubjects, key = { it.id }) { sub ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(sub.name, style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                                        StatusPill(
                                            text = if (sub.compulsory) "Compulsory" else "Elective",
                                            isPositive = sub.compulsory,
                                            isWarning = !sub.compulsory
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Maximum Marks: ${ResultCalculator.formatMarks(sub.maxMarks)}  •  Passing Marks: ${ResultCalculator.formatMarks(sub.passingMarks)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isAdmin) {
                                    IconButton(onClick = {
                                        editingSubject = sub
                                        showSubjectDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${sub.name}", tint = EducatorsBlue)
                                    }
                                    IconButton(onClick = { viewModel.deleteSubject(snapshot.campus.id, sub.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete ${sub.name}", tint = CrimsonFail)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubjectDialog && selectedClassId.isNotBlank()) {
        SubjectFormDialog(
            existing = editingSubject,
            onDismiss = { showSubjectDialog = false },
            onSave = { name, maxMarks, passingMarks, compulsory ->
                viewModel.saveSubject(
                    campus = snapshot.campus,
                    subjectId = editingSubject?.id,
                    classId = selectedClassId,
                    name = name,
                    maxMarks = maxMarks,
                    passingMarks = passingMarks,
                    compulsory = compulsory,
                    sortOrder = editingSubject?.sortOrder ?: (classSubjects.size + 1).toLong()
                )
                showSubjectDialog = false
            }
        )
    }
}

@Composable
private fun SubjectFormDialog(
    existing: Subject?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var maxMarksText by remember { mutableStateOf(existing?.let { ResultCalculator.formatMarks(it.maxMarks) } ?: "100") }
    var passingMarksText by remember { mutableStateOf(existing?.let { ResultCalculator.formatMarks(it.passingMarks) } ?: "40") }
    var compulsory by remember { mutableStateOf(existing?.compulsory ?: true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Subject" else "Edit Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("subject_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxMarksText,
                        onValueChange = { maxMarksText = it },
                        label = { Text("Max Marks *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("subject_max_input")
                    )
                    OutlinedTextField(
                        value = passingMarksText,
                        onValueChange = { passingMarksText = it },
                        label = { Text("Passing Marks *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("subject_pass_input")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Compulsory Subject", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = compulsory, onCheckedChange = { compulsory = it })
                }
                if (errorMsg != null) {
                    Text(errorMsg ?: "", color = CrimsonFail, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val max = maxMarksText.toDoubleOrNull()
                    val pass = passingMarksText.toDoubleOrNull()
                    when {
                        name.isBlank() -> errorMsg = "Subject Name is required."
                        max == null || max <= 0.0 || max > 1000.0 -> errorMsg = "Max Marks must be between 1 and 1000."
                        pass == null || pass < 0.0 || pass > max -> errorMsg = "Passing Marks must be between 0 and Max Marks."
                        else -> onSave(name, max, pass, compulsory)
                    }
                },
                modifier = Modifier.testTag("save_subject_btn")
            ) { Text("Save Subject") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
