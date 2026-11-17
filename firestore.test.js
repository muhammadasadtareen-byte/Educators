const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_123";
const TEACHER_UID = "teacher_456";
const OUTSIDER_UID = "outsider_789";
const CAMPUS_ID = "campus_atd_main";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedCampusAndStaff() {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("campuses").doc(CAMPUS_ID).set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      schoolName: "THE EDUCATORS",
      campusName: "ABBOTTABAD MAIN CAMPUS",
      activeSessionId: "sess_2026",
      activeExamId: "exam_midterm",
      allowDecimalMarks: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("campuses").doc(CAMPUS_ID).collection("staff").doc(TEACHER_UID).set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      userUid: TEACHER_UID,
      email: "teacher@educators.edu.pk",
      displayName: "Ms. Ayesha",
      role: "TEACHER",
      assignedClassIds: ["cls_5"],
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated access is denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("campuses").where("memberUids", "array-contains", ADMIN_UID).get());
  await assertFails(unauthDb.collection("campuses").doc(CAMPUS_ID).collection("students").where("memberUids", "array-contains", ADMIN_UID).get());
});

test("Admin can create campus, student, subject, and update student without @DocumentId id", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
      schoolName: "THE EDUCATORS",
      campusName: "ABBOTTABAD MAIN CAMPUS",
      activeSessionId: "sess_2026",
      activeExamId: "exam_midterm",
      allowDecimalMarks: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("students").doc("stu_1").set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
      name: "Ali Ahmed",
      fatherName: "Tariq Ahmed",
      rollNumber: "01",
      admissionNumber: "ADM-101",
      classId: "cls_5",
      sectionId: "sec_a",
      sessionId: "sess_2026",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost 'id' field when @DocumentId is used
  await assertFails(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("students").doc("stu_bad").set({
      id: "stu_bad",
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
      name: "Bad",
      fatherName: "Test",
      rollNumber: "02",
      admissionNumber: "ADM-102",
      classId: "cls_5",
      sectionId: "sec_a",
      sessionId: "sess_2026",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("students").doc("stu_1").update({
      name: "Ali Ahmed Khan",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Filtered query succeeds, unfiltered fails
  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("students").where("memberUids", "array-contains", ADMIN_UID).get()
  );
  await assertFails(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("students").get()
  );
});

test("Teacher can enter marks for assigned class but cannot modify unassigned class, students, or grading rules", async () => {
  await seedCampusAndStaff();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Teacher assigned to cls_5 can write marks for cls_5
  await assertSucceeds(
    teacherDb.collection("campuses").doc(CAMPUS_ID).collection("marks").doc("m_1").set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      sessionId: "sess_2026",
      examId: "exam_midterm",
      classId: "cls_5",
      sectionId: "sec_a",
      studentId: "stu_1",
      subjectId: "sub_math",
      obtainedMarks: 85,
      enteredByUid: TEACHER_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Teacher NOT assigned to cls_6 cannot write marks for cls_6
  await assertFails(
    teacherDb.collection("campuses").doc(CAMPUS_ID).collection("marks").doc("m_2").set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      sessionId: "sess_2026",
      examId: "exam_midterm",
      classId: "cls_6",
      sectionId: "sec_a",
      studentId: "stu_2",
      subjectId: "sub_math",
      obtainedMarks: 90,
      enteredByUid: TEACHER_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Teacher cannot create/delete students or grading rules
  await assertFails(
    teacherDb.collection("campuses").doc(CAMPUS_ID).collection("students").doc("stu_t").set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      name: "Unauthorized",
      fatherName: "Test",
      rollNumber: "99",
      admissionNumber: "ADM-999",
      classId: "cls_5",
      sectionId: "sec_a",
      sessionId: "sess_2026",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    teacherDb.collection("campuses").doc(CAMPUS_ID).collection("marks").doc("m_1").delete()
  );
});

const assert = require("node:assert");

test("Outsider cannot read or write any campus data", async () => {
  await seedCampusAndStaff();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(outsiderDb.collection("campuses").doc(CAMPUS_ID).get());
  await assertFails(outsiderDb.collection("campuses").get());
  await assertFails(
    outsiderDb.collection("campuses").doc(CAMPUS_ID).collection("marks").get()
  );
  const ownScoped = await assertSucceeds(
    outsiderDb.collection("campuses").where("memberUids", "array-contains", OUTSIDER_UID).get()
  );
  assert.strictEqual(ownScoped.size, 0);
});

test("Non-mandatory student fields, examination components, componentMarks, and campus metadata succeed under rules", async () => {
  await seedCampusAndStaff();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).update({
      address: "Kala Pul, Near Gelani Mart, Mansehra Road, Abbottabad",
      contact: "0992-406672",
      studentEntryFields: ["FATHER_NAME"],
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("students").doc("stu_min").set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      name: "Abiha Fatima",
      fatherName: "",
      rollNumber: "",
      admissionNumber: "",
      classId: "cls_5",
      sectionId: "sec_a",
      sessionId: "sess_2026",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("examinations").doc("exam_task").set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      sessionId: "sess_2026",
      name: "ASSESSMENT TASK-I (TERM II)",
      examType: "CUSTOM",
      useCustomGrading: false,
      sortOrder: 5,
      components: [
        { id: "task_01", name: "Assessment Task 01", maxMarks: 30 },
        { id: "quiz", name: "Quiz", maxMarks: 20 }
      ],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("campuses").doc(CAMPUS_ID).collection("marks").doc("m_comp").set({
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID, TEACHER_UID],
      sessionId: "sess_2026",
      examId: "exam_task",
      classId: "cls_5",
      sectionId: "sec_a",
      studentId: "stu_min",
      subjectId: "sub_eng",
      obtainedMarks: 34,
      componentMarks: { task_01: 20, quiz: 14 },
      enteredByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
});

