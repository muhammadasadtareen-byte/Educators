package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AcademicSession
import com.example.data.model.Campus
import com.example.data.model.ClassSection
import com.example.data.model.ExamType
import com.example.data.model.Examination
import com.example.data.model.GradingRule
import com.example.data.model.MarkEntry
import com.example.data.model.ResultRecord
import com.example.data.model.SchoolClass
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Student
import com.example.data.model.StudentResultSummary
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.domain.ImportedMarkDraft
import com.example.domain.ImportedStudentDraft
import com.example.domain.ImportedSubjectDraft
import com.example.domain.ResultCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SchoolRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun shortId(prefix: String): String {
        val raw = UUID.randomUUID().toString().replace("-", "").take(10)
        return "${prefix}_$raw"
    }

    private fun campusDoc(campusId: String) = db.collection("campuses").document(campusId)

    /**
     * Ensures the signed-in user has a UserProfile and at least one accessible Campus workspace.
     * If the user has been added as staff to an existing campus, joins that campus automatically;
     * otherwise initializes a fresh Campus workspace for The Educators Abbottabad Main Campus.
     */
    suspend fun ensureCampusInitialized(): Result<String> {
        var path = "campuses"
        return try {
            val uid = requireUserId()
            val email = auth.currentUser?.email.orEmpty()
            val displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Administrator" }

            // 1. Check user profile
            val userRef = db.collection("users").document(uid)
            path = userRef.path
            val userSnap = userRef.get().await()
            val existingProfile = userSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (existingProfile != null && existingProfile.activeCampusId.isNotBlank()) {
                val campusSnap = campusDoc(existingProfile.activeCampusId).get().await()
                if (campusSnap.exists()) {
                    return Result.success(existingProfile.activeCampusId)
                }
            }

            // 2. Check if user is already a member of any campus (e.g. invited Teacher/Admin)
            val memberCampuses = db.collection("campuses")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            val existingCampusId = memberCampuses.documents.firstOrNull()?.id
            if (existingCampusId != null) {
                val userPayload = mapOf(
                    "userId" to uid,
                    "email" to email.take(120),
                    "displayName" to displayName.take(100),
                    "activeCampusId" to existingCampusId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (userSnap.exists()) {
                    userRef.update(
                        mapOf(
                            "email" to email.take(120),
                            "displayName" to displayName.take(100),
                            "activeCampusId" to existingCampusId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    userRef.set(userPayload).await()
                }
                return Result.success(existingCampusId)
            }

            // 3. Create new Campus workspace for this user
            val campusId = "campus_${uid.take(16)}"
            val sessionId = "sess_2026_27"
            val examMonthlyId = "exam_monthly"
            val examMidtermId = "exam_midterm"
            val examFinalId = "exam_final"
            val examAnnualId = "exam_annual"

            val cRef = campusDoc(campusId)
            path = cRef.path
            val members = listOf(uid)

            // Create the parent campus document first so subcollection security rules
            // (isCampusAdmin -> get(/campuses/{campusId})) can read it before batch commit.
            cRef.set(
                mapOf(
                    "ownerId" to uid,
                    "memberUids" to members,
                    "schoolName" to "THE EDUCATORS",
                    "campusName" to "ABBOTTABAD MAIN CAMPUS",
                    "activeSessionId" to sessionId,
                    "activeExamId" to examMidtermId,
                    "allowDecimalMarks" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val batch = db.batch()

            // Owner Staff record (ADMIN)
            batch.set(
                cRef.collection("staff").document(uid),
                mapOf(
                    "ownerId" to uid,
                    "memberUids" to members,
                    "userUid" to uid,
                    "email" to email.take(120),
                    "displayName" to displayName.take(100),
                    "role" to StaffRole.ADMIN.name,
                    "assignedClassIds" to emptyList<String>(),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // Default Academic Session
            batch.set(
                cRef.collection("sessions").document(sessionId),
                mapOf(
                    "ownerId" to uid,
                    "memberUids" to members,
                    "name" to "2026-2027",
                    "current" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // Default 4 Examinations
            val exams = listOf(
                Triple(examMonthlyId, "Monthly Test", ExamType.MONTHLY_TEST to 1L),
                Triple(examMidtermId, "Midterm", ExamType.MIDTERM to 2L),
                Triple(examFinalId, "Final Term", ExamType.FINAL_TERM to 3L),
                Triple(examAnnualId, "Annual Examination", ExamType.ANNUAL to 4L)
            )
            exams.forEach { (eId, eName, pair) ->
                batch.set(
                    cRef.collection("examinations").document(eId),
                    mapOf(
                        "ownerId" to uid,
                        "memberUids" to members,
                        "sessionId" to sessionId,
                        "name" to eName,
                        "examType" to pair.first.name,
                        "useCustomGrading" to false,
                        "sortOrder" to pair.second,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Default 7-Tier Grading Criteria
            ResultCalculator.defaultGradingTemplates().forEachIndexed { idx, rule ->
                batch.set(
                    cRef.collection("grading_rules").document("rule_def_${idx + 1}"),
                    mapOf(
                        "ownerId" to uid,
                        "memberUids" to members,
                        "examId" to "DEFAULT",
                        "minPercentage" to rule.minPercentage,
                        "maxPercentage" to rule.maxPercentage,
                        "grade" to rule.grade,
                        "remarks" to rule.remarks,
                        "passing" to rule.passing,
                        "sortOrder" to rule.sortOrder,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // User profile
            if (userSnap.exists()) {
                batch.update(
                    userRef,
                    mapOf(
                        "email" to email.take(120),
                        "displayName" to displayName.take(100),
                        "activeCampusId" to campusId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    userRef,
                    mapOf(
                        "userId" to uid,
                        "email" to email.take(120),
                        "displayName" to displayName.take(100),
                        "activeCampusId" to campusId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(campusId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- REALTIME OBSERVATION FLOWS (Query-Rule Parity: .whereArrayContains("memberUids", uid)) ---

    fun observeCampus(campusId: String): Flow<Campus?> =
        campusDoc(campusId).snapshots().map { snap ->
            snap.toObject(Campus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }

    fun <T> observeCampusCollection(
        campusId: String,
        collection: String,
        clazz: Class<T>
    ): Flow<List<T>> {
        val uid = requireUserId()
        return campusDoc(campusId).collection(collection)
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    fun observeStaff(campusId: String) = observeCampusCollection(campusId, "staff", StaffMember::class.java)
    fun observeSessions(campusId: String) = observeCampusCollection(campusId, "sessions", AcademicSession::class.java)
    fun observeExaminations(campusId: String) = observeCampusCollection(campusId, "examinations", Examination::class.java)
    fun observeClasses(campusId: String) = observeCampusCollection(campusId, "classes", SchoolClass::class.java)
    fun observeSections(campusId: String) = observeCampusCollection(campusId, "sections", ClassSection::class.java)
    fun observeSubjects(campusId: String) = observeCampusCollection(campusId, "subjects", Subject::class.java)
    fun observeStudents(campusId: String) = observeCampusCollection(campusId, "students", Student::class.java)
    fun observeMarks(campusId: String) = observeCampusCollection(campusId, "marks", MarkEntry::class.java)
    fun observeGradingRules(campusId: String) = observeCampusCollection(campusId, "grading_rules", GradingRule::class.java)
    fun observeResultRecords(campusId: String) = observeCampusCollection(campusId, "result_records", ResultRecord::class.java)

    // --- CAMPUS & SETTINGS MUTATIONS ---

    suspend fun updateCampusSettings(
        campus: Campus,
        schoolName: String,
        campusName: String,
        allowDecimalMarks: Boolean,
        activeSessionId: String,
        activeExamId: String,
        logoBase64: String?
    ): Result<Unit> {
        val path = campusDoc(campus.id).path
        return try {
            requireUserId()
            val payload = mutableMapOf<String, Any>(
                "schoolName" to schoolName.trim().take(120),
                "campusName" to campusName.trim().take(120),
                "allowDecimalMarks" to allowDecimalMarks,
                "activeSessionId" to activeSessionId,
                "activeExamId" to activeExamId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!logoBase64.isNullOrBlank()) {
                payload["logoBase64"] = logoBase64
            }
            campusDoc(campus.id).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- SESSIONS & EXAMINATIONS ---

    suspend fun addSession(campus: Campus, name: String, isCurrent: Boolean): Result<Unit> {
        val id = shortId("sess")
        val ref = campusDoc(campus.id).collection("sessions").document(id)
        return try {
            requireUserId()
            val payload = mapOf(
                "ownerId" to campus.ownerId,
                "memberUids" to campus.memberUids,
                "name" to name.trim().take(50),
                "current" to isCurrent,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun addExamination(
        campus: Campus,
        sessionId: String,
        name: String,
        examType: ExamType,
        useCustomGrading: Boolean,
        sortOrder: Long
    ): Result<Unit> {
        val id = shortId("exam")
        val ref = campusDoc(campus.id).collection("examinations").document(id)
        return try {
            requireUserId()
            val payload = mapOf(
                "ownerId" to campus.ownerId,
                "memberUids" to campus.memberUids,
                "sessionId" to sessionId,
                "name" to name.trim().take(80),
                "examType" to examType.name,
                "useCustomGrading" to useCustomGrading,
                "sortOrder" to sortOrder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateExaminationCustomGrading(campusId: String, examId: String, useCustom: Boolean): Result<Unit> {
        val ref = campusDoc(campusId).collection("examinations").document(examId)
        return try {
            requireUserId()
            ref.update(
                mapOf(
                    "useCustomGrading" to useCustom,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    // --- CLASSES & SECTIONS ---

    suspend fun addClassWithSections(
        campus: Campus,
        name: String,
        sortOrder: Long,
        sectionNames: List<String>
    ): Result<String> {
        val classId = shortId("cls")
        val ref = campusDoc(campus.id).collection("classes").document(classId)
        return try {
            requireUserId()
            val batch = db.batch()
            batch.set(
                ref,
                mapOf(
                    "ownerId" to campus.ownerId,
                    "memberUids" to campus.memberUids,
                    "name" to name.trim().take(50),
                    "sortOrder" to sortOrder,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val cleanedSections = sectionNames.map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("A") }
            cleanedSections.forEach { secName ->
                val secRef = campusDoc(campus.id).collection("sections").document(shortId("sec"))
                batch.set(
                    secRef,
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to campus.memberUids,
                        "classId" to classId,
                        "name" to secName.take(30),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(classId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun seedStandardClasses(campus: Campus, existingClasses: List<SchoolClass>): Result<Int> {
        val ref = campusDoc(campus.id).collection("classes")
        return try {
            requireUserId()
            val existingNames = existingClasses.map { it.name.lowercase() }.toSet()
            val toAdd = ResultCalculator.standardClassNames()
                .mapIndexed { idx, name -> idx to name }
                .filter { (_, name) -> !existingNames.contains(name.lowercase()) }
            if (toAdd.isEmpty()) return Result.success(0)

            val batch = db.batch()
            toAdd.forEach { (idx, name) ->
                val clsId = shortId("cls")
                batch.set(
                    ref.document(clsId),
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to campus.memberUids,
                        "name" to name,
                        "sortOrder" to (idx + 1).toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val secRef = campusDoc(campus.id).collection("sections").document(shortId("sec"))
                batch.set(
                    secRef,
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to campus.memberUids,
                        "classId" to clsId,
                        "name" to "A",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(toAdd.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun addSection(campus: Campus, classId: String, name: String): Result<Unit> {
        val ref = campusDoc(campus.id).collection("sections").document(shortId("sec"))
        return try {
            requireUserId()
            ref.set(
                mapOf(
                    "ownerId" to campus.ownerId,
                    "memberUids" to campus.memberUids,
                    "classId" to classId,
                    "name" to name.trim().take(30),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSection(campusId: String, sectionId: String): Result<Unit> {
        val ref = campusDoc(campusId).collection("sections").document(sectionId)
        return try {
            requireUserId()
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteClass(campusId: String, classId: String): Result<Unit> {
        val ref = campusDoc(campusId).collection("classes").document(classId)
        return try {
            requireUserId()
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    // --- SUBJECTS ---

    suspend fun saveSubject(
        campus: Campus,
        subjectId: String?,
        classId: String,
        name: String,
        maxMarks: Double,
        passingMarks: Double,
        compulsory: Boolean,
        sortOrder: Long
    ): Result<Unit> {
        val id = subjectId?.takeIf { it.isNotBlank() } ?: shortId("sub")
        val ref = campusDoc(campus.id).collection("subjects").document(id)
        return try {
            requireUserId()
            if (subjectId.isNullOrBlank()) {
                ref.set(
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to campus.memberUids,
                        "classId" to classId,
                        "name" to name.trim().take(80),
                        "maxMarks" to maxMarks,
                        "passingMarks" to passingMarks,
                        "compulsory" to compulsory,
                        "sortOrder" to sortOrder,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "classId" to classId,
                        "name" to name.trim().take(80),
                        "maxMarks" to maxMarks,
                        "passingMarks" to passingMarks,
                        "compulsory" to compulsory,
                        "sortOrder" to sortOrder,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (subjectId == null) OperationType.CREATE else OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun addDefaultSubjectsForClass(campus: Campus, classId: String, existingForClass: List<Subject>): Result<Int> {
        val col = campusDoc(campus.id).collection("subjects")
        return try {
            requireUserId()
            val existingNames = existingForClass.map { it.name.lowercase() }.toSet()
            val toAdd = ResultCalculator.class5SampleSubjects()
                .mapIndexed { idx, triple -> idx to triple }
                .filter { (_, triple) -> !existingNames.contains(triple.first.lowercase()) }
            if (toAdd.isEmpty()) return Result.success(0)

            val batch = db.batch()
            toAdd.forEach { (idx, triple) ->
                batch.set(
                    col.document(shortId("sub")),
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to campus.memberUids,
                        "classId" to classId,
                        "name" to triple.first,
                        "maxMarks" to triple.second,
                        "passingMarks" to triple.third,
                        "compulsory" to true,
                        "sortOrder" to (existingForClass.size + idx + 1).toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(toAdd.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, col.path)
            Result.failure(e)
        }
    }

    suspend fun importSubjects(campus: Campus, drafts: List<ImportedSubjectDraft>): Result<Int> {
        val col = campusDoc(campus.id).collection("subjects")
        return try {
            requireUserId()
            drafts.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEachIndexed { idx, d ->
                    batch.set(
                        col.document(shortId("sub")),
                        mapOf(
                            "ownerId" to campus.ownerId,
                            "memberUids" to campus.memberUids,
                            "classId" to d.classId,
                            "name" to d.name.take(80),
                            "maxMarks" to d.maxMarks,
                            "passingMarks" to d.passingMarks,
                            "compulsory" to d.compulsory,
                            "sortOrder" to (idx + 1).toLong(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(drafts.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, col.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubject(campusId: String, subjectId: String): Result<Unit> {
        val ref = campusDoc(campusId).collection("subjects").document(subjectId)
        return try {
            requireUserId()
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    // --- STUDENTS ---

    suspend fun saveStudent(
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
    ): Result<Unit> {
        val id = studentId?.takeIf { it.isNotBlank() } ?: shortId("stu")
        val ref = campusDoc(campus.id).collection("students").document(id)
        return try {
            requireUserId()
            if (studentId.isNullOrBlank()) {
                val payload = mapOf(
                    "ownerId" to campus.ownerId,
                    "memberUids" to campus.memberUids,
                    "name" to name.trim().take(100),
                    "fatherName" to fatherName.trim().take(100),
                    "rollNumber" to rollNumber.trim().take(30),
                    "admissionNumber" to admissionNumber.trim().take(40),
                    "classId" to classId,
                    "sectionId" to sectionId,
                    "sessionId" to sessionId,
                    "photoBase64" to photoBase64?.takeIf { it.isNotBlank() },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.set(payload).await()
            } else {
                val payload = mapOf(
                    "name" to name.trim().take(100),
                    "fatherName" to fatherName.trim().take(100),
                    "rollNumber" to rollNumber.trim().take(30),
                    "admissionNumber" to admissionNumber.trim().take(40),
                    "classId" to classId,
                    "sectionId" to sectionId,
                    "sessionId" to sessionId,
                    "photoBase64" to photoBase64?.takeIf { it.isNotBlank() },
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (studentId == null) OperationType.CREATE else OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun importStudents(campus: Campus, drafts: List<ImportedStudentDraft>): Result<Int> {
        val col = campusDoc(campus.id).collection("students")
        return try {
            requireUserId()
            drafts.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { d ->
                    batch.set(
                        col.document(shortId("stu")),
                        mapOf(
                            "ownerId" to campus.ownerId,
                            "memberUids" to campus.memberUids,
                            "name" to d.name.take(100),
                            "fatherName" to d.fatherName.take(100),
                            "rollNumber" to d.rollNumber.take(30),
                            "admissionNumber" to d.admissionNumber.take(40),
                            "classId" to d.classId,
                            "sectionId" to d.sectionId,
                            "sessionId" to d.sessionId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(drafts.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, col.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStudent(campusId: String, studentId: String): Result<Unit> {
        val ref = campusDoc(campusId).collection("students").document(studentId)
        return try {
            requireUserId()
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    // --- MARKS (Deterministic ID prevents duplicates!) ---

    fun deterministicMarkId(sessionId: String, examId: String, studentId: String, subjectId: String): String {
        val s = "${sessionId}_${examId}_${studentId}_${subjectId}"
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return s.take(128)
    }

    suspend fun saveMarkEntry(
        campus: Campus,
        sessionId: String,
        examId: String,
        student: Student,
        subject: Subject,
        obtainedMarks: Double,
        existingMark: MarkEntry?
    ): Result<Unit> {
        val markId = deterministicMarkId(sessionId, examId, student.id, subject.id)
        val ref = campusDoc(campus.id).collection("marks").document(markId)
        return try {
            val uid = requireUserId()
            require(obtainedMarks in 0.0..subject.maxMarks) {
                "Obtained marks must be between 0 and ${subject.maxMarks}"
            }
            if (existingMark == null) {
                ref.set(
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to campus.memberUids,
                        "sessionId" to sessionId,
                        "examId" to examId,
                        "classId" to student.classId,
                        "sectionId" to student.sectionId,
                        "studentId" to student.id,
                        "subjectId" to subject.id,
                        "obtainedMarks" to obtainedMarks,
                        "enteredByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "classId" to student.classId,
                        "sectionId" to student.sectionId,
                        "obtainedMarks" to obtainedMarks,
                        "enteredByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingMark == null) OperationType.CREATE else OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun importMarks(
        campus: Campus,
        sessionId: String,
        examId: String,
        drafts: List<ImportedMarkDraft>,
        existingMarks: List<MarkEntry>
    ): Result<Int> {
        val col = campusDoc(campus.id).collection("marks")
        return try {
            val uid = requireUserId()
            val existingIds = existingMarks.map { it.id }.toSet()
            drafts.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { d ->
                    val markId = deterministicMarkId(sessionId, examId, d.studentId, d.subjectId)
                    val docRef = col.document(markId)
                    if (existingIds.contains(markId)) {
                        batch.update(
                            docRef,
                            mapOf(
                                "classId" to d.classId,
                                "sectionId" to d.sectionId,
                                "obtainedMarks" to d.obtainedMarks,
                                "enteredByUid" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        batch.set(
                            docRef,
                            mapOf(
                                "ownerId" to campus.ownerId,
                                "memberUids" to campus.memberUids,
                                "sessionId" to sessionId,
                                "examId" to examId,
                                "classId" to d.classId,
                                "sectionId" to d.sectionId,
                                "studentId" to d.studentId,
                                "subjectId" to d.subjectId,
                                "obtainedMarks" to d.obtainedMarks,
                                "enteredByUid" to uid,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                batch.commit().await()
            }
            Result.success(drafts.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path)
            Result.failure(e)
        }
    }

    // --- RESULT RECORDS (Teacher Remarks & Synced Result Snapshot) ---

    suspend fun saveResultRecord(
        campus: Campus,
        sessionId: String,
        examId: String,
        summary: StudentResultSummary,
        teacherRemarks: String,
        existingRecord: ResultRecord?
    ): Result<Unit> {
        val recordId = "${sessionId}_${examId}_${summary.student.id}"
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val ref = campusDoc(campus.id).collection("result_records").document(recordId)
        return try {
            requireUserId()
            val cleanRemarks = teacherRemarks.trim().take(300).takeIf { it.isNotBlank() }
            if (existingRecord == null) {
                val payload = mapOf(
                    "ownerId" to campus.ownerId,
                    "memberUids" to campus.memberUids,
                    "sessionId" to sessionId,
                    "examId" to examId,
                    "classId" to summary.student.classId,
                    "sectionId" to summary.student.sectionId,
                    "studentId" to summary.student.id,
                    "totalMaxMarks" to summary.totalMaxMarks,
                    "totalObtainedMarks" to summary.totalObtainedMarks,
                    "percentage" to summary.overallPercentage.coerceIn(0.0, 100.0),
                    "grade" to summary.overallGrade.take(20),
                    "passed" to summary.passed,
                    "status" to summary.status.name,
                    "position" to (summary.position?.toLong() ?: 0L),
                    "teacherRemarks" to cleanRemarks,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.set(payload).await()
            } else {
                val payload = mapOf(
                    "classId" to summary.student.classId,
                    "sectionId" to summary.student.sectionId,
                    "totalMaxMarks" to summary.totalMaxMarks,
                    "totalObtainedMarks" to summary.totalObtainedMarks,
                    "percentage" to summary.overallPercentage.coerceIn(0.0, 100.0),
                    "grade" to summary.overallGrade.take(20),
                    "passed" to summary.passed,
                    "status" to summary.status.name,
                    "position" to (summary.position?.toLong() ?: 0L),
                    "teacherRemarks" to cleanRemarks,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingRecord == null) OperationType.CREATE else OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    // --- GRADING RULES ---

    suspend fun saveGradingRule(
        campus: Campus,
        ruleId: String?,
        examId: String,
        minPercentage: Double,
        maxPercentage: Double,
        grade: String,
        remarks: String,
        passing: Boolean,
        sortOrder: Long
    ): Result<Unit> {
        val id = ruleId?.takeIf { it.isNotBlank() } ?: shortId("rule")
        val ref = campusDoc(campus.id).collection("grading_rules").document(id)
        return try {
            requireUserId()
            val cleanGrade = grade.trim().ifBlank { "-" }.take(10)
            val cleanRemarks = remarks.trim().ifBlank { "Grade" }.take(60)
            if (ruleId.isNullOrBlank()) {
                ref.set(
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to campus.memberUids,
                        "examId" to examId,
                        "minPercentage" to minPercentage.coerceIn(0.0, 100.0),
                        "maxPercentage" to maxPercentage.coerceIn(minPercentage, 100.0),
                        "grade" to cleanGrade,
                        "remarks" to cleanRemarks,
                        "passing" to passing,
                        "sortOrder" to sortOrder,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "examId" to examId,
                        "minPercentage" to minPercentage.coerceIn(0.0, 100.0),
                        "maxPercentage" to maxPercentage.coerceIn(minPercentage, 100.0),
                        "grade" to cleanGrade,
                        "remarks" to cleanRemarks,
                        "passing" to passing,
                        "sortOrder" to sortOrder,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (ruleId == null) OperationType.CREATE else OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteGradingRule(campusId: String, ruleId: String): Result<Unit> {
        val ref = campusDoc(campusId).collection("grading_rules").document(ruleId)
        return try {
            requireUserId()
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    // --- STAFF & ROLE MANAGEMENT ---

    suspend fun saveStaffMember(
        campus: Campus,
        userUid: String,
        email: String,
        displayName: String,
        role: StaffRole,
        assignedClassIds: List<String>,
        active: Boolean
    ): Result<Unit> {
        val cleanUid = userUid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val ref = campusDoc(campus.id).collection("staff").document(cleanUid)
        return try {
            val currentUid = requireUserId()
            require(cleanUid.isNotBlank()) { "Staff User UID is required." }

            val updatedMembers = (campus.memberUids + cleanUid).distinct().take(50)
            val existingSnap = ref.get().await()
            if (existingSnap.exists()) {
                ref.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "email" to email.trim().take(120),
                        "displayName" to displayName.trim().ifBlank { "Staff Member" }.take(100),
                        "role" to role.name,
                        "assignedClassIds" to assignedClassIds.take(30),
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "ownerId" to campus.ownerId,
                        "memberUids" to updatedMembers,
                        "userUid" to cleanUid,
                        "email" to email.trim().take(120),
                        "displayName" to displayName.trim().ifBlank { "Staff Member" }.take(100),
                        "role" to role.name,
                        "assignedClassIds" to assignedClassIds.take(30),
                        "active" to active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (updatedMembers != campus.memberUids) {
                campusDoc(campus.id).update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                syncMemberUidsAcrossCampus(campus.id, updatedMembers, currentUid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    private suspend fun syncMemberUidsAcrossCampus(
        campusId: String,
        memberUids: List<String>,
        adminUid: String
    ) {
        val collections = listOf(
            "staff", "sessions", "examinations", "classes", "sections",
            "subjects", "students", "marks", "grading_rules", "result_records"
        )
        val docRefsWithCollection = mutableListOf<Pair<String, com.google.firebase.firestore.DocumentReference>>()
        for (colName in collections) {
            val snap = campusDoc(campusId).collection(colName)
                .whereArrayContains("memberUids", adminUid)
                .get()
                .await()
            snap.documents.forEach { d ->
                docRefsWithCollection.add(colName to d.reference)
            }
        }
        docRefsWithCollection.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (colName, docRef) ->
                val updates = mutableMapOf<String, Any>(
                    "memberUids" to memberUids,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (colName == "marks") {
                    updates["enteredByUid"] = adminUid
                }
                batch.update(docRef, updates)
            }
            batch.commit().await()
        }
    }
}
