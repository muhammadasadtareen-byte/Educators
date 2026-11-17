package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StaffRole {
    ADMIN, TEACHER;

    companion object {
        fun fromRaw(raw: String?): StaffRole =
            entries.firstOrNull { it.name == raw } ?: TEACHER
    }
}

enum class ExamType(val label: String) {
    MONTHLY_TEST("Monthly Test"),
    MIDTERM("Midterm"),
    FINAL_TERM("Final Term"),
    ANNUAL("Annual Examination"),
    CUSTOM("Custom Exam");

    companion object {
        fun fromRaw(raw: String?): ExamType =
            entries.firstOrNull { it.name == raw } ?: MIDTERM
    }
}

enum class ResultStatus {
    COMPLETE, INCOMPLETE;

    companion object {
        fun fromRaw(raw: String?): ResultStatus =
            entries.firstOrNull { it.name == raw } ?: INCOMPLETE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val activeCampusId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExamComponent(
    val id: String = "",
    val name: String = "",
    val maxMarks: Double = 0.0
)

object StudentEntryField {
    const val FATHER_NAME = "FATHER_NAME"
    const val ROLL_NUMBER = "ROLL_NUMBER"
    const val ADMISSION_NUMBER = "ADMISSION_NUMBER"
    const val PHOTO = "PHOTO"

    val defaultOptionalFields = listOf(FATHER_NAME, ROLL_NUMBER, ADMISSION_NUMBER)
}

object ExamComponentPresets {
    fun consolidatedTaskPreset(): List<ExamComponent> = listOf(
        ExamComponent(id = "task_01", name = "Assessment Task 01", maxMarks = 30.0),
        ExamComponent(id = "quiz", name = "Quiz", maxMarks = 20.0)
    )

    fun midYearTermPreset(): List<ExamComponent> = listOf(
        ExamComponent(id = "classwork", name = "Classwork Average", maxMarks = 10.0),
        ExamComponent(id = "quiz", name = "Quiz", maxMarks = 10.0),
        ExamComponent(id = "assess_1", name = "Assessment-I", maxMarks = 15.0),
        ExamComponent(id = "assess_2", name = "Assessment-II", maxMarks = 25.0)
    )
}

@Keep
@IgnoreExtraProperties
data class Campus(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val schoolName: String = "THE EDUCATORS",
    val campusName: String = "ABBOTTABAD MAIN CAMPUS",
    val address: String = "",
    val contact: String = "",
    val studentEntryFields: List<String> = StudentEntryField.defaultOptionalFields,
    val logoBase64: String? = null,
    val activeSessionId: String = "",
    val activeExamId: String = "",
    val allowDecimalMarks: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val userUid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = StaffRole.TEACHER.name,
    val assignedClassIds: List<String> = emptyList(),
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedRole(): StaffRole = StaffRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class AcademicSession(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val current: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Examination(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val sessionId: String = "",
    val name: String = "",
    val examType: String = ExamType.MIDTERM.name,
    val useCustomGrading: Boolean = false,
    val sortOrder: Long = 0L,
    val components: List<ExamComponent> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedExamType(): ExamType = ExamType.fromRaw(examType)
}

@Keep
@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ClassSection(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val classId: String = "",
    val name: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val classId: String = "",
    val name: String = "",
    val maxMarks: Double = 100.0,
    val passingMarks: Double = 40.0,
    val compulsory: Boolean = true,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val fatherName: String = "",
    val rollNumber: String = "",
    val admissionNumber: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val sessionId: String = "",
    val photoBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MarkEntry(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val sessionId: String = "",
    val examId: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val studentId: String = "",
    val subjectId: String = "",
    val obtainedMarks: Double = 0.0,
    val componentMarks: Map<String, Double> = emptyMap(),
    val enteredByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GradingRule(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val examId: String = "DEFAULT",
    val minPercentage: Double = 0.0,
    val maxPercentage: Double = 100.0,
    val grade: String = "",
    val remarks: String = "",
    val passing: Boolean = true,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ResultRecord(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val sessionId: String = "",
    val examId: String = "",
    val classId: String = "",
    val sectionId: String = "",
    val studentId: String = "",
    val totalMaxMarks: Double = 0.0,
    val totalObtainedMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val grade: String = "Pending",
    val passed: Boolean = false,
    val status: String = ResultStatus.INCOMPLETE.name,
    val position: Long = 0L,
    val teacherRemarks: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// Computed Domain Models for UI, PDF & Excel

data class SubjectResult(
    val subject: Subject,
    val markEntry: MarkEntry?,
    val obtainedMarks: Double?,
    val highestMarksInClass: Double? = null,
    val percentage: Double?,
    val grade: String,
    val remarks: String,
    val passed: Boolean?,
    val entered: Boolean
)

data class StudentResultSummary(
    val student: Student,
    val className: String,
    val sectionName: String,
    val sessionName: String,
    val examName: String,
    val examComponents: List<ExamComponent> = emptyList(),
    val gradingRulesUsed: List<GradingRule> = emptyList(),
    val subjectResults: List<SubjectResult>,
    val enteredSubjectCount: Int,
    val totalSubjectCount: Int,
    val totalMaxMarks: Double,
    val totalObtainedMarks: Double,
    val overallPercentage: Double,
    val overallGrade: String,
    val gradeRemarks: String,
    val status: ResultStatus,
    val passed: Boolean,
    val failedCompulsorySubjects: List<String>,
    val position: Int?, // null if incomplete
    val teacherRemarks: String
)

data class ClassPerformanceStats(
    val schoolClass: SchoolClass,
    val totalStudents: Int,
    val completedResults: Int,
    val pendingResults: Int,
    val passedCount: Int,
    val failedCount: Int,
    val averagePercentage: Double,
    val gradeDistribution: Map<String, Int>,
    val subjectAverages: List<Pair<Subject, Double>>
)

data class ImportRowError(
    val rowNumber: Int,
    val message: String
)

data class ImportPreviewResult<T>(
    val validRows: List<T>,
    val errors: List<ImportRowError>
)
