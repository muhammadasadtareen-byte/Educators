package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.AuthHelper
import com.example.data.model.ExamType
import com.example.data.model.GradingRule
import com.example.data.model.StaffRole
import com.example.domain.PdfReportGenerator
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val isAdmin = snapshot.effectiveRole == StaffRole.ADMIN

    var schoolName by remember(snapshot.campus.schoolName) { mutableStateOf(snapshot.campus.schoolName) }
    var campusName by remember(snapshot.campus.campusName) { mutableStateOf(snapshot.campus.campusName) }
    var allowDecimalMarks by remember(snapshot.campus.allowDecimalMarks) { mutableStateOf(snapshot.campus.allowDecimalMarks) }
    var logoBase64 by remember(snapshot.campus.logoBase64) { mutableStateOf(snapshot.campus.logoBase64) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            logoBase64 = FileExportHelper.uriToCompressedBase64(context, uri, maxPx = 220)
        }
    }

    // Grading editor state
    var selectedGradingTarget by remember { mutableStateOf("DEFAULT") }
    val activeRuleList = remember(snapshot.gradingRules, selectedGradingTarget) {
        snapshot.gradingRules.filter { it.examId == selectedGradingTarget }.sortedByDescending { it.minPercentage }
    }
    var editingRule by remember { mutableStateOf<GradingRule?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }

    var showAddSessionDialog by remember { mutableStateOf(false) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var showAddStaffDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Account & Role Access
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account & Role Access", style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Signed-in UID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(viewModel.currentUserId, style = MaterialTheme.typography.labelSmall, color = EducatorsBlue)
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(viewModel.currentUserId))
                            viewModel.postMessage("User UID copied.")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy UID", tint = EducatorsBlue)
                        }
                    }

                    if (snapshot.isOwnerOrAdmin) {
                        Text(
                            text = "Preview Role Permissions (Admin vs Teacher):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = snapshot.effectiveRole == StaffRole.ADMIN,
                                onClick = { viewModel.setRolePreview(null, emptyList()) },
                                label = { Text("Administrator") }
                            )
                            FilterChip(
                                selected = snapshot.effectiveRole == StaffRole.TEACHER,
                                onClick = {
                                    viewModel.setRolePreview(
                                        StaffRole.TEACHER,
                                        snapshot.classes.take(2).map { it.id }
                                    )
                                },
                                label = { Text("Teacher View") }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { AuthHelper.signOut(credentialManager, scope) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonFail),
                        border = BorderStroke(1.dp, CrimsonFail.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("sign_out_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // 2. School Branding & Marks Configuration (Admin only)
        if (isAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("School Branding & Marks Policy", style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val bmp = remember(logoBase64) { PdfReportGenerator.decodeBase64Bitmap(logoBase64) }
                            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(56.dp)) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "School Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_school_crest),
                                        contentDescription = "Default Crest",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Upload School Logo")
                            }
                        }

                        OutlinedTextField(
                            value = schoolName,
                            onValueChange = { schoolName = it },
                            label = { Text("School Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = campusName,
                            onValueChange = { campusName = it },
                            label = { Text("Campus Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow Decimal Marks", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Permits fractional marks (e.g. 84.5)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = allowDecimalMarks, onCheckedChange = { allowDecimalMarks = it })
                        }

                        Button(
                            onClick = {
                                viewModel.updateCampusSettings(
                                    campus = snapshot.campus,
                                    schoolName = schoolName,
                                    campusName = campusName,
                                    allowDecimalMarks = allowDecimalMarks,
                                    activeSessionId = snapshot.selectedSessionId,
                                    activeExamId = snapshot.selectedExamId,
                                    logoBase64 = logoBase64
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                            modifier = Modifier
                                .align(Alignment.End)
                                .heightIn(min = 48.dp)
                                .testTag("save_campus_settings_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Campus Settings")
                        }
                    }
                }
            }

            // 3. Academic Sessions & Examinations
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Academic Sessions & Examinations", style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showAddSessionDialog = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Session")
                            }
                            OutlinedButton(
                                onClick = { showAddExamDialog = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Exam")
                            }
                        }

                        snapshot.examinations.forEach { exam ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exam.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        if (exam.useCustomGrading) "Uses exam-specific grading" else "Uses default grading scale",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = exam.useCustomGrading,
                                    onCheckedChange = {
                                        viewModel.toggleExamCustomGrading(snapshot.campus.id, exam.id, it)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Editable Grading Criteria
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Grading System Criteria", style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                            TextButton(onClick = {
                                editingRule = null
                                showRuleDialog = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Tier")
                            }
                        }

                        val gradingTargets = listOf("DEFAULT" to "Default Grading Scale") +
                            snapshot.examinations.map { it.id to "${it.name} (Custom)" }
                        SelectorDropdown(
                            label = "Editing Rules For",
                            selectedText = gradingTargets.firstOrNull { it.first == selectedGradingTarget }?.second ?: "Default",
                            items = gradingTargets,
                            itemLabel = { it.second },
                            onSelect = { selectedGradingTarget = it.first }
                        )

                        activeRuleList.forEach { rule ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GradePill(grade = rule.grade, passed = rule.passing)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${rule.minPercentage}% – ${rule.maxPercentage}%",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            "${rule.remarks} • ${if (rule.passing) "Passing" else "Fail"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = {
                                        editingRule = rule
                                        showRuleDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${rule.grade}", tint = EducatorsBlue)
                                    }
                                    IconButton(onClick = { viewModel.deleteGradingRule(snapshot.campus.id, rule.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete ${rule.grade}", tint = CrimsonFail)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Staff & Teacher Permissions
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Staff Accounts & Permissions", style = MaterialTheme.typography.titleMedium, color = EducatorsNavy)
                            TextButton(onClick = { showAddStaffDialog = true }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Staff")
                            }
                        }
                        snapshot.staff.forEach { member ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
                                        Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (member.parsedRole() == StaffRole.TEACHER) {
                                            val clsNames = member.assignedClassIds.mapNotNull { snapshot.classesById[it]?.name }
                                            Text(
                                                "Assigned: ${clsNames.ifEmpty { listOf("None") }.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EducatorsBlue
                                            )
                                        }
                                    }
                                    StatusPill(
                                        text = member.role,
                                        isPositive = member.parsedRole() == StaffRole.ADMIN,
                                        isWarning = member.parsedRole() == StaffRole.TEACHER
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Grading Rule Dialog
    if (showRuleDialog) {
        var grade by remember { mutableStateOf(editingRule?.grade ?: "") }
        var minText by remember { mutableStateOf(editingRule?.minPercentage?.toString() ?: "") }
        var maxText by remember { mutableStateOf(editingRule?.maxPercentage?.toString() ?: "") }
        var remarks by remember { mutableStateOf(editingRule?.remarks ?: "") }
        var passing by remember { mutableStateOf(editingRule?.passing ?: true) }
        AlertDialog(
            onDismissRequest = { showRuleDialog = false },
            title = { Text(if (editingRule == null) "Add Grading Tier" else "Edit Grading Tier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade (e.g. A+)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { minText = it },
                            label = { Text("Min %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxText,
                            onValueChange = { maxText = it },
                            label = { Text("Max %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks (e.g. Outstanding)") }, singleLine = true)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Passing Grade")
                        Switch(checked = passing, onCheckedChange = { passing = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val min = minText.toDoubleOrNull()
                    val max = maxText.toDoubleOrNull()
                    if (grade.isNotBlank() && remarks.isNotBlank() && min != null && max != null && min in 0.0..max && max <= 100.0) {
                        viewModel.saveGradingRule(
                            campus = snapshot.campus,
                            ruleId = editingRule?.id,
                            examId = selectedGradingTarget,
                            minPct = min,
                            maxPct = max,
                            grade = grade,
                            remarks = remarks,
                            passing = passing,
                            sortOrder = editingRule?.sortOrder ?: (activeRuleList.size + 1).toLong()
                        )
                        showRuleDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRuleDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddSessionDialog) {
        var name by remember { mutableStateOf("2027-2028") }
        AlertDialog(
            onDismissRequest = { showAddSessionDialog = false },
            title = { Text("New Academic Session") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Session Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addSession(snapshot.campus, name, true)
                        showAddSessionDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddSessionDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddExamDialog) {
        var name by remember { mutableStateOf("") }
        var examType by remember { mutableStateOf(ExamType.MIDTERM) }
        var customGrading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddExamDialog = false },
            title = { Text("New Examination") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Exam Name") }, singleLine = true)
                    SelectorDropdown(
                        label = "Exam Type",
                        selectedText = examType.label,
                        items = ExamType.entries,
                        itemLabel = { it.label },
                        onSelect = { examType = it }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Use Custom Grading Rules")
                        Switch(checked = customGrading, onCheckedChange = { customGrading = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && snapshot.selectedSessionId.isNotBlank()) {
                        viewModel.addExamination(
                            snapshot.campus, snapshot.selectedSessionId, name, examType, customGrading,
                            (snapshot.examinations.size + 1).toLong()
                        )
                        showAddExamDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddExamDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddStaffDialog) {
        var uid by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(StaffRole.TEACHER) }
        var selectedClasses by remember { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { showAddStaffDialog = false },
            title = { Text("Add / Update Staff Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uid, onValueChange = { uid = it }, label = { Text("User Google UID *") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address *") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StaffRole.entries.forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.name) })
                        }
                    }
                    if (role == StaffRole.TEACHER) {
                        Text("Assign Classes:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            snapshot.classes.forEach { cls ->
                                val picked = selectedClasses.contains(cls.id)
                                FilterChip(
                                    selected = picked,
                                    onClick = {
                                        selectedClasses = if (picked) selectedClasses - cls.id else selectedClasses + cls.id
                                    },
                                    label = { Text(cls.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (uid.isNotBlank() && name.isNotBlank() && email.isNotBlank()) {
                        viewModel.saveStaffMember(
                            campus = snapshot.campus,
                            userUid = uid,
                            email = email,
                            displayName = name,
                            role = role,
                            assignedClassIds = selectedClasses.toList(),
                            active = true
                        )
                        showAddStaffDialog = false
                    }
                }) { Text("Save Staff") }
            },
            dismissButton = { TextButton(onClick = { showAddStaffDialog = false }) { Text("Cancel") } }
        )
    }
}
