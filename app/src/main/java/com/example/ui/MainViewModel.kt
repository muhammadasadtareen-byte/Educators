package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.SchoolRepository
import com.example.data.model.AcademicSession
import com.example.data.model.Campus
import com.example.data.model.ClassPerformanceStats
import com.example.data.model.ClassSection
import com.example.data.model.ExamComponent
import com.example.data.model.ExamType
import com.example.data.model.Examination
import com.example.data.model.GradingRule
import com.example.data.model.MarkEntry
import com.example.data.model.ResultRecord
import com.example.data.model.ResultStatus
import com.example.data.model.SchoolClass
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Student
import com.example.data.model.StudentResultSummary
import com.example.data.model.Subject
import com.example.domain.ImportedMarkDraft
import com.example.domain.ImportedStudentDraft
import com.example.domain.ImportedSubjectDraft
import com.example.domain.ResultCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SchoolSnapshot(
    val campus: Campus,
    val staff: List<StaffMember>,
    val sessions: List<AcademicSession>,
    val examinations: List<Examination>,
    val classes: List<SchoolClass>,
    val sections: List<ClassSection>,
    val subjects: List<Subject>,
    val students: List<Student>,
    val marks: List<MarkEntry>,
    val gradingRules: List<GradingRule>,
    val resultRecords: List<ResultRecord>,
    val effectiveRole: StaffRole,
    val isOwnerOrAdmin: Boolean,
    val assignedClassIds: List<String>,
    val selectedSessionId: String,
    val selectedExamId: String
) {
    val classesById: Map<String, SchoolClass> = classes.associateBy { it.id }
    val sectionsById: Map<String, ClassSection> = sections.associateBy { it.id }
    val sessionsById: Map<String, AcademicSession> = sessions.associateBy { it.id }
    val selectedExam: Examination? = examinations.firstOrNull { it.id == selectedExamId } ?: examinations.firstOrNull()
    val selectedSession: AcademicSession? = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()

    val visibleClasses: List<SchoolClass> =
        if (effectiveRole == StaffRole.ADMIN || assignedClassIds.isEmpty()) {
            classes.sortedBy { it.sortOrder }
        } else {
            classes.filter { it.id in assignedClassIds }.sortedBy { it.sortOrder }
        }

    val visibleStudents: List<Student> =
        students.filter { st ->
            (selectedSessionId.isBlank() || st.sessionId == selectedSessionId) &&
                (effectiveRole == StaffRole.ADMIN || assignedClassIds.isEmpty() || st.classId in assignedClassIds)
        }

    val activeExamMarks: List<MarkEntry> =
        marks.filter {
            (selectedSessionId.isBlank() || it.sessionId == selectedSessionId) &&
                (selectedExamId.isBlank() || it.examId == selectedExamId)
        }

    val activeResultRecords: List<ResultRecord> =
        resultRecords.filter {
            (selectedSessionId.isBlank() || it.sessionId == selectedSessionId) &&
                (selectedExamId.isBlank() || it.examId == selectedExamId)
        }

    val allStudentSummaries: List<StudentResultSummary> = ResultCalculator.computeClassResults(
        students = visibleStudents,
        subjects = subjects,
        marks = activeExamMarks,
        resultRecords = activeResultRecords,
        classesById = classesById,
        sectionsById = sectionsById,
        sessionsById = sessionsById,
        examination = selectedExam,
        allGradingRules = gradingRules
    )

    val completedResultCount: Int = allStudentSummaries.count { it.status == ResultStatus.COMPLETE }
    val pendingResultCount: Int = allStudentSummaries.count { it.status == ResultStatus.INCOMPLETE }

    val classStats: List<ClassPerformanceStats> = visibleClasses.map { cls ->
        ResultCalculator.computeClassPerformance(cls, allStudentSummaries, subjects)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: SchoolRepository,
    val currentUserId: String
) : ViewModel() {

    private val _campusId = MutableStateFlow<String?>(null)
    private val _initError = MutableStateFlow<String?>(null)
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    private val _selectedExamId = MutableStateFlow<String?>(null)
    // Allows an Administrator to preview Teacher permissions with selected classes
    private val _rolePreviewOverride = MutableStateFlow<StaffRole?>(null)
    private val _previewAssignedClassIds = MutableStateFlow<List<String>>(emptyList())

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        initializeWorkspace()
    }

    fun initializeWorkspace() {
        viewModelScope.launch {
            _initError.value = null
            val res = repository.ensureCampusInitialized()
            res.onSuccess { cid -> _campusId.value = cid }
                .onFailure { err -> _initError.value = err.message ?: "Failed to initialize campus workspace." }
        }
    }

    val uiState: StateFlow<UiState<SchoolSnapshot>> = combine(_campusId, _initError) { cid, err -> cid to err }
        .flatMapLatest { (campusId, initErr) ->
            if (initErr != null) {
                flowOf<UiState<SchoolSnapshot>>(UiState.Error(initErr))
            } else if (campusId == null) {
                flowOf<UiState<SchoolSnapshot>>(UiState.Loading)
            } else {
                observeSchoolSnapshotFlow(campusId)
                    .map<SchoolSnapshot, UiState<SchoolSnapshot>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        emit(UiState.Error(root.message ?: "Unable to sync school records."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private fun observeSchoolSnapshotFlow(campusId: String): Flow<SchoolSnapshot> {
        val coreFlow = combine(
            repository.observeCampus(campusId),
            repository.observeStaff(campusId),
            repository.observeSessions(campusId),
            repository.observeExaminations(campusId),
            repository.observeClasses(campusId)
        ) { campus, staff, sessions, exams, classes ->
            CoreBundle(campus, staff, sessions, exams, classes)
        }

        val academicFlow = combine(
            repository.observeSections(campusId),
            repository.observeSubjects(campusId),
            repository.observeStudents(campusId),
            repository.observeMarks(campusId),
            repository.observeGradingRules(campusId)
        ) { sections, subjects, students, marks, rules ->
            AcademicBundle(sections, subjects, students, marks, rules)
        }

        val controlsFlow = combine(
            repository.observeResultRecords(campusId),
            _selectedSessionId,
            _selectedExamId,
            _rolePreviewOverride,
            _previewAssignedClassIds
        ) { records, sessOverride, examOverride, roleOverride, previewClasses ->
            ControlBundle(records, sessOverride, examOverride, roleOverride, previewClasses)
        }

        return combine(coreFlow, academicFlow, controlsFlow) { core, acad, ctrl ->
            val campus = core.campus ?: Campus(id = campusId, ownerId = currentUserId, memberUids = listOf(currentUserId))
            val myStaff = core.staff.firstOrNull { it.userUid == currentUserId || it.id == currentUserId }
            val isOwnerOrAdmin = campus.ownerId == currentUserId || myStaff?.parsedRole() == StaffRole.ADMIN
            val actualRole = if (isOwnerOrAdmin) StaffRole.ADMIN else (myStaff?.parsedRole() ?: StaffRole.TEACHER)
            val effectiveRole = if (isOwnerOrAdmin && ctrl.roleOverride != null) ctrl.roleOverride else actualRole
            val assignedClasses = when {
                isOwnerOrAdmin && effectiveRole == StaffRole.TEACHER -> ctrl.previewClasses
                myStaff != null -> myStaff.assignedClassIds
                else -> emptyList()
            }

            val sessId = ctrl.sessionOverride
                ?: campus.activeSessionId.takeIf { id -> core.sessions.any { it.id == id } }
                ?: core.sessions.firstOrNull { it.current }?.id
                ?: core.sessions.firstOrNull()?.id
                ?: ""

            val examId = ctrl.examOverride
                ?: campus.activeExamId.takeIf { id -> core.exams.any { it.id == id } }
                ?: core.exams.sortedBy { it.sortOrder }.firstOrNull()?.id
                ?: ""

            SchoolSnapshot(
                campus = campus,
                staff = core.staff,
                sessions = core.sessions.sortedByDescending { it.name },
                examinations = core.exams.sortedBy { it.sortOrder },
                classes = core.classes.sortedBy { it.sortOrder },
                sections = acad.sections.sortedBy { it.name },
                subjects = acad.subjects.sortedWith(compareBy<Subject> { it.sortOrder }.thenBy { it.name }),
                students = acad.students.sortedWith(compareBy<Student> { it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.rollNumber }.thenBy { it.name }),
                marks = acad.marks,
                gradingRules = acad.rules.sortedBy { it.sortOrder },
                resultRecords = ctrl.records,
                effectiveRole = effectiveRole,
                isOwnerOrAdmin = isOwnerOrAdmin,
                assignedClassIds = assignedClasses,
                selectedSessionId = sessId,
                selectedExamId = examId
            )
        }
    }

    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
    }

    fun selectExam(examId: String) {
        _selectedExamId.value = examId
    }

    fun setRolePreview(role: StaffRole?, assignedClassIds: List<String>) {
        _rolePreviewOverride.value = role
        _previewAssignedClassIds.value = assignedClassIds
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun postMessage(msg: String) {
        _actionMessage.value = msg
    }

    private fun runMutation(successMessage: String? = null, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = block()
            _isBusy.value = false
            res.onSuccess { if (!successMessage.isNullOrBlank()) _actionMessage.value = successMessage }
                .onFailure { _actionMessage.value = it.message ?: "Operation failed." }
        }
    }

    // --- MUTATIONS ---

    fun updateCampusSettings(
        campus: Campus,
        schoolName: String,
        campusName: String,
        address: String = campus.address,
        contact: String = campus.contact,
        studentEntryFields: List<String> = campus.studentEntryFields,
        allowDecimalMarks: Boolean,
        activeSessionId: String,
        activeExamId: String,
        logoBase64: String?
    ) = runMutation("Campus settings saved.") {
        repository.updateCampusSettings(
            campus, schoolName, campusName, address, contact, studentEntryFields, allowDecimalMarks, activeSessionId, activeExamId, logoBase64
        )
    }

    fun addSession(campus: Campus, name: String, current: Boolean) = runMutation("Academic session added.") {
        repository.addSession(campus, name, current)
    }

    fun addExamination(
        campus: Campus,
        sessionId: String,
        name: String,
        examType: ExamType,
        useCustomGrading: Boolean,
        sortOrder: Long,
        components: List<ExamComponent> = emptyList(),
        selectAfterCreate: Boolean = true
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addExamination(campus, sessionId, name, examType, useCustomGrading, sortOrder, components)
            _isBusy.value = false
            res.onSuccess { newId ->
                if (selectAfterCreate) _selectedExamId.value = newId
                _actionMessage.value = "Examination '$name' added."
            }.onFailure {
                _actionMessage.value = it.message ?: "Failed to add examination."
            }
        }
    }

    fun toggleExamCustomGrading(campusId: String, examId: String, useCustom: Boolean) = runMutation {
        repository.updateExaminationCustomGrading(campusId, examId, useCustom)
    }

    fun updateExamination(
        campusId: String,
        examId: String,
        sessionId: String,
        name: String,
        examType: ExamType,
        useCustomGrading: Boolean,
        sortOrder: Long,
        components: List<ExamComponent> = emptyList()
    ) = runMutation("Examination updated.") {
        repository.updateExamination(campusId, examId, sessionId, name, examType, useCustomGrading, sortOrder, components)
    }

    fun deleteExamination(campusId: String, examId: String) = runMutation("Examination deleted.") {
        repository.deleteExamination(campusId, examId)
    }

    fun addClass(campus: Campus, name: String, sortOrder: Long, sectionsCsv: String) {
        val secList = sectionsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addClassWithSections(campus, name, sortOrder, secList)
            _isBusy.value = false
            res.onSuccess { _actionMessage.value = "Class '$name' created." }
                .onFailure { _actionMessage.value = it.message ?: "Failed to add class." }
        }
    }

    fun seedStandardClasses(campus: Campus, existing: List<SchoolClass>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.seedStandardClasses(campus, existing)
            _isBusy.value = false
            res.onSuccess { count ->
                _actionMessage.value = if (count > 0) "Added $count standard classes (Playgroup - Class 10)." else "All standard classes already exist."
            }.onFailure { _actionMessage.value = it.message ?: "Failed to create classes." }
        }
    }

    fun addSection(campus: Campus, classId: String, name: String) = runMutation("Section '$name' added.") {
        repository.addSection(campus, classId, name)
    }

    fun deleteSection(campusId: String, sectionId: String) = runMutation("Section deleted.") {
        repository.deleteSection(campusId, sectionId)
    }

    fun deleteClass(campusId: String, classId: String) = runMutation("Class deleted.") {
        repository.deleteClass(campusId, classId)
    }

    fun saveSubject(
        campus: Campus,
        subjectId: String?,
        classId: String,
        name: String,
        maxMarks: Double,
        passingMarks: Double,
        compulsory: Boolean,
        sortOrder: Long
    ) = runMutation(if (subjectId == null) "Subject added." else "Subject updated.") {
        repository.saveSubject(campus, subjectId, classId, name, maxMarks, passingMarks, compulsory, sortOrder)
    }

    fun addSampleSubjects(campus: Campus, classId: String, existing: List<Subject>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addDefaultSubjectsForClass(campus, classId, existing)
            _isBusy.value = false
            res.onSuccess { count ->
                _actionMessage.value = if (count > 0) "Added $count standard subjects." else "Subjects already configured."
            }.onFailure { _actionMessage.value = it.message ?: "Failed to add subjects." }
        }
    }

    fun deleteSubject(campusId: String, subjectId: String) = runMutation("Subject deleted.") {
        repository.deleteSubject(campusId, subjectId)
    }

    fun saveStudent(
        campus: Campus,
        studentId: String?,
        name: String,
        fatherName: String,
        rollNumber: String,
        admissionNumber: String,
        classId: String,
        sectionId: String,
        sessionId: String,
        photoBase64: String?
    ) = runMutation(if (studentId == null) "Student added." else "Student updated.") {
        repository.saveStudent(campus, studentId, name, fatherName, rollNumber, admissionNumber, classId, sectionId, sessionId, photoBase64)
    }

    fun deleteStudent(campusId: String, studentId: String) = runMutation("Student removed.") {
        repository.deleteStudent(campusId, studentId)
    }

    suspend fun saveMarkEntrySuspend(
        campus: Campus,
        sessionId: String,
        examId: String,
        student: Student,
        subject: Subject,
        obtainedMarks: Double,
        existingMark: MarkEntry?,
        componentMarks: Map<String, Double> = emptyMap()
    ): Result<Unit> {
        return repository.saveMarkEntry(campus, sessionId, examId, student, subject, obtainedMarks, existingMark, componentMarks)
    }

    fun saveTeacherRemarks(
        campus: Campus,
        sessionId: String,
        examId: String,
        summary: StudentResultSummary,
        remarks: String,
        existingRecord: ResultRecord?
    ) = runMutation("Teacher's remarks saved.") {
        repository.saveResultRecord(campus, sessionId, examId, summary, remarks, existingRecord)
    }

    fun saveGradingRule(
        campus: Campus,
        ruleId: String?,
        examId: String,
        minPct: Double,
        maxPct: Double,
        grade: String,
        remarks: String,
        passing: Boolean,
        sortOrder: Long
    ) = runMutation("Grading rule saved.") {
        repository.saveGradingRule(campus, ruleId, examId, minPct, maxPct, grade, remarks, passing, sortOrder)
    }

    fun deleteGradingRule(campusId: String, ruleId: String) = runMutation("Grading rule deleted.") {
        repository.deleteGradingRule(campusId, ruleId)
    }

    fun saveStaffMember(
        campus: Campus,
        userUid: String,
        email: String,
        displayName: String,
        role: StaffRole,
        assignedClassIds: List<String>,
        active: Boolean
    ) = runMutation("Staff member saved.") {
        repository.saveStaffMember(campus, userUid, email, displayName, role, assignedClassIds, active)
    }

    fun importStudents(campus: Campus, drafts: List<ImportedStudentDraft>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.importStudents(campus, drafts)
            _isBusy.value = false
            res.onSuccess { count -> _actionMessage.value = "Imported $count students successfully." }
                .onFailure { _actionMessage.value = it.message ?: "Import failed." }
        }
    }

    fun importMarks(
        campus: Campus,
        sessionId: String,
        examId: String,
        drafts: List<ImportedMarkDraft>,
        existingMarks: List<MarkEntry>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.importMarks(campus, sessionId, examId, drafts, existingMarks)
            _isBusy.value = false
            res.onSuccess { count -> _actionMessage.value = "Imported $count mark entries successfully." }
                .onFailure { _actionMessage.value = it.message ?: "Marks import failed." }
        }
    }

    fun importSubjects(campus: Campus, drafts: List<ImportedSubjectDraft>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.importSubjects(campus, drafts)
            _isBusy.value = false
            res.onSuccess { count -> _actionMessage.value = "Imported $count subjects successfully." }
                .onFailure { _actionMessage.value = it.message ?: "Subjects import failed." }
        }
    }

    private data class CoreBundle(
        val campus: Campus?,
        val staff: List<StaffMember>,
        val sessions: List<AcademicSession>,
        val exams: List<Examination>,
        val classes: List<SchoolClass>
    )

    private data class AcademicBundle(
        val sections: List<ClassSection>,
        val subjects: List<Subject>,
        val students: List<Student>,
        val marks: List<MarkEntry>,
        val rules: List<GradingRule>
    )

    private data class ControlBundle(
        val records: List<ResultRecord>,
        val sessionOverride: String?,
        val examOverride: String?,
        val roleOverride: StaffRole?,
        val previewClasses: List<String>
    )
}
