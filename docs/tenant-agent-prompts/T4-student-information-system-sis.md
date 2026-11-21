# Chat T4 — Student Information System (SIS), Demographics & Guardian Management

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-3-development`.

## Your mission

Deliver the comprehensive Student Information System (SIS) (**Module 14: Actions 162–174**). You own student master profiles, demographic records, parent/guardian relationships (up to 3 digital representatives), immutable student behavioral notes (`fn_note_guard`), and statutory legal hold compliance (`app.legal_hold`).

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T4-S1** | `ACT-162`..`ACT-165` | Student Master Directory: comprehensive student profile (personal info, enrollment ID, roll number, department, program, section, status [ACTIVE, SUSPENDED, WITHDRAWN, ALUMNI]). Zero hard deletion. | §7.14 |
| **T4-S2** | `ACT-166`, `ACT-167` | Guardian & Representative Registry: link up to 3 parents/guardians per student with relationship type (Father, Mother, Legal Guardian, Local Sponsor), contact authorization, and emergency flags. | §7.14 |
| **T4-S3** | `ACT-168`..`ACT-170` | Immutable Student Behavioral Notes (`fn_note_guard`): counseling and disciplinary notes. Disallow `UPDATE`; allow `DELETE` only by `edu_retention` past retention date if no legal hold exists. | §7.14 |
| **T4-S4** | `ACT-171`..`ACT-174` | Legal Holds & Statutory Protection: apply and release legal holds on student records (`app.legal_hold`) preventing document purges during active investigations. | §7.14 |

---

### Detailed Technical Specifications

#### Story T4-S1: Student Master Profile (M14)
- **Database Table:** `app.student_profile` (`id`, `tenant_id`, `institution_id`, `user_id`, `admission_number`, `roll_number`, `program_id`, `section_id`, `date_of_birth`, `gender`, `blood_group`, `national_id_hash`, `address_json`, `status`).
- **Hard Rule: Zero Hard Deletions:**
  - Deleting a student via API sets `status = WITHDRAWN` and records `withdrawn_at = now()`. Physical row deletion is blocked by database trigger `trg_prevent_student_hard_delete`.
- **Backend:**
  - `StudentController.java` (`GET /api/students`, `POST /api/students`, `GET /api/students/{id}`, `PUT /api/students/{id}`, `DELETE /api/students/{id}`).
  - Search & filter: filter by institution, department, program, batch, section, and enrollment status with cursor-based pagination.
- **Frontend:**
  - `StudentDirectoryPage.tsx`: Data table with quick filters, status badges, avatar preview, and export to CSV/Excel.

#### Story T4-S2: Guardian & Representative Registry
- **Database Table:** `app.guardian_relationship` (`id`, `tenant_id`, `student_id`, `guardian_name`, `relationship_type`, `phone`, `email`, `is_primary_contact`, `is_emergency_contact`, `can_pickup`).
- **Validation:**
  - Maximum 3 guardians per student enforced by constraint `ck_max_guardians`.
  - Exactly one guardian must have `is_primary_contact = true`.
- **Frontend:**
  - `GuardianManagerModal.tsx` allowing dynamic addition and verification of guardian contact details.

#### Story T4-S3 & T4-S4: Immutable Behavioral Notes & Legal Holds
- **Database Tables:**
  - `app.student_note` (`id`, `tenant_id`, `student_id`, `author_user_id`, `category` [ACADEMIC, BEHAVIORAL, MEDICAL, COUNSELING], `note_text`, `is_confidential`, `created_at`).
  - `app.legal_hold` (`id`, `tenant_id`, `target_entity_type` [STUDENT, STAFF], `target_entity_id`, `reason`, `placed_by_user_id`, `placed_at`, `released_at`, `is_active`).
- **Hard Rule (`fn_note_guard`, DB-60):**
  - Any `UPDATE` on `app.student_note` raises an exception: `STUDENT_NOTES_ARE_IMMUTABLE`.
  - Physical `DELETE` is allowed only by database role `edu_retention` after retention expiry AND provided `app.legal_hold.is_active = false`.
- **Backend:**
  - `StudentNoteController.java` (`GET /api/students/{id}/notes`, `POST /api/students/{id}/notes`).
  - `LegalHoldController.java` (`POST /api/legal-holds`, `POST /api/legal-holds/{id}/release`).
- **Frontend:**
  - `StudentNotesTimeline.tsx`: Read-only audit timeline rendering note timestamp, author name, and confidential shield icon.

---

## Database migrations you own

Use **only** numbers **61** and **62**.
- `61_v41_student_profile_and_guardians.sql`: `app.student_profile`, `app.guardian_relationship`, trigger `trg_prevent_student_hard_delete`.
- `62_v41_student_notes_and_legal_holds.sql`: `app.student_note`, `app.legal_hold`, trigger `trg_student_note_guard`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.student.controller.StudentController`
  - `com.edu360.api.student.controller.StudentNoteController`
  - `com.edu360.api.student.controller.LegalHoldController`
  - `com.edu360.api.student.service.StudentService`
  - `com.edu360.api.student.service.StudentNoteService`
- **Frontend:**
  - `app/src/features/students/pages/StudentDirectoryPage.tsx`
  - `app/src/features/students/pages/StudentProfileDetailPage.tsx`
  - `app/src/features/students/components/GuardianManagerModal.tsx`
  - `app/src/features/students/components/StudentNotesTimeline.tsx`
  - `app/src/features/students/components/LegalHoldBanner.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/student/StudentServiceIT.java`
  - `api/src/test/java/com/edu360/api/student/StudentNoteGuardIT.java`
  - `app/src/features/students/__tests__/StudentDirectoryPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T2 for programs, batches, and sections.
- **Chats T5, T6, T7, T8 depend on you:** Admissions converts applicants to your student profiles; Timetable, Attendance, Exams, and Fees bind to your student IDs.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=StudentServiceIT,StudentNoteGuardIT`
2. `cd app && npm test -- StudentDirectoryPage`
3. `npm run lint && npm run build`
