package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ResultStatus
import com.example.data.model.StudentResultSummary
import com.example.domain.PdfReportGenerator
import com.example.domain.ResultCalculator
import com.example.domain.XlsxEngine
import com.example.ui.MainViewModel
import com.example.ui.SchoolSnapshot
import com.example.ui.theme.AcademicGold
import com.example.ui.theme.CrimsonFail
import com.example.ui.theme.EducatorsBlue
import com.example.ui.theme.EducatorsBlueSurface
import com.example.ui.theme.EducatorsNavy
import com.example.ui.theme.EmeraldPass
import java.io.File

@Composable
fun ResultCardsScreen(
    snapshot: SchoolSnapshot,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedClassId by remember(snapshot.visibleClasses) {
        mutableStateOf(snapshot.visibleClasses.firstOrNull()?.id.orEmpty())
    }
    val classSummaries = remember(snapshot.allStudentSummaries, selectedClassId) {
        snapshot.allStudentSummaries.filter { selectedClassId.isBlank() || it.student.classId == selectedClassId }
    }
    var selectedStudentId by remember(classSummaries) {
        mutableStateOf(classSummaries.firstOrNull()?.student?.id.orEmpty())
    }
    val selectedSummary: StudentResultSummary? = classSummaries.firstOrNull { it.student.id == selectedStudentId }
        ?: classSummaries.firstOrNull()

    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            viewModel.postMessage("PDF saved successfully.")
        }
        pendingSaveFile = null
    }
    val createXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            viewModel.postMessage("Excel result exported successfully.")
        }
        pendingSaveFile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CampusContextBar(
            snapshot = snapshot,
            onSelectSession = { viewModel.selectSession(it) },
            onSelectExam = { viewModel.selectExam(it) }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorDropdown(
                label = "Class",
                selectedText = snapshot.classesById[selectedClassId]?.name ?: "All Classes",
                items = snapshot.visibleClasses,
                itemLabel = { it.name },
                onSelect = { selectedClassId = it.id },
                modifier = Modifier.weight(1f)
            )
            SelectorDropdown(
                label = "Student",
                selectedText = selectedSummary?.let { "${it.student.rollNumber} - ${it.student.name}" } ?: "No Students",
                items = classSummaries,
                itemLabel = { "${it.student.rollNumber} - ${it.student.name}" },
                onSelect = { selectedStudentId = it.student.id },
                modifier = Modifier.weight(1.3f)
            )
        }

        if (classSummaries.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val file = PdfReportGenerator.generateResultCardsPdf(
                        context = context,
                        summaries = classSummaries,
                        schoolName = snapshot.campus.schoolName,
                        campusName = snapshot.campus.campusName,
                        schoolLogoBase64 = snapshot.campus.logoBase64
                    )
                    pendingSaveFile = file
                    createPdfLauncher.launch("All_Result_Cards_${snapshot.classesById[selectedClassId]?.name ?: "Class"}.pdf")
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("download_all_cards_pdf_btn")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download All ${classSummaries.size} Result Cards (Single PDF)")
            }
        }

        if (selectedSummary == null) {
            EmptyStateCard(
                icon = Icons.Default.Badge,
                title = "No Student Selected",
                description = "Add students and enter marks to preview and print formal A4 Result Cards."
            )
        } else {
            // Formal Result Card Preview
            ResultCardPreview(snapshot = snapshot, summary = selectedSummary)

            // Teacher's Remarks Editor
            var remarksDraft by remember(selectedSummary.student.id, selectedSummary.teacherRemarks) {
                mutableStateOf(selectedSummary.teacherRemarks)
            }
            val existingRecord = snapshot.activeResultRecords.firstOrNull { it.studentId == selectedSummary.student.id }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Teacher's Remarks", style = MaterialTheme.typography.titleSmall, color = EducatorsNavy)
                    OutlinedTextField(
                        value = remarksDraft,
                        onValueChange = { remarksDraft = it },
                        label = { Text("Remarks for Result Card") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.saveTeacherRemarks(
                                campus = snapshot.campus,
                                sessionId = snapshot.selectedSessionId,
                                examId = snapshot.selectedExamId,
                                summary = selectedSummary,
                                remarks = remarksDraft,
                                existingRecord = existingRecord
                            )
                        },
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Remarks to Cloud")
                    }
                }
            }

            // Export & Print Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val file = PdfReportGenerator.generateResultCardsPdf(
                            context = context,
                            summaries = listOf(selectedSummary),
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            schoolLogoBase64 = snapshot.campus.logoBase64
                        )
                        pendingSaveFile = file
                        createPdfLauncher.launch(file.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EducatorsNavy),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("download_card_pdf_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save PDF")
                }

                OutlinedButton(
                    onClick = {
                        val file = PdfReportGenerator.generateResultCardsPdf(
                            context = context,
                            summaries = listOf(selectedSummary),
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            schoolLogoBase64 = snapshot.campus.logoBase64
                        )
                        PdfReportGenerator.printPdfFile(context, file, "ResultCard_${selectedSummary.student.name}")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("print_card_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print A4")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val file = PdfReportGenerator.generateResultCardsPdf(
                            context = context,
                            summaries = listOf(selectedSummary),
                            schoolName = snapshot.campus.schoolName,
                            campusName = snapshot.campus.campusName,
                            schoolLogoBase64 = snapshot.campus.logoBase64
                        )
                        FileExportHelper.shareFile(context, file, "application/pdf", "Share Result Card PDF")
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share PDF")
                }

                OutlinedButton(
                    onClick = {
                        val bytes = XlsxEngine.exportIndividualStudentResult(selectedSummary)
                        val file = FileExportHelper.writeBytesToCache(
                            context,
                            "Result_${selectedSummary.student.rollNumber}.xlsx",
                            bytes
                        )
                        pendingSaveFile = file
                        createXlsxLauncher.launch(file.name)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save Excel")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ResultCardPreview(
    snapshot: SchoolSnapshot,
    summary: StudentResultSummary
) {
    val logoBitmap = remember(snapshot.campus.logoBase64) {
        PdfReportGenerator.decodeBase64Bitmap(snapshot.campus.logoBase64)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, EducatorsNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(4.dp)
                .border(1.dp, AcademicGold, RoundedCornerShape(13.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // School Heading
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(50.dp)) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = "School Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_school_crest),
                            contentDescription = "School Crest",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.campus.schoolName.uppercase(),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                        color = EducatorsNavy,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = snapshot.campus.campusName.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = EducatorsBlue,
                        textAlign = TextAlign.Center
                    )
                }
                StudentAvatar(photoBase64 = summary.student.photoBase64, name = summary.student.name, sizeDp = 46)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EducatorsNavy)
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "RESULT CARD",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            // Student Particulars
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = EducatorsBlueSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Student Name", summary.student.name, "Session", summary.sessionName)
                    InfoRow("Father's Name", summary.student.fatherName, "Examination", summary.examName)
                    InfoRow("Roll Number", summary.student.rollNumber, "Class / Sec", "${summary.className} - ${summary.sectionName}")
                }
            }

            // Marks Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EducatorsNavy)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text("Subject", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1.8f))
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    Text("Obt.", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    Text("%", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                    Text("Grade", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                }
                summary.subjectResults.forEachIndexed { idx, sr ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (idx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sr.subject.name + if (sr.passed == false && sr.subject.compulsory) " *" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.weight(1.8f)
                        )
                        Text(
                            text = ResultCalculator.formatMarks(sr.subject.maxMarks),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF334155),
                            modifier = Modifier.weight(0.8f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = sr.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "Pending",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (sr.passed == false) CrimsonFail else Color(0xFF0F172A),
                            modifier = Modifier.weight(0.8f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = sr.percentage?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "-",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF334155),
                            modifier = Modifier.weight(0.9f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = sr.grade,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (sr.passed == false) CrimsonFail else EducatorsNavy,
                            modifier = Modifier.weight(0.7f),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                }
            }

            if (summary.failedCompulsorySubjects.isNotEmpty()) {
                Text(
                    text = "* Failed Compulsory Subject(s): ${summary.failedCompulsorySubjects.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CrimsonFail
                )
            }

            // Bottom Summary
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = EducatorsBlueSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow(
                        "Total Marks",
                        ResultCalculator.formatMarks(summary.totalMaxMarks),
                        "Obtained",
                        ResultCalculator.formatMarks(summary.totalObtainedMarks)
                    )
                    InfoRow(
                        "Overall %",
                        ResultCalculator.formatPercentage(summary.overallPercentage),
                        "Overall Grade",
                        summary.overallGrade
                    )
                    InfoRow(
                        "Class Position",
                        ResultCalculator.formatOrdinal(summary.position),
                        "Status",
                        when {
                            summary.status == ResultStatus.INCOMPLETE -> "Incomplete"
                            summary.passed -> "PASS"
                            else -> "FAIL"
                        }
                    )
                    Text(
                        text = "Teacher's Remarks: ${summary.teacherRemarks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0F172A)
                    )
                }
            }

            // Signatures
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SignatureBlock("Class Teacher", Modifier.weight(1f))
                SignatureBlock("Exam In-charge", Modifier.weight(1f))
                SignatureBlock("Principal", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoRow(l1: String, v1: String, l2: String, v2: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$l1: $v1",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF0F172A),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$l2: $v2",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF0F172A),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SignatureBlock(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = Color(0xFF475569), thickness = 1.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF475569),
            textAlign = TextAlign.Center
        )
    }
}
