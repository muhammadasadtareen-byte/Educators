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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.example.data.model.ExamComponent
import com.example.data.model.ExamType
import com.example.data.model.Examination
import com.example.data.model.GradingRule
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.StudentEntryField
import com.example.domain.PdfReportGenerator
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass

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
    var campusAddress by remember(snapshot.campus.address) { mutableStateOf(snapshot.campus.address) }
    var campusContact by remember(snapshot.campus.contact) { mutableStateOf(snapshot.campus.contact) }
    var coordinatorWhatsApp by remember(snapshot.campus.coordinatorWhatsApp) { mutableStateOf(snapshot.campus.coordinatorWhatsApp) }
    var studentEntryFields by remember(snapshot.campus.studentEntryFields) {
        mutableStateOf(snapshot.campus.studentEntryFields.toSet())
    }
    var allowDecimalMarks by remember(snapshot.campus.allowDecimalMarks) { mutableStateOf(snapshot.campus.allowDecimalMarks) }
    var logoBase64 by remember(snapshot.campus.logoBase64) { mutableStateOf(snapshot.campus.logoBase64) }
    var secondaryLogoBase64 by remember(snapshot.campus.secondaryLogoBase64) {
        mutableStateOf(snapshot.campus.secondaryLogoBase64?.takeIf { it.isNotBlank() })
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = FileExportHelper.uriToCompressedBase64(context, uri, maxPx = 220)
            if (encoded != null) {
                logoBase64 = encoded
                // Immediately persist to Firestore so all connected teacher accounts receive the updated logo
                viewModel.updateCampusSettings(
                    campus = snapshot.campus,
                    schoolName = schoolName,
                    campusName = campusName,
                    address = campusAddress,
                    contact = campusContact,
                    studentEntryFields = studentEntryFields.toList(),
                    allowDecimalMarks = allowDecimalMarks,
                    activeSessionId = snapshot.selectedSessionId,
                    activeExamId = snapshot.selectedExamId,
                    logoBase64 = encoded,
                    secondaryLogoBase64 = secondaryLogoBase64,
                    coordinatorWhatsApp = coordinatorWhatsApp
                )
            }
        }
    }

    val secondaryLogoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = FileExportHelper.uriToCompressedBase64(context, uri, maxPx = 220)
            if (encoded != null) {
                secondaryLogoBase64 = encoded
                viewModel.updateCampusSettings(
                    campus = snapshot.campus,
                    schoolName = schoolName,
                    campusName = campusName,
                    address = campusAddress,
                    contact = campusContact,
                    studentEntryFields = studentEntryFields.toList(),
                    allowDecimalMarks = allowDecimalMarks,
                    activeSessionId = snapshot.selectedSessionId,
                    activeExamId = snapshot.selectedExamId,
                    logoBase64 = logoBase64,
                    secondaryLogoBase64 = encoded
                )
            }
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
    var editingExam by remember { mutableStateOf<Examination?>(null) }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<StaffMember?>(null) }
    var pendingDeleteStaff by remember { mutableStateOf<StaffMember?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            if (isAdmin) {
                TabLegendCard(
                    tabTitle = "Settings",
                    summary = "Configure school branding, academic sessions, editable examinations, grading scales & staff roles.",
                    items = listOf(
                        LegendBadge("ADMIN", EmeraldPass, "Full school, exam & student control"),
                        LegendBadge("TEACHER", AmberPending, "Marks & reports for assigned classes"),
                        LegendBadge("Edit Exam", EducatorsBlue, "Rename, reorder or customize exam")
                    )
                )
            } else {
                TabLegendCard(
                    tabTitle = "Teacher Account",
                    summary = "View your assigned classes, school portal details, and manage your signed-in account.",
                    items = listOf(
                        LegendBadge("TEACHER", AmberPending, "Manage assigned class students, exams & marks"),
                        LegendBadge("Live Sync", EmeraldPass, "Changes sync instantly with school portal")
                    )
                )
            }
        }

        // 1. Account & Role Access
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
                        Text(
                            text = if (isAdmin) "Account & Role Access" else "Teacher Profile & Access",
                            style = MaterialTheme.typography.titleMedium,
                            color = EducatorsNavy
                        )
                        StatusPill(
                            text = snapshot.effectiveRole.name,
                            isPositive = isAdmin,
                            isWarning = !isAdmin
                        )
                    }

                    if (snapshot.currentActorName.isNotBlank() || snapshot.currentActorEmail.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (snapshot.currentActorName.isNotBlank()) {
                                Text(
                                    text = snapshot.currentActorName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EducatorsNavy
                                )
                            }
                            if (snapshot.currentActorEmail.isNotBlank()) {
                                Text(
                                    text = snapshot.currentActorEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!isAdmin) {
                        val assignedNames = snapshot.visibleClasses.map { it.name }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Assigned Classes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EducatorsNavy
                                )
                                Text(
                                    text = if (assignedNames.isEmpty()) {
                                        "No classes assigned yet. Contact your school Admin."
                                    } else {
                                        assignedNames.joinToString(", ")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (assignedNames.isEmpty()) CrimsonFail else EducatorsBlue
                                )
                            }
                        }
                    }

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

                    // Campus switching is strictly Admin-only so Teachers never see personal Admin workspaces
                    val switchableCampuses = if (isAdmin) snapshot.accessibleCampuses else emptyList()
                    if (isAdmin && switchableCampuses.size > 1) {
                        Text(
                            text = "Switch Campus Portal:",
                            style = MaterialTheme.typography.labelMedium,
                            color = EducatorsNavy
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            switchableCampuses.forEach { camp ->
                                val isCurrent = camp.id == snapshot.campus.id
                                val isPersonal = camp.ownerId == viewModel.currentUserId
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrent) EducatorsBlue.copy(alpha = 0.10f) else MaterialTheme.colorScheme.background,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) EducatorsBlue else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = camp.schoolName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = EducatorsNavy
                                            )
                                            Text(
                                                text = "${camp.campusName} • ${if (isPersonal) "Your Campus" else "Shared School Portal"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isCurrent) {
                                            StatusPill(text = "Active", isPositive = true)
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.switchCampus(
                                                        camp.id,
                                                        "${camp.schoolName} (${camp.campusName})"
                                                    )
                                                },
                                                modifier = Modifier
                                                    .heightIn(min = 40.dp)
                                                    .testTag("switch_campus_${camp.id}")
                                            ) {
                                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Switch")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Role preview is ONLY visible to true Administrators. Real teachers NEVER see this.
                    if (snapshot.isOwnerOrAdmin) {
                        if (isAdmin) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setRolePreview(
                                        StaffRole.TEACHER,
                                        snapshot.classes.take(2).map { it.id }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("preview_teacher_portal_btn")
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Preview Teacher Portal")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.setRolePreview(null, emptyList()) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("exit_teacher_preview_btn")
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Exit Teacher Preview")
                            }
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
                            SchoolLogoBadge(logoBase64 = logoBase64, sizeDp = 56, paddingDp = 3)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        logoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("upload_school_logo_btn")
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Upload Left School Logo")
                                }
                                Text(
                                    text = "Top-left logo on reports. Syncs instantly to all teachers.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Optional Top-Right Logo / Emblem for Progress Report & Consolidated Sheet
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!secondaryLogoBase64.isNullOrBlank()) {
                                    OptionalCornerLogo(
                                        logoBase64 = secondaryLogoBase64,
                                        sizeDp = 52,
                                        paddingDp = 2
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Empty",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Right-Corner Image (Optional)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = EducatorsNavy
                                    )
                                    Text(
                                        text = "Shown on top-right of Result Cards & Consolidated Sheets. Leave empty if not needed.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                secondaryLogoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .testTag("upload_secondary_logo_btn")
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (secondaryLogoBase64.isNullOrBlank()) "Add Right Image" else "Change")
                                        }
                                        if (!secondaryLogoBase64.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    secondaryLogoBase64 = null
                                                    viewModel.updateCampusSettings(
                                                        campus = snapshot.campus,
                                                        schoolName = schoolName,
                                                        campusName = campusName,
                                                        address = campusAddress,
                                                        contact = campusContact,
                                                        studentEntryFields = studentEntryFields.toList(),
                                                        allowDecimalMarks = allowDecimalMarks,
                                                        activeSessionId = snapshot.selectedSessionId,
                                                        activeExamId = snapshot.selectedExamId,
                                                        logoBase64 = logoBase64,
                                                        secondaryLogoBase64 = ""
                                                    )
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonFail),
                                                border = BorderStroke(1.dp, CrimsonFail.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .heightIn(min = 44.dp)
                                                    .testTag("remove_secondary_logo_btn")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
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
                        OutlinedTextField(
                            value = campusAddress,
                            onValueChange = { campusAddress = it },
                            label = { Text("Campus Address (for Consolidated Sheet & Reports)") },
                            placeholder = { Text("e.g. Kala Pul, Mansehra Road, Abbottabad") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = campusContact,
                            onValueChange = { campusContact = it },
                            label = { Text("Contact Number") },
                            placeholder = { Text("e.g. 0992-406672") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = coordinatorWhatsApp,
                            onValueChange = { coordinatorWhatsApp = it.take(30) },
                            label = { Text("School Coordinator WhatsApp (Daily Attendance)") },
                            placeholder = { Text("e.g. 0300-1234567 or +923001234567") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_coordinator_whatsapp_input")
                        )

                        Text(
                            text = "Default Optional Entries When Adding Students",
                            style = MaterialTheme.typography.labelLarge,
                            color = EducatorsNavy
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val fieldOptions = listOf(
                                StudentEntryField.FATHER_NAME to "Father's Name",
                                StudentEntryField.PARENT_WHATSAPP to "Parent WhatsApp",
                                StudentEntryField.ROLL_NUMBER to "Roll No",
                                StudentEntryField.ADMISSION_NUMBER to "Admission No",
                                StudentEntryField.PHOTO to "Photo"
                            )
                            fieldOptions.forEach { (key, label) ->
                                val selected = studentEntryFields.contains(key)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        studentEntryFields = if (selected) studentEntryFields - key else studentEntryFields + key
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

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
                                    address = campusAddress,
                                    contact = campusContact,
                                    studentEntryFields = studentEntryFields.toList(),
                                    allowDecimalMarks = allowDecimalMarks,
                                    activeSessionId = snapshot.selectedSessionId,
                                    activeExamId = snapshot.selectedExamId,
                                    logoBase64 = logoBase64,
                                    secondaryLogoBase64 = secondaryLogoBase64,
                                    coordinatorWhatsApp = coordinatorWhatsApp
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
                            val sessionLabel = snapshot.sessionsById[exam.sessionId]?.name ?: "Active Session"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exam.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(
                                            "${exam.parsedExamType().label} • $sessionLabel • " +
                                                if (exam.useCustomGrading) "Custom grading" else "Default grading",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (exam.components.isNotEmpty()) {
                                            val totalCompMax = exam.components.sumOf { it.maxMarks }
                                            val linkedCount = exam.components.count { it.isCarryForward() }
                                            Text(
                                                text = "Total ${com.example.domain.ResultCalculator.formatMarks(totalCompMax)} • " +
                                                    "${exam.components.size} part(s)" +
                                                    if (linkedCount > 0) " • $linkedCount carried from term(s)" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EducatorsBlue
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { editingExam = exam },
                                        modifier = Modifier.testTag("edit_exam_${exam.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${exam.name}", tint = EducatorsBlue)
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
                            TextButton(
                                onClick = {
                                    editingStaff = null
                                    showAddStaffDialog = true
                                },
                                modifier = Modifier.testTag("add_staff_btn")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Staff")
                            }
                        }
                        Text(
                            text = "Tap the edit icon next to any teacher to assign or update their classes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        snapshot.staff.forEach { member ->
                            val isOwnerRow = member.userUid == snapshot.campus.ownerId || member.id == snapshot.campus.ownerId
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
                                                "Assigned: ${clsNames.ifEmpty { listOf("None — tap Edit to assign") }.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (clsNames.isEmpty()) CrimsonFail else EducatorsBlue
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusPill(
                                            text = member.role,
                                            isPositive = member.parsedRole() == StaffRole.ADMIN,
                                            isWarning = member.parsedRole() == StaffRole.TEACHER
                                        )
                                        if (!isOwnerRow) {
                                            IconButton(
                                                onClick = {
                                                    editingStaff = member
                                                    showAddStaffDialog = true
                                                },
                                                modifier = Modifier.testTag("edit_staff_${member.userUid}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit ${member.displayName}",
                                                    tint = EducatorsBlue
                                                )
                                            }
                                            IconButton(
                                                onClick = { pendingDeleteStaff = member },
                                                modifier = Modifier.testTag("delete_staff_${member.userUid}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Remove ${member.displayName}",
                                                    tint = CrimsonFail
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
        ExamEditorDialog(
            existing = null,
            sessions = snapshot.sessions,
            availableExams = snapshot.examinations,
            defaultSessionId = snapshot.selectedSessionId,
            defaultSortOrder = (snapshot.examinations.size + 1).toLong(),
            canDelete = false,
            onDismiss = { showAddExamDialog = false },
            onSave = { name, examType, sessionId, customGrading, sortOrder, components ->
                viewModel.addExamination(
                    campus = snapshot.campus,
                    sessionId = sessionId,
                    name = name,
                    examType = examType,
                    useCustomGrading = customGrading,
                    sortOrder = sortOrder,
                    components = components
                )
                showAddExamDialog = false
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
            onSave = { name, examType, sessionId, customGrading, sortOrder, components ->
                viewModel.updateExamination(
                    campusId = snapshot.campus.id,
                    examId = target.id,
                    sessionId = sessionId,
                    name = name,
                    examType = examType,
                    useCustomGrading = customGrading,
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

    if (showAddStaffDialog) {
        val targetStaff = editingStaff
        var uid by remember(targetStaff?.userUid) { mutableStateOf(targetStaff?.userUid ?: "") }
        var email by remember(targetStaff?.email) { mutableStateOf(targetStaff?.email ?: "") }
        var name by remember(targetStaff?.displayName) { mutableStateOf(targetStaff?.displayName ?: "") }
        var role by remember(targetStaff?.role) { mutableStateOf(targetStaff?.parsedRole() ?: StaffRole.TEACHER) }
        var selectedClasses by remember(targetStaff?.userUid, targetStaff?.assignedClassIds) {
            mutableStateOf((targetStaff?.assignedClassIds ?: emptyList()).toSet())
        }
        val canSave = uid.isNotBlank() && name.isNotBlank() && email.isNotBlank() &&
            (role == StaffRole.ADMIN || selectedClasses.isNotEmpty() || snapshot.classes.isEmpty())

        AlertDialog(
            onDismissRequest = {
                showAddStaffDialog = false
                editingStaff = null
            },
            title = {
                Text(if (targetStaff == null) "Add Staff Account" else "Edit Staff & Assigned Classes")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uid,
                        onValueChange = { if (targetStaff == null) uid = it },
                        readOnly = targetStaff != null,
                        label = { Text("User Google UID *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StaffRole.entries.forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.name) })
                        }
                    }
                    if (role == StaffRole.TEACHER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Assign Classes (${selectedClasses.size} selected):", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { selectedClasses = snapshot.classes.map { it.id }.toSet() }) {
                                    Text("All")
                                }
                                TextButton(onClick = { selectedClasses = emptySet() }) {
                                    Text("Clear")
                                }
                            }
                        }
                        if (selectedClasses.isEmpty() && snapshot.classes.isNotEmpty()) {
                            Text(
                                text = "Tap at least one class below (e.g., Class 8) so this teacher can see students and enter marks.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonFail
                            )
                        }
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
                Button(
                    enabled = canSave,
                    onClick = {
                        viewModel.saveStaffMember(
                            campus = snapshot.campus,
                            userUid = uid,
                            email = email,
                            displayName = name,
                            role = role,
                            assignedClassIds = if (role == StaffRole.TEACHER) selectedClasses.toList() else emptyList(),
                            active = true
                        )
                        showAddStaffDialog = false
                        editingStaff = null
                    },
                    modifier = Modifier.testTag("save_staff_btn")
                ) { Text("Save Staff") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddStaffDialog = false
                    editingStaff = null
                }) { Text("Cancel") }
            }
        )
    }

    pendingDeleteStaff?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteStaff = null },
            title = { Text("Remove Staff Access?") },
            text = { Text("Remove ${target.displayName} (${target.email}) from ${snapshot.campus.campusName}?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonFail),
                    onClick = {
                        viewModel.removeStaffMember(snapshot.campus, target.userUid.ifBlank { target.id })
                        pendingDeleteStaff = null
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteStaff = null }) { Text("Cancel") }
            }
        )
    }
}
