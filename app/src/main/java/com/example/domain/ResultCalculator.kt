package com.example.domain

import com.example.data.model.AcademicSession
import com.example.data.model.ClassSection
import com.example.data.model.ClassPerformanceStats
import com.example.data.model.ExamComponent
import com.example.data.model.ExamSubjectConfig
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

enum class StudentResultSortOrder(val label: String) {
    ENTRY_ORDER("Entry Order"),
    HIGHEST_TO_LOWEST("Highest to Lowest Grade"),
    ROLL_AND_NAME("Roll / Name")
}

object ResultCalculator {

    val studentEntryOrderComparator: Comparator<Student> = compareBy<Student>(
        { it.createdAt?.seconds ?: Long.MAX_VALUE },
        { it.createdAt?.nanoseconds ?: Int.MAX_VALUE },
        { it.rollNumber.trim().toIntOrNull() ?: Int.MAX_VALUE },
        { it.rollNumber.trim().ifBlank { "\uFFFF" } },
        { it.id },
        { it.name.lowercase() }
    )

    fun sortStudentSummaries(
        summaries: List<StudentResultSummary>,
        order: StudentResultSortOrder
    ): List<StudentResultSummary> = when (order) {
        StudentResultSortOrder.ENTRY_ORDER -> summaries.sortedWith(
            Comparator { a, b -> studentEntryOrderComparator.compare(a.student, b.student) }
        )
        StudentResultSortOrder.HIGHEST_TO_LOWEST -> summaries.sortedWith(
            compareBy<StudentResultSummary> { if (it.status == ResultStatus.COMPLETE) 0 else 1 }
                .thenBy { if (it.passed) 0 else 1 }
                .thenByDescending { (it.overallPercentage * 100.0).roundToInt() }
                .thenByDescending { (it.totalObtainedMarks * 100.0).roundToInt() }
                .thenBy(studentEntryOrderComparator) { it.student }
        )
        StudentResultSortOrder.ROLL_AND_NAME -> summaries.sortedWith(
            compareBy<StudentResultSummary> { it.student.rollNumber.trim().toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.student.rollNumber.trim().ifBlank { "\uFFFF" } }
                .thenBy { it.student.name.lowercase() }
        )
    }

    fun defaultGradingTemplates(): List<GradingRule> = listOf(
        GradingRule(examId = "DEFAULT", minPercentage = 90.0, maxPercentage = 100.0, grade = "A+", remarks = "Outstanding", passing = true, sortOrder = 1),
        GradingRule(examId = "DEFAULT", minPercentage = 80.0, maxPercentage = 89.99, grade = "A", remarks = "Excellent", passing = true, sortOrder = 2),
        GradingRule(examId = "DEFAULT", minPercentage = 70.0, maxPercentage = 79.99, grade = "B+", remarks = "Very Good", passing = true, sortOrder = 3),
        GradingRule(examId = "DEFAULT", minPercentage = 60.0, maxPercentage = 69.99, grade = "B", remarks = "Good", passing = true, sortOrder = 4),
        GradingRule(examId = "DEFAULT", minPercentage = 50.0, maxPercentage = 59.99, grade = "C", remarks = "Satisfactory", passing = true, sortOrder = 5),
        GradingRule(examId = "DEFAULT", minPercentage = 40.0, maxPercentage = 49.99, grade = "D", remarks = "Pass", passing = true, sortOrder = 6),
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

    const val PASSING_PERCENTAGE = 40.0

    fun effectiveComponentsForSubject(
        subject: Subject,
        examination: Examination?,
        subjectConfigsByKey: Map<String, ExamSubjectConfig> = emptyMap()
    ): List<ExamComponent> {
        val comps = examination?.components.orEmpty()
        if (comps.isEmpty()) return emptyList()
        val cfg = examination?.let { subjectConfigsByKey["${it.id}_${subject.id}"] } ?: return comps
        if (cfg.componentMaxMarks.isEmpty()) return comps
        return comps.map { comp ->
            val override = cfg.componentMaxMarks[comp.id]
            if (override != null && override > 0.0) {
                comp.copy(
                    maxMarks = override,
                    weightPercentage = if (comp.isCarryForward()) override else comp.weightPercentage
                )
            } else {
                comp
            }
        }
    }

    fun effectiveSubjectMaxMarks(
        subject: Subject,
        examination: Examination?,
        subjectConfigsByKey: Map<String, ExamSubjectConfig> = emptyMap()
    ): Double {
        val comps = effectiveComponentsForSubject(subject, examination, subjectConfigsByKey)
        val compSum = comps.sumOf { it.maxMarks }
        if (comps.isNotEmpty() && compSum > 0.0) return compSum
        val cfg = examination?.let { subjectConfigsByKey["${it.id}_${subject.id}"] }
        if (cfg != null && cfg.totalMaxMarks > 0.0) return cfg.totalMaxMarks
        return subject.maxMarks
    }

    fun effectiveSubjectPassingMarks(
        subject: Subject,
        examination: Examination?,
        subjectConfigsByKey: Map<String, ExamSubjectConfig> = emptyMap()
    ): Double {
        val effMax = effectiveSubjectMaxMarks(subject, examination, subjectConfigsByKey)
        return ((effMax * (PASSING_PERCENTAGE / 100.0)) * 100.0).roundToInt() / 100.0
    }

    fun resolveCarryForwardComponentMarks(
        studentId: String,
        subject: Subject,
        examination: Examination?,
        allExaminationsById: Map<String, Examination>,
        allSessionMarksByKey: Map<String, MarkEntry>,
        subjectConfigsByKey: Map<String, ExamSubjectConfig> = emptyMap(),
        visitedExamIds: Set<String> = emptySet()
    ): Map<String, Double> {
        val effComps = effectiveComponentsForSubject(subject, examination, subjectConfigsByKey)
        if (examination == null || effComps.isEmpty()) return emptyMap()
        val nextVisited = visitedExamIds + examination.id
        val out = linkedMapOf<String, Double>()
        effComps.forEach { comp ->
            if (comp.isCarryForward() && comp.maxMarks > 0.0 && comp.sourceExamId !in nextVisited) {
                val sourceExam = allExaminationsById[comp.sourceExamId] ?: return@forEach
                val sourceResolved = resolveSubjectScoreForExam(
                    studentId = studentId,
                    subject = subject,
                    examination = sourceExam,
                    allExaminationsById = allExaminationsById,
                    allSessionMarksByKey = allSessionMarksByKey,
                    subjectConfigsByKey = subjectConfigsByKey,
                    visitedExamIds = nextVisited
                )
                if (sourceResolved != null && sourceResolved.second > 0.0) {
                    val ratio = (sourceResolved.first / sourceResolved.second).coerceIn(0.0, 1.0)
                    val carried = ((ratio * comp.maxMarks) * 100.0).roundToInt() / 100.0
                    out[comp.id] = carried
                }
            }
        }
        return out
    }

    /**
     * Returns Pair(obtainedMarks, effectiveMaxMarks) for a student/subject in a given exam,
     * recursively resolving any linked prior term/assessment carry-forward components.
     * Returns null if no marks have been entered for this exam yet.
     */
    private fun resolveSubjectScoreForExam(
        studentId: String,
        subject: Subject,
        examination: Examination?,
        allExaminationsById: Map<String, Examination>,
        allSessionMarksByKey: Map<String, MarkEntry>,
        subjectConfigsByKey: Map<String, ExamSubjectConfig>,
        visitedExamIds: Set<String>
    ): Pair<Double, Double>? {
        val examId = examination?.id.orEmpty()
        val mark = allSessionMarksByKey["${examId}_${studentId}_${subject.id}"]
        val effMax = effectiveSubjectMaxMarks(subject, examination, subjectConfigsByKey)
        val comps = effectiveComponentsForSubject(subject, examination, subjectConfigsByKey)
        if (comps.isEmpty()) {
            return mark?.let { it.obtainedMarks.coerceIn(0.0, effMax) to effMax }
        }
        val carryForward = resolveCarryForwardComponentMarks(
            studentId = studentId,
            subject = subject,
            examination = examination,
            allExaminationsById = allExaminationsById,
            allSessionMarksByKey = allSessionMarksByKey,
            subjectConfigsByKey = subjectConfigsByKey,
            visitedExamIds = visitedExamIds
        )
        val manualComps = comps.filter { !it.isCarryForward() }
        val merged = linkedMapOf<String, Double>()
        comps.forEach { c ->
            if (c.isCarryForward()) {
                carryForward[c.id]?.let { merged[c.id] = it }
            } else {
                mark?.componentMarks?.get(c.id)?.let { merged[c.id] = it.coerceIn(0.0, c.maxMarks) }
            }
        }
        val hasEntered = if (manualComps.isEmpty()) {
            merged.isNotEmpty()
        } else {
            mark != null
        }
        if (!hasEntered) return null
        val totalObt = if (merged.isNotEmpty()) {
            merged.values.sum().coerceIn(0.0, effMax)
        } else {
            (mark?.obtainedMarks ?: 0.0).coerceIn(0.0, effMax)
        }
        return totalObt to effMax
    }

    fun calculateSubjectResult(
        subject: Subject,
        markEntry: MarkEntry?,
        gradingRules: List<GradingRule>,
        highestMarksInClass: Double? = null,
        effectiveMaxMarks: Double = subject.maxMarks,
        resolvedComponentMarks: Map<String, Double> = markEntry?.componentMarks ?: emptyMap(),
        effectiveComponents: List<ExamComponent> = emptyList(),
        forceEntered: Boolean = false,
        overrideObtainedMarks: Double? = null
    ): SubjectResult {
        val effMax = if (effectiveMaxMarks > 0.0) effectiveMaxMarks else subject.maxMarks
        val effPass = ((effMax * (PASSING_PERCENTAGE / 100.0)) * 100.0).roundToInt() / 100.0
        val isEntered = forceEntered || markEntry != null

        if (!isEntered) {
            return SubjectResult(
                subject = subject,
                markEntry = null,
                obtainedMarks = null,
                highestMarksInClass = highestMarksInClass,
                percentage = null,
                grade = "-",
                remarks = "Pending",
                passed = null,
                entered = false,
                effectiveMaxMarks = effMax,
                effectivePassingMarks = effPass,
                resolvedComponentMarks = resolvedComponentMarks,
                effectiveComponents = effectiveComponents
            )
        }
        val rawObtained = overrideObtainedMarks ?: markEntry?.obtainedMarks ?: 0.0
        val obtained = rawObtained.coerceIn(0.0, effMax)
        val pct = if (effMax > 0.0) (obtained / effMax) * 100.0 else 0.0
        val rule = resolveGradeForPercentage(pct, gradingRules)
        val passedByPct = pct + 1e-6 >= PASSING_PERCENTAGE
        val passed = passedByPct && rule.passing
        val effectiveGrade = if (!passedByPct && rule.passing) "F" else rule.grade
        val effectiveRemarks = if (!passedByPct && rule.passing) "Below 40% Passing" else rule.remarks

        return SubjectResult(
            subject = subject,
            markEntry = markEntry,
            obtainedMarks = obtained,
            highestMarksInClass = highestMarksInClass ?: obtained,
            percentage = pct,
            grade = effectiveGrade,
            remarks = effectiveRemarks,
            passed = passed,
            entered = true,
            effectiveMaxMarks = effMax,
            effectivePassingMarks = effPass,
            resolvedComponentMarks = resolvedComponentMarks,
            effectiveComponents = effectiveComponents
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
        allGradingRules: List<GradingRule>,
        allExaminations: List<Examination> = emptyList(),
        allSessionMarks: List<MarkEntry> = marks,
        examSubjectConfigs: List<ExamSubjectConfig> = emptyList()
    ): List<StudentResultSummary> {
        if (students.isEmpty()) return emptyList()

        val activeRules = resolveActiveGradingRules(allGradingRules, examination)
        val sortedSubjects = subjects.sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name })
        val marksByStudentAndSubject = marks.associateBy { "${it.studentId}_${it.subjectId}" }
        val recordsByStudentId = resultRecords.associateBy { it.studentId }
        val subjectConfigsByKey = examSubjectConfigs.associateBy { "${it.examId}_${it.subjectId}" }
        val examComponents = examination?.components.orEmpty()
        val hasCarryForward = examComponents.any { it.isCarryForward() }

        val allExamsById = buildMap {
            allExaminations.forEach { put(it.id, it) }
            if (examination != null) put(examination.id, examination)
        }
        val allSessionMarksByKey = allSessionMarks.associateBy { "${it.examId}_${it.studentId}_${it.subjectId}" }

        // Pre-resolve every (student, subject) effective score & component map so Highest Marks in Class
        // reflects per-subject total overrides and carried-forward term weights accurately too.
        data class ResolvedCell(
            val markEntry: MarkEntry?,
            val effMax: Double,
            val effComponents: List<ExamComponent>,
            val resolvedCompMap: Map<String, Double>,
            val entered: Boolean,
            val obtained: Double?
        )

        val resolvedByStudentSubject = mutableMapOf<Pair<String, String>, ResolvedCell>()
        val highestByClassSubject = mutableMapOf<Pair<String, String>, Double>()

        students.forEach { student ->
            val classSubjects = sortedSubjects.filter { it.classId == student.classId }
            classSubjects.forEach { sub ->
                val mark = marksByStudentAndSubject["${student.id}_${sub.id}"]
                val subEffComponents = effectiveComponentsForSubject(sub, examination, subjectConfigsByKey)
                val subManualComponents = subEffComponents.filter { !it.isCarryForward() }
                val effMax = effectiveSubjectMaxMarks(sub, examination, subjectConfigsByKey)
                val carryMap = if (hasCarryForward) {
                    resolveCarryForwardComponentMarks(
                        studentId = student.id,
                        subject = sub,
                        examination = examination,
                        allExaminationsById = allExamsById,
                        allSessionMarksByKey = allSessionMarksByKey,
                        subjectConfigsByKey = subjectConfigsByKey
                    )
                } else emptyMap()

                val mergedCompMap = linkedMapOf<String, Double>()
                if (subEffComponents.isNotEmpty()) {
                    subEffComponents.forEach { comp ->
                        if (comp.isCarryForward()) {
                            carryMap[comp.id]?.let { mergedCompMap[comp.id] = it }
                        } else {
                            mark?.componentMarks?.get(comp.id)?.let {
                                mergedCompMap[comp.id] = it.coerceIn(0.0, comp.maxMarks)
                            }
                        }
                    }
                } else if (mark != null) {
                    mergedCompMap.putAll(mark.componentMarks)
                }

                val entered = when {
                    subEffComponents.isEmpty() -> mark != null
                    subManualComponents.isEmpty() -> mergedCompMap.isNotEmpty()
                    else -> mark != null
                }

                val obtained: Double? = if (!entered) {
                    null
                } else if (subEffComponents.isNotEmpty() && mergedCompMap.isNotEmpty()) {
                    mergedCompMap.values.sum().coerceIn(0.0, effMax)
                } else {
                    (mark?.obtainedMarks ?: 0.0).coerceIn(0.0, effMax)
                }

                resolvedByStudentSubject[student.id to sub.id] = ResolvedCell(
                    markEntry = mark,
                    effMax = effMax,
                    effComponents = subEffComponents,
                    resolvedCompMap = mergedCompMap,
                    entered = entered,
                    obtained = obtained
                )
                if (obtained != null) {
                    val key = student.classId to sub.id
                    val prev = highestByClassSubject[key]
                    if (prev == null || obtained > prev) {
                        highestByClassSubject[key] = obtained
                    }
                }
            }
        }

        val rawSummaries = students.map { student ->
            val classSubjects = sortedSubjects.filter { it.classId == student.classId }
            val subjectResults = classSubjects.map { sub ->
                val cell = resolvedByStudentSubject[student.id to sub.id]
                val highest = highestByClassSubject[student.classId to sub.id]
                calculateSubjectResult(
                    subject = sub,
                    markEntry = cell?.markEntry,
                    gradingRules = activeRules,
                    highestMarksInClass = highest,
                    effectiveMaxMarks = cell?.effMax ?: effectiveSubjectMaxMarks(sub, examination, subjectConfigsByKey),
                    resolvedComponentMarks = cell?.resolvedCompMap ?: emptyMap(),
                    effectiveComponents = cell?.effComponents ?: effectiveComponentsForSubject(sub, examination, subjectConfigsByKey),
                    forceEntered = cell?.entered == true,
                    overrideObtainedMarks = cell?.obtained
                )
            }
            val enteredCount = subjectResults.count { it.entered }
            val totalCount = subjectResults.size
            val isComplete = totalCount > 0 && enteredCount == totalCount

            // IMPORTANT: Overall percentage = (Total Obtained / Total Effective Maximum) * 100
            val totalMaxMarks = subjectResults.sumOf { it.effectiveMaxMarks }
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
                (overallPct + 1e-6 >= PASSING_PERCENTAGE) &&
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
                !passedOverall -> "Fail (Below 40%)"
                else -> overallRule.remarks
            }

            val savedRecord = recordsByStudentId[student.id]
            val defaultTeacherRemark = when {
                !isComplete -> "Result pending completion of all subjects."
                passedOverall && overallPct >= 85.0 -> "A marvelous result. Keep on aiming higher. We are proud of you."
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
                examComponents = examComponents,
                gradingRulesUsed = activeRules.sortedByDescending { it.minPercentage },
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
        listOf("A+", "A", "B+", "B", "C", "D", "E", "F", "F*").forEach { g ->
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
        String.format(java.util.Locale.US, "%.1f%%", value)

    fun formatPercentNumber(value: Double): String =
        String.format(java.util.Locale.US, "%.1f", value)

    fun sumComponentMarks(componentMarks: Map<String, Double>): Double =
        componentMarks.values.sum()

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
