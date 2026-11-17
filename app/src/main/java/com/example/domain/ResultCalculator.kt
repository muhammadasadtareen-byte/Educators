package com.example.domain

import com.example.data.model.AcademicSession
import com.example.data.model.ClassSection
import com.example.data.model.ClassPerformanceStats
import com.example.data.model.Examination
import com.example.data.model.GradingRule
import com.example.data.model.MarkEntry
import com.example.data.model.ResultRecord
import com.example.data.model.ResultStatus
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.StudentResultSummary
import com.example.data.model.Subject
import com.example.data.model.SubjectResult
import kotlin.math.roundToInt

object ResultCalculator {

    fun defaultGradingTemplates(): List<GradingRule> = listOf(
        GradingRule(examId = "DEFAULT", minPercentage = 90.0, maxPercentage = 100.0, grade = "A+", remarks = "Outstanding", passing = true, sortOrder = 1),
        GradingRule(examId = "DEFAULT", minPercentage = 80.0, maxPercentage = 89.99, grade = "A", remarks = "Excellent", passing = true, sortOrder = 2),
        GradingRule(examId = "DEFAULT", minPercentage = 70.0, maxPercentage = 79.99, grade = "B", remarks = "Very Good", passing = true, sortOrder = 3),
        GradingRule(examId = "DEFAULT", minPercentage = 60.0, maxPercentage = 69.99, grade = "C", remarks = "Good", passing = true, sortOrder = 4),
        GradingRule(examId = "DEFAULT", minPercentage = 50.0, maxPercentage = 59.99, grade = "D", remarks = "Satisfactory", passing = true, sortOrder = 5),
        GradingRule(examId = "DEFAULT", minPercentage = 40.0, maxPercentage = 49.99, grade = "E", remarks = "Pass", passing = true, sortOrder = 6),
        GradingRule(examId = "DEFAULT", minPercentage = 0.0, maxPercentage = 39.99, grade = "F", remarks = "Fail", passing = false, sortOrder = 7)
    )

    fun standardClassNames(): List<String> = listOf(
        "Playgroup", "Nursery", "Prep",
        "Class 1", "Class 2", "Class 3", "Class 4", "Class 5",
        "Class 6", "Class 7", "Class 8", "Class 9", "Class 10"
    )

    fun class5SampleSubjects(): List<Triple<String, Double, Double>> = listOf(
        Triple("English", 100.0, 40.0),
        Triple("Urdu", 100.0, 40.0),
        Triple("Mathematics", 100.0, 40.0),
        Triple("Science", 100.0, 40.0),
        Triple("Social Studies", 100.0, 40.0),
        Triple("Islamiat", 50.0, 20.0),
        Triple("Computer", 50.0, 20.0)
    )

    fun resolveActiveGradingRules(
        allRules: List<GradingRule>,
        examination: Examination?
    ): List<GradingRule> {
        val examSpecific = if (examination != null && examination.useCustomGrading) {
            allRules.filter { it.examId == examination.id }
        } else {
            emptyList()
        }
        val chosen = if (examSpecific.isNotEmpty()) {
            examSpecific
        } else {
            allRules.filter { it.examId == "DEFAULT" }
        }
        return (if (chosen.isNotEmpty()) chosen else defaultGradingTemplates())
            .sortedByDescending { it.minPercentage }
    }

    fun resolveGradeForPercentage(
        percentage: Double,
        rules: List<GradingRule>
    ): GradingRule {
        val clamped = percentage.coerceIn(0.0, 100.0)
        val sorted = rules.sortedByDescending { it.minPercentage }
        // First match strictly within [min, max]
        val exact = sorted.firstOrNull { clamped >= it.minPercentage && clamped <= it.maxPercentage }
        if (exact != null) return exact
        // Fallback: highest threshold <= clamped (handles gaps like 89.995% between 89.99 and 90.0)
        val threshold = sorted.firstOrNull { clamped >= it.minPercentage }
        return threshold ?: sorted.lastOrNull()
            ?: GradingRule(grade = "F", remarks = "Fail", passing = false)
    }

    fun calculateSubjectResult(
        subject: Subject,
        markEntry: MarkEntry?,
        gradingRules: List<GradingRule>
    ): SubjectResult {
        if (markEntry == null) {
            return SubjectResult(
                subject = subject,
                markEntry = null,
                obtainedMarks = null,
                percentage = null,
                grade = "-",
                remarks = "Pending",
                passed = null,
                entered = false
            )
        }
        val obtained = markEntry.obtainedMarks.coerceIn(0.0, subject.maxMarks)
        val pct = if (subject.maxMarks > 0.0) (obtained / subject.maxMarks) * 100.0 else 0.0
        val rule = resolveGradeForPercentage(pct, gradingRules)
        val passedByMarks = obtained >= subject.passingMarks
        val passed = passedByMarks && rule.passing
        val effectiveGrade = if (!passedByMarks && rule.passing) "F" else rule.grade
        val effectiveRemarks = if (!passedByMarks && rule.passing) "Below Passing Marks" else rule.remarks

        return SubjectResult(
            subject = subject,
            markEntry = markEntry,
            obtainedMarks = obtained,
            percentage = pct,
            grade = effectiveGrade,
            remarks = effectiveRemarks,
            passed = passed,
            entered = true
        )
    }

    fun computeClassResults(
        students: List<Student>,
        subjects: List<Subject>,
        marks: List<MarkEntry>,
        resultRecords: List<ResultRecord>,
        classesById: Map<String, SchoolClass>,
        sectionsById: Map<String, ClassSection>,
        sessionsById: Map<String, AcademicSession>,
        examination: Examination?,
        allGradingRules: List<GradingRule>
    ): List<StudentResultSummary> {
        if (students.isEmpty()) return emptyList()

        val activeRules = resolveActiveGradingRules(allGradingRules, examination)
        val sortedSubjects = subjects.sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name })
        val marksByStudentAndSubject = marks.associateBy { "${it.studentId}_${it.subjectId}" }
        val recordsByStudentId = resultRecords.associateBy { it.studentId }

        val rawSummaries = students.map { student ->
            val classSubjects = sortedSubjects.filter { it.classId == student.classId }
            val subjectResults = classSubjects.map { sub ->
                val mark = marksByStudentAndSubject["${student.id}_${sub.id}"]
                calculateSubjectResult(sub, mark, activeRules)
            }
            val enteredCount = subjectResults.count { it.entered }
            val totalCount = subjectResults.size
            val isComplete = totalCount > 0 && enteredCount == totalCount

            // IMPORTANT: Overall percentage = (Total Obtained / Total Maximum) * 100
            val totalMaxMarks = classSubjects.sumOf { it.maxMarks }
            val totalObtainedMarks = subjectResults.sumOf { it.obtainedMarks ?: 0.0 }
            val overallPct = if (totalMaxMarks > 0.0) {
                (totalObtainedMarks / totalMaxMarks) * 100.0
            } else {
                0.0
            }

            val failedCompulsory = subjectResults
                .filter { it.entered && it.subject.compulsory && it.passed == false }
                .map { it.subject.name }
            val anySubjectFailed = subjectResults.any { it.entered && it.passed == false }

            val overallRule = resolveGradeForPercentage(overallPct, activeRules)
            val status = if (isComplete) ResultStatus.COMPLETE else ResultStatus.INCOMPLETE

            val passedOverall = isComplete &&
                overallRule.passing &&
                failedCompulsory.isEmpty() &&
                !anySubjectFailed

            val gradeDisplay = when {
                !isComplete -> "Pending"
                !passedOverall -> if (failedCompulsory.isNotEmpty() && overallRule.passing) "F*" else "F"
                else -> overallRule.grade
            }

            val remarksDisplay = when {
                !isComplete -> "Incomplete ($enteredCount/$totalCount subjects)"
                failedCompulsory.isNotEmpty() -> "Failed Compulsory: ${failedCompulsory.joinToString(", ")}"
                !passedOverall -> "Fail"
                else -> overallRule.remarks
            }

            val savedRecord = recordsByStudentId[student.id]
            val defaultTeacherRemark = when {
                !isComplete -> "Result pending completion of all subjects."
                passedOverall && overallPct >= 85.0 -> "Excellent academic performance. Keep it up!"
                passedOverall && overallPct >= 70.0 -> "Very good performance. Strive for higher distinction."
                passedOverall -> "Satisfactory progress. More consistent practice recommended."
                else -> "Needs focused attention and remedial support in weak subjects."
            }

            StudentResultSummary(
                student = student,
                className = classesById[student.classId]?.name ?: "Class",
                sectionName = sectionsById[student.sectionId]?.name ?: "-",
                sessionName = sessionsById[student.sessionId]?.name ?: "-",
                examName = examination?.name ?: "Examination",
                subjectResults = subjectResults,
                enteredSubjectCount = enteredCount,
                totalSubjectCount = totalCount,
                totalMaxMarks = totalMaxMarks,
                totalObtainedMarks = totalObtainedMarks,
                overallPercentage = overallPct,
                overallGrade = gradeDisplay,
                gradeRemarks = remarksDisplay,
                status = status,
                passed = passedOverall,
                failedCompulsorySubjects = failedCompulsory,
                position = null,
                teacherRemarks = savedRecord?.teacherRemarks?.takeIf { it.isNotBlank() } ?: defaultTeacherRemark
            )
        }

        // Compute class positions for COMPLETE results (equal marks receive equal position)
        // Group ranking per (classId, sectionId) or per classId; we compute per classId+sectionId if section matches,
        // or generally over the passed student slice. Let's group by classId so class position reflects the class!
        val positionsByStudentId = mutableMapOf<String, Int>()
        rawSummaries
            .filter { it.status == ResultStatus.COMPLETE }
            .groupBy { it.student.classId }
            .forEach { (_, classGroup) ->
                val sorted = classGroup.sortedWith(
                    compareByDescending<StudentResultSummary> { (it.totalObtainedMarks * 100.0).roundToInt() }
                        .thenByDescending { (it.overallPercentage * 100.0).roundToInt() }
                )
                var currentRank = 0
                var lastScoreKey: Long? = null
                sorted.forEachIndexed { index, summary ->
                    val scoreKey = (summary.totalObtainedMarks * 100.0).roundToInt().toLong()
                    if (lastScoreKey == null || scoreKey != lastScoreKey) {
                        currentRank = index + 1
                        lastScoreKey = scoreKey
                    }
                    positionsByStudentId[summary.student.id] = currentRank
                }
            }

        return rawSummaries.map { summary ->
            summary.copy(position = positionsByStudentId[summary.student.id])
        }
    }

    fun computeClassPerformance(
        schoolClass: SchoolClass,
        summaries: List<StudentResultSummary>,
        subjects: List<Subject>
    ): ClassPerformanceStats {
        val classSummaries = summaries.filter { it.student.classId == schoolClass.id }
        val completed = classSummaries.filter { it.status == ResultStatus.COMPLETE }
        val pending = classSummaries.size - completed.size
        val passed = completed.count { it.passed }
        val failed = completed.count { !it.passed }
        val avgPct = if (completed.isNotEmpty()) {
            completed.map { it.overallPercentage }.average()
        } else {
            0.0
        }
        val gradeDist = linkedMapOf<String, Int>()
        listOf("A+", "A", "B", "C", "D", "E", "F", "F*").forEach { g ->
            val count = completed.count { it.overallGrade == g }
            if (count > 0) gradeDist[g] = count
        }

        val classSubjects = subjects.filter { it.classId == schoolClass.id }.sortedBy { it.sortOrder }
        val subjectAvgs = classSubjects.map { sub ->
            val entered = classSummaries.mapNotNull { s ->
                s.subjectResults.firstOrNull { it.subject.id == sub.id && it.entered }?.percentage
            }
            val avg = if (entered.isNotEmpty()) entered.average() else 0.0
            sub to avg
        }

        return ClassPerformanceStats(
            schoolClass = schoolClass,
            totalStudents = classSummaries.size,
            completedResults = completed.size,
            pendingResults = pending,
            passedCount = passed,
            failedCount = failed,
            averagePercentage = avgPct,
            gradeDistribution = gradeDist,
            subjectAverages = subjectAvgs
        )
    }

    fun formatMarks(value: Double): String {
        val rounded = (value * 100.0).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    fun formatPercentage(value: Double): String =
        String.format(java.util.Locale.US, "%.2f%%", value)

    fun formatOrdinal(position: Int?): String {
        if (position == null || position <= 0) return "-"
        val mod100 = position % 100
        val suffix = if (mod100 in 11..13) "th" else when (position % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$position$suffix"
    }
}
