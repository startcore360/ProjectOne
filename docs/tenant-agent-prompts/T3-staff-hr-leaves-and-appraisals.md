# Chat T3 — Staff Management, HR, Leaves & Performance Appraisals

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-3-development`.

## Your mission

Deliver the complete Human Resources, Staff Lifecycle, Leave Management, and Performance Appraisal suite (**Modules 11, 12, and 13: Actions 123–161**). You own staff profiles, service registers, credential/qualification verification, leave policies, balance accruals, multi-tier leave approval chains with date-bounded delegations (Rule D-001), and annual faculty appraisal cycles.

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T3-S1** | `ACT-123`..`ACT-132` | Staff Directory & Service Book: profile creation, academic designation (Professor, Associate, Assistant, Lab Assistant), employment status, and official document attachments. | §7.11 |
| **T3-S2** | `ACT-133`..`ACT-140` | Staff Credential & Qualification Verification: degrees, board certifications, and verification workflow replacing temporary stubs with formal audit trails. | §7.11 |
| **T3-S3** | `ACT-141`..`ACT-146` | Leave Policy & Balance Accrual: define leave categories (Casual, Medical, Earned, Maternity), balance allocations, carry-over rules, and encashment parameters. | §7.12 |
| **T3-S4** | `ACT-147`..`ACT-152` | Leave Request & Acting Delegation Engine: leave submission with medical attachments, 2-tier approval chain, and date-bounded acting delegation (Rule `D-001`). | §7.12 |
| **T3-S5** | `ACT-153`..`ACT-161` | Staff Performance Appraisals: appraisal cycles, self-evaluation forms, peer feedback, HOD KPI scoring, and promotion readiness flags. | §7.13 |

---

### Detailed Technical Specifications

#### Story T3-S1 & T3-S2: Staff Directory & Qualification Verification (M11)
- **Database Tables:**
  - `app.staff_profile` (`id`, `tenant_id`, `institution_id`, `user_id`, `employee_code`, `designation`, `department_id`, `date_of_joining`, `employment_type`, `status`).
  - `app.staff_qualification` (`id`, `staff_id`, `degree_name`, `institution_name`, `year_of_passing`, `document_ref`, `verification_status` [PENDING, VERIFIED, REJECTED], `verified_by_user_id`, `verified_at`).
- **Backend:**
  - `StaffProfileController.java` (`GET /api/staff`, `POST /api/staff`, `GET /api/staff/{id}`, `PUT /api/staff/{id}`).
  - `StaffQualificationController.java` (`POST /api/staff/{id}/qualifications`, `POST /api/staff/{id}/qualifications/{qid}/verify`).
  - Replace mock stubs: integrate file validation with ClamAV virus scanning before persisting credentials.
- **Frontend:**
  - `StaffDirectoryPage.tsx`: Tabbed staff profile (Overview, Service History, Qualifications, Assigned Classes).

#### Story T3-S3 & T3-S4: Leave Management & Delegation Engine (M12)
- **Database Tables:**
  - `app.leave_policy` (`id`, `tenant_id`, `institution_id`, `leave_type`, `annual_quota`, `requires_document`, `carry_forward_max`).
  - `app.leave_balance` (`id`, `staff_id`, `leave_policy_id`, `allocated_days`, `used_days`, `year`).
  - `app.leave_request` (`id`, `staff_id`, `leave_policy_id`, `start_date`, `end_date`, `days_count`, `reason`, `status` [PENDING, APPROVED, REJECTED], `approved_by_user_id`, `acting_user_id`).
  - `app.role_delegation` (`id`, `tenant_id`, `institution_id`, `delegator_user_id`, `delegate_user_id`, `role_code`, `start_date`, `end_date`, `is_active`).
- **Hard Business Rules:**
  - **Rule D-001 (Delegation Bounds):**
    - Delegate must hold same-or-higher role rank.
    - Exception: Principal $\rightarrow$ Vice-Principal is an explicit allowed delegation.
    - Cannot re-delegate (delegations cannot be nested).
    - Date-bounded: delegation automatically terminates at `end_date 23:59:59`.
  - Leave balance debit occurs atomically upon approval. Attempting to approve leave when balance is insufficient returns 422 `INSUFFICIENT_LEAVE_BALANCE`.

#### Story T3-S5: Staff Performance Appraisals (M13)
- **Database Tables:**
  - `app.appraisal_cycle` (`id`, `tenant_id`, `institution_id`, `academic_year_id`, `name`, `start_date`, `end_date`, `status`).
  - `app.appraisal_submission` (`id`, `cycle_id`, `staff_id`, `self_score`, `hod_score`, `principal_score`, `final_rating`, `status`, `comments_json`).
- **Frontend:**
  - `AppraisalReviewPage.tsx`: KPI scorecard interface with weighted sliders, qualitative reviews, and export to PDF.

---

## Database migrations you own

Use **only** numbers **59** and **60**.
- `59_v41_staff_qualifications_and_service.sql`: `app.staff_qualification`, verification audit ledger.
- `60_v41_leave_management_and_delegation.sql`: `app.leave_request`, `app.role_delegation`, trigger `trg_leave_balance_debit`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.staff.controller.StaffProfileController`
  - `com.edu360.api.staff.controller.StaffQualificationController`
  - `com.edu360.api.staff.controller.StaffLeaveController`
  - `com.edu360.api.staff.controller.StaffAppraisalController`
  - `com.edu360.api.staff.service.StaffService`
  - `com.edu360.api.staff.service.StaffLeaveService`
  - `com.edu360.api.staff.service.StaffAppraisalService`
- **Frontend:**
  - `app/src/features/staff/pages/StaffDirectoryPage.tsx`
  - `app/src/features/staff/pages/StaffLeavePage.tsx`
  - `app/src/features/staff/pages/StaffAppraisalPage.tsx`
  - `app/src/features/staff/components/LeaveRequestModal.tsx`
  - `app/src/features/staff/components/QualificationVerificationModal.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/staff/StaffLeaveIT.java`
  - `api/src/test/java/com/edu360/api/staff/StaffAppraisalIT.java`
  - `app/src/features/staff/__tests__/StaffLeavePage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T1 for security context and Chat T2 for institutions and departments.
- **Others depend on you:** Chat T6 (Timetable) checks staff leave status before generating timetable slots.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=StaffLeaveIT,StaffAppraisalIT`
2. `cd app && npm test -- StaffLeavePage`
3. `npm run lint && npm run build`
