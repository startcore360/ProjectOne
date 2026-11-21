# Chat T5 — Admissions, Application Processing, Merit Lists & Quota Allocation

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-2-development`.

## Your mission

Deliver the complete Student Admissions and Application Processing pipeline (**Module 15: Actions 175–186**). You own admission cycles, digital application forms, document verification, quota seat reservations, automated merit ranking calculations, non-delegable merit list approvals (Rule `C12`), seat allotment workflows, and applicant-to-student conversion.

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T5-S1** | `ACT-175`, `ACT-176` | Admission Cycle Management: create admission intake cycles per program, application date windows, registration fee rules, and seat capacity limits. | §7.15 |
| **T5-S2** | `ACT-177`..`ACT-179` | Applicant Portal & Form Submission: digital application intake, applicant registration, photo/document upload with ClamAV scanning, and application fee receipt. | §7.15 |
| **T5-S3** | `ACT-180`, `ACT-181` | Document Verification & Scrutiny: admission officer document verification dashboard, defect flagging, and applicant document correction requests. | §7.15 |
| **T5-S4** | `ACT-182`..`ACT-184` | Automated Merit Ranking & Quota Allotment: compute merit scores based on qualifying exam marks, apply reservation quotas (General, Merit, Sports, Management), and generate provisional rankings. | §7.15 |
| **T5-S5** | `ACT-185`, `ACT-186` | Master Merit List Approval & Enrollment Conversion: enforce **Rule `C12`** (merit approval can NEVER be delegated or bulk-approved; PO/Principal only). Convert admitted applicants into active student records in Chat T4's SIS. | §7.15 |

---

### Detailed Technical Specifications

#### Story T5-S1: Admission Cycle Configuration (M15)
- **Database Tables:** `app.admission_cycle` (`id`, `tenant_id`, `institution_id`, `academic_year_id`, `program_id`, `cycle_name`, `start_date`, `end_date`, `application_fee`, `total_seats`, `status` [DRAFT, OPEN, CLOSED, ARCHIVED]).
- **Backend:** `AdmissionCycleController.java` (`GET /api/admissions/cycles`, `POST /api/admissions/cycles`, `PUT /api/admissions/cycles/{id}`).

#### Story T5-S2 & T5-S3: Application Intake & Document Verification
- **Database Tables:**
  - `app.admission_application` (`id`, `tenant_id`, `cycle_id`, `application_number`, `applicant_name`, `email`, `phone`, `qualifying_marks_percent`, `category`, `documents_json`, `verification_status` [PENDING, VERIFIED, DEFECTIVE, REJECTED], `status` [SUBMITTED, SCRUTINIZED, SHORTLISTED, ADMITTED, WITHDRAWN]).
- **Backend:**
  - File upload endpoint with MIME-type restriction (PDF, JPG, PNG ≤ 5MB) and virus scanning before storage.

#### Story T5-S4 & T5-S5: Merit List Engine & Rule C12 Enforcement
- **Database Tables:**
  - `app.merit_list` (`id`, `tenant_id`, `cycle_id`, `round_number`, `generated_at`, `status` [PROVISIONAL, APPROVED, PUBLISHED], `approved_by_user_id`, `approved_at`).
  - `app.merit_list_item` (`id`, `merit_list_id`, `application_id`, `merit_rank`, `computed_score`, `allocated_quota`, `seat_status` [OFFERED, ACCEPTED, DECLINED, EXPIRED]).
- **Hard Rule C12 (Non-Delegable Merit Approval):**
  - Stored function `fn_validate_merit_approval()`:
    - Checks that the approving user holds the substantive role of `PRINCIPAL`, `VICE_CHANCELLOR`, or `PRODUCT_OWNER`.
    - If the user is acting under a delegation (`acting_for_role`), the transaction is **aborted with HTTP 403 `MERIT_APPROVAL_NON_DELEGABLE`**.
    - Bulk-approval endpoints are strictly prohibited for merit lists.
- **Conversion Workflow:**
  - When seat is `ACCEPTED` and initial admission fee is confirmed, `AdmissionEnrollmentService.convertToStudent(applicationId)` calls Chat T4's `StudentService.createStudent()` generating admission number and student profile.

---

## Database migrations you own

Use **only** numbers **63** and **64**.
- `63_v41_admission_cycles_and_applications.sql`: `app.admission_cycle`, `app.admission_application`.
- `64_v41_merit_list_and_seat_allocation.sql`: `app.merit_list`, `app.merit_list_item`, function `fn_validate_merit_approval()`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.admissions.controller.AdmissionCycleController`
  - `com.edu360.api.admissions.controller.AdmissionApplicationController`
  - `com.edu360.api.admissions.controller.MeritListController`
  - `com.edu360.api.admissions.service.AdmissionService`
  - `com.edu360.api.admissions.service.MeritCalculationService`
  - `com.edu360.api.admissions.service.AdmissionEnrollmentService`
- **Frontend:**
  - `app/src/features/admissions/pages/AdmissionCyclesPage.tsx`
  - `app/src/features/admissions/pages/ApplicationScrutinyPage.tsx`
  - `app/src/features/admissions/pages/MeritListGenerationPage.tsx`
  - `app/src/features/admissions/components/MeritApprovalModal.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/admissions/MeritListCalculationIT.java`
  - `api/src/test/java/com/edu360/api/admissions/MeritApprovalRuleC12IT.java`
  - `app/src/features/admissions/__tests__/MeritListGenerationPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T2 for programs/quotas and Chat T4 for converting admitted applicants to active students.
- **Others depend on you:** Chat T8 (Fees) bills admission registration fees.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=MeritListCalculationIT,MeritApprovalRuleC12IT`
2. `cd app && npm test -- MeritListGenerationPage`
3. `npm run lint && npm run build`
