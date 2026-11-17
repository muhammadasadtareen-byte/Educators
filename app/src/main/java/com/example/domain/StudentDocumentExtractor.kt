package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Xml
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs

enum class DocumentSourceKind(val label: String) {
    IMAGE("Image"),
    PDF("PDF Document"),
    WORD("Word Document")
}

data class ExtractedStudentCandidate(
    val tempId: String,
    val rollNumber: String,
    val name: String,
    val fatherName: String,
    val admissionNumber: String,
    val selected: Boolean = true
)

data class StudentExtractionResult(
    val sourceKind: DocumentSourceKind,
    val rawText: String,
    val candidates: List<ExtractedStudentCandidate>
)

object StudentDocumentExtractor {

    suspend fun extractFromUri(
        context: Context,
        uri: Uri,
        sourceKind: DocumentSourceKind,
        startingRoll: Int = 1
    ): Result<StudentExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val rawText = when (sourceKind) {
                DocumentSourceKind.IMAGE -> extractTextFromImageUri(context, uri)
                DocumentSourceKind.PDF -> extractTextFromPdfUri(context, uri)
                DocumentSourceKind.WORD -> extractTextFromWordUri(context, uri)
            }
            if (rawText.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("No readable text was found in this ${sourceKind.label.lowercase()}.")
                )
            }
            val candidates = parseStudentCandidates(rawText, startingRoll)
            Result.success(
                StudentExtractionResult(
                    sourceKind = sourceKind,
                    rawText = rawText,
                    candidates = candidates
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractTextFromImageUri(context: Context, uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val visionText = recognizer.process(image).await()
            reconstructSpatialLines(visionText)
        } finally {
            recognizer.close()
        }
    }

    private suspend fun extractTextFromPdfUri(context: Context, uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open PDF file.")
        val pageOutputs = mutableListOf<String>()
        try {
            PdfRenderer(pfd).use { renderer ->
                val pageCount = minOf(renderer.pageCount, 25)
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        val targetWidth = 1650
                        val scale = (targetWidth.toFloat() / page.width.coerceAtLeast(1)).coerceIn(1.2f, 3.0f)
                        val bmpW = (page.width * scale).toInt().coerceIn(400, 2400)
                        val bmpH = (page.height * scale).toInt().coerceIn(400, 3400)
                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        try {
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val visionText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                            val pageText = reconstructSpatialLines(visionText)
                            if (pageText.isNotBlank()) {
                                pageOutputs.add(pageText)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            recognizer.close()
            pfd.close()
        }
        return pageOutputs.joinToString("\n")
    }

    private fun extractTextFromWordUri(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read Word document.")
        return extractTextFromWordBytes(bytes)
    }

    fun extractTextFromWordBytes(bytes: ByteArray): String {
        if (bytes.size < 4) return ""
        // OOXML .docx (ZIP starts with "PK")
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return extractTextFromDocxStream(ByteArrayInputStream(bytes))
        }
        // Legacy binary .doc (OLE2 Compound File starts with D0 CF 11 E0)
        if (bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() &&
            bytes[3] == 0xE0.toByte()
        ) {
            return extractTextFromLegacyDocBytes(bytes)
        }
        // Fallback plain text / CSV / RTF
        val text = bytes.toString(Charsets.UTF_8)
        return if (text.startsWith("{\\rtf")) stripRtfFormatting(text) else text
    }

    fun extractTextFromDocxStream(inputStream: InputStream): String {
        var docXmlBytes: ByteArray? = null
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.equals("word/document.xml", ignoreCase = true)) {
                    docXmlBytes = zis.readBytes()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val xmlBytes = docXmlBytes
            ?: throw IllegalStateException("Invalid .docx file (missing word/document.xml).")
        return parseWordDocumentXml(xmlBytes)
    }

    private fun parseWordDocumentXml(xmlBytes: ByteArray): String {
        val lines = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var inTableRow = false
        var inTableCell = false
        val rowCells = mutableListOf<String>()
        val cellText = StringBuilder()
        val paragraphText = StringBuilder()
        var inParagraph = false
        var inTextNode = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':')
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "tr" -> {
                        inTableRow = true
                        rowCells.clear()
                    }
                    "tc" -> {
                        inTableCell = true
                        cellText.clear()
                    }
                    "p" -> {
                        inParagraph = true
                        paragraphText.clear()
                    }
                    "t" -> if (inParagraph) inTextNode = true
                    "tab" -> if (inParagraph) paragraphText.append("\t")
                    "br", "cr" -> if (inParagraph) paragraphText.append("\n")
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph && inTextNode) {
                        paragraphText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "t" -> inTextNode = false
                    "p" -> {
                        val pStr = paragraphText.toString().trim()
                        if (inTableCell) {
                            if (pStr.isNotEmpty()) {
                                if (cellText.isNotEmpty()) cellText.append(" ")
                                cellText.append(pStr)
                            }
                        } else if (pStr.isNotEmpty()) {
                            pStr.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
                        }
                        inParagraph = false
                    }
                    "tc" -> {
                        if (inTableRow) {
                            rowCells.add(cellText.toString().trim())
                        }
                        inTableCell = false
                    }
                    "tr" -> {
                        if (rowCells.any { it.isNotBlank() }) {
                            lines.add(rowCells.joinToString("\t"))
                        }
                        inTableRow = false
                    }
                }
            }
            event = parser.next()
        }
        return lines.joinToString("\n")
    }

    private fun extractTextFromLegacyDocBytes(bytes: ByteArray): String {
        val ignored = setOf(
            "Microsoft Word", "Normal.dot", "MSWordDoc", "Word.Document",
            "Times New Roman", "Calibri", "Arial", "Cambria", "Root Entry",
            "SummaryInformation", "DocumentSummaryInformation", "WordDocument"
        )
        val found = LinkedHashSet<String>()

        // Scan UTF-16LE runs (used by modern binary .doc)
        val utf16Buf = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            val ch = ((hi shl 8) or lo).toChar()
            if (hi == 0 && (ch.isLetterOrDigit() || ch == ' ' || ch == '.' || ch == ',' || ch == '-' || ch == '/' || ch == '\t')) {
                utf16Buf.append(ch)
                i += 2
            } else {
                if (utf16Buf.length >= 4) {
                    val s = utf16Buf.toString().trim()
                    if (s.any { it.isLetter() } && ignored.none { s.contains(it, ignoreCase = true) }) {
                        found.add(s)
                    }
                }
                utf16Buf.clear()
                i += 2
            }
        }

        // Also scan ASCII runs if UTF-16LE yielded little
        if (found.size < 3) {
            val asciiBuf = StringBuilder()
            for (b in bytes) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c.isLetterOrDigit() || c == ' ' || c == '.' || c == ',' || c == '-' || c == '/' || c == '\t') {
                    asciiBuf.append(c)
                } else {
                    if (asciiBuf.length >= 4) {
                        val s = asciiBuf.toString().trim()
                        if (s.any { it.isLetter() } && ignored.none { s.contains(it, ignoreCase = true) }) {
                            found.add(s)
                        }
                    }
                    asciiBuf.clear()
                }
            }
        }
        if (found.isEmpty()) {
            throw IllegalStateException("Legacy .doc format could not be decoded cleanly. Please save as .docx or .pdf and retry.")
        }
        return found.joinToString("\n")
    }

    private fun stripRtfFormatting(rtf: String): String {
        return rtf
            .replace(Regex("\\\\par\\b"), "\n")
            .replace(Regex("\\\\tab\\b"), "\t")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d*\\s?"), "")
            .replace("{", "")
            .replace("}", "")
    }

    /**
     * Reconstructs reading-order rows across columns by grouping ML Kit Text.Line objects
     * with overlapping vertical centers, then sorting each row left-to-right.
     */
    private fun reconstructSpatialLines(visionText: Text): String {
        val allLines = visionText.textBlocks.flatMap { it.lines }
            .filter { it.text.isNotBlank() }
        if (allLines.isEmpty()) return visionText.text

        val sortedByTop = allLines.sortedBy { it.boundingBox?.centerY() ?: 0 }
        val rowBands = mutableListOf<MutableList<Text.Line>>()
        val rowCenterYs = mutableListOf<Float>()
        val rowHeights = mutableListOf<Float>()

        for (line in sortedByTop) {
            val box = line.boundingBox
            if (box == null) {
                rowBands.add(mutableListOf(line))
                rowCenterYs.add(0f)
                rowHeights.add(20f)
                continue
            }
            val cy = box.centerY().toFloat()
            val h = box.height().coerceAtLeast(12).toFloat()

            var matchedIdx = -1
            for (idx in rowBands.indices) {
                val threshold = maxOf(rowHeights[idx], h) * 0.55f
                if (abs(cy - rowCenterYs[idx]) <= threshold) {
                    matchedIdx = idx
                    break
                }
            }
            if (matchedIdx >= 0) {
                rowBands[matchedIdx].add(line)
                rowCenterYs[matchedIdx] = rowBands[matchedIdx].mapNotNull { it.boundingBox?.centerY()?.toFloat() }.average().toFloat()
            } else {
                rowBands.add(mutableListOf(line))
                rowCenterYs.add(cy)
                rowHeights.add(h)
            }
        }

        return rowBands.joinToString("\n") { band ->
            val ordered = band.sortedBy { it.boundingBox?.left ?: 0 }
            val sb = StringBuilder()
            var prevRight: Int? = null
            var prevHeight = 20
            ordered.forEachIndexed { idx, l ->
                val txt = l.text.trim()
                if (idx > 0) {
                    val box = l.boundingBox
                    val gap = if (box != null && prevRight != null) box.left - prevRight!! else 0
                    if (gap > prevHeight * 1.1f) {
                        sb.append("\t")
                    } else {
                        sb.append(" ")
                    }
                }
                sb.append(txt)
                l.boundingBox?.let {
                    prevRight = it.right
                    prevHeight = it.height().coerceAtLeast(12)
                }
            }
            sb.toString()
        }
    }

    /**
     * Parses raw text lines (from OCR, PDF, or Word) into editable student candidates.
     * Supports:
     * 1) Multi-column tables with or without header row (Roll | Name | Father Name | Adm No)
     * 2) Inline "Name S/O Father Name" or "Name D/O Father Name"
     * 3) Numbered lists ("1. Ali Khan", "02) Sara Ahmed", "103 - Hamza Raza")
     * 4) Plain one-student-per-line lists
     */
    fun parseStudentCandidates(
        rawText: String,
        startingRoll: Int = 1
    ): List<ExtractedStudentCandidate> {
        val rawLines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) return emptyList()

        // Detect optional table header columns
        var rollCol = -1
        var nameCol = -1
        var fatherCol = -1
        var admCol = -1
        var startIdx = 0

        for (i in 0 until minOf(rawLines.size, 8)) {
            val cols = splitColumns(rawLines[i])
            if (cols.size >= 2) {
                val lower = cols.map { it.lowercase() }
                val nIdx = lower.indexOfFirst { it.contains("student") || it == "name" || it.contains("candidate") }
                val fIdx = lower.indexOfFirst { it.contains("father") || it.contains("guardian") || it.contains("parent") }
                val rIdx = lower.indexOfFirst { it.contains("roll") || it == "sr" || it == "sr#" || it.contains("sr.") || it.contains("s.no") }
                val aIdx = lower.indexOfFirst { it.contains("adm") || it.contains("reg") }
                if (nIdx >= 0 || (fIdx >= 0 && rIdx >= 0)) {
                    nameCol = nIdx
                    fatherCol = fIdx
                    rollCol = rIdx
                    admCol = aIdx
                    startIdx = i + 1
                    break
                }
            }
        }

        val candidates = mutableListOf<ExtractedStudentCandidate>()
        var autoCounter = startingRoll.coerceAtLeast(1)

        for (i in startIdx until rawLines.size) {
            val line = rawLines[i]
            if (isBoilerplateOrNoise(line)) continue

            val cols = splitColumns(line)
            var extractedRoll = ""
            var extractedName = ""
            var extractedFather = ""
            var extractedAdm = ""

            if (nameCol >= 0 && cols.size >= 2) {
                extractedName = cols.getOrNull(nameCol).orEmpty()
                if (fatherCol >= 0) extractedFather = cols.getOrNull(fatherCol).orEmpty()
                if (rollCol >= 0) extractedRoll = cols.getOrNull(rollCol).orEmpty()
                if (admCol >= 0) extractedAdm = cols.getOrNull(admCol).orEmpty()
            } else if (cols.size >= 2) {
                // Heuristic table row without explicit header
                val first = cols[0]
                if (looksLikeRollOrSerial(first)) {
                    extractedRoll = first.trimEnd('.', ')', '-')
                    extractedName = cols.getOrNull(1).orEmpty()
                    extractedFather = cols.getOrNull(2).orEmpty()
                    extractedAdm = cols.getOrNull(3).orEmpty()
                } else {
                    extractedName = cols[0]
                    extractedFather = cols.getOrNull(1).orEmpty()
                    if (cols.size >= 3 && looksLikeRollOrSerial(cols[2])) {
                        extractedRoll = cols[2]
                    }
                }
            } else {
                // Single-column line: check leading roll/serial and inline S/O or D/O
                var body = line
                val leadingMatch = Regex("^([A-Za-z0-9\\-]{1,10})[.):\\-]+\\s+(.+)$").find(body)
                if (leadingMatch != null && leadingMatch.groupValues[1].any { it.isDigit() }) {
                    extractedRoll = leadingMatch.groupValues[1].trim()
                    body = leadingMatch.groupValues[2].trim()
                } else {
                    // Also handle leading bullets like "• Ali Khan" or "- Ali Khan"
                    body = body.replace(Regex("^[•\\-*]+\\s*"), "").trim()
                    val numSpaceMatch = Regex("^(\\d{1,5})\\s+([A-Za-z].+)$").find(body)
                    if (numSpaceMatch != null) {
                        extractedRoll = numSpaceMatch.groupValues[1].trim()
                        body = numSpaceMatch.groupValues[2].trim()
                    }
                }

                // Check S/O or D/O
                val parentSplit = Regex("\\s+(?:s/o|d/o|son\\s+of|daughter\\s+of)\\s+", RegexOption.IGNORE_CASE).split(body, limit = 2)
                if (parentSplit.size == 2) {
                    extractedName = parentSplit[0]
                    extractedFather = parentSplit[1]
                } else {
                    extractedName = body
                }
            }

            // If name still contains inline S/O or D/O, split it
            val inlineParent = Regex("\\s+(?:s/o|d/o|son\\s+of|daughter\\s+of)\\s+", RegexOption.IGNORE_CASE)
                .split(extractedName, limit = 2)
            if (inlineParent.size == 2) {
                extractedName = inlineParent[0]
                if (extractedFather.isBlank()) {
                    extractedFather = inlineParent[1]
                }
            }

            val cleanName = cleanPersonName(extractedName)
            if (!isPlausibleStudentName(cleanName)) continue

            val cleanFather = cleanPersonName(extractedFather).ifBlank { "-" }
            val cleanRoll = extractedRoll.replace(Regex("[^A-Za-z0-9\\-]"), "").take(20)
                .ifBlank { (autoCounter++).toString() }
            val cleanAdm = extractedAdm.replace(Regex("[^A-Za-z0-9\\-]"), "").take(30)
                .ifBlank { "ADM-$cleanRoll" }

            candidates.add(
                ExtractedStudentCandidate(
                    tempId = "ext_${candidates.size + 1}_${cleanName.hashCode()}",
                    rollNumber = cleanRoll,
                    name = cleanName.take(100),
                    fatherName = cleanFather.take(100),
                    admissionNumber = cleanAdm
                )
            )
        }
        return candidates
    }

    private fun splitColumns(line: String): List<String> {
        val raw = when {
            line.contains('\t') -> line.split('\t')
            line.contains('|') -> line.split('|')
            line.contains(';') -> line.split(';')
            line.contains(Regex("\\s{2,}")) -> line.split(Regex("\\s{2,}"))
            line.count { it == ',' } >= 1 -> line.split(',')
            else -> listOf(line)
        }
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun looksLikeRollOrSerial(token: String): Boolean {
        val cleaned = token.trim().trimEnd('.', ')', '-')
        return cleaned.length <= 12 &&
            cleaned.any { it.isDigit() } &&
            !cleaned.contains(' ')
    }

    private fun cleanPersonName(raw: String): String {
        return raw
            .replace(Regex("^[•\\-*.,:;]+\\s*"), "")
            .replace(Regex("\\s*[•\\-*,:;]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isPlausibleStudentName(name: String): Boolean {
        if (name.length < 2) return false
        val letterCount = name.count { it.isLetter() }
        if (letterCount < 2) return false
        // Reject strings that are mostly digits (dates, phone numbers, marks totals)
        val digitCount = name.count { it.isDigit() }
        if (digitCount > letterCount) return false
        if (isBoilerplateOrNoise(name)) return false
        return true
    }

    private fun isBoilerplateOrNoise(line: String): Boolean {
        val lower = line.trim().lowercase()
        if (lower.length < 2) return true
        if (lower.contains("@") || lower.contains("http://") || lower.contains("https://")) return true
        if (Regex("^[\\d\\s/\\-:.,|]+$").matches(lower)) return true

        val exactOrPrefixHeaders = listOf(
            "the educators", "abbottabad", "campus", "student list", "students list",
            "class:", "section:", "session:", "academic session", "examination",
            "attendance register", "roll no", "roll number", "student name", "father name",
            "father's name", "admission no", "sr no", "sr.", "s.no", "page ",
            "signature", "principal", "class teacher", "total students", "date:"
        )
        return exactOrPrefixHeaders.any { kw ->
            lower == kw || lower.startsWith(kw) || (lower.contains("student name") && lower.contains("father"))
        }
    }
}
