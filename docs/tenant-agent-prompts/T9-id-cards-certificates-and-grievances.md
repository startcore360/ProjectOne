# Chat T9 — Identity Cards, Certificates & Disciplinary Grievance Security

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-3-development`.

## Your mission

Deliver Student and Staff Identity Cards, Official Academic Certificates, and Disciplinary Grievance Management (**Modules 9, 10, and 16: Actions 110–122, 187–195**). You own cryptographic ID card generation (SVG/PDF), replacement requests, Transfer & Bonafide certificate generation with automated No-Dues clearance verification, confidential complaint filing, and the strict Accused Party Exclusion Gate (`fn_complaint_guard`).

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T9-S1** | `ACT-110`..`ACT-115` | Student & Staff ID Card Generation: customizable card templates (portrait/landscape), QR/Barcode encoding signed student ID, batch PDF printing, and lost ID replacement workflow. | §7.9 |
| **T9-S2** | `ACT-116`..`ACT-122` | Official Certificates & Document Verification: Transfer Certificates (TC), Bonafide, Conduct certificates; automated No-Dues clearance check across Library, Fees, and Dept. | §7.10 |
| **T9-S3** | `ACT-121`, `ACT-122` | Public Certificate Verification: public lightweight endpoint (`/verify-certificate/{code}`) validating authenticity via cryptographic QR hash without authentication. | §7.10 |
| **T9-S4** | `ACT-187`..`ACT-191` | Disciplinary Tracking & Case Management: incident recording, evidence upload, hearing notices, disciplinary committee minutes, and penalty issuance. | §7.16 |
| **T9-S5** | `ACT-192`..`ACT-195` | Confidential Grievance Gate (`fn_complaint_guard`): anonymous/named grievance submission; database gate enforcing Decision PO-T05: only Internal Complaints Committee (ICC) and Principal can view cases; accused faculty and immediate HOD are strictly barred from viewing to prevent bias. | §7.16 |

---

### Detailed Technical Specifications

#### Story T9-S1: Identity Cards (M09)
- **Database Tables:**
  - `app.id_card_template` (`id`, `tenant_id`, `institution_id`, `name`, `layout` [PORTRAIT, LANDSCAPE], `background_image_ref`, `fields_config_json`).
  - `app.id_card_issuance` (`id`, `tenant_id`, `user_id`, `template_id`, `card_number`, `issue_date`, `expiry_date`, `qr_hash`, `status` [ACTIVE, REPLACED, REVOKED]).
- **Backend:** SVG/PDF renderer generating high-resolution printable ID cards with embedded cryptographic QR codes.

#### Story T9-S2 & T9-S3: Official Certificates & No-Dues Engine (M10)
- **Database Tables:**
  - `app.certificate_template` (`id`, `tenant_id`, `institution_id`, `type` [TRANSFER_CERTIFICATE, BONAFIDE, CONDUCT, DEGREE], `template_html`).
  - `app.certificate_request` (`id`, `tenant_id`, `student_id`, `template_id`, `reason`, `status` [REQUESTED, NO_DUES_PENDING, APPROVED, ISSUED, REJECTED], `issued_by_user_id`, `certificate_number`, `verification_code`).
- **Automated No-Dues Clearance Engine:**
  - `NoDuesVerificationService.verifyClearance(studentId)`:
    - Checks Chat T8's `app.fee_invoice` for outstanding balances.
    - Checks Chat T10's `app.book_circulation` for unreturned library books or overdue fines.
    - Checks departmental equipment clearance.
    - If any dues exist, status transitions to `NO_DUES_PENDING` with an itemized dues list.
- **Public Certificate Verification:**
  - `PublicVerificationController.java` (`GET /api/public/certificates/verify/{code}`):
    - Unauthenticated endpoint returning certificate status, student name, degree program, and issuance date.

#### Story T9-S4 & T9-S5: Disciplinary Cases & Confidentiality Gate (M16)
- **Database Tables:**
  - `app.disciplinary_incident` (`id`, `tenant_id`, `institution_id`, `student_id`, `reported_by_user_id`, `incident_date`, `severity` [LOW, MEDIUM, HIGH, CRITICAL], `description`, `action_taken`).
  - `app.grievance_case` (`id`, `tenant_id`, `complainant_user_id`, `accused_user_id`, `category` [HARASSMENT, DISCRIMINATION, ACADEMIC_MISCONDUCT, GENERAL], `case_number`, `details`, `status` [SUBMITTED, UNDER_INVESTIGATION, HEARING_SCHEDULED, RESOLVED, DISMISSED]).
- **Hard Rule: Confidential Grievance Gate (`fn_complaint_guard`, DB-70):**
  - Stored function and RLS policy on `app.grievance_case`:
    - If the current authenticated user (`current_setting('app.user_id')`) equals `accused_user_id`:
      - Query returns **0 rows** on `SELECT`.
      - Direct lookup by ID returns HTTP 404 `NOT_FOUND` (masking case existence).
    - Access is strictly restricted to the Grievance Redressal Committee and Tenant DPO.
- **Frontend:**
  - `GrievanceCaseManagementPage.tsx`: Highly confidential case review view with encrypted attachment viewer and hearing notes editor.

---

## Database migrations you own

Use **only** numbers **71** and **72**.
- `71_v41_id_cards_and_certificates.sql`: `app.id_card_template`, `app.id_card_issuance`, `app.certificate_request`.
- `72_v41_disciplinary_and_grievance_guard.sql`: `app.disciplinary_incident`, `app.grievance_case`, RLS policy `p_grievance_confidentiality`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.document.controller.IdCardController`
  - `com.edu360.api.document.controller.CertificateController`
  - `com.edu360.api.document.controller.PublicVerificationController`
  - `com.edu360.api.governance.controller.GrievanceCaseController`
  - `com.edu360.api.governance.service.NoDuesVerificationService`
  - `com.edu360.api.governance.service.GrievanceService`
- **Frontend:**
  - `app/src/features/documents/pages/IdCardManagementPage.tsx`
  - `app/src/features/documents/pages/CertificateRequestPage.tsx`
  - `app/src/features/governance/pages/GrievanceCaseManagementPage.tsx`
  - `app/src/features/documents/components/PrintableIdCard.tsx`
  - `app/src/features/governance/components/HearingMinutesModal.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/governance/GrievanceComplaintGuardIT.java`
  - `api/src/test/java/com/edu360/api/document/NoDuesClearanceIT.java`
  - `app/src/features/governance/__tests__/GrievanceCaseManagementPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T4 for students, Chat T8 for fee clearance in No-Dues, and Chat T10 for library book clearance.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=GrievanceComplaintGuardIT,NoDuesClearanceIT`
2. `cd app && npm test -- GrievanceCaseManagementPage`
3. `npm run lint && npm run build`
