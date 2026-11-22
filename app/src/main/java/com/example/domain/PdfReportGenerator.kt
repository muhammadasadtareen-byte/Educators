package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.example.data.model.ExamComponent
import com.example.data.model.ResultStatus
import com.example.data.model.StudentResultSummary
import com.example.data.model.Subject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.ceil

object PdfReportGenerator {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val A4_LANDSCAPE_WIDTH = 842
    private const val A4_LANDSCAPE_HEIGHT = 595
    private const val MARGIN = 30f

    private val navyColor = Color.parseColor("#08408A")
    private val tealColor = Color.parseColor("#0B4F6C")
    private val blueColor = Color.parseColor("#1565C0")
    private val ribbonRoseColor = Color.parseColor("#C83E6B")
    private val lightBlueBg = Color.parseColor("#EAF2FF")
    private val lightGrayHeaderBg = Color.parseColor("#D8DEE9")
    private val borderColor = Color.parseColor("#64748B")
    private val lightBorderColor = Color.parseColor("#CBD5E1")
    private val darkText = Color.parseColor("#0F172A")
    private val mutedText = Color.parseColor("#475569")
    private val goldColor = Color.parseColor("#D97706")
    private val passGreen = Color.parseColor("#15803D")
    private val failRed = Color.parseColor("#B91C1C")

    private val chartPalette = listOf(
        Color.parseColor("#1D4ED8"), // Component 1 / Blue
        Color.parseColor("#0891B2"), // Component 2 / Cyan
        Color.parseColor("#7C3AED"), // Component 3 / Purple
        Color.parseColor("#D97706"), // Component 4 / Amber
        Color.parseColor("#0F766E"), // Obtained / Teal
        Color.parseColor("#0F172A")  // Highest in Class / Slate Navy
    )

    // 1. Individual & Batch Student Progress Reports (1 per Portrait A4 page)
    fun generateResultCardsPdf(
        context: Context,
        summaries: List<StudentResultSummary>,
        schoolName: String,
        campusName: String,
        schoolLogoBase64: String?,
        address: String = "",
        contact: String = "",
        secondaryLogoBase64: String? = null
    ): File {
        val doc = PdfDocument()
        val logoBitmap = decodeLogoBitmap(context, schoolLogoBase64)
        val secondaryLogoBitmap = decodeBase64Bitmap(secondaryLogoBase64)
        val totalPages = summaries.size.coerceAtLeast(1)

        summaries.forEachIndexed { idx, summary ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, idx + 1).create()
            val page = doc.startPage(pageInfo)
            drawResultCardPage(
                canvas = page.canvas,
                summary = summary,
                schoolName = schoolName,
                campusName = campusName,
                address = address,
                contact = contact,
                logoBitmap = logoBitmap,
                secondaryLogoBitmap = secondaryLogoBitmap,
                pageNumber = idx + 1,
                totalPages = totalPages
            )
            doc.finishPage(page)
        }

        val fileName = if (summaries.size == 1) {
            val rollPrefix = summaries.first().student.rollNumber.takeIf { it.isNotBlank() }?.let { "${it}_" } ?: ""
            "ProgressReport_${rollPrefix}${summaries.first().student.name.replace(" ", "_")}.pdf"
        } else {
            "All_ProgressReports_${System.currentTimeMillis()}.pdf"
        }
        return savePdfToExports(context, doc, fileName)
    }

    // 2. Complete Class Consolidated Sheet (Multi-page Landscape A4 matching school format)
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
        summaries: List<StudentResultSummary>,
        address: String = "",
        contact: String = "",
        examComponents: List<ExamComponent> = emptyList(),
        secondaryLogoBase64: String? = null
    ): File {
        val doc = PdfDocument()
        val logoBitmap = decodeLogoBitmap(context, schoolLogoBase64)
        val secondaryLogoBitmap = decodeBase64Bitmap(secondaryLogoBase64)
        val sortedSubjects = subjects.sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name })
        val useComponents = examComponents.isNotEmpty() && sortedSubjects.isNotEmpty()
        val compTotal = examComponents.sumOf { it.maxMarks }
        val totalMax = summaries.firstOrNull()?.totalMaxMarks
            ?: sortedSubjects.sumOf { if (useComponents && compTotal > 0.0) compTotal else it.maxMarks }

        // Resolve per-subject effective component max marks (honoring any per-subject overrides)
        val effectiveComponentsBySubjectId: Map<String, List<ExamComponent>> = sortedSubjects.associate { sub ->
            val resolved = summaries.firstOrNull()
                ?.subjectResults
                ?.firstOrNull { it.subject.id == sub.id }
                ?.effectiveComponents
                ?.takeIf { it.isNotEmpty() }
                ?: examComponents
            sub.id to resolved
        }

        // Compute landscape column widths cleanly so all subjects, assessments, totals & grades fit
        val startX = 24f
        val usableW = A4_LANDSCAPE_WIDTH - 48f // 794f
        val nameW = 104f
        val obtW = 44f
        val pctW = 36f
        val gradeW = 38f
        val middleTotalW = (usableW - nameW - obtW - pctW - gradeW).coerceAtLeast(120f)

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Pre-compute banner height and wrapped header layouts so rowsPerPage is always exact
        var bannerEndY = 37f + 14f // school + campus
        if (address.isNotBlank()) bannerEndY += 12f
        if (contact.isNotBlank()) bannerEndY += 11f
        bannerEndY += 14f + 14f + 10f // Consolidated Sheet + Class/Exam + gap

        data class WrappedHeaderCell(
            val lines: List<String>,
            val textSize: Float
        )

        val subjectTopCells = mutableListOf<WrappedHeaderCell>()
        val componentSubCells = mutableListOf<List<WrappedHeaderCell>>()
        val singleSubCells = mutableListOf<WrappedHeaderCell>()
        val compColW: Float
        val topRowH: Float
        val subRowH: Float
        val totalHeaderH: Float

        if (useComponents) {
            val totalSubCols = sortedSubjects.size * examComponents.size
            compColW = middleTotalW / totalSubCols.coerceAtLeast(1)
            val baseSubjSize = when {
                sortedSubjects.size > 9 -> 6.8f
                sortedSubjects.size > 7 -> 7.4f
                else -> 8.2f
            }
            val baseCompSize = when {
                compColW >= 54f -> 6.8f
                compColW >= 40f -> 6.0f
                compColW >= 32f -> 5.5f
                else -> 4.9f
            }

            var maxTopLines = 1
            var maxSubLines = 2
            var minCompFontUsed = baseCompSize

            sortedSubjects.forEach { sub ->
                val groupW = compColW * examComponents.size
                val (sLines, sSize) = fitWrappedLines(
                    text = sub.name,
                    maxWidth = (groupW - 4f).coerceAtLeast(14f),
                    baseTextSize = baseSubjSize,
                    minTextSize = 4.6f,
                    paint = headerTextPaint
                )
                subjectTopCells.add(WrappedHeaderCell(sLines, sSize))
                if (sLines.size > maxTopLines) maxTopLines = sLines.size

                val subEffectiveComps = effectiveComponentsBySubjectId[sub.id].orEmpty()
                val cellsForSub = examComponents.map { comp ->
                    val effMax = subEffectiveComps.firstOrNull { it.id == comp.id }?.maxMarks ?: comp.maxMarks
                    val maxTag = "(${ResultCalculator.formatMarks(effMax)})"
                    val (cLines, cSize) = fitComponentHeaderLines(
                        componentName = comp.name,
                        maxMarksTag = maxTag,
                        maxWidth = (compColW - 3f).coerceAtLeast(12f),
                        baseTextSize = baseCompSize,
                        minTextSize = 4.0f,
                        paint = headerTextPaint
                    )
                    if (cLines.size > maxSubLines) maxSubLines = cLines.size
                    if (cSize < minCompFontUsed) minCompFontUsed = cSize
                    WrappedHeaderCell(cLines, cSize)
                }
                componentSubCells.add(cellsForSub)
            }

            val topLineStep = baseSubjSize + 2.2f
            val subLineStep = baseCompSize + 1.8f
            topRowH = (maxTopLines * topLineStep + 7f).coerceAtLeast(18f)
            subRowH = (maxSubLines * subLineStep + 7f).coerceAtLeast(22f)
            totalHeaderH = topRowH + subRowH
        } else {
            val subCount = sortedSubjects.size.coerceAtLeast(1)
            compColW = middleTotalW / subCount
            val baseSize = if (sortedSubjects.size > 8) 7.2f else 8.2f
            var maxLines = 1
            sortedSubjects.forEach { sub ->
                val (lines, sz) = fitComponentHeaderLines(
                    componentName = sub.name,
                    maxMarksTag = "(${ResultCalculator.formatMarks(sub.maxMarks)})",
                    maxWidth = (compColW - 4f).coerceAtLeast(14f),
                    baseTextSize = baseSize,
                    minTextSize = 4.6f,
                    paint = headerTextPaint
                )
                singleSubCells.add(WrappedHeaderCell(lines, sz))
                if (lines.size > maxLines) maxLines = lines.size
            }
            topRowH = (maxLines * (baseSize + 2.0f) + 8f).coerceAtLeast(24f)
            subRowH = 0f
            totalHeaderH = topRowH
        }

        val rowH = 18.5f
        val footerSafeBottom = A4_LANDSCAPE_HEIGHT - 36f
        val availableBodyH = (footerSafeBottom - (bannerEndY + totalHeaderH)).coerceAtLeast(rowH * 10f)
        val rowsPerPage = (availableBodyH / rowH).toInt().coerceIn(12, 22)
        val chunks = if (summaries.isEmpty()) listOf(emptyList()) else summaries.chunked(rowsPerPage)
        val totalPages = chunks.size

        chunks.forEachIndexed { pageIdx, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_LANDSCAPE_WIDTH, A4_LANDSCAPE_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Outer box border
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
            }
            canvas.drawRect(18f, 18f, A4_LANDSCAPE_WIDTH - 18f, A4_LANDSCAPE_HEIGHT - 18f, framePaint)

            // Top-left school logo & optional top-right secondary logo (left empty if not uploaded)
            if (logoBitmap != null) {
                canvas.drawBitmap(logoBitmap, null, RectF(26f, 24f, 74f, 72f), null)
            }
            if (secondaryLogoBitmap != null) {
                canvas.drawBitmap(secondaryLogoBitmap, null, RectF(A4_LANDSCAPE_WIDTH - 74f, 24f, A4_LANDSCAPE_WIDTH - 26f, 72f), null)
            }

            // Centered Consolidated Sheet Header
            val centerX = A4_LANDSCAPE_WIDTH / 2f
            val hTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                textSize = 15.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val hCampusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val hMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            val hSheetTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                isUnderlineText = true
                textAlign = Paint.Align.CENTER
            }
            val hExamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                textSize = 11f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            var y = 37f
            canvas.drawText(schoolName.uppercase(), centerX, y, hTitlePaint)
            y += 14f
            canvas.drawText(campusName.uppercase(), centerX, y, hCampusPaint)
            if (address.isNotBlank()) {
                y += 12f
                canvas.drawText("Address: $address", centerX, y, hMetaPaint)
            }
            if (contact.isNotBlank()) {
                y += 11f
                canvas.drawText("Contact: $contact", centerX, y, hMetaPaint)
            }
            y += 14f
            canvas.drawText("Consolidated Sheet", centerX, y, hSheetTitlePaint)
            y += 14f
            canvas.drawText("$className - $sectionLabel *** ${examName.uppercase()}", centerX, y, hExamPaint)
            y += 10f

            val subColWidths = mutableListOf<Float>()
            val headerBgPaint = Paint().apply { color = tealColor }
            val headerStrokePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 0.7f }

            if (useComponents) {
                // Name vertical cell
                val nameRect = RectF(startX, y, startX + nameW, y + totalHeaderH)
                canvas.drawRect(nameRect, headerBgPaint)
                canvas.drawRect(nameRect, headerStrokePaint)
                headerTextPaint.textSize = 9.5f
                headerTextPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("Name", startX + 4f, y + totalHeaderH / 2f + 3f, headerTextPaint)
                headerTextPaint.textAlign = Paint.Align.CENTER

                var x = startX + nameW
                sortedSubjects.forEachIndexed { subIdx, _ ->
                    val groupW = compColW * examComponents.size
                    val topRect = RectF(x, y, x + groupW, y + topRowH)
                    canvas.drawRect(topRect, headerBgPaint)
                    canvas.drawRect(topRect, headerStrokePaint)
                    val subjCell = subjectTopCells[subIdx]
                    drawCenteredLines(
                        canvas = canvas,
                        lines = subjCell.lines,
                        rect = topRect,
                        textSize = subjCell.textSize,
                        paint = headerTextPaint
                    )

                    val subCells = componentSubCells[subIdx]
                    subCells.forEach { cCell ->
                        val compRect = RectF(x, y + topRowH, x + compColW, y + totalHeaderH)
                        canvas.drawRect(compRect, headerBgPaint)
                        canvas.drawRect(compRect, headerStrokePaint)
                        drawCenteredLines(
                            canvas = canvas,
                            lines = cCell.lines,
                            rect = compRect,
                            textSize = cCell.textSize,
                            paint = headerTextPaint
                        )
                        subColWidths.add(compColW)
                        x += compColW
                    }
                }

                // Obt., % age, Grade vertical cells (multi-line so Obt. (400) never clips)
                val tailHeaders = listOf(
                    listOf("Obt.", "(${ResultCalculator.formatMarks(totalMax)})") to obtW,
                    listOf("% age") to pctW,
                    listOf("Grade") to gradeW
                )
                tailHeaders.forEach { (lines, w) ->
                    val rect = RectF(x, y, x + w, y + totalHeaderH)
                    canvas.drawRect(rect, headerBgPaint)
                    canvas.drawRect(rect, headerStrokePaint)
                    drawCenteredLines(
                        canvas = canvas,
                        lines = lines,
                        rect = rect,
                        textSize = 7.8f,
                        paint = headerTextPaint
                    )
                    x += w
                }
                y += totalHeaderH
            } else {
                var x = startX
                val nameRect = RectF(x, y, x + nameW, y + totalHeaderH)
                canvas.drawRect(nameRect, headerBgPaint)
                canvas.drawRect(nameRect, headerStrokePaint)
                headerTextPaint.textSize = 9.2f
                headerTextPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("Name", x + 4f, y + totalHeaderH / 2f + 3f, headerTextPaint)
                headerTextPaint.textAlign = Paint.Align.CENTER
                x += nameW

                singleSubCells.forEach { sCell ->
                    val rect = RectF(x, y, x + compColW, y + totalHeaderH)
                    canvas.drawRect(rect, headerBgPaint)
                    canvas.drawRect(rect, headerStrokePaint)
                    drawCenteredLines(
                        canvas = canvas,
                        lines = sCell.lines,
                        rect = rect,
                        textSize = sCell.textSize,
                        paint = headerTextPaint
                    )
                    subColWidths.add(compColW)
                    x += compColW
                }

                val tailHeaders = listOf(
                    listOf("Obt.", "(${ResultCalculator.formatMarks(totalMax)})") to obtW,
                    listOf("% age") to pctW,
                    listOf("Grade") to gradeW
                )
                tailHeaders.forEach { (lines, w) ->
                    val rect = RectF(x, y, x + w, y + totalHeaderH)
                    canvas.drawRect(rect, headerBgPaint)
                    canvas.drawRect(rect, headerStrokePaint)
                    drawCenteredLines(
                        canvas = canvas,
                        lines = lines,
                        rect = rect,
                        textSize = 7.8f,
                        paint = headerTextPaint
                    )
                    x += w
                }
                y += totalHeaderH
            }

            // Data rows
            val allWidths = listOf(nameW) + subColWidths + listOf(obtW, pctW, gradeW)
            val baseCellTextSize = if (allWidths.size > 16) 7.1f else 8.2f
            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkText
                textSize = baseCellTextSize
            }
            val cellStroke = Paint().apply { color = borderColor; style = Paint.Style.STROKE; strokeWidth = 0.6f }

            pageRows.forEachIndexed { rIdx, summary ->
                val bg = if (rIdx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
                val bgPaint = Paint().apply { color = bg }
                val values = buildList {
                    add(summary.student.name)
                    if (useComponents) {
                        sortedSubjects.forEach { sub ->
                            val sr = summary.subjectResults.firstOrNull { it.subject.id == sub.id }
                            examComponents.forEach { comp ->
                                val compMark = sr?.resolvedComponentMarks?.get(comp.id)
                                add(compMark?.let { ResultCalculator.formatMarks(it) } ?: "-")
                            }
                        }
                    } else {
                        sortedSubjects.forEach { sub ->
                            val sr = summary.subjectResults.firstOrNull { it.subject.id == sub.id }
                            add(sr?.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "-")
                        }
                    }
                    add(ResultCalculator.formatMarks(summary.totalObtainedMarks))
                    add(String.format(Locale.US, "%.1f%%", summary.overallPercentage))
                    add(summary.overallGrade)
                }

                var x = startX
                values.forEachIndexed { cIdx, v ->
                    val w = allWidths[cIdx]
                    val rect = RectF(x, y, x + w, y + rowH)
                    canvas.drawRect(rect, bgPaint)
                    canvas.drawRect(rect, cellStroke)
                    cellPaint.color = if (cIdx == values.lastIndex && !summary.passed && summary.status == ResultStatus.COMPLETE) {
                        failRed
                    } else darkText
                    cellPaint.typeface = if (cIdx == 0 || cIdx >= values.size - 3) {
                        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    } else Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    cellPaint.textAlign = if (cIdx == 0) Paint.Align.LEFT else Paint.Align.CENTER

                    // Gently auto-shrink text size for long names or narrow cells so full data shows
                    val innerMaxW = (w - if (cIdx == 0) 6f else 3f).coerceAtLeast(8f)
                    var sz = baseCellTextSize
                    cellPaint.textSize = sz
                    while (sz > 5.4f && cellPaint.measureText(v) > innerMaxW) {
                        sz -= 0.35f
                        cellPaint.textSize = sz
                    }
                    val tx = if (cIdx == 0) x + 3.5f else x + w / 2f
                    val baselineY = y + rowH / 2f - (cellPaint.descent() + cellPaint.ascent()) / 2f
                    canvas.drawText(fitText(v, innerMaxW, cellPaint), tx, baselineY, cellPaint)
                    x += w
                }
                y += rowH
            }

            // Footer
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                textSize = 8f
            }
            footerPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("$schoolName — $campusName", startX, A4_LANDSCAPE_HEIGHT - 26f, footerPaint)
            footerPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Page ${pageIdx + 1}/$totalPages  •  Session: $sessionName", A4_LANDSCAPE_WIDTH - 24f, A4_LANDSCAPE_HEIGHT - 26f, footerPaint)

            doc.finishPage(page)
        }

        return savePdfToExports(context, doc, "Consolidated_Sheet_${className.replace(" ", "_")}.pdf")
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

        val effectiveMax = summaries.firstOrNull()
            ?.subjectResults
            ?.firstOrNull { it.subject.id == subject.id }
            ?.effectiveMaxMarks
            ?: subject.maxMarks

        chunks.forEachIndexed { pageIdx, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawStandardHeader(
                canvas,
                schoolName,
                campusName,
                "SUBJECT MARKS SHEET — ${subject.name.uppercase()}",
                "$className ($sectionName) | $examName | $sessionName | Max: ${ResultCalculator.formatMarks(effectiveMax)}",
                logoBitmap
            )

            val colWidths = listOf(55f, 145f, 125f, 65f, 65f, 45f, 35f)
            val headers = listOf("Roll No", "Student Name", "Father's Name", "Max Marks", "Obtained", "%", "Grade")
            y = drawTableHeaderRow(canvas, MARGIN, y, colWidths, headers, 24f)

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; textSize = 9f }
            pageRows.forEachIndexed { idx, summary ->
                val sr = summary.subjectResults.firstOrNull { it.subject.id == subject.id }
                val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
                val cells = listOf(
                    summary.student.rollNumber.ifBlank { "-" },
                    summary.student.name.take(24),
                    summary.student.fatherName.takeIf { it.isNotBlank() && it != "-" }?.take(20) ?: "",
                    ResultCalculator.formatMarks(sr?.effectiveMaxMarks ?: effectiveMax),
                    sr?.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "Pending",
                    sr?.percentage?.let { String.format(Locale.US, "%.1f%%", it) } ?: "-",
                    sr?.grade ?: "-"
                )
                y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, 22f, bg, cellPaint)
            }

            drawPageFooter(canvas, campusName, pageIdx + 1, totalPages)
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

        val colWidths = listOf(108f, 65f, 70f, 65f, 55f, 55f, 62f, 55f)
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
                String.format(Locale.US, "%.1f%%", passRate),
                String.format(Locale.US, "%.1f%%", st.averagePercentage)
            )
            y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, 22f, bg, cellPaint)
        }

        drawPageFooter(canvas, campusName, 1, 1)
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

            val colWidths = listOf(45f, 125f, 80f, 55f, 60f, 58f, 42f, 70f)
            val headers = listOf("Roll", "Student Name", "Class", "Total", "Obtained", "%", "Grade", "Status")
            y = drawTableHeaderRow(canvas, MARGIN, y, colWidths, headers, 24f)

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; textSize = 9f }
            pageRows.forEachIndexed { idx, s ->
                val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
                val status = when {
                    s.status == ResultStatus.INCOMPLETE -> "Pending"
                    s.passed -> "Pass (${ResultCalculator.formatOrdinal(s.position)})"
                    else -> "Fail"
                }
                val cells = listOf(
                    s.student.rollNumber.ifBlank { "-" },
                    s.student.name.take(20),
                    "${s.className}-${s.sectionName}",
                    ResultCalculator.formatMarks(s.totalMaxMarks),
                    ResultCalculator.formatMarks(s.totalObtainedMarks),
                    String.format(Locale.US, "%.1f%%", s.overallPercentage),
                    s.overallGrade,
                    status
                )
                y = drawTableDataRow(canvas, MARGIN, y, colWidths, cells, 22f, bg, cellPaint)
            }

            drawPageFooter(canvas, campusName, pageIdx + 1, totalPages)
            doc.finishPage(page)
        }
        return savePdfToExports(context, doc, "Student_Result_Summaries.pdf")
    }

    // --- INDIVIDUAL A4 PROGRESS REPORT RENDERER ---

    private fun drawResultCardPage(
        canvas: Canvas,
        summary: StudentResultSummary,
        schoolName: String,
        campusName: String,
        address: String,
        contact: String,
        logoBitmap: Bitmap?,
        secondaryLogoBitmap: Bitmap?,
        pageNumber: Int,
        totalPages: Int
    ) {
        val contentLeft = MARGIN
        val contentRight = A4_WIDTH - MARGIN
        val contentW = contentRight - contentLeft // 535f
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        // Subtle outer border
        val outerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRect(18f, 18f, A4_WIDTH - 18f, A4_HEIGHT - 18f, outerBorder)

        // Top Left Logo + Centered "Progress Report" Ribbon + Optional Top Right Image (empty if not uploaded)
        var y = 26f
        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, null, RectF(contentLeft, y, contentLeft + 46f, y + 46f), null)
        }
        val stuBmp = decodeBase64Bitmap(summary.student.photoBase64)
        val rightRect = RectF(contentRight - 46f, y, contentRight, y + 46f)
        when {
            secondaryLogoBitmap != null -> {
                canvas.drawBitmap(secondaryLogoBitmap, null, rightRect, null)
            }
            stuBmp != null -> {
                canvas.drawBitmap(stuBmp, null, rightRect, null)
                canvas.drawRect(rightRect, strokePaint)
            }
            // Otherwise leave right corner completely empty
        }

        // Pink/Crimson Progress Report Ribbon Header (balanced symmetrically)
        val ribbonLeft = contentLeft + 56f
        val ribbonRight = contentRight - 56f
        val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ribbonRoseColor }
        canvas.drawRoundRect(RectF(ribbonLeft, y + 2f, ribbonRight, y + 32f), 4f, 4f, ribbonPaint)
        val ribbonText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Progress Report", (ribbonLeft + ribbonRight) / 2f, y + 22f, ribbonText)

        y += 50f

        // Centered School Name, Exam Name, Campus Name
        val schoolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val examTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val campusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(schoolName.uppercase(), A4_WIDTH / 2f, y, schoolPaint)
        y += 14f
        canvas.drawText(summary.examName.uppercase(), A4_WIDTH / 2f, y, examTitlePaint)
        y += 13f
        canvas.drawText(campusName.uppercase(), A4_WIDTH / 2f, y, campusPaint)
        if (address.isNotBlank() || contact.isNotBlank()) {
            val metaStr = listOfNotNull(
                address.takeIf { it.isNotBlank() },
                contact.takeIf { it.isNotBlank() }?.let { "Ph: $it" }
            ).joinToString(" | ")
            val metaP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mutedText
                textSize = 8f
                textAlign = Paint.Align.CENTER
            }
            y += 11f
            canvas.drawText(metaStr, A4_WIDTH / 2f, y, metaP)
        }
        y += 8f

        // Particulars Grid (Clean bordered table)
        val hasFather = summary.student.fatherName.isNotBlank() && summary.student.fatherName != "-"
        val hasRoll = summary.student.rollNumber.isNotBlank()
        val hasAdm = summary.student.admissionNumber.isNotBlank()

        val labelBg = Paint().apply { color = Color.parseColor("#E5E7EB") }
        val whiteBg = Paint().apply { color = Color.WHITE }
        val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val gridValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val pRowH = 18f
        val lw1 = 82f
        val vw1 = 185f
        val lw2 = 82f
        val vw2 = contentW - lw1 - vw1 - lw2

        fun drawParticularRow(l1: String, v1: String, l2: String, v2: String) {
            val c = listOf(lw1 to l1, vw1 to v1, lw2 to l2, vw2 to v2)
            var rx = contentLeft
            c.forEachIndexed { idx, (w, txt) ->
                val rect = RectF(rx, y, rx + w, y + pRowH)
                canvas.drawRect(rect, if (idx % 2 == 0) labelBg else whiteBg)
                canvas.drawRect(rect, strokePaint)
                val p = if (idx % 2 == 0) gridLabelPaint else gridValPaint
                if (txt.isNotBlank()) {
                    canvas.drawText(fitText(txt, w - 6f, p), rx + 4f, y + 12.5f, p)
                }
                rx += w
            }
            y += pRowH
        }

        drawParticularRow(
            "Student Name", summary.student.name,
            if (hasFather) "Father's Name" else "Position",
            if (hasFather) summary.student.fatherName else ResultCalculator.formatOrdinal(summary.position)
        )
        drawParticularRow(
            "Class - Section", "${summary.className} - ${summary.sectionName}",
            "Session", summary.sessionName
        )
        if (hasRoll || hasAdm) {
            drawParticularRow(
                if (hasRoll) "Roll Number" else "", if (hasRoll) summary.student.rollNumber else "",
                if (hasAdm) "Admission No" else "", if (hasAdm) summary.student.admissionNumber else ""
            )
        }

        y += 8f

        // Unified Marks Table
        val components = summary.examComponents
        val subjColW = if (components.isNotEmpty()) 112f else 170f
        val totColW = 54f
        val highColW = 54f
        val pctColW = 46f
        val gradeColW = 36f
        val compAreaW = (contentW - subjColW - totColW - highColW - pctColW - gradeColW).coerceAtLeast(0f)
        val singleCompW = if (components.isNotEmpty()) compAreaW / components.size else 0f

        val marksColWidths = buildList {
            add(subjColW)
            repeat(components.size) { add(singleCompW) }
            add(totColW); add(highColW); add(pctColW); add(gradeColW)
        }
        val marksHeaders = buildList {
            add("Subjects")
            components.forEach { add("${it.name}\n(${ResultCalculator.formatMarks(it.maxMarks)})") }
            add("Total Marks\nObtained")
            add("Highest Marks\nin Class")
            add("Percentage")
            add("Grade")
        }

        val mhH = 26f
        val mhBg = Paint().apply { color = lightGrayHeaderBg }
        val mhPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = if (components.size > 3) 7f else 7.8f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        var mx = contentLeft
        marksHeaders.forEachIndexed { idx, h ->
            val w = marksColWidths[idx]
            val rect = RectF(mx, y, mx + w, y + mhH)
            canvas.drawRect(rect, mhBg)
            canvas.drawRect(rect, strokePaint)
            val lines = h.split("\n")
            if (lines.size == 1) {
                canvas.drawText(fitText(lines[0], w - 4f, mhPaint), mx + w / 2f, y + 16f, mhPaint)
            } else {
                canvas.drawText(fitText(lines[0], w - 3f, mhPaint), mx + w / 2f, y + 10.5f, mhPaint)
                canvas.drawText(fitText(lines[1], w - 3f, mhPaint), mx + w / 2f, y + 21f, mhPaint)
            }
            mx += w
        }
        y += mhH

        val maxSubjectRows = summary.subjectResults.size.coerceAtLeast(1)
        val subjRowH = if (maxSubjectRows > 9) 15f else 17f
        val mCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = if (maxSubjectRows > 9) 7.5f else 8.3f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        summary.subjectResults.forEachIndexed { idx, sr ->
            val bg = if (idx % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
            val bgP = Paint().apply { color = bg }
            val rowCells = buildList {
                add(sr.subject.name + if (sr.passed == false && sr.subject.compulsory) " *" else "")
                components.forEach { comp ->
                    val compVal = sr.resolvedComponentMarks[comp.id]
                    add(compVal?.let { ResultCalculator.formatMarks(it) } ?: "-")
                }
                add(sr.obtainedMarks?.let { ResultCalculator.formatMarks(it) } ?: "-")
                add(sr.highestMarksInClass?.let { ResultCalculator.formatMarks(it) } ?: "-")
                add(sr.percentage?.let { String.format(Locale.US, "%.1f", it) } ?: "-")
                add(sr.grade)
            }
            mx = contentLeft
            rowCells.forEachIndexed { cIdx, v ->
                val w = marksColWidths[cIdx]
                val rect = RectF(mx, y, mx + w, y + subjRowH)
                canvas.drawRect(rect, bgP)
                canvas.drawRect(rect, strokePaint)
                mCellPaint.textAlign = if (cIdx == 0) Paint.Align.LEFT else Paint.Align.CENTER
                mCellPaint.color = if (cIdx == rowCells.lastIndex && sr.passed == false) failRed else darkText
                val tx = if (cIdx == 0) mx + 4f else mx + w / 2f
                canvas.drawText(fitText(v, w - 4f, mCellPaint), tx, y + subjRowH / 2f + 3f, mCellPaint)
                mx += w
            }
            y += subjRowH
        }

        // GRAND TOTAL row
        val gtH = 19f
        val gtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val gtCells = buildList {
            add("GRAND TOTAL (${ResultCalculator.formatMarks(summary.totalMaxMarks)})")
            repeat(components.size) { add("") }
            add(ResultCalculator.formatMarks(summary.totalObtainedMarks))
            add("")
            add(String.format(Locale.US, "%.1f", summary.overallPercentage))
            add(summary.overallGrade)
        }
        mx = contentLeft
        // Merge GRAND TOTAL label across Subject + component cols cleanly
        val mergedLabelW = subjColW + components.size * singleCompW
        val gtLabelRect = RectF(mx, y, mx + mergedLabelW, y + gtH)
        canvas.drawRect(gtLabelRect, mhBg)
        canvas.drawRect(gtLabelRect, strokePaint)
        gtPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(gtCells[0], mx + mergedLabelW / 2f, y + 13f, gtPaint)
        mx += mergedLabelW
        val tailStart = 1 + components.size
        for (cIdx in tailStart until gtCells.size) {
            val w = marksColWidths[cIdx]
            val rect = RectF(mx, y, mx + w, y + gtH)
            canvas.drawRect(rect, mhBg)
            canvas.drawRect(rect, strokePaint)
            canvas.drawText(gtCells[cIdx], mx + w / 2f, y + 13f, gtPaint)
            mx += w
        }
        y += gtH + 7f

        // Teacher's Remarks Box
        val remHeadH = 15f
        val remBodyH = 20f
        canvas.drawRect(RectF(contentLeft, y, contentRight, y + remHeadH), mhBg)
        canvas.drawRect(RectF(contentLeft, y, contentRight, y + remHeadH), strokePaint)
        gridLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Teacher's Remarks", contentLeft + 5f, y + 11f, gridLabelPaint)
        y += remHeadH
        canvas.drawRect(RectF(contentLeft, y, contentRight, y + remBodyH), whiteBg)
        canvas.drawRect(RectF(contentLeft, y, contentRight, y + remBodyH), strokePaint)
        canvas.drawText(fitText(summary.teacherRemarks, contentW - 10f, gridValPaint), contentLeft + 5f, y + 13f, gridValPaint)
        y += remBodyH + 8f

        // Compute remaining height for Chart, Signatures (3x17=51f), and Grading Key (44f)
        val bottomReserved = 130f // Signatures + Grading Key + footer margin
        val chartHeight = (A4_HEIGHT - 24f - bottomReserved - y).coerceIn(115f, 180f)

        // Draw Grouped Bar Chart + Legend
        drawProgressReportBarChart(
            canvas = canvas,
            summary = summary,
            left = contentLeft,
            top = y,
            width = contentW,
            height = chartHeight,
            strokePaint = strokePaint
        )
        y += chartHeight + 8f

        // 3-Row Signature | Dated Grid
        val sigRowH = 17f
        val sigColW = 150f
        val sigLineW = 140f
        val datedLabelW = 55f
        val datedValW = contentW - sigColW - sigLineW - datedLabelW
        val sigTitles = listOf(
            "Class Teacher's Signature",
            "Principal's Signature",
            "Parent/Guardian's Signature"
        )
        sigTitles.forEach { title ->
            var sx = contentLeft
            val r1 = RectF(sx, y, sx + sigColW, y + sigRowH)
            canvas.drawRect(r1, mhBg); canvas.drawRect(r1, strokePaint)
            canvas.drawText(title, sx + 5f, y + 11.5f, gridLabelPaint)
            sx += sigColW

            val r2 = RectF(sx, y, sx + sigLineW, y + sigRowH)
            canvas.drawRect(r2, whiteBg); canvas.drawRect(r2, strokePaint)
            sx += sigLineW

            val r3 = RectF(sx, y, sx + datedLabelW, y + sigRowH)
            canvas.drawRect(r3, mhBg); canvas.drawRect(r3, strokePaint)
            canvas.drawText("Dated", sx + 6f, y + 11.5f, gridLabelPaint)
            sx += datedLabelW

            val r4 = RectF(sx, y, sx + datedValW, y + sigRowH)
            canvas.drawRect(r4, whiteBg); canvas.drawRect(r4, strokePaint)
            y += sigRowH
        }
        y += 8f

        // Bottom GRADING KEY Table
        val rules = summary.gradingRulesUsed.ifEmpty { ResultCalculator.defaultGradingTemplates() }
            .sortedByDescending { it.minPercentage }
        val gkHeaderH = 15f
        val gkBodyH = 26f
        canvas.drawRect(RectF(contentLeft, y, contentRight, y + gkHeaderH), mhBg)
        canvas.drawRect(RectF(contentLeft, y, contentRight, y + gkHeaderH), strokePaint)
        val gkTitleP = Paint(gridLabelPaint).apply { textAlign = Paint.Align.CENTER; textSize = 8.5f }
        canvas.drawText("GRADING KEY", contentLeft + contentW / 2f, y + 11f, gkTitleP)
        y += gkHeaderH

        if (rules.isNotEmpty()) {
            val cellW = contentW / rules.size
            val gkGradeP = Paint(gridLabelPaint).apply { textAlign = Paint.Align.CENTER; textSize = 7.5f }
            val gkRangeP = Paint(gridValPaint).apply { textAlign = Paint.Align.CENTER; textSize = 7.2f }
            rules.forEachIndexed { idx, rule ->
                val cx = contentLeft + idx * cellW
                val r = RectF(cx, y, cx + cellW, y + gkBodyH)
                canvas.drawRect(r, whiteBg)
                canvas.drawRect(r, strokePaint)
                canvas.drawText("Grade ${rule.grade}", cx + cellW / 2f, y + 10.5f, gkGradeP)
                canvas.drawText(formatBandText(rule.minPercentage, rule.maxPercentage), cx + cellW / 2f, y + 21f, gkRangeP)
            }
        }

        drawPageFooter(canvas, campusName, pageNumber, totalPages)
    }

    private fun drawProgressReportBarChart(
        canvas: Canvas,
        summary: StudentResultSummary,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        strokePaint: Paint
    ) {
        // Outer chart border
        canvas.drawRect(RectF(left, top, left + width, top + height), Paint().apply { color = Color.WHITE })
        canvas.drawRect(RectF(left, top, left + width, top + height), strokePaint)

        val legendH = 20f
        val xLabelH = 18f
        val topPad = 10f
        val plotLeft = left + 30f
        val plotRight = left + width - 10f
        val plotTop = top + topPad
        val plotBottom = top + height - legendH - xLabelH
        val plotW = (plotRight - plotLeft).coerceAtLeast(40f)
        val plotH = (plotBottom - plotTop).coerceAtLeast(30f)

        val components = summary.examComponents
        // Compute Y max dynamically cleanly (e.g., max of subject maxMarks, obtained, highest)
        val rawMax = summary.subjectResults.maxOfOrNull { sr ->
            maxOf(sr.effectiveMaxMarks, sr.obtainedMarks ?: 0.0, sr.highestMarksInClass ?: 0.0)
        }?.coerceAtLeast(10.0) ?: 100.0
        val ySteps = 5
        val yMax = (ceil(rawMax / 10.0) * 10.0).coerceAtLeast(10.0)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lightBorderColor; strokeWidth = 0.6f }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; strokeWidth = 0.9f }
        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7f
            textAlign = Paint.Align.RIGHT
        }

        for (step in 0..ySteps) {
            val frac = step.toFloat() / ySteps.toFloat()
            val gy = plotBottom - frac * plotH
            canvas.drawLine(plotLeft, gy, plotRight, gy, if (step == 0) axisPaint else gridPaint)
            val tickVal = (yMax * frac).toInt()
            canvas.drawText(tickVal.toString(), plotLeft - 4f, gy + 2.5f, axisLabelPaint)
        }
        canvas.drawLine(plotLeft, plotTop, plotLeft, plotBottom, axisPaint)

        val subs = summary.subjectResults
        if (subs.isNotEmpty()) {
            val seriesPerSubject = (if (components.isNotEmpty()) components.size + 2 else 2)
            val clusterSlotW = plotW / subs.size
            val clusterInnerW = clusterSlotW * 0.78f
            val barW = (clusterInnerW / seriesPerSubject).coerceIn(2.5f, 12f)
            val barGap = 1.2f
            val actualClusterW = seriesPerSubject * barW + (seriesPerSubject - 1) * barGap

            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkText
                textSize = if (subs.size > 7) 6.5f else 7.2f
                textAlign = Paint.Align.CENTER
            }

            subs.forEachIndexed { idx, sr ->
                val cx = plotLeft + idx * clusterSlotW + clusterSlotW / 2f
                var bx = cx - actualClusterW / 2f

                val barValues = buildList {
                    components.forEachIndexed { cIdx, comp ->
                        add((sr.resolvedComponentMarks[comp.id] ?: 0.0) to chartPalette[cIdx % 4])
                    }
                    add((sr.obtainedMarks ?: 0.0) to chartPalette[4])
                    add((sr.highestMarksInClass ?: 0.0) to chartPalette[5])
                }

                barValues.forEach { (value, color) ->
                    val clampedH = ((value / yMax).coerceIn(0.0, 1.0) * plotH).toFloat()
                    if (clampedH > 0.5f) {
                        barPaint.color = color
                        canvas.drawRect(RectF(bx, plotBottom - clampedH, bx + barW, plotBottom), barPaint)
                    }
                    bx += barW + barGap
                }

                canvas.drawText(
                    fitText(sr.subject.name, clusterSlotW - 3f, subLabelPaint),
                    cx,
                    plotBottom + 11f,
                    subLabelPaint
                )
            }
        }

        // Chart Legend row inside bottom of chart container
        val legendItems = buildList {
            components.forEachIndexed { idx, comp -> add(comp.name to chartPalette[idx % 4]) }
            add("Obtained Marks" to chartPalette[4])
            add("Highest Marks in Class" to chartPalette[5])
        }
        val legendY = top + height - 7f
        val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = if (legendItems.size > 4) 6.3f else 7.2f
        }
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val itemSlotW = (width - 20f) / legendItems.size.coerceAtLeast(1)
        legendItems.forEachIndexed { idx, (lbl, col) ->
            val lx = left + 12f + idx * itemSlotW
            swatchPaint.color = col
            canvas.drawRect(RectF(lx, legendY - 7f, lx + 10f, legendY + 1f), swatchPaint)
            canvas.drawText(fitText(lbl, itemSlotW - 14f, legPaint), lx + 13f, legendY, legPaint)
        }
    }

    private fun formatBandText(minPct: Double, maxPct: Double): String {
        val minInt = minPct.toInt()
        val maxInt = if (maxPct >= 99.9) 100 else ceil(maxPct).toInt()
        return "$minInt% - $maxInt%"
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
            canvas.drawText(fitText(h, w - 6f, textPaint), x + 4f, startY + rowHeight / 2f + 3f, textPaint)
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
        val strokePaint = Paint().apply { color = lightBorderColor; style = Paint.Style.STROKE; strokeWidth = 0.7f }
        var x = startX
        cells.forEachIndexed { idx, value ->
            val w = colWidths[idx]
            val rect = RectF(x, startY, x + w, startY + rowHeight)
            canvas.drawRect(rect, bgPaint)
            canvas.drawRect(rect, strokePaint)
            canvas.drawText(fitText(value, w - 6f, textPaint), x + 4f, startY + rowHeight / 2f + 3.5f, textPaint)
            x += w
        }
        return startY + rowHeight
    }

    private fun drawPageFooter(canvas: Canvas, campusName: String, pageNumber: Int, totalPages: Int) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "$campusName — Page $pageNumber of $totalPages",
            A4_WIDTH / 2f,
            A4_HEIGHT - 24f,
            footerPaint
        )
    }

    private fun fitText(text: String, maxWidth: Float, paint: Paint): String {
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "..") > maxWidth) {
            end--
        }
        return if (end <= 1) text.take(1) else text.substring(0, end) + ".."
    }

    /**
     * Wraps a header label onto multiple lines cleanly without dropping words or numbers.
     * Gently steps font size down if any single token is wider than [maxWidth].
     */
    private fun fitWrappedLines(
        text: String,
        maxWidth: Float,
        baseTextSize: Float,
        minTextSize: Float,
        paint: Paint
    ): Pair<List<String>, Float> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return listOf("") to baseTextSize
        val tokens = tokenizeHeaderText(cleaned)
        val probe = Paint(paint)
        var size = baseTextSize
        while (size > minTextSize) {
            probe.textSize = size
            val allTokensFit = tokens.all { probe.measureText(it) <= maxWidth }
            if (allTokensFit) break
            size = (size - 0.3f).coerceAtLeast(minTextSize)
        }
        probe.textSize = size
        val lines = wrapTokens(tokens, maxWidth, probe)
        return lines to size
    }

    /**
     * Wraps a component/subject name cleanly and keeps [maxMarksTag] (e.g. "(30)") intact on the last line
     * so total marks are always completely visible.
     */
    private fun fitComponentHeaderLines(
        componentName: String,
        maxMarksTag: String,
        maxWidth: Float,
        baseTextSize: Float,
        minTextSize: Float,
        paint: Paint
    ): Pair<List<String>, Float> {
        val probe = Paint(paint)
        val tokens = tokenizeHeaderText(componentName.trim())
        var size = baseTextSize
        while (size > minTextSize) {
            probe.textSize = size
            val fits = tokens.all { probe.measureText(it) <= maxWidth } &&
                probe.measureText(maxMarksTag) <= maxWidth
            if (fits) break
            size = (size - 0.3f).coerceAtLeast(minTextSize)
        }
        probe.textSize = size
        val nameLines = wrapTokens(tokens, maxWidth, probe).toMutableList()
        if (nameLines.isEmpty()) {
            nameLines.add(maxMarksTag)
        } else {
            val combinedLast = "${nameLines.last()} $maxMarksTag"
            if (probe.measureText(combinedLast) <= maxWidth) {
                nameLines[nameLines.lastIndex] = combinedLast
            } else {
                nameLines.add(maxMarksTag)
            }
        }
        return nameLines to size
    }

    private fun tokenizeHeaderText(text: String): List<String> {
        val out = mutableListOf<String>()
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            if (word.contains("/") && word.length > 8) {
                val parts = word.split("/")
                parts.forEachIndexed { i, p ->
                    if (p.isNotBlank()) out.add(if (i < parts.lastIndex) "$p/" else p)
                }
            } else {
                out.add(word)
            }
        }
        return out
    }

    private fun wrapTokens(tokens: List<String>, maxWidth: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        tokens.forEach { token ->
            if (paint.measureText(token) > maxWidth) {
                if (current.isNotEmpty()) {
                    lines.add(current)
                    current = ""
                }
                // Split an extra-long single token across lines without dropping characters
                var rem = token
                while (rem.isNotEmpty()) {
                    var take = rem.length
                    while (take > 1 && paint.measureText(rem.substring(0, take)) > maxWidth) {
                        take--
                    }
                    lines.add(rem.substring(0, take))
                    rem = rem.substring(take)
                }
            } else if (current.isEmpty()) {
                current = token
            } else {
                val candidate = "$current $token"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    lines.add(current)
                    current = token
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun drawCenteredLines(
        canvas: Canvas,
        lines: List<String>,
        rect: RectF,
        textSize: Float,
        paint: Paint
    ) {
        if (lines.isEmpty()) return
        paint.textSize = textSize
        paint.textAlign = Paint.Align.CENTER
        val lineStep = textSize + 1.8f
        val blockH = (lines.size - 1) * lineStep
        val centerX = (rect.left + rect.right) / 2f
        val centerY = (rect.top + rect.bottom) / 2f
        val fontMidOffset = -(paint.descent() + paint.ascent()) / 2f
        val firstBaselineY = centerY - blockH / 2f + fontMidOffset
        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, centerX, firstBaselineY + idx * lineStep, paint)
        }
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
