# Chat T7 — Assignments, Examinations, Marks Moderation & Report Cards

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Deliver the complete academic evaluation and grading engine (**Modules 6 and 7: Actions 55–87**). You own homework/assignment publishing, student submission uploads with virus scanning, exam cycle scheduling, hall ticket generation with 75% attendance verification, marks entry, two-tier marks moderation, mathematical SGPA and CGPA compilation (§20.1–§20.3), Report Card Publish Gate validation (`fn_publish_gate`), and Term Lock enforcement on grades.

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T7-S1** | `ACT-055`..`ACT-065` | Homework & Assignment Lifecycle: assignment creation with rubric weights, student document submission, late penalty calculation, and teacher grading. | §7.6 |
| **T7-S2** | `ACT-066`..`ACT-072` | Exam Cycle & Schedule Management: exam types (Internal, Mid-term, Final Semester), exam timetable, hall allocation, and invigilator assignment. | §7.7 |
| **T7-S3** | `ACT-073`..`ACT-075` | Hall Ticket Generation & Attendance Gate: verify student meets minimum 75% adjusted attendance (via Chat T6) before issuing downloadable cryptographic hall tickets. | §7.7 |
| **T7-S4** | `ACT-076`..`ACT-080` | Marks Capture, Moderation & Term Lock: faculty marks sheet, two-tier moderation workflow (Course Teacher $\rightarrow$ External Examiner), and Term Lock blocking edits with 409 `TERM_LOCKED`. | §7.7 |
| **T7-S5** | `ACT-081`..`ACT-087` | SGPA/CGPA Compilation & Publish Gate: automated SGPA and CGPA weighted formulas (§20.1–§20.3) using hierarchical customizable grade scales (Decision PO-T01); enforce **`fn_publish_gate`** blocking publication if fee dues exceed threshold or moderation is pending. | §7.7 |

---

### Detailed Technical Specifications

#### Story T7-S1: Homework & Assignments (M06)
- **Database Tables:**
  - `app.assignment` (`id`, `tenant_id`, `section_id`, `course_id`, `teacher_id`, `title`, `max_marks`, `due_date`, `allow_late_submission`, `rubric_json`).
  - `app.assignment_submission` (`id`, `assignment_id`, `student_id`, `submitted_at`, `file_ref`, `is_late`, `marks_awarded`, `feedback`, `status` [SUBMITTED, GRADED, RETURNED]).
- **Backend:** File upload with ClamAV virus scanning; late submissions automatically flagged based on `now() > due_date`.

#### Story T7-S2 & T7-S3: Exam Scheduling & Hall Ticket Verification (M07)
- **Database Tables:**
  - `app.exam_cycle` (`id`, `tenant_id`, `institution_id`, `academic_year_id`, `term_id`, `name`, `start_date`, `end_date`, `status`).
  - `app.exam_schedule` (`id`, `exam_cycle_id`, `course_id`, `exam_date`, `start_time`, `end_time`, `max_marks`, `pass_marks`, `room_id`).
  - `app.hall_ticket` (`id`, `exam_cycle_id`, `student_id`, `ticket_number`, `is_eligible`, `ineligible_reason`, `qr_code_hash`).
- **Attendance Eligibility Gate:**
  - `HallTicketService.generateTickets(cycleId)` evaluates attendance percentage from Chat T6. If `< 75%`, `is_eligible = false` and `ineligible_reason = 'ATTENDANCE_BELOW_75_PERCENT'`.

#### Story T7-S4 & T7-S5: Marks Entry, SGPA/CGPA & Publish Gate (M07)
- **Database Tables:**
  - `app.exam_mark` (`id`, `exam_schedule_id`, `student_id`, `marks_obtained`, `is_absent`, `moderated_marks`, `moderator_user_id`, `status` [ENTERED, MODERATED, APPROVED]).
  - `app.report_card` (`id`, `tenant_id`, `student_id`, `term_id`, `sgpa`, `cgpa`, `total_credits`, `grade_letter`, `status` [DRAFT, MODERATED, PUBLISHED], `published_at`).
- **Mathematical Formulations (§20.1–§20.3):**
  - **Semester Grade Point Average (SGPA):**
    $$\text{SGPA} = \frac{\sum_{i=1}^{n} (\text{Credits}_i \times \text{Grade Point}_i)}{\sum_{i=1}^{n} \text{Credits}_i}$$
  - **Cumulative Grade Point Average (CGPA):**
    $$\text{CGPA} = \frac{\sum_{j=1}^{m} (\text{SGPA}_j \times \text{Total Semester Credits}_j)}{\sum_{j=1}^{m} \text{Total Semester Credits}_j}$$
- **Hard Rule: Publish Gate Trigger (`fn_publish_gate`, DB-50):**
  - Trigger on `app.report_card` update to `PUBLISHED`:
    - Checks that all courses in the term have `status = 'APPROVED'` in marks moderation.
    - Checks Chat T8's `app.fee_invoice` for outstanding dues. If unpaid dues exceed threshold, raises `PUBLISH_GATE_BLOCKED_FEE_DUES`.
- **Hard Rule: Term Lock Gate (DB-42):**
  - Trigger on `app.exam_mark` rejects updates if `is_term_locked(term_id)` is true with HTTP 409 `TERM_LOCKED`.
- **Frontend:**
  - `ReportCardViewer.tsx`: Responsive student grade card with printable official transcript layout and QR verification code.

---

## Database migrations you own

Use **only** numbers **67** and **68**.
- `67_v41_assignment_and_submission_schema.sql`: `app.assignment`, `app.assignment_submission`.
- `68_v41_exam_cycle_marks_and_report_cards.sql`: `app.exam_cycle`, `app.exam_schedule`, `app.exam_mark`, `app.report_card`, function `fn_publish_gate()`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.academic.controller.AssignmentController`
  - `com.edu360.api.academic.controller.ExaminationController`
  - `com.edu360.api.academic.controller.ReportCardController`
  - `com.edu360.api.academic.service.AssignmentService`
  - `com.edu360.api.academic.service.ExaminationService`
  - `com.edu360.api.academic.calculator.SGPACalculator`
  - `com.edu360.api.academic.calculator.CGPACalculator`
- **Frontend:**
  - `app/src/features/academic/pages/AssignmentManagementPage.tsx`
  - `app/src/features/academic/pages/ExamSchedulePage.tsx`
  - `app/src/features/academic/pages/MarksEntrySheetPage.tsx`
  - `app/src/features/academic/pages/ReportCardViewerPage.tsx`
  - `app/src/features/academic/components/PublishGateBlockModal.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/academic/SGPACalculatorTest.java`
  - `api/src/test/java/com/edu360/api/academic/ReportCardPublishGateIT.java`
  - `app/src/features/academic/__tests__/MarksEntrySheetPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T1 for `is_term_locked()`, Chat T4 for students, Chat T6 for attendance 75% gate, and Chat T8 for fee clearance in publish gate.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=SGPACalculatorTest,ReportCardPublishGateIT`
2. `cd app && npm test -- MarksEntrySheetPage`
3. `npm run lint && npm run build`
