package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.DocumentSourceKind
import com.example.domain.ExtractedStudentCandidate
import com.example.domain.ImportedStudentDraft
import com.example.domain.StudentDocumentExtractor
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsBlueSurface
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import kotlinx.coroutines.launch

@Composable
fun StudentExtractImportDialog(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val availableSections = remember(selectedClassId, snapshot.sections) {
        snapshot.sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(selectedClassId, availableSections) {
        mutableStateOf(availableSections.firstOrNull()?.id.orEmpty())
    }
    var selectedSessionId by remember(snapshot.selectedSessionId) {
        mutableStateOf(snapshot.selectedSessionId.ifBlank { snapshot.sessions.firstOrNull()?.id.orEmpty() })
    }

    var isExtracting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rawExtractedText by remember { mutableStateOf("") }
    var showRawText by remember { mutableStateOf(false) }

    val candidates = remember { mutableStateListOf<ExtractedStudentCandidate>() }

    fun nextStartingRoll(): Int {
        val maxExisting = snapshot.students
            .filter { it.classId == selectedClassId && it.sectionId == selectedSectionId }
            .mapNotNull { it.rollNumber.toIntOrNull() }
            .maxOrNull() ?: 0
        return maxExisting + 1
    }

    fun launchExtraction(uri: android.net.Uri, kind: DocumentSourceKind) {
        scope.launch {
            isExtracting = true
            errorMessage = null
            statusMessage = "Extracting student names from ${kind.label}..."
            val res = StudentDocumentExtractor.extractFromUri(
                context = context,
                uri = uri,
                sourceKind = kind,
                startingRoll = nextStartingRoll()
            )
            isExtracting = false
            res.onSuccess { result ->
                rawExtractedText = result.rawText
                candidates.clear()
                candidates.addAll(result.candidates)
                if (result.candidates.isEmpty()) {
                    showRawText = true
                    statusMessage = "Text extracted from ${kind.label}, but no clear student names matched. Review raw text or add rows manually."
                } else {
                    statusMessage = "Extracted ${result.candidates.size} student(s) from ${kind.label}. Review & edit before saving."
                }
            }.onFailure { err ->
                statusMessage = null
                errorMessage = err.message ?: "Could not read ${kind.label}."
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) launchExtraction(uri, DocumentSourceKind.IMAGE)
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) launchExtraction(uri, DocumentSourceKind.PDF)
    }

    val wordPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) launchExtraction(uri, DocumentSourceKind.WORD)
    }

    val selectedCount = candidates.count { it.selected && it.name.isNotBlank() }

    AlertDialog(
        onDismissRequest = { if (!isExtracting) onDismiss() },
        title = { Text("Extract Students (Image / PDF / Word)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select an image (photo/screenshot), PDF, or Word (.docx/.doc) file containing student names.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Source Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isExtracting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("extract_from_image_btn")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Image", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                        enabled = !isExtracting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("extract_from_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            wordPicker.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "text/plain"
                                )
                            )
                        },
                        enabled = !isExtracting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("extract_from_word_btn")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Word", maxLines = 1)
                    }
                }

                // Target Class / Section / Session
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorDropdown(
                        label = "Target Class",
                        selectedText = snapshot.classesById[selectedClassId]?.name ?: "Select Class",
                        items = snapshot.visibleClasses,
                        itemLabel = { it.name },
                        onSelect = { selectedClassId = it.id },
                        modifier = Modifier.weight(1f)
                    )
                    SelectorDropdown(
                        label = "Section",
                        selectedText = availableSections.firstOrNull { it.id == selectedSectionId }?.name ?: "Section",
                        items = availableSections,
                        itemLabel = { it.name },
                        onSelect = { selectedSectionId = it.id },
                        modifier = Modifier.weight(1f)
                    )
                }
                SelectorDropdown(
                    label = "Academic Session",
                    selectedText = snapshot.sessionsById[selectedSessionId]?.name ?: "Select Session",
                    items = snapshot.sessions,
                    itemLabel = { it.name },
                    onSelect = { selectedSessionId = it.id }
                )

                if (isExtracting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = EducatorsNavy)
                        Text(statusMessage ?: "Reading document...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonFail
                    )
                } else if (statusMessage != null) {
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = EmeraldPass
                    )
                }

                // Optional Raw Text Reviewer
                if (rawExtractedText.isNotBlank()) {
                    OutlinedButton(
                        onClick = { showRawText = !showRawText },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
                    ) {
                        Icon(
                            if (showRawText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showRawText) "Hide Raw Extracted Text" else "View / Re-parse Raw Text")
                    }
                    AnimatedVisibility(visible = showRawText) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = rawExtractedText,
                                onValueChange = { rawExtractedText = it },
                                label = { Text("Raw Extracted Text") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                onClick = {
                                    val reparsed = StudentDocumentExtractor.parseStudentCandidates(
                                        rawExtractedText,
                                        nextStartingRoll()
                                    )
                                    candidates.clear()
                                    candidates.addAll(reparsed)
                                    statusMessage = "Parsed ${reparsed.size} student(s) from edited text."
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Re-extract Names from Text")
                            }
                        }
                    }
                }

                // Editable Extracted Rows
                if (candidates.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Extracted Students ($selectedCount selected)",
                            style = MaterialTheme.typography.titleSmall,
                            color = EducatorsNavy
                        )
                        TextButton(
                            onClick = {
                                val nextRoll = (candidates.mapNotNull { it.rollNumber.toIntOrNull() }.maxOrNull()
                                    ?: (nextStartingRoll() + candidates.size - 1)) + 1
                                candidates.add(
                                    ExtractedStudentCandidate(
                                        tempId = "manual_${System.nanoTime()}",
                                        rollNumber = nextRoll.toString(),
                                        name = "",
                                        fatherName = "-",
                                        admissionNumber = "ADM-$nextRoll"
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Row")
                        }
                    }

                    candidates.forEachIndexed { idx, item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (item.selected) EducatorsBlueSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Checkbox(
                                        checked = item.selected,
                                        onCheckedChange = { checked ->
                                            candidates[idx] = item.copy(selected = checked)
                                        }
                                    )
                                    OutlinedTextField(
                                        value = item.rollNumber,
                                        onValueChange = { r ->
                                            candidates[idx] = item.copy(
                                                rollNumber = r,
                                                admissionNumber = if (item.admissionNumber == "ADM-${item.rollNumber}") "ADM-$r" else item.admissionNumber
                                            )
                                        },
                                        label = { Text("Roll") },
                                        singleLine = true,
                                        modifier = Modifier.width(76.dp)
                                    )
                                    OutlinedTextField(
                                        value = item.name,
                                        onValueChange = { n -> candidates[idx] = item.copy(name = n) },
                                        label = { Text("Student Name") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { candidates.removeAt(idx) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove row", tint = CrimsonFail)
                                    }
                                }
                                OutlinedTextField(
                                    value = if (item.fatherName == "-") "" else item.fatherName,
                                    onValueChange = { f ->
                                        candidates[idx] = item.copy(fatherName = f.ifBlank { "-" })
                                    },
                                    label = { Text("Father's Name (optional)") },
                                    placeholder = { Text("-") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cls = snapshot.classesById[selectedClassId]
                    val sec = availableSections.firstOrNull { it.id == selectedSectionId }
                    val ses = snapshot.sessionsById[selectedSessionId]
                    if (cls == null || sec == null || ses == null) {
                        errorMessage = "Please select Class, Section, and Academic Session."
                        return@Button
                    }
                    val drafts = candidates
                        .filter { it.selected && it.name.trim().isNotEmpty() }
                        .mapIndexed { idx, c ->
                            val roll = c.rollNumber.trim().ifBlank { (nextStartingRoll() + idx).toString() }
                            ImportedStudentDraft(
                                name = c.name.trim(),
                                fatherName = c.fatherName.trim().ifBlank { "-" },
                                rollNumber = roll,
                                admissionNumber = c.admissionNumber.trim().ifBlank { "ADM-$roll" },
                                classId = cls.id,
                                className = cls.name,
                                sectionId = sec.id,
                                sectionName = sec.name,
                                sessionId = ses.id,
                                sessionName = ses.name
                            )
                        }
                    if (drafts.isEmpty()) {
                        errorMessage = "Select at least one student with a valid name."
                    } else {
                        viewModel.importStudents(snapshot.campus, drafts)
                        onDismiss()
                    }
                },
                enabled = !isExtracting && selectedCount > 0 && selectedClassId.isNotBlank() && selectedSectionId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                modifier = Modifier.testTag("confirm_extracted_students_btn")
            ) {
                Text("Import $selectedCount Students")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExtracting
            ) {
                Text("Cancel")
            }
        }
    )
}
