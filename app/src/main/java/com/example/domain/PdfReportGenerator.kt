package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Base64
import com.example.R
import com.example.data.model.ClassPerformanceStats
import com.example.data.model.ResultStatus
import com.example.data.model.StudentResultSummary
import com.example.data.model.Subject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfReportGenerator {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MARGIN = 34f

    private val navyColor = Color.parseColor("#08408A")
    private val blueColor = Color.parseColor("#1565C0")
    private val lightBlueBg = Color.parseColor("#EAF2FF")
    private val borderColor = Color.parseColor("#CBD5E1")
    private val darkText = Color.parseColor("#0F172A")
    private val mutedText = Color.parseColor("#475569")
    private val goldColor = Color.parseColor("#D97706")
    private val passGreen = Color.parseColor("#15803D")
    private val failRed = Color.parseColor("#B91C1C")

    // 1. Individual & Batch Student Result Cards (1 per A4 page)
    fun generateResultCardsPdf(
        context: Context,
        summaries: List<StudentResultSummary>,
        schoolName: String,
        campusName: String,
        schoolLogoBase64: String?
    ): File {
        val doc = PdfDocument()
        val logoBitmap = decodeLogoBitmap(context, schoolLogoBase64)
        val totalPages = summaries.size.coerceAtLeast(1)

        summaries.forEachIndexed { idx, summary ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, idx + 1).create()
            val page = doc.startPage(pageInfo)
            drawResultCardPage(
                canvas = page.canvas,
                summary = summary,
                schoolName = schoolName,
                campusName = campusName,
                logoBitmap = logoBitmap,
                pageNumber = idx + 1,
                totalPages = totalPages
            )
            doc.finishPage(page)
        }

        val fileName = if (summaries.size == 1) {
            "ResultCard_${summaries.first().student.rollNumber}_${summaries.first().student.name.replace(" ", "_")}.pdf"
        } else {
            "All_ResultCards_${System.currentTimeMillis()}.pdf"
        }
        return savePdfToExports(context, doc, fileName)
    }

    // 2. Complete Class Result Sheet (Multi-page A4)
    fun generateClassResultSheetPdf(
        context: Context,
        schoolName: String,
        campusName: String,
        schoolLogoBase64: String?,
        className: String,
        sectionLabel: String,
        examName: String,
        sessionName: String,
        subjects: List<Subject>,
        summaries: List<StudentResultSummary>
    ): File {
        val doc = PdfDocument()
        val logoBitmap = decodeLogoBitmap(context, schoolLogoBase64)
        val sortedSubjects = subjects.sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name })
        val rowsPerPage = 22
        val chunks = if (summaries.isEmpty()) listOf(emptyList()) else summaries.chunked(rowsPerPage)
        val totalPages = chunks.size

        chunks.forEachIndexed { pageIdx, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawStandardHeader(
                canvas = canvas,
                schoolName = schoolName,
                campusName = campusName,
                reportTitle = "CLASS RESULT SHEET — $className ($sectionLabel)",
                subtitle = "$examName | Session: $sessionName",
                logoBitmap = logoBitmap
            )

            // Column layout
            val usableW = A4_WIDTH - 2 * MARGIN
            val fixedWidths = floatArrayOf(34f, 108f) // Roll, Name
            val tailWidths = floatArrayOf(44f, 44f, 40f, 34f, 42f) // Total, Obt, %, Gr, Pos/St
            val subCount = sortedSubjects.size.coerceAtLeast(1)
            val remainingW = (usableW - fixedWidths.sum() - tailWidths.sum()).coerceAtLeast(60f)
            val subColW = remainingW / subCount

            val colWidths = buildList {
                add(fixedWidths[0])
                add(fixedWidths[1])
                repeat(sortedSubjects.size) { add(subColW) }
                tailWidths.forEach { add(it) }
            }
            val headers = buildList {
                add("Roll")
                add("Student Name")
                sortedSubjects.forEach { add(it.name.take(6)) }
                add("Total")
                add("Obt.")
                add("%")
                add("Gr.")
                add("Pos/St")
            }

            y = drawTableHeaderRow(canvas, MARGIN, y, colWidths, headers, rowHeight = 24f)

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkText
                textSize = if (sortedSubjects.size > 7) 7.5f else 8.5f
            }
            val rowH = 22f
            pageRows.forEachIndexed { idx, summary ->
                val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
                val cells = buildList {
                    add(summary.student.rollNumber)
                    add(summary.student.name.take(18))
                    sortedSubjects.forEach { sub ->
                        val sr = summary.subjectResults.firstOrNull { it.subject.id == sub.id }
                        add(sr?.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "-")
                    }
                    add(ResultCalculator.formatMarks(summary.totalMaxMarks))
                    add(ResultCalculator.formatMarks(summary.totalObtainedMarks))
                    add(String.format(java.util.Locale.US, "%.1f", summary.overallPercentage))
                    add(summary.overallGrade)
                    val posText = if (summary.status == ResultStatus.INCOMPLETE) "Inc"
                    else if (summary.passed) ResultCalculator.formatOrdinal(summary.position)
                    else "Fail"
                    add(posText)
                }
                y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, rowH, bg, cellPaint)
            }

            drawPageFooter(canvas, pageIdx + 1, totalPages)
            doc.finishPage(page)
        }

        return savePdfToExports(context, doc, "ClassResult_${className.replace(" ", "_")}.pdf")
    }

    // 3. Subject-Wise Marks Sheet PDF
    fun generateSubjectMarksPdf(
        context: Context,
        schoolName: String,
        campusName: String,
        schoolLogoBase64: String?,
        className: String,
        sectionName: String,
        examName: String,
        sessionName: String,
        subject: Subject,
        summaries: List<StudentResultSummary>
    ): File {
        val doc = PdfDocument()
        val logoBitmap = decodeLogoBitmap(context, schoolLogoBase64)
        val chunks = if (summaries.isEmpty()) listOf(emptyList()) else summaries.chunked(24)
        val totalPages = chunks.size

        chunks.forEachIndexed { pageIdx, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawStandardHeader(
                canvas,
                schoolName,
                campusName,
                "SUBJECT MARKS SHEET — ${subject.name.uppercase()}",
                "$className ($sectionName) | $examName | $sessionName | Max: ${ResultCalculator.formatMarks(subject.maxMarks)}",
                logoBitmap
            )

            val colWidths = listOf(55f, 145f, 125f, 65f, 65f, 40f, 32f)
            val headers = listOf("Roll No", "Student Name", "Father's Name", "Max Marks", "Obtained", "%", "Grade")
            y = drawTableHeaderRow(canvas, MARGIN, y, colWidths, headers, 24f)

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; textSize = 9f }
            pageRows.forEachIndexed { idx, summary ->
                val sr = summary.subjectResults.firstOrNull { it.subject.id == subject.id }
                val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
                val cells = listOf(
                    summary.student.rollNumber,
                    summary.student.name.take(24),
                    summary.student.fatherName.take(20),
                    ResultCalculator.formatMarks(subject.maxMarks),
                    sr?.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "Pending",
                    sr?.percentage?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "-",
                    sr?.grade ?: "-"
                )
                y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, 22f, bg, cellPaint)
            }

            drawPageFooter(canvas, pageIdx + 1, totalPages)
            doc.finishPage(page)
        }
        return savePdfToExports(context, doc, "SubjectMarks_${subject.name.replace(" ", "_")}.pdf")
    }

    // 4. Class Performance Report PDF
    fun generatePerformanceReportPdf(
        context: Context,
        schoolName: String,
        campusName: String,
        schoolLogoBase64: String?,
        examName: String,
        sessionName: String,
        stats: List<ClassPerformanceStats>
    ): File {
        val doc = PdfDocument()
        val logoBitmap = decodeLogoBitmap(context, schoolLogoBase64)
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        var y = drawStandardHeader(
            canvas,
            schoolName,
            campusName,
            "CLASS-WISE PERFORMANCE REPORT",
            "$examName | Session: $sessionName",
            logoBitmap
        )

        val colWidths = listOf(105f, 65f, 70f, 65f, 55f, 55f, 60f, 52f)
        val headers = listOf("Class", "Students", "Completed", "Pending", "Pass", "Fail", "Pass %", "Avg %")
        y = drawTableHeaderRow(canvas, MARGIN, y, colWidths, headers, 24f)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; textSize = 9f }
        stats.forEachIndexed { idx, st ->
            val passRate = if (st.completedResults > 0) (st.passedCount * 100.0) / st.completedResults else 0.0
            val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
            val cells = listOf(
                st.schoolClass.name,
                st.totalStudents.toString(),
                st.completedResults.toString(),
                st.pendingResults.toString(),
                st.passedCount.toString(),
                st.failedCount.toString(),
                String.format(java.util.Locale.US, "%.1f%%", passRate),
                String.format(java.util.Locale.US, "%.1f%%", st.averagePercentage)
            )
            y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, 22f, bg, cellPaint)
        }

        drawPageFooter(canvas, 1, 1)
        doc.finishPage(page)
        return savePdfToExports(context, doc, "Class_Performance_Report.pdf")
    }

    // 5. Student Result Summary Report PDF
    fun generateStudentSummaryListPdf(
        context: Context,
        schoolName: String,
        campusName: String,
        schoolLogoBase64: String?,
        examName: String,
        sessionName: String,
        summaries: List<StudentResultSummary>
    ): File {
        val doc = PdfDocument()
        val logoBitmap = decodeLogoBitmap(context, schoolLogoBase64)
        val chunks = if (summaries.isEmpty()) listOf(emptyList()) else summaries.chunked(24)
        val totalPages = chunks.size

        chunks.forEachIndexed { pageIdx, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawStandardHeader(
                canvas, schoolName, campusName,
                "STUDENT RESULT SUMMARIES",
                "$examName | Session: $sessionName",
                logoBitmap
            )

            val colWidths = listOf(45f, 125f, 80f, 55f, 60f, 55f, 42f, 65f)
            val headers = listOf("Roll", "Student Name", "Class", "Total", "Obtained", "%", "Grade", "Status")
            y = drawTableHeaderRow(canvas, MARGIN, y, colWidths, headers, 24f)

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; textSize = 9f }
            pageRows.forEachIndexed { idx, s ->
                val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
                val status = when {
                    s.status == ResultStatus.INCOMPLETE -> "Incomplete"
                    s.passed -> "Pass (${ResultCalculator.formatOrdinal(s.position)})"
                    else -> "Fail"
                }
                val cells = listOf(
                    s.student.rollNumber,
                    s.student.name.take(20),
                    "${s.className}-${s.sectionName}",
                    ResultCalculator.formatMarks(s.totalMaxMarks),
                    ResultCalculator.formatMarks(s.totalObtainedMarks),
                    String.format(java.util.Locale.US, "%.1f%%", s.overallPercentage),
                    s.overallGrade,
                    status
                )
                y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, 22f, bg, cellPaint)
            }

            drawPageFooter(canvas, pageIdx + 1, totalPages)
            doc.finishPage(page)
        }
        return savePdfToExports(context, doc, "Student_Result_Summaries.pdf")
    }

    // --- INDIVIDUAL A4 RESULT CARD RENDERER ---

    private fun drawResultCardPage(
        canvas: Canvas,
        summary: StudentResultSummary,
        schoolName: String,
        campusName: String,
        logoBitmap: Bitmap?,
        pageNumber: Int,
        totalPages: Int
    ) {
        // Outer formal double border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRect(20f, 20f, A4_WIDTH - 20f, A4_HEIGHT - 20f, borderPaint)
        borderPaint.strokeWidth = 0.8f
        borderPaint.color = goldColor
        canvas.drawRect(25f, 25f, A4_WIDTH - 25f, A4_HEIGHT - 25f, borderPaint)

        // Header Banner
        var y = 40f
        if (logoBitmap != null) {
            val logoRect = RectF(MARGIN + 6f, y, MARGIN + 64f, y + 58f)
            canvas.drawBitmap(logoBitmap, null, logoRect, null)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blueColor
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(schoolName.uppercase(), A4_WIDTH / 2f, y + 22f, titlePaint)
        canvas.drawText(campusName.uppercase(), A4_WIDTH / 2f, y + 40f, subtitlePaint)

        // Student Photo on top right if present
        decodeBase64Bitmap(summary.student.photoBase64)?.let { stuBmp ->
            val photoRect = RectF(A4_WIDTH - MARGIN - 58f, y, A4_WIDTH - MARGIN - 4f, y + 60f)
            canvas.drawBitmap(stuBmp, null, photoRect, null)
            val frame = Paint().apply { style = Paint.Style.STROKE; color = borderColor; strokeWidth = 1f }
            canvas.drawRect(photoRect, frame)
        }

        y += 68f
        // RESULT CARD Ribbon
        val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navyColor }
        val ribbonRect = RectF(A4_WIDTH / 2f - 105f, y, A4_WIDTH / 2f + 105f, y + 24f)
        canvas.drawRoundRect(ribbonRect, 6f, 6f, ribbonPaint)
        val ribbonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("RESULT CARD", A4_WIDTH / 2f, y + 16.5f, ribbonTextPaint)

        y += 38f

        // Student Info Box
        val infoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lightBlueBg }
        val infoRect = RectF(MARGIN, y, A4_WIDTH - MARGIN, y + 88f)
        canvas.drawRoundRect(infoRect, 8f, 8f, infoBgPaint)
        val infoStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = 1f
        }
        canvas.drawRoundRect(infoRect, 8f, 8f, infoStroke)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val leftX = MARGIN + 12f
        val rightX = A4_WIDTH / 2f + 12f
        canvas.drawText("Student Name:", leftX, y + 22f, labelPaint)
        canvas.drawText(summary.student.name, leftX + 86f, y + 22f, valPaint)
        canvas.drawText("Father's Name:", leftX, y + 42f, labelPaint)
        canvas.drawText(summary.student.fatherName, leftX + 86f, y + 42f, valPaint)
        canvas.drawText("Roll Number:", leftX, y + 62f, labelPaint)
        canvas.drawText(summary.student.rollNumber, leftX + 86f, y + 62f, valPaint)
        canvas.drawText("Admission No:", leftX, y + 80f, labelPaint)
        canvas.drawText(summary.student.admissionNumber, leftX + 86f, y + 80f, valPaint)

        canvas.drawText("Academic Session:", rightX, y + 22f, labelPaint)
        canvas.drawText(summary.sessionName, rightX + 98f, y + 22f, valPaint)
        canvas.drawText("Examination:", rightX, y + 42f, labelPaint)
        canvas.drawText(summary.examName, rightX + 98f, y + 42f, valPaint)
        canvas.drawText("Class & Section:", rightX, y + 62f, labelPaint)
        canvas.drawText("${summary.className} - ${summary.sectionName}", rightX + 98f, y + 62f, valPaint)
        canvas.drawText("Result Status:", rightX, y + 80f, labelPaint)
        val statusTextPaint = Paint(valPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = when {
                summary.status == ResultStatus.INCOMPLETE -> goldColor
                summary.passed -> passGreen
                else -> failRed
            }
        }
        val statusLabel = when {
            summary.status == ResultStatus.INCOMPLETE -> "INCOMPLETE"
            summary.passed -> "PASS"
            else -> "FAIL"
        }
        canvas.drawText(statusLabel, rightX + 98f, y + 80f, statusTextPaint)

        y += 104f

        // Marks Table
        val colWidths = listOf(185f, 85f, 95f, 85f, 77f) // total = 527 = 595 - 68
        val headers = listOf("Subject", "Total Marks", "Obtained Marks", "Percentage", "Grade")
        y = drawTableHeaderRow(canvas, MARGIN, y, colWidths, headers, 26f)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; textSize = 10f }
        summary.subjectResults.forEachIndexed { idx, sr ->
            val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
            val cells = listOf(
                sr.subject.name + if (sr.subject.compulsory) "" else " (Elec.)",
                ResultCalculator.formatMarks(sr.subject.maxMarks),
                sr.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "Pending",
                sr.percentage?.let { ResultCalculator.formatPercentage(it) } ?: "-",
                sr.grade
            )
            y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, 25f, bg, cellPaint)
        }

        // Totals Row
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val totalCells = listOf(
            "GRAND TOTAL",
            ResultCalculator.formatMarks(summary.totalMaxMarks),
            ResultCalculator.formatMarks(summary.totalObtainedMarks),
            ResultCalculator.formatPercentage(summary.overallPercentage),
            summary.overallGrade
        )
        y = drawTableDataRow(canvas, MARGIN, y, colWidths, totalCells, 28f, lightBlueBg, totalPaint)

        y += 18f

        // Bottom Summary Box
        val sumRect = RectF(MARGIN, y, A4_WIDTH - MARGIN, y + 92f)
        canvas.drawRoundRect(sumRect, 8f, 8f, infoBgPaint)
        canvas.drawRoundRect(sumRect, 8f, 8f, infoStroke)

        canvas.drawText("Total Marks:", leftX, y + 22f, labelPaint)
        canvas.drawText(ResultCalculator.formatMarks(summary.totalMaxMarks), leftX + 86f, y + 22f, valPaint)
        canvas.drawText("Obtained Marks:", leftX, y + 42f, labelPaint)
        canvas.drawText(ResultCalculator.formatMarks(summary.totalObtainedMarks), leftX + 86f, y + 42f, valPaint)
        canvas.drawText("Overall %:", leftX, y + 62f, labelPaint)
        canvas.drawText(ResultCalculator.formatPercentage(summary.overallPercentage), leftX + 86f, y + 62f, valPaint)

        canvas.drawText("Overall Grade:", rightX, y + 22f, labelPaint)
        canvas.drawText("${summary.overallGrade} (${summary.gradeRemarks})", rightX + 92f, y + 22f, valPaint)
        canvas.drawText("Class Position:", rightX, y + 42f, labelPaint)
        canvas.drawText(ResultCalculator.formatOrdinal(summary.position), rightX + 92f, y + 42f, valPaint)
        canvas.drawText("Result Status:", rightX, y + 62f, labelPaint)
        canvas.drawText(statusLabel, rightX + 92f, y + 62f, statusTextPaint)

        canvas.drawText("Teacher's Remarks:", leftX, y + 82f, labelPaint)
        canvas.drawText(summary.teacherRemarks.take(72), leftX + 104f, y + 82f, valPaint)

        // Signature Section near bottom
        val sigY = A4_HEIGHT - 95f
        val linePaint = Paint().apply { color = darkText; strokeWidth = 1f }
        val sigLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val sigPositions = listOf(
            Pair(MARGIN + 75f, "Class Teacher"),
            Pair(A4_WIDTH / 2f, "Examination In-charge"),
            Pair(A4_WIDTH - MARGIN - 75f, "Principal")
        )
        sigPositions.forEach { (cx, title) ->
            canvas.drawLine(cx - 60f, sigY, cx + 60f, sigY, linePaint)
            canvas.drawText(title, cx, sigY + 15f, sigLabelPaint)
        }

        drawPageFooter(canvas, pageNumber, totalPages)
    }

    // --- DRAWING HELPERS ---

    private fun drawStandardHeader(
        canvas: Canvas,
        schoolName: String,
        campusName: String,
        reportTitle: String,
        subtitle: String,
        logoBitmap: Bitmap?
    ): Float {
        var y = 32f
        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, null, RectF(MARGIN, y, MARGIN + 48f, y + 48f), null)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val campusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blueColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val reportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(schoolName.uppercase(), A4_WIDTH / 2f, y + 16f, titlePaint)
        canvas.drawText(campusName.uppercase(), A4_WIDTH / 2f, y + 31f, campusPaint)
        canvas.drawText(reportTitle, A4_WIDTH / 2f, y + 49f, reportPaint)
        canvas.drawText(subtitle, A4_WIDTH / 2f, y + 63f, subPaint)
        y += 74f
        return y
    }

    private fun drawTableHeaderRow(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        colWidths: List<Float>,
        headers: List<String>,
        rowHeight: Float
    ): Float {
        val bgPaint = Paint().apply { color = navyColor }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (colWidths.size > 9) 7.5f else 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val strokePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 0.6f }

        var x = startX
        headers.forEachIndexed { idx, h ->
            val w = colWidths[idx]
            val rect = RectF(x, startY, x + w, startY + rowHeight)
            canvas.drawRect(rect, bgPaint)
            canvas.drawRect(rect, strokePaint)
            canvas.drawText(h, x + 4f, startY + rowHeight / 2f + 3f, textPaint)
            x += w
        }
        return startY + rowHeight
    }

    private fun drawTableDataRow(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        colWidths: List<Float>,
        cells: List<String>,
        rowHeight: Float,
        bgColor: Int,
        textPaint: Paint
    ): Float {
        val bgPaint = Paint().apply { color = bgColor }
        val strokePaint = Paint().apply { color = borderColor; style = Paint.Style.STROKE; strokeWidth = 0.7f }
        var x = startX
        cells.forEachIndexed { idx, value ->
            val w = colWidths[idx]
            val rect = RectF(x, startY, x + w, startY + rowHeight)
            canvas.drawRect(rect, bgPaint)
            canvas.drawRect(rect, strokePaint)
            canvas.drawText(value, x + 4f, startY + rowHeight / 2f + 3.5f, textPaint)
            x += w
        }
        return startY + rowHeight
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, totalPages: Int) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "The Educators Abbottabad Main Campus — Page $pageNumber of $totalPages",
            A4_WIDTH / 2f,
            A4_HEIGHT - 28f,
            footerPaint
        )
    }

    private fun decodeLogoBitmap(context: Context, logoBase64: String?): Bitmap? {
        return decodeBase64Bitmap(logoBase64)
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.img_school_crest)
    }

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun savePdfToExports(context: Context, doc: PdfDocument, fileName: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        return file
    }

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    val fd = destination?.fileDescriptor
                    if (fd == null) {
                        callback?.onWriteFailed("Invalid print destination file descriptor")
                        return
                    }
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(fd).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(
            jobName,
            adapter,
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
        )
    }
}
