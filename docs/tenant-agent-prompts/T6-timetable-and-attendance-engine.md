# Chat T6 — Timetable Engine, GiST Exclusion Constraints & Attendance Management

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Deliver the automated Timetable Generator and daily/period Attendance tracking engine (**Modules 4 and 5: Actions 42–54**). You own timetable grid generation, PostgreSQL GiST exclusion constraints preventing teacher/room double-booking, online meeting links, daily & period attendance capture, biometric device sync, medical exemption formulas (§20.4), and Term Lock integration (`is_term_locked()`).

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T6-S1** | `ACT-042`..`ACT-044` | Timetable Slot Generation & GiST Conflict Guard: weekly timetable grid by section/room, teacher assignment, and PostgreSQL GiST exclusion constraints preventing clashing bookings. | §7.4 |
| **T6-S2** | `ACT-042`..`ACT-044` | Online Class Sessions: schedule virtual lecture links (Zoom, Google Meet, Teams) with automated cryptographic session tokens and student access logs. | §7.4 |
| **T6-S3** | `ACT-045`..`ACT-049` | Attendance Capture (Daily & Period): faculty attendance entry sheet (Present, Absent, Late, Excused), mobile-first quick toggles, and bulk submission API. | §7.5 |
| **T6-S4** | `ACT-050`..`ACT-052` | Medical Exemption & Adjusted Attendance (§20.4): student medical exemption request submission, doctor certificate attachment, and automated recalculation of adjusted attendance %. | §7.5 |
| **T6-S5** | `ACT-053`, `ACT-054` | Term Lock & Attendance Gates: verify **`is_term_locked()`** rejecting attendance edits on locked terms with HTTP 409 `TERM_LOCKED`; configurable attendance threshold gate per institution/department (Decision PO-T02) for exam eligibility. | §7.5 |

---

### Detailed Technical Specifications

#### Story T6-S1 & T6-S2: Timetable Engine & GiST Exclusion Constraints (M04)
- **Database Tables:**
  - `app.room` (`id`, `tenant_id`, `institution_id`, `room_number`, `building`, `capacity`, `room_type` [LECTURE_HALL, LAB, AUDITORIUM]).
  - `app.timetable_slot` (`id`, `tenant_id`, `institution_id`, `section_id`, `course_id`, `teacher_id`, `room_id`, `day_of_week`, `start_time`, `end_time`, `online_meeting_url`).
- **PostgreSQL GiST Double-Booking Prevention (DB-30):**
  - Requires extension `btree_gist`.
  - Constraint 1 (Teacher Clash): `EXCLUDE USING gist (teacher_id WITH =, day_of_week WITH =, tsrange(start_time, end_time) WITH &&)`.
  - Constraint 2 (Room Clash): `EXCLUDE USING gist (room_id WITH =, day_of_week WITH =, tsrange(start_time, end_time) WITH &&)`.
  - Attempting to schedule a teacher or room during an overlapping slot raises database error `23P01 exclusion_violation`, caught and translated by `GlobalExceptionHandler` to HTTP 409 `TEACHER_DOUBLE_BOOKED` or `ROOM_OCCUPIED`.
- **Frontend:**
  - `TimetableGridView.tsx`: Interactive drag-and-drop timetable grid with instant visual clash detection.

#### Story T6-S3, T6-S4 & T6-S5: Attendance Capture, Formulas & Term Lock Gate (M05)
- **Database Tables:**
  - `app.attendance_session` (`id`, `tenant_id`, `section_id`, `timetable_slot_id`, `session_date`, `taken_by_teacher_id`, `session_type` [DAILY, PERIOD]).
  - `app.attendance_entry` (`id`, `session_id`, `student_id`, `status` [PRESENT, ABSENT, LATE, EXCUSED], `remarks`).
  - `app.attendance_medical_exemption` (`id`, `tenant_id`, `student_id`, `start_date`, `end_date`, `exempted_sessions_count`, `document_ref`, `status` [PENDING, APPROVED, REJECTED], `approved_by_user_id`).
- **Mathematical Specification (§20.4):**
  - Standard Attendance:
    $$\text{Attendance } \% = \frac{\text{Sessions Attended}}{\text{Total Valid Sessions Held}} \times 100$$
  - Adjusted Medical Exemption Attendance:
    $$\text{Adjusted Attendance } \% = \frac{\text{Sessions Attended} + \text{Exempted Medical Sessions}}{\text{Total Valid Sessions Held}} \times 100$$
- **Hard Rule: Term Lock Gate (DB-42):**
  - Trigger `trg_check_term_lock_attendance` on `app.attendance_session` calls `is_term_locked(term_id)`. If true, the insert/update is aborted:
    `RAISE EXCEPTION 'Academic term is locked. Attendance cannot be modified.' USING ERRCODE = 'P0001';`
- **Frontend:**
  - `AttendanceRegisterPage.tsx`: Fast tabular attendance marking with keyboard shortcuts (P/A/L), summary stats header, and medical exemption badge.

---

## Database migrations you own

Use **only** numbers **65** and **66**.
- `65_v41_timetable_gist_constraints.sql`: `app.room`, `app.timetable_slot`, GiST exclusion constraints on room and teacher.
- `66_v41_attendance_and_medical_exemptions.sql`: `app.attendance_session`, `app.attendance_entry`, `app.attendance_medical_exemption`, trigger `trg_check_term_lock_attendance`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.academic.controller.TimetableController`
  - `com.edu360.api.academic.controller.AttendanceController`
  - `com.edu360.api.academic.controller.MedicalExemptionController`
  - `com.edu360.api.academic.service.TimetableService`
  - `com.edu360.api.academic.service.AttendanceService`
  - `com.edu360.api.academic.calculator.AttendancePercentageCalculator`
- **Frontend:**
  - `app/src/features/academic/pages/TimetableGridView.tsx`
  - `app/src/features/academic/pages/AttendanceRegisterPage.tsx`
  - `app/src/features/academic/components/MedicalExemptionModal.tsx`
  - `app/src/features/academic/components/ClashWarningToast.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/academic/TimetableGiSTConflictIT.java`
  - `api/src/test/java/com/edu360/api/academic/AttendanceCalculationIT.java`
  - `app/src/features/academic/__tests__/AttendanceRegisterPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T1 for `is_term_locked()` and Chat T4 for students.
- **Others depend on you:** Chat T7 (Examinations) calls your attendance percentage calculator to verify the 75% hall ticket eligibility gate.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=TimetableGiSTConflictIT,AttendanceCalculationIT`
2. `cd app && npm test -- AttendanceRegisterPage`
3. `npm run lint && npm run build`
