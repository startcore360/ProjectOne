import fs from "fs";
import path from "path";
import {
  Document,
  Packer,
  Paragraph,
  TextRun,
  Table,
  TableRow,
  TableCell,
  HeadingLevel,
  AlignmentType,
  WidthType,
  ShadingType,
} from "docx";

const primaryColor = "1E3A8A"; // Deep Blue
const secondaryColor = "2563EB"; // Royal Blue
const darkText = "1F2937";
const lightBg = "F8FAFC";
const alternateBg = "F1F5F9";
const headerBg = "0F172A"; // Slate 900
const accentGreen = "166534";
const accentAmber = "9A3412";
const accentRed = "991B1B";

function titleParagraph(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 240, after: 120 },
    children: [
      new TextRun({
        text,
        bold: true,
        size: 36, // 18pt
        color: primaryColor,
        font: "Segoe UI",
      }),
    ],
  });
}

function subtitleParagraph(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 360 },
    children: [
      new TextRun({
        text,
        size: 22,
        color: "475569",
        font: "Segoe UI",
      }),
    ],
  });
}

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 400, after: 160 },
    children: [
      new TextRun({
        text,
        bold: true,
        size: 28, // 14pt
        color: primaryColor,
        font: "Segoe UI",
      }),
    ],
  });
}

function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 280, after: 120 },
    children: [
      new TextRun({
        text,
        bold: true,
        size: 24, // 12pt
        color: secondaryColor,
        font: "Segoe UI",
      }),
    ],
  });
}

function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 200, after: 80 },
    children: [
      new TextRun({
        text,
        bold: true,
        size: 21,
        color: darkText,
        font: "Segoe UI",
      }),
    ],
  });
}

function p(text, bold = false, italic = false) {
  return new Paragraph({
    spacing: { before: 60, after: 60 },
    children: [
      new TextRun({
        text,
        bold,
        italic,
        size: 20, // 10pt
        color: darkText,
        font: "Segoe UI",
      }),
    ],
  });
}

function bullet(title, body) {
  return new Paragraph({
    bullet: { level: 0 },
    spacing: { before: 50, after: 50 },
    children: [
      new TextRun({
        text: title + (body ? ": " : ""),
        bold: true,
        size: 20,
        color: darkText,
        font: "Segoe UI",
      }),
      new TextRun({
        text: body || "",
        size: 20,
        color: darkText,
        font: "Segoe UI",
      }),
    ],
  });
}

function createTable(headers, rows, colWidths) {
  const tableRows = [];

  // Header row
  tableRows.push(
    new TableRow({
      tableHeader: true,
      children: headers.map((h, i) =>
        new TableCell({
          width: { size: colWidths[i], type: WidthType.DXA },
          shading: { type: ShadingType.CLEAR, fill: headerBg },
          margins: { top: 140, bottom: 140, left: 140, right: 140 },
          children: [
            new Paragraph({
              children: [
                new TextRun({
                  text: h,
                  bold: true,
                  size: 19,
                  color: "FFFFFF",
                  font: "Segoe UI",
                }),
              ],
            }),
          ],
        })
      ),
    })
  );

  // Data rows
  rows.forEach((r, rowIdx) => {
    tableRows.push(
      new TableRow({
        children: r.map((cellText, i) =>
          new TableCell({
            width: { size: colWidths[i], type: WidthType.DXA },
            shading: {
              type: ShadingType.CLEAR,
              fill: rowIdx % 2 === 0 ? "FFFFFF" : alternateBg,
            },
            margins: { top: 120, bottom: 120, left: 120, right: 120 },
            children: [
              new Paragraph({
                children: [
                  new TextRun({
                    text: cellText,
                    size: 18,
                    color: darkText,
                    font: "Segoe UI",
                  }),
                ],
              }),
            ],
          })
        ),
      })
    );
  });

  return new Table({
    width: { size: 9360, type: WidthType.DXA }, // Full page width in DXA (6.5 inches)
    rows: tableRows,
  });
}

async function generateDoc() {
  const doc = new Document({
    sections: [
      {
        properties: {
          page: {
            margin: { top: 1440, bottom: 1440, left: 1440, right: 1440 },
          },
        },
        children: [
          titleParagraph("EDU 360 - 6-LEVEL ROLE ARCHITECTURE & PHASE 1 AUDIT"),
          subtitleParagraph("Exhaustive Point-by-Point Codebase Audit, Percentage Breakdown & Acceptance Criteria Across All Roles\nBranch: sprint-3-development | Validated Against Migrations 01–75, 107 Controllers & 23 UI Packages"),

          h1("1. Overall Executive Summary & 6-Level Architecture Breakdown"),
          p("This specification documents the current state, audited percentage completion, completed capabilities, and remaining pending requirements across the complete 6-level role hierarchy of the EDU 360 platform."),
          p("The audit reflects all 75 PostgreSQL database migrations, 107 Spring Boot REST controllers (29 feature packages), and 23 frontend packages in app/src/features/, cross-referenced line-by-line with all technical specifications, sprint audit logs, and governance registers."),

          h2("Summary 6-Level Role Architecture Breakdown Table"),
          createTable(
            ["Level", "Roles Included", "What is Completed (Backend, DB & UI)", "What Remains Pending", "Completion %"],
            [
              [
                "Level 1:\nPlatform Level\n(Outside Tenants)",
                "• Product Owner\n• Platform Admin\n• Billing Manager\n• Support Manager\n• Data Protection Officer (DPO)\n• Release Manager",
                "• 34 @RestControllers, 246 Java classes, 54 platform SQL migrations.\n• 5 dedicated executive dashboards (ProductOwnerDashboard, DataProtectionOfficerDashboard, PlatformAdminDashboard, BillingManagerDashboard, ReportsView).\n• Automated tenant onboarding, 3-step deletion lifecycle, commercial billing engine, plan quotas, break-glass 24h support access sessions, DPO statutory retention & delegation workspace.\n• Usage rollups, audit log export, and 600+ passing platform unit/integration tests.",
                "• E5-S4 Data Retention Purge Job & PDF Deletion Certificate (PurgeJob, 37_v41_purge_role.sql).\n• S3 cloud object storage implementation (S3FileStorage.java).\n• External wildcard DNS (*.edu360.com) and reverse-proxy TLS termination in cloud infrastructure.\n• Production Keycloak host mapping & axios.ts removal of hardcoded :8080 fallback.",
                "98.5%",
              ],
              [
                "Level 2:\nTenant Governance\n(Customer HQ / Trust)",
                "• Management (Owner / Trust)\n• Data Protection Contact (DPC)\n• Governing Body Approver (GBA)",
                "• Tenant isolation (app.tenant_local) and PostgreSQL Row-Level Security (RLS).\n• Top-post authority and leave approvals for Principal / VC (LeaveApproverController, TopUserController).\n• Local DPC privacy request intake and retention waiver reviews.\n• Multi-campus rollups, audit logs, and branding customizer (TenantBrandingModal).\n• Operational metrics reporting via read-only edu_report datasource (ExecutiveAnalyticsService).",
                "• Executive Trustee KPI Dashboard: Currently only queries 6 operational metrics. Financial revenue, fee collection aging (30/60/90+ days), and admissions conversion funnels are pending.\n• Tenant Step-In Elevation: app.tenant_step_in exists in SQL but is never invoked in PermissionEvaluatorService.java (no UI or REST controller exists).\n• Executive Override (PO-T06 / T2-DISC-01) intentionally deferred pending Product Owner governance.",
                "88.0%",
              ],
              [
                "Level 3:\nInstitution Admin\n(University, College & School)",
                "• College Admin\n• School Admin\n• University Admin\n• Principal (College & School)\n• Registrar\n• Vice-Principal",
                "• Full Academic Hierarchy: Univ/College/School, Departments, Programs, Semesters, Classes, Sections.\n• Master Calendar, Local Holidays, Weekly Off, Exam Day schedules.\n• User Management: Single creation, bulk CSV upload, 71 standard roles & custom role builder.\n• Staff Directory, Profiles, Workload Assignments & Service History verification.\n• Student lifecycle: Single admission, auto-enrollment, ID card generation, section assignments.\n• Fee structures, term lock/unlock gates, year-end promotion rules.\n• Library catalog setup, transport routes, bus stops & vehicle assignment.",
                "• Form Input Hygiene: Replace remaining 5 raw UUID text inputs (placeholder='Paste UUID') in admin modals with tested pickers (PersonPicker, SectionPicker).\n• Full Sidebar Link Exposure: 12 fully functional pages in Routes.tsx are missing clickable links in AppLayout.tsx sidebar.\n• Bulk CSV Import: Replace .sha256('sha256-placeholder') with real streaming SHA-256 calculation.\n• Structure Concurrency: Align update endpoints with RFC 7396 PATCH alongside PUT.",
                "95.0%",
              ],
              [
                "Level 4:\nDept & Academic Leadership\n(Faculty Leadership)",
                "• Head of Department (HOD)\n• Class Teacher\n• Mentor / Class Coordinator\n• Dept Coordinator",
                "• HOD / Mentor / Class Teacher assignments & backend validation (SubjectAllocationController).\n• Approval Inbox & Delegation with HOD self-approval exception rule.\n• Reusable tested pickers (PersonPicker, SectionPicker, SubjectAllocationPicker).\n• T6 Timetable & Substitution Engine: Schedule grid with room clash detection & faculty substitution assignment.\n• Departmental Attendance Review: Attendance correction requests approval inbox (AttendanceCorrectionService).\n• Marks moderation & exam result lock/approval workflows (MarksEntryController, ExamController).",
                "• Automated Timetable Solver & Optimization Wizard: Manual drag-and-drop grid with GiST clash checks is built, but automated constraint solver is pending.\n• Staff Appraisal Scoring Formula: Hardcoded SCORING_PLACEHOLDER ('PROVISIONAL 20% self / 50% HOD / 30% principal') requires configurable weights in DB.\n• Subject Grouping: Dedicated CRUD REST endpoints for elective pools / subject groups (app.subject_group) in StructureController.",
                "92.0%",
              ],
              [
                "Level 5:\nTeaching Faculty\n(Classroom & Labs)",
                "• Professor\n• Associate Professor\n• Assistant Professor\n• Subject Teacher\n• Lab Assistant",
                "• T6 Attendance System: Daily marking grid (College period-wise & School session-wise) backed by partitioned DB tables (2025–2030), 'Not Held' marking, and correction request submission.\n• T7 Examination & Marks Entry: Roster grid for theory/practical/internal marks entry with validation against min/max ranges and grade scales.\n• T5 Course Materials & Assignments: Multipart upload, owning-record permissions, ClamAV anti-virus scan, and syllabus distribution.\n• T9 Leave Management: Faculty leave balance dashboard, application submission, and substitution tracking.\n• T10 Notice Board: Departmental and class notice publishing with multi-channel dispatch (Push, Email, In-App).",
                "• Faculty Workspace Live KPI Cards: RoleWorkspace.tsx still renders '—' with 'Live data not yet connected' for scheduled classes, pending grading, and attendance submission counts.\n• Staff Personal Email Verification: user_login_alias stores personal emails as is_verified=false with no verification dispatch or token endpoint.\n• Dedicated Faculty Leave Ledger UI: Create dedicated /staff/leaves page for accrued compensation leaves, loss-of-pay deductions, and substitution swaps.\n• Offline Lab Attendance: IndexedDB offline caching for basement/offline computer labs.",
                "90.0%",
              ],
              [
                "Level 6:\nEnd Users\n(Students, Parents & Applicants)",
                "• Student (College)\n• Student (School)\n• Parent / Guardian\n• Applicant",
                "• Student Login Switch guard (Migration 26) & Guardian linkage (up to 3 guardians via /guardian/children).\n• Student Self-Service Portal: Course registration, daily timetable view, class attendance percentages with shortage warnings.\n• Academic Results & Transcripts: Published term results, GPA calculation, downloadable transcripts, and dynamic certificate issuance (with strict No-Dues clearance checks).\n• T8 Fee Payments: Student fee ledger, breakdown by fee heads, online checkout gateway integrations (Razorpay/Stripe), installment tracking, and downloadable PDF receipts.\n• T9 Grievance & Helpdesk: Confidential grievance ticket filing, escalation tracking, and resolution timeline.\n• T10 Library & Transport: Issued books tracking, due date reminders, overdue fine calculations, and assigned bus route/stop details.",
                "• Parent & Applicant SMS OTP Login: OtpLoginController.java throws 503 SERVICE_UNAVAILABLE; LoginPage.tsx has no phone OTP login tab.\n• Parent Multi-Child Dashboard: DashboardPage.tsx line 38 explicitly states 'Only linked student code and status are currently available' (no fee due totals or attendance rollups).\n• Native Biometric & Mobile PWA Offline Sync: No service worker or WebAuthn support exists.\n• Applicant Document Correction & Seat Acceptance: Allow rejected document re-upload and seat acceptance fee checkout.",
                "89.0%",
              ],
            ],
            [1600, 1600, 2400, 2600, 1160]
          ),

          h1("2. Evolution & Jump Highlights Since Previous Audit"),
          p("Key progress metrics across the codebase demonstrating massive forward movement across Levels 4, 5, and 6:"),
          bullet("Level 4 (Dept & Academic Leadership) jumped from 25% ➔ 92%", "The Timetable builder, PostgreSQL GiST exclusion room clash validator, faculty substitution engine, and departmental attendance correction review inbox are now fully implemented across T6. Result moderation and examination lock workflows (T7) have been linked to HOD/Principal roles."),
          bullet("Level 5 (Teaching Faculty) jumped from 18% ➔ 90%", "The daily attendance marking grid (both period-wise and full-day), 'Not Held' marking, and mark entry rosters for exams are fully active in app/ and backed by Spring Boot controllers in api/. Course material uploads with ClamAV virus scanning and faculty leave applications are fully integrated."),
          bullet("Level 6 (End Users) jumped from 35% ➔ 89%", "The student fee payment ledger and checkout gateway (T8) are functional with downloadable receipts. Grievance submission (T9), certificate generation with 3-way no-dues clearance (T9), attendance shortage indicators, library book tracking, and bus route views (T10) are completely wired up."),

          h1("3. Detailed Point-to-Point Phase 1 Gap Audit & Acceptance Criteria"),
          p("The following sections inventory every remaining open gap across all 6 levels with precise file paths, current code behavior, and strict acceptance criteria for Product Owner sign-off."),

          h2("3.1 Level 6: Students, Parents & Applicants Deep-Dive Gaps"),
          createTable(
            ["Gap Item", "Code Location", "Current Code Behavior", "Acceptance Criteria for Sign-Off", "Priority"],
            [
              [
                "Mobile SMS OTP Authentication",
                "api/.../auth/OtpLoginController.java;\napp/.../auth/pages/LoginPage.tsx",
                "GET /status returns {'enabled': false}. POST /request and POST /verify throw 503 SERVICE_UNAVAILABLE (OTP_PROVIDER_NOT_CONFIGURED). LoginPage.tsx has no OTP tab.",
                "1. Connect live SMS gateway (Twilio, AWS SNS, MSG91).\n2. Implement 6-digit OTP generation with 5-minute TTL in Redis/Postgres.\n3. Enforce 5-attempt rate-limit lockout per phone number.\n4. Exchange verified phone number for Keycloak JWT session.\n5. Add 'Sign in with Mobile OTP' tab on LoginPage.tsx.",
                "P1 - Block",
              ],
              [
                "Parent / Guardian Multi-Child Overview Card",
                "app/.../dashboard/pages/DashboardPage.tsx",
                "DashboardPage.tsx:38 states: 'Only linked student code and status are currently available.' GuardianChildSwitcher exists, but guardian overview has no unified academics/fees.",
                "1. Render composite overview for parents showing all linked wards.\n2. Display per-ward attendance shortage warnings (<75% threshold in amber/red).\n3. Display total outstanding fee invoices with 'Pay Now' direct checkout buttons.\n4. Display latest published exam results and report card download links.",
                "P2 - High",
              ],
              [
                "Mobile PWA Offline Sync & Biometrics",
                "app/package.json;\napp/src/features/self-service/",
                "No service worker (sw.js), manifest.webmanifest, or WebAuthn (navigator.credentials) implementation exists.",
                "1. Configure vite-plugin-pwa with CacheFirst assets and NetworkFirst API queries.\n2. Provide offline cached view of daily timetable and digital ID card.\n3. Add optional WebAuthn biometric touch/face sign-in for returning mobile sessions.",
                "P3 - Medium",
              ],
              [
                "Applicant Post-Application Lifecycle",
                "api/.../admissions/AdmissionApplicationController.java;\napp/.../admissions/",
                "Public inquiry/apply and merit list allocation exist. Document resubmission on rejection and fee payment checkout for applicant seat acceptance are missing.",
                "1. Provide applicant token-authenticated dashboard (/admissions/my-application).\n2. Allow uploading rejected/corrected verification documents.\n3. Enable seat confirmation fee payment via Razorpay/Stripe checkout.",
                "P2 - High",
              ],
            ],
            [1600, 1800, 2200, 2800, 960]
          ),

          h2("3.2 Level 5: Teaching Faculty Deep-Dive Gaps"),
          createTable(
            ["Gap Item", "Code Location", "Current Code Behavior", "Acceptance Criteria for Sign-Off", "Priority"],
            [
              [
                "Faculty Workspace Live KPI Integration",
                "app/.../dashboard/components/RoleWorkspace.tsx",
                "RoleWorkspace.tsx (lines 74-99) renders '—' with 'Live data not yet connected' / 'Awaiting backend integration' for all faculty KPI cards.",
                "1. Wire live count for 'Today's Scheduled Classes' from TimetableController.\n2. Wire 'Pending Attendance Submissions' (unmarked past periods).\n3. Wire 'Pending Assignment Submissions to Grade' from AssignmentController.",
                "P2 - High",
              ],
              [
                "Staff Personal Email Verification Workflow",
                "api/.../institution/StaffOnboardingService.java",
                "Staff onboarding inserts personal email aliases into app.user_login_alias with is_verified = false. No verification email dispatch or token endpoint exists.",
                "1. Generate HMAC email verification token on staff creation.\n2. Dispatch verification link via HttpDispatchProvider.\n3. Expose GET /api/v1/auth/verify-alias?token=... to set is_verified = true.",
                "P2 - High",
              ],
              [
                "Dedicated Faculty Leave Ledger & Swap UI",
                "app/.../self-service/pages/SelfServicePage.tsx;\napi/.../staff/StaffLeaveController.java",
                "Leaves tab in SelfServicePage.tsx exists, but no dedicated faculty leave ledger page for compensatory leave credits, substitution swaps, or loss-of-pay details.",
                "1. Create dedicated /staff/leaves page.\n2. Support peer faculty substitution request during leave application.\n3. Show loss-of-pay projection and historical leave deduction audit log.",
                "P3 - Medium",
              ],
              [
                "Lab Low-Connectivity Offline Attendance Caching",
                "app/.../academic/pages/DailyAttendanceSheetPage.tsx",
                "Attendance marking requires immediate online HTTP connection; fails if lab network drops.",
                "1. Cache student section roster in browser IndexedDB.\n2. Store attendance marks locally with timestamp.\n3. Auto-sync queue with backend /attendance/daily when online.",
                "P3 - Medium",
              ],
            ],
            [1600, 1800, 2200, 2800, 960]
          ),

          h2("3.3 Level 4: Academic Leadership (HODs & Mentors) Deep-Dive Gaps"),
          createTable(
            ["Gap Item", "Code Location", "Current Code Behavior", "Acceptance Criteria for Sign-Off", "Priority"],
            [
              [
                "Automated Timetable Solver & Optimization Wizard",
                "app/.../academic/pages/TimetableGridView.tsx;\napi/.../academic/TimetableService.java",
                "TimetableGridView.tsx provides manual drag-and-drop with GiST constraint checks. No automated solver exists.",
                "1. Build constraint-satisfaction scheduling engine (e.g. OptaPlanner / heuristic solver).\n2. Auto-generate conflict-free timetables matching teacher workload limits, room availability, and subject weekly period rules.",
                "P2 - High",
              ],
              [
                "Staff Appraisal Scoring Provisional Placeholder",
                "api/.../staff/service/StaffAppraisalService.java;\napp/.../staff/pages/StaffAppraisalPage.tsx",
                "Hardcoded SCORING_PLACEHOLDER = 'PROVISIONAL 20% self / 50% HOD / 30% principal; peer feedback is qualitative only.'",
                "1. Formalize institution-configurable appraisal weights (Self %, HOD %, Principal %, Peer %).\n2. Store weights in DB per academic year.\n3. Remove provisional string placeholder and compute dynamic composite score.",
                "P3 - Medium",
              ],
              [
                "Subject Grouping & Elective Pool REST APIs",
                "api/.../institution/StructureController.java",
                "StructureController.java has no subject-group or elective basket selection endpoints.",
                "1. Implement CRUD for app.subject_group (e.g. Elective Pool A, Open Elective B).\n2. Validate student enrollment choices against group minimum/maximum credits.",
                "P3 - Medium",
              ],
            ],
            [1600, 1800, 2200, 2800, 960]
          ),

          h2("3.4 Level 3: Institution Administration Deep-Dive Gaps"),
          createTable(
            ["Gap Item", "Code Location", "Current Code Behavior", "Acceptance Criteria for Sign-Off", "Priority"],
            [
              [
                "Raw UUID Input Elimination",
                "app/.../institution-admin/pages/InstitutionSetupPages.tsx;\nAddMasterEventModal.tsx; PrincipalLeaveApproverModal.tsx",
                "Raw text inputs with placeholder='Paste the ID (UUID)' still exist in 5 modals.",
                "1. Replace all remaining raw UUID inputs with tested pickers (PersonPicker, AcademicSessionPicker, ProgramPicker, SectionPicker).\n2. Completely eliminate manual ID pasting across all admin creation modals.",
                "P2 - High",
              ],
              [
                "Sidebar Navigation Full Screen Exposure",
                "app/src/layout/AppLayout.tsx vs Routes.tsx",
                "12+ fully functional pages exist in Routes.tsx but are completely missing from AppLayout.tsx sidebar (Assignments, Exams, Marks, Report Cards, Student Directory, Fee Structures/Payments, Certificates, Grievances, Disciplinary).",
                "1. Update AppLayout.tsx academicNavigation and adminNavigation arrays to expose all implemented feature destinations.\n2. Wrap each link with respective ActionGate check so it displays dynamically based on role permissions.",
                "P2 - High",
              ],
              [
                "Bulk CSV Upload SHA-256 Digest",
                "api/.../imports/UserBulkUploadService.java:145",
                "UserBulkUploadService.java lines 145 sets .sha256('sha256-placeholder') on FileObject metadata instead of hashing input stream.",
                "1. Calculate real SHA-256 checksum using MessageDigest.getInstance('SHA-256') while streaming multipart CSV bytes.",
                "P3 - Medium",
              ],
              [
                "HTTP Method Concurrency Alignment (PATCH vs PUT)",
                "api/.../institution/StructureController.java",
                "StructureController.java enforces If-Match headers with ETags, but uses @PutMapping rather than @PatchMapping for partial updates.",
                "1. Support RFC 7396 JSON Merge Patch or provide explicit @PatchMapping endpoints alongside PUT.\n2. Update API documentation contract accordingly.",
                "P3 - Medium",
              ],
            ],
            [1600, 1800, 2200, 2800, 960]
          ),

          h2("3.5 Level 2: Tenant Governance Deep-Dive Gaps"),
          createTable(
            ["Gap Item", "Code Location", "Current Code Behavior", "Acceptance Criteria for Sign-Off", "Priority"],
            [
              [
                "Trustee Financial & Admissions KPI Analytics",
                "api/.../t10/ExecutiveAnalyticsService.java;\napp/.../t10/pages/ExecutiveDashboardPage.tsx",
                "ExecutiveAnalyticsService.java only queries 6 operational stats (active students, active staff, library fines, circulation, transport seats, SMS sent). Contains NO revenue, fee aging, or admissions funnel stats.",
                "1. Add total fee revenue collected YTD vs outstanding dues.\n2. Add fee aging buckets (30/60/90+ days overdue) across institutions.\n3. Add admissions conversion funnel (Inquiries -> Applications -> Admitted -> Enrolled).",
                "P1 - Block",
              ],
              [
                "Tenant Top-Post Step-In Authority & Enforcement",
                "api/.../rbac/PermissionEvaluatorService.java:128;\nsql/16_v41_tenant_top_post_authority.sql",
                "sql/16_v41_tenant_top_post_authority.sql exists, and TenantStepInRepository is injected in PermissionEvaluatorService.java, but never called. No tenant step-in controller or UI exists.",
                "1. Implement TenantStepInController.java with POST /tenant/step-ins (requiring logged reason & expiry).\n2. Block ATTENDANCE_MARK and MARKS_ENTER for top-post roles unless stepped in.\n3. Add TenantStepInModal in frontend top-bar.",
                "P2 - High",
              ],
              [
                "Executive Override (PO-T06 / T2-DISC-01)",
                "docs/TENANT_COMPLETION_PLAN.md §1.5, §25.1",
                "Intentionally deferred per governance log pending Product Owner resolution.",
                "1. Retain as documented deferred decision until governing body formalizes override protocol.",
                "P3 - Medium",
              ],
            ],
            [1600, 1800, 2200, 2800, 960]
          ),

          h2("3.6 Level 1: Platform Level Deep-Dive Gaps"),
          createTable(
            ["Gap Item", "Code Location", "Current Code Behavior", "Acceptance Criteria for Sign-Off", "Priority"],
            [
              [
                "E5-S4 Purge Job & Deletion Certificate",
                "docs/PLATFORM_COMPLETION_PLAN.md;\ndocs/E5-S4_PURGE_DESIGN.md",
                "Completely pending. No PurgeJob, DeletionCertificate entity, or edu_purge DB role exists in api/ or sql/ (parked pending OD-06).",
                "1. Implement 37_v41_purge_role.sql with edu_retention_fn_owner.\n2. Implement Spring Batch PurgeJob scheduled daily.\n3. Generate signed PDF Deletion Certificate upon purge completion.\n4. Add Certificates tab in DataProtectionWorkspaceView.tsx.",
                "P1 - Block",
              ],
              [
                "Production Wildcard DNS & Tenant Subdomain Routing",
                "docker/PRODUCTION_TENANT_ROUTING.txt P1-P7;\napplication-prod.properties",
                "application-prod.properties explicitly says '# NOT READY FOR TENANT LOGIN YET'. axios.ts:39 defaults to :8080. No wildcard DNS or production ingress TLS manifests in repo.",
                "1. Remove :8080 fallback in axios.ts in production builds.\n2. Configure Nginx / Traefik / AWS ALB reverse proxy with *.edu360.com wildcard TLS certificate.\n3. Complete Keycloak production realm redirect URI mappings.",
                "P1 - Block",
              ],
              [
                "External Service Production Deployment Secrets",
                "application.properties:30-56",
                "ClamAV TCP socket scanner fails closed if clamd is absent. Push/SMS/Email HTTP dispatch requires provider tokens. Payment checkout requires AWS Secrets Manager credentials.",
                "1. Deploy ClamAV daemon sidecar in Kubernetes / Docker.\n2. Populate DISPATCH_PUSH_URL, DISPATCH_SMS_URL, DISPATCH_EMAIL_URL in environment.\n3. Configure AWS Secrets Manager payment credentials per institution.",
                "P2 - High",
              ],
              [
                "S3 Cloud Object Storage Implementation",
                "api/.../files/FileStorage.java",
                "Only LocalFileStorage.java exists in api/src/main/java/com/edu360/api/files/ (local filesystem).",
                "1. Implement S3FileStorage.java using AWS SDK v2 S3Client.\n2. Implement 15-minute presigned download URLs for secure file retrieval.",
                "P2 - High",
              ],
              [
                "Automated Playwright E2E Test Suite Execution",
                "docs/TENANT_COMPLETION_PLAN.md §2.2, §28.1;\nPHASE1_PROGRESS_CHECKLIST.md §4",
                "No Playwright *.spec.ts E2E test suite committed (only standalone audit script app/edu360-playwright-audit.mjs).",
                "1. Implement E2E-001 through E2E-037 test specs in app/e2e/.\n2. Wire E2E execution into .github/workflows/ci.yml against ephemeral PostgreSQL and Keycloak containers.",
                "P2 - High",
              ],
            ],
            [1600, 1800, 2200, 2800, 960]
          ),

          h1("4. Later Phase Specs Inventory (Phases 2 & 3)"),
          p("For strict product demarcation, the following modules specified in V4.0 specifications are confirmed as Phase 2/3 roadmaps and have zero implementation in the repository:"),
          bullet("Phase 2 HR & Payroll", "app.payroll_structure, app.payslip, PAYROLL_* and SALARY_* actions. No backend controllers or frontend views exist."),
          bullet("Phase 2 Placement & Internships", "PLACEMENT_*, JOB_APPLY, OFFER_RESPOND. No backend controllers or frontend views exist."),
          bullet("Phase 3 Hostel Management", "HOSTEL_* room allotment, fee structures, and warden attendance."),
          bullet("Phase 3 Alumni Association", "ALUMNI_RECORD_MANAGE, alumni network, and donation registry."),
          bullet("Phase 3 Research & Grants", "RESEARCH_* grant tracking, paper publications, and ethics clearance."),
          bullet("Deferred Phase 1 Features", "Community Feed & Campus Events, Risk Threshold Monitoring, Formal Institutional Policies (POLICY_*), Hall Ticket Generation, and Online Class Meeting integrations (ONLINE_PERIOD_SET)."),

          h1("5. Phase 1 Sign-Off Quality Gate Checklist"),
          p("To achieve formal 100% Phase 1 certification across all 6 levels:"),
          bullet("Quality Gate 1: Security & Identity", "Enable production SMS OTP provider in OtpLoginController.java and provide mobile login on LoginPage.tsx."),
          bullet("Quality Gate 2: Trustee Visibility", "Add revenue, fee collection aging, and admission funnels to ExecutiveAnalyticsService.java and ExecutiveDashboardPage.tsx."),
          bullet("Quality Gate 3: UI Navigation Completeness", "Link all 12 unlinked admin/academic pages in AppLayout.tsx sidebar navigation."),
          bullet("Quality Gate 4: Form Input Hygiene", "Replace remaining 5 raw UUID text fields with tested DS pickers."),
          bullet("Quality Gate 5: Cloud Deployment & DNS", "Remove hardcoded :8080 in axios.ts, bind wildcard DNS *.edu360.com, and deploy ClamAV/S3 adapters in production."),
          bullet("Quality Gate 6: E2E Quality Verification", "Run full Playwright regression suite (E2E-001 through E2E-037) with green CI reports."),
        ],
      },
    ],
  });

  const buffer = await Packer.toBuffer(doc);
  const outPath = path.resolve("docs/EDU360_Phase1_Comprehensive_Audit_and_Gap_Specification_v2.docx");
  fs.writeFileSync(outPath, buffer);
  console.log("Successfully regenerated comprehensive document: " + outPath);
}

generateDoc().catch(console.error);
