# EDU 360: Top Post Authority Rules

**Status:** Approved by the project owner, 2026-11-07, including decisions A to E. The Product Owner rows are implemented. The tenant rows are not (Phase 3).
**Decision record:** `docs/GOVERNANCE_DECISION_LOG.md`, round 2026-11-07.
**Source data:** D2 sheet *Data* (278 actions and their "Never allowed" rules) and `sql/05_ref_data.sql` (who holds each action today).

The treatment column was prepared for this review. The register does not classify its own rules this way.

## How the top posts relate

Product Owner runs the platform. Inside a university tenant the order is University Admin (TPA-05, 9 November 2026), then Management, then Vice Chancellor, then Principal or School Principal. Inside a standalone college or school, the College or School Admin set up when the tenant is created is the owner and comes first (TPA-06, 28 November 2026), then Management, then the Principal.

| Top post | Actions today | Actions under this rule | Area |
|---|---:|---:|---|
| Product Owner | 24 | 28 of the 32 platform actions | Platform only, never tenant data without a tenant grant |
| Management | 32 | 233 | Own tenant |
| Vice Chancellor | 32 | 231 | Own university |
| Principal (College) | 73 | 210 | Own college |
| School Principal | 75 | 188 | Own school |

The tenant figures are upper limits. They drop for any action that stays locked under the decisions in section 3. Having an action does not skip an approval chain, and approvals still happen one step at a time.

## 1. What no top post can do

**Personal actions (22).** These belong to a student, parent or applicant, such as paying a fee as a parent or submitting an application.

**Data protection approvals (4 actions, Product Owner).** `TENANT_EXPORT_APPROVE`, `TENANT_DELETION_APPROVE`, `RETENTION_CHANGE_APPROVE` and `APPROVAL_DELEGATE` stay with the Data Protection Officer. D2 action 198 says "Product Owner cannot approve".

**Merit list approval.** `MERIT_LIST_APPROVE` stays with the Principal or School Principal because it can never be delegated (D2 action 20). No higher post can step in on it.

**Rules locked for everyone (45).** Appendix A marks these "Locked for everyone". Examples:
- Nobody approves their own request, leave, marks or salary change.
- Nobody changes their own roles.
- Receipts, student notes and audit logs are never edited.
- Nobody publishes results before final approval.
- Student records are never hard-deleted.
- Platform roles never see a tenant's keys, webhooks or audit logs.

## 2. Rules that do not affect top posts (12)

These limit a lower role, such as "Billing Manager cannot give discounts". They stay as they are and apply to any custom role built from that lower role's work. Appendix A marks them "Limits a lower role".

## 3. Decisions made (5)

These "Never allowed" rules apply to a top post, or leave it out, so they conflict with "the top post can do everything". The project owner approved every recommendation below on 2026-11-07.

| | Register rule | Decision | Reason |
|---|---|---|---|
| A | Principal never creates users (`USER_CREATE`) | **Allow** | Normal for a top post, and it's logged |
| B | Admins and Principals never mark attendance for others; Admins never enter marks (`ATTENDANCE_MARK`, `MARKS_ENTER`) | **Allow only by stepping in, with a reason** | Useful when a teacher is absent, but it changes student records, so it must be visible |
| C | Admin and Principal never configure fees (`FEE_STRUCTURE_CREATE`) | **Allow, keep fee approval separate** | They can prepare the structure but can't approve what they prepared |
| D | Principal, Finance Director and others see salary totals only (`PAYROLL_APPROVE_TOTALS`, `SALARY_INDIVIDUAL_VIEW`) | **Keep locked** | Individual salaries are private, and rule C11 limits them deliberately |
| E | Only Records Officer and Controller confirm graduation (`GRADUATION_CONFIRM`) | **Allow only by stepping in, with a reason** | Keeps work moving when nobody holds those roles |

## 4. Who can do what after handing work over

| When giving a permission, the top post chooses | Who does the work | Can the top post still do it? |
|---|---|---|
| Shared | Both | Yes, directly |
| Only this person | The assigned person | Only by clicking "Step in" and giving a reason, which is recorded |

In both cases the top post can take the permission back at any time. Stepping in on an approval covers one step of a request, never their own request, and never skips a step.

## Appendix A: every "Never allowed" rule (64)

| # | Action | Rule in the register (D2) | Treatment |
|---:|---|---|---|
| 9 | `USER_CREATE` | Principal never creates users | Decision A |
| 45 | `ATTENDANCE_MARK` | Admins, Principals and HOD-as-approver never mark for others | Decision B |
| 64 | `MARKS_ENTER` | Admins never enter marks | Decision B |
| 89 | `FEE_STRUCTURE_CREATE` | Admin and Principal never configure fees | Decision C |
| 223 | `PAYROLL_APPROVE_TOTALS` | Never sees individual salaries | Decision D |
| 225 | `SALARY_INDIVIDUAL_VIEW` | Principal, Finance Director and Accountant see totals only (Changed in 3.3) Finance Director, Registrar, Principals, College Accountant and all others see totals only [C11]. | Decision D |
| 257 | `GRADUATION_CONFIRM` | (Changed in 3.3) Only the Records Officer and Controller confirm graduation; the Alumni Coordinator manages alumni after graduation [3.3]. | Decision E |
| 15 | `ROLE_ASSIGN_NON_SENSITIVE` | Cannot assign Admin, Principal, HOD, Accountant, HR, Payroll or exam roles | Limits a lower role |
| 49 | `ATTENDANCE_CORRECTION_APPROVE` | School Admin no longer approves | Limits a lower role |
| 55 | `EXAM_RULES_SET` | Controller no longer sets rules | Limits a lower role |
| 76 | `RESULT_WITHDRAW_APPROVE` | College Admin cannot withdraw without approval | Limits a lower role |
| 124 | `STUDENT_LEAVE_VIEW` | Admins never approve leave | Limits a lower role |
| 133 | `LOSS_OF_PAY_CONFIRM` | HR Executive cannot confirm | Limits a lower role |
| 201 | `PLAN_MANAGE` | Platform Admin cannot change pricing | Limits a lower role |
| 208 | `DISCOUNT_APPLY` | Billing Manager cannot give discounts | Limits a lower role |
| 219 | `BACKUP_STATUS_VIEW` | IT cannot download backups | Limits a lower role |
| 234 | `FINE_WAIVE` | Library Assistant cannot waive | Limits a lower role |
| 235 | `INVENTORY_AUDIT` | Library Assistant cannot | Limits a lower role |
| 252 | `HOSTEL_MANAGE` | Warden cannot edit academic records | Limits a lower role |
| 5 | `PROFILE_EDIT_OWN` | Cannot change own roles | Locked for everyone |
| 20 | `APPROVAL_DELEGATE` | Delegate cannot delegate again No other lower-role pairs (for example HOD → Professor) [D-001, 3.2]. (Changed in 3.3) Merit-list approval can never be delegated [WF-37, C12]. | Locked for everyone |
| 21 | `APPROVAL_BULK` | (Changed in 3.3) Data Protection Officer approvals (exports, deletions, retention) are never bulk-approved; each is reviewed separately [C12]. | Locked for everyone |
| 24 | `INSTITUTION_BLOCK_REQUEST` | Only the platform bills or suspends tenants | Locked for everyone |
| 29 | `CALENDAR_LOCAL_SET` | Cannot change university semester dates | Locked for everyone |
| 38 | `STUDENT_STATUS_CHANGE` | No hard delete of student records | Locked for everyone |
| 65 | `MARKS_EDIT_DRAFT` | No edits after submitting | Locked for everyone |
| 68 | `MARKS_APPROVE_HOD` | Cannot approve own marks | Locked for everyone |
| 69 | `MARKS_REVIEW_CLASS_TEACHER` | Cannot review own subject marks (skipped) | Locked for everyone |
| 74 | `RESULT_PUBLISH` | Nobody publishes before final approval | Locked for everyone |
| 78 | `RESULT_VIEW_OWN` | Cannot request re-evaluation in v1 | Locked for everyone |
| 104 | `PAYMENT_MARK_PAID_MANUAL` | Never without a gateway reference | Locked for everyone |
| 105 | `RECEIPT_DOWNLOAD` | Receipts are never edited | Locked for everyone |
| 128 | `STAFF_LEAVE_VERIFY_HR` | No HR step in schools | Locked for everyone |
| 131 | `PRINCIPAL_LEAVE_APPROVE` | The Principal never approves own leave [D-002, 3.2] | Locked for everyone |
| 140 | `MESSAGE_SEND` | Students cannot message students; parents reach the Principal only through the office; school students cannot message | Locked for everyone |
| 143 | `NOTIFICATION_OPTIONS_SET` | Users cannot switch notifications off | Locked for everyone |
| 149 | `COMMUNITY_LIKE_COMMENT` | No share in v1; school students have no community access | Locked for everyone |
| 154 | `COMPLAINT_RAISE` | Staff cannot raise complaints | Locked for everyone |
| 159 | `NOTE_CORRECT` | Notes are never edited or deleted | Locked for everyone |
| 180 | `BULK_CORRECT` | Never writes attendance, marks or fees directly | Locked for everyone |
| 186 | `AUDIT_LOG_VIEW` | No role can edit or delete logs | Locked for everyone |
| 189 | `COMPLIANCE_REPORT_MANAGE` | (Changed in 3.3) The preparer never approves the same report [AP-04, PQ-18]. | Locked for everyone |
| 190 | `ACCREDITATION_EVIDENCE_MAP` | (Changed in 3.3) The preparer never approves the same item [AP-04, PQ-18]. | Locked for everyone |
| 191 | `GOVERNANCE_ITEM_APPROVE` | (Changed in 3.3) Nobody approves an item they prepared or drafted [AP-04, PQ-18]. | Locked for everyone |
| 213 | `SUPPORT_ACCESS_USE` | The only way platform staff see tenant data | Locked for everyone |
| 214 | `RELEASE_MANAGE` | No per-tenant rollout in v1 | Locked for everyone |
| 215 | `INTEGRATION_TYPES_MANAGE` | Platform roles never see a tenant's keys or webhooks | Locked for everyone |
| 216 | `PLATFORM_AUDIT_VIEW` | Never tenant audit logs | Locked for everyone |
| 227 | `OWN_SALARY_CHANGE_APPROVE` | Nobody changes their own salary without approval | Locked for everyone |
| 255 | `DRIVER_ROUTE_VIEW` | Never phone numbers or addresses | Locked for everyone |
| 262 | `TENANT_INTEGRATION_MANAGE` | Platform roles never see these | Locked for everyone |
| 266 | `SENIOR_LEAVE_APPROVE` | Nobody approves their own leave | Locked for everyone |
| 267 | `PLATFORM_USER_MANAGE` | Platform users never get tenant roles | Locked for everyone |
| 268 | `TENANT_TOP_USERS_CREATE` | Cannot give these roles to themselves | Locked for everyone |
| 269 | `PRINCIPAL_LEAVE_APPROVER_SET` | Can never be the Principal | Locked for everyone |
| 270 | `CONSENT_RECORD` | Consent cannot be backdated | Locked for everyone |
| 271 | `UNIVERSITY_POLICY_APPROVE` | Never approves a policy they drafted [AP-04] | Locked for everyone |
| 272 | `STUDENT_RECORD_EDIT` | Cannot change the student ID or status (status has its own actions); no hard delete [MX-09] | Locked for everyone |
| 273 | `RECORD_CORRECTION_APPLY` | Never without an approved request | Locked for everyone |
| 275 | `VC_LEAVE_APPROVER_SET` | Can never be the Vice-Chancellor | Locked for everyone |
| 276 | `TENANT_OVERSIGHT_VIEW` | No create, edit, delete or approval through this action | Locked for everyone |
| 277 | `PLATFORM_REPORTS_VIEW` | Never tenant data | Locked for everyone |
| 278 | `INTEGRATION_SETTINGS_VIEW` | Never sees keys or secrets; cannot change settings | Locked for everyone |
| 198 | `TENANT_DELETION_APPROVE` | Product Owner cannot approve (Data Protect M) | Locked; named holder only |

## Appendix B: all 278 actions

Has = holds it in the register today. Gains = new under this rule. No = can never hold it. Blank = outside that post's area.

| # | Module | Action | What it does | Group | PO | Mgmt | VC | Principal | School Principal | Treatment |
|---:|---|---|---|---|---|---|---|---|---|---|
| 1 | Sign-in and account | `AUTH_LOGIN` | Log in | Both | Has | Has | Has | Has | Has |  |
| 2 | Sign-in and account | `AUTH_FIRST_LOGIN_RESET` | Change the printed first-login password | Personal |  |  |  |  |  |  |
| 3 | Sign-in and account | `AUTH_PASSWORD_CHANGE` | Change own password | Both | Has | Has | Has | Has | Has |  |
| 4 | Sign-in and account | `AUTH_DEVICES_MANAGE` | See own active devices and log them out | Both | Has | Has | Has | Has | Has |  |
| 5 | Sign-in and account | `PROFILE_EDIT_OWN` | Edit own profile and contact details | Both | Has | Has | Has | Has | Has | Locked for everyone |
| 6 | Sign-in and account | `ROLE_SWITCH` | Switch the active role | Both | Has | Has | Has | Has | Has |  |
| 7 | Sign-in and account | `SELF_SERVICE_VIEW` | See own payslips, notices and leave | Tenant |  | Gains | Has | Has | Has |  |
| 8 | Users and roles | `USER_VIEW` | See user accounts | Tenant |  | Has | Has | Has | Has |  |
| 9 | Users and roles | `USER_CREATE` | Create a user (single) | Tenant |  | Gains | Gains | Gains | Gains | Decision A |
| 10 | Users and roles | `USER_BULK_UPLOAD` | Create users from a file | Tenant |  | Gains | Gains | Gains | Gains |  |
| 11 | Users and roles | `USER_EDIT` | Edit a user account | Tenant |  | Gains | Gains | Gains | Gains |  |
| 12 | Users and roles | `USER_DEACTIVATE` | Deactivate a user | Tenant |  | Gains | Gains | Gains | Gains |  |
| 13 | Users and roles | `USER_PASSWORD_RESET_UNLOCK` | Reset a password or unlock an account | Tenant |  | Gains | Gains | Gains | Gains |  |
| 14 | Users and roles | `ROLE_ASSIGN` | Give a user any role | Tenant |  | Gains | Gains | Gains | Gains |  |
| 15 | Users and roles | `ROLE_ASSIGN_NON_SENSITIVE` | Give a user a non-sensitive role | Tenant |  | Gains | Gains | Gains | Gains | Limits a lower role |
| 16 | Users and roles | `ROLE_CUSTOM_MANAGE` | Create a custom role and set its rights | Tenant |  | Gains | Gains | Gains | Gains |  |
| 17 | Users and roles | `GUARDIAN_LINK` | Link a parent or guardian to a student | Tenant |  | Gains | Gains | Gains | Gains |  |
| 18 | Users and roles | `GUARDIAN_CHANGE_REQUEST` | Ask to change or block a guardian | Tenant |  | Gains | Gains | Gains | Gains |  |
| 19 | Users and roles | `GUARDIAN_CHANGE_APPROVE` | Approve a guardian change or block | Tenant |  | Gains | Gains | Has | Has |  |
| 20 | Users and roles | `APPROVAL_DELEGATE` | Hand own approvals to someone else for set dates | Both | No | Has | Has | Has | Has | Locked for everyone |
| 21 | Users and roles | `APPROVAL_BULK` | Approve several requests at once | Tenant |  | Has | Has | Has | Has | Locked for everyone |
| 22 | Institution setup | `INSTITUTION_CREATE_DRAFT` | Create a college or school (as Draft) | Tenant |  | Gains | Gains |  |  |  |
| 23 | Institution setup | `INSTITUTION_ACTIVATE` | Approve a Draft institution so it goes live | Tenant |  | Gains | Has |  |  |  |
| 24 | Institution setup | `INSTITUTION_BLOCK_REQUEST` | Ask to block an institution's access | Tenant |  | Gains | Gains |  |  | Locked for everyone |
| 25 | Institution setup | `INSTITUTION_BLOCK_APPROVE` | Approve or reject an institution block | Tenant |  | Gains | Has |  |  |  |
| 26 | Institution setup | `STRUCTURE_MANAGE` | Create and edit departments, programs, years, semesters, sections, classes, subjects and subject groups | Tenant |  | Gains | Has | Has | Has |  |
| 27 | Institution setup | `PROGRAM_RULES_SET` | Set program and curriculum rules | Tenant |  | Gains | Gains |  |  |  |
| 28 | Institution setup | `CALENDAR_MASTER_SET` | Set semester dates and university holidays | Tenant |  | Gains | Gains | Gains | Gains |  |
| 29 | Institution setup | `CALENDAR_LOCAL_SET` | Add local holidays and events | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 30 | Institution setup | `WEEKLY_OFF_SET` | Set the weekly-off pattern | Tenant |  | Gains | Gains | Gains | Gains |  |
| 31 | Institution setup | `GRADING_SCHEME_SET` | Set grade points, credits and grade bands | Tenant |  | Gains | Gains | Gains | Gains |  |
| 32 | Institution setup | `HOD_ASSIGN` | Make a faculty member HOD | Tenant |  | Gains | Gains | Gains |  |  |
| 33 | Institution setup | `SUBJECT_ALLOCATE` | Give subjects to teachers | Tenant |  | Gains | Gains | Gains | Gains |  |
| 34 | Institution setup | `MENTOR_ASSIGN` | Assign a mentor / class coordinator to a section | Tenant |  | Gains | Gains | Gains |  |  |
| 35 | Institution setup | `CLASS_TEACHER_ASSIGN` | Assign a Class Teacher to a class | Tenant |  | Gains | Gains |  | Gains |  |
| 36 | Institution setup | `PROMOTION_RUN` | Run year-end promotion | Tenant |  | Gains | Gains | Gains | Gains |  |
| 37 | Institution setup | `PROMOTION_APPROVE` | Approve year-end promotion | Tenant |  | Gains | Gains | Has | Has |  |
| 38 | Institution setup | `STUDENT_STATUS_CHANGE` | Change a student's status (On hold, Withdrawn, Transferred, Dropped, Detained, Graduated) | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 39 | Institution setup | `STUDENT_LOGIN_SWITCH` | Turn on view-only login for chosen school classes | Tenant |  | Gains | Gains |  | Gains |  |
| 40 | Institution setup | `TERM_LOCK` | Lock the term | Tenant |  | Gains | Gains | Has | Has |  |
| 41 | Institution setup | `TERM_UNLOCK` | Unlock a locked term | Tenant |  | Gains | Gains | Has | Has |  |
| 42 | Timetable | `TIMETABLE_BUILD` | Build and publish the timetable | Tenant |  | Gains | Gains | Gains | Gains |  |
| 43 | Timetable | `ONLINE_PERIOD_SET` | Mark a period as online (meeting created automatically) | Tenant |  | Gains | Gains | Gains | Gains |  |
| 44 | Timetable | `TIMETABLE_VIEW` | See the timetable | Tenant |  | Gains | Gains | Has | Has |  |
| 45 | Attendance | `ATTENDANCE_MARK` | Mark student attendance | Tenant |  | Gains | Gains | Gains | Gains | Decision B |
| 46 | Attendance | `ATTENDANCE_NOT_HELD` | Mark a period "Not held" | Tenant |  | Gains | Gains | Gains | Gains |  |
| 47 | Attendance | `SUBSTITUTE_ASSIGN` | Assign a substitute teacher for a date | Tenant |  | Gains | Gains | Has | Has |  |
| 48 | Attendance | `ATTENDANCE_CORRECTION_REQUEST` | Ask to correct submitted attendance | Tenant |  | Gains | Gains | Gains | Gains |  |
| 49 | Attendance | `ATTENDANCE_CORRECTION_APPROVE` | Approve or reject an attendance correction | Tenant |  | Gains | Gains | Gains | Has | Limits a lower role |
| 50 | Attendance | `ATTENDANCE_BULK_UPLOAD` | Upload attendance from a file | Tenant |  | Gains | Gains | Gains | Gains |  |
| 51 | Attendance | `ATTENDANCE_BULK_UPLOAD_APPROVE` | Approve an Admin attendance upload | Tenant |  | Gains | Gains | Has | Has |  |
| 52 | Attendance | `ATTENDANCE_RULES_SET` | Set attendance rules (minimums, formula, school marking pattern) | Tenant |  | Gains | Gains | Gains | Gains |  |
| 53 | Attendance | `ATTENDANCE_VIEW` | See attendance and defaulter lists | Tenant |  | Has | Has | Has | Has |  |
| 54 | Attendance | `ATTENDANCE_VIEW_OWN` | See own or own child's attendance | Personal |  |  |  |  |  |  |
| 55 | Exams | `EXAM_RULES_SET` | Set exam rules (internal / external split, grading) | Tenant |  | Gains | Gains |  |  | Limits a lower role |
| 56 | Exams | `EXAM_PATTERN_SET` | Set exam patterns and grade bands | Tenant |  | Gains | Gains | Gains | Gains |  |
| 57 | Exams | `EXAM_SCHEDULE_UNIVERSITY` | Create university exam schedules | Tenant |  | Gains | Gains |  |  |  |
| 58 | Exams | `EXAM_SCHEDULE_INTERNAL` | Create internal exam schedules | Tenant |  | Gains | Gains | Gains | Gains |  |
| 59 | Exams | `EXAM_SCHEDULE_APPROVE` | Approve exam schedules | Tenant |  | Gains | Gains | Gains | Gains |  |
| 60 | Exams | `HALL_TICKET_CHECKS_SET` | Switch hall-ticket checks (attendance, overdue fees) on or off | Tenant |  | Gains | Gains | Gains | Gains |  |
| 61 | Exams | `HALL_TICKET_GENERATE` | Generate hall tickets | Tenant |  | Gains | Gains | Gains | Gains |  |
| 62 | Exams | `HALL_TICKET_OVERRIDE` | Release a withheld hall ticket for one student | Tenant |  | Gains | Gains | Has | Has |  |
| 63 | Exams | `HALL_TICKET_DOWNLOAD` | Download a hall ticket | Personal |  |  |  |  |  |  |
| 64 | Exams | `MARKS_ENTER` | Enter marks (draft) | Tenant |  | Gains | Gains | Gains | Gains | Decision B |
| 65 | Exams | `MARKS_EDIT_DRAFT` | Edit marks while in Draft | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 66 | Exams | `MARKS_SUBMIT` | Submit marks for approval | Tenant |  | Gains | Gains | Gains | Gains |  |
| 67 | Exams | `MARKS_EXTERNAL_UPLOAD` | Upload external / paper marks | Tenant |  | Gains | Gains | Gains |  |  |
| 68 | Exams | `MARKS_APPROVE_HOD` | Approve or reject department marks | Tenant |  | Gains | Gains | Gains |  | Locked for everyone |
| 69 | Exams | `MARKS_REVIEW_CLASS_TEACHER` | Review subject marks for the class | Tenant |  | Gains | Gains |  | Gains | Locked for everyone |
| 70 | Exams | `MARKS_APPROVE_PRINCIPAL` | Approve or reject results | Tenant |  | Gains | Gains | Has | Has |  |
| 71 | Exams | `SUPPLEMENTARY_EXAM_MANAGE` | Track backlogs and run supplementary exam registration | Tenant |  | Gains | Gains | Gains |  |  |
| 72 | Exams | `SUPPLEMENTARY_REGISTER` | See own backlogs and register for supplementary exams | Personal |  |  |  |  |  |  |
| 73 | Exams | `EXAM_VIEW` | See exam schedules and marks status | Tenant |  | Has | Has | Has | Has |  |
| 74 | Results | `RESULT_PUBLISH` | Publish results | Tenant |  | Gains | Gains | Gains | Has | Locked for everyone |
| 75 | Results | `RESULT_WITHDRAW` | Withdraw a published result | Tenant |  | Gains | Gains | Gains | Has |  |
| 76 | Results | `RESULT_WITHDRAW_APPROVE` | Approve a College Admin's result withdrawal | Tenant |  | Gains | Gains | Has |  | Limits a lower role |
| 77 | Results | `RESULT_VIEW` | See results and analysis | Tenant |  | Has | Has | Has | Has |  |
| 78 | Results | `RESULT_VIEW_OWN` | See own or own child's published results | Personal |  |  |  |  |  | Locked for everyone |
| 79 | Assignments and homework | `ASSIGNMENT_CREATE` | Create an assignment or homework | Tenant |  | Gains | Gains | Gains | Gains |  |
| 80 | Assignments and homework | `ASSIGNMENT_CLOSE` | Close an assignment | Tenant |  | Gains | Gains | Gains | Gains |  |
| 81 | Assignments and homework | `STUDY_MATERIAL_UPLOAD` | Upload study material | Tenant |  | Gains | Gains | Gains | Gains |  |
| 82 | Assignments and homework | `ASSIGNMENT_SUBMIT` | Submit an assignment | Personal |  |  |  |  |  |  |
| 83 | Assignments and homework | `ASSIGNMENT_EVALUATE` | Mark and give feedback | Tenant |  | Gains | Gains | Gains | Gains |  |
| 84 | Assignments and homework | `HOMEWORK_STATUS_MARK` | Mark school homework status | Tenant |  | Gains | Gains |  | Gains |  |
| 85 | Assignments and homework | `ASSIGNMENT_VIEW_OWN` | See assignments, status and feedback | Personal |  |  |  |  |  |  |
| 86 | Assignments and homework | `HOMEWORK_COMPLIANCE_VIEW` | See homework compliance | Tenant |  | Gains | Gains | Has | Has |  |
| 87 | Fees | `FEE_POLICY_SET` | Set the fee policy (heads, ranges, schemes, penalty policy) | Tenant |  | Has | Gains | Has | Has |  |
| 88 | Fees | `FEE_POLICY_DATA_ENTER` | Enter fee policy data for the Finance Director | Tenant |  | Gains | Gains |  |  |  |
| 89 | Fees | `FEE_STRUCTURE_CREATE` | Build a fee structure | Tenant |  | Gains | Gains | Gains | Gains | Decision C |
| 90 | Fees | `FEE_STRUCTURE_APPROVE` | Approve a fee structure | Tenant |  | Gains | Gains | Has | Has |  |
| 91 | Fees | `FEE_EXCEPTION_REQUEST` | Ask for an out-of-range fee | Tenant |  | Gains | Gains | Gains | Gains |  |
| 92 | Fees | `FEE_EXCEPTION_APPROVE_FIRST` | First approval of a fee exception | Tenant |  | Has | Gains |  |  |  |
| 93 | Fees | `FEE_EXCEPTION_APPROVE_PRINCIPAL` | Principal approval of a fee exception | Tenant |  | Gains | Gains | Has | Has |  |
| 94 | Fees | `INSTALLMENT_SET` | Define installments | Tenant |  | Gains | Gains | Gains | Gains |  |
| 95 | Fees | `SCHOLARSHIP_MANAGE` | Create scholarship schemes and run eligibility | Tenant |  | Gains | Gains | Gains | Gains |  |
| 96 | Fees | `SCHOLARSHIP_LIST_APPROVE` | Approve the scholarship list | Tenant |  | Gains | Gains | Has | Has |  |
| 97 | Fees | `CONCESSION_REQUEST` | Ask for a one-off discount for a student | Tenant |  | Gains | Gains | Gains | Gains |  |
| 98 | Fees | `CONCESSION_APPROVE` | Approve a concession | Tenant |  | Gains | Gains | Has | Has |  |
| 99 | Fees | `LATE_FEE_RULES_SET` | Set late-fee rules | Tenant |  | Gains | Gains | Gains | Gains |  |
| 100 | Fees | `LATE_FEE_WAIVER_REQUEST` | Ask to waive a late fee | Tenant |  | Gains | Gains | Gains | Gains |  |
| 101 | Fees | `LATE_FEE_WAIVER_APPROVE` | Approve a late-fee waiver | Tenant |  | Gains | Gains | Has | Has |  |
| 102 | Fees | `FEE_PAY_ONLINE` | Pay fees online | Personal |  |  |  |  |  |  |
| 103 | Fees | `PAYMENT_OFFLINE_RECORD` | Record a cash or cheque payment | Tenant |  | Gains | Gains | Gains | Gains |  |
| 104 | Fees | `PAYMENT_MARK_PAID_MANUAL` | Mark an online payment as paid by hand | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 105 | Fees | `RECEIPT_DOWNLOAD` | Download a receipt | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 106 | Fees | `RECEIPT_CANCEL_REQUEST` | Ask to cancel a wrong receipt | Tenant |  | Gains | Gains | Gains | Gains |  |
| 107 | Fees | `RECEIPT_CANCEL_APPROVE` | Approve a receipt cancellation | Tenant |  | Gains | Gains | Has | Has |  |
| 108 | Fees | `PERIOD_LOCK` | Lock a financial month | Tenant |  | Gains | Gains | Gains | Gains |  |
| 109 | Fees | `PERIOD_UNLOCK_REQUEST` | Ask to unlock a locked month | Tenant |  | Gains | Gains | Gains | Gains |  |
| 110 | Fees | `PERIOD_UNLOCK_APPROVE` | Approve unlocking a month | Tenant |  | Gains | Gains | Has | Has |  |
| 111 | Fees | `FEE_REMINDER_SEND` | Send a manual fee reminder | Tenant |  | Gains | Gains | Gains | Gains |  |
| 112 | Fees | `NO_DUES_OVERRIDE` | Override the no-dues check for a certificate or TC | Tenant |  | Gains | Gains | Has | Has |  |
| 113 | Fees | `LEDGER_VIEW` | See student ledgers, collections and fee reports | Tenant |  | Has | Has | Has | Has |  |
| 114 | Fees | `DUES_STATUS_VIEW` | See whether a student has dues (yes / no only) | Tenant |  | Gains | Gains | Gains | Gains |  |
| 115 | Fees | `DAILY_COLLECTION_REPORT_VIEW` | See the daily collection report | Tenant |  | Has | Gains | Has | Has |  |
| 116 | Fees | `TALLY_EXPORT` | Export the ledger as a Tally-format file | Tenant |  | Gains | Gains | Gains | Gains |  |
| 117 | Fees | `FEE_VIEW_OWN` | See own or own child's fees, dues and receipts | Personal |  |  |  |  |  |  |
| 118 | Leave | `LEAVE_TYPES_SET` | Set leave types and the sandwich rule | Tenant |  | Gains | Gains | Gains | Gains |  |
| 119 | Leave | `STUDENT_LEAVE_APPLY` | Apply for student leave | Personal |  |  |  |  |  |  |
| 120 | Leave | `STUDENT_LEAVE_APPROVE_MENTOR` | Approve or reject student leave as mentor | Tenant |  | Gains | Gains | Gains |  |  |
| 121 | Leave | `STUDENT_LEAVE_APPROVE_HOD` | Approve or reject student leave | Tenant |  | Gains | Gains | Gains |  |  |
| 122 | Leave | `STUDENT_LEAVE_APPROVE_CT` | Approve or reject student leave | Tenant |  | Gains | Gains |  | Gains |  |
| 123 | Leave | `STUDENT_LEAVE_APPROVE_PRINCIPAL` | Approve or reject long student leave | Tenant |  | Gains | Gains | Has | Has |  |
| 124 | Leave | `STUDENT_LEAVE_VIEW` | See student leave | Tenant |  | Has | Gains | Gains | Gains | Limits a lower role |
| 125 | Leave | `LEAVE_CANCEL_OWN` | Cancel own leave request | Tenant |  | Gains | Has | Has | Has |  |
| 126 | Leave | `STAFF_LEAVE_APPLY` | Apply for own leave | Tenant |  | Gains | Has | Has | Has |  |
| 127 | Leave | `STAFF_LEAVE_RECOMMEND_HOD` | Recommend faculty leave | Tenant |  | Gains | Gains | Gains |  |  |
| 128 | Leave | `STAFF_LEAVE_VERIFY_HR` | Verify staff leave (balance, overlap) | Tenant |  | Gains | Gains | Gains |  | Locked for everyone |
| 129 | Leave | `STAFF_LEAVE_APPROVE_PRINCIPAL` | Approve or reject staff leave | Tenant |  | Gains | Gains | Has | Has |  |
| 130 | Leave | `STAFF_LEAVE_APPROVE_REGISTRAR` | Approve university staff leave | Tenant |  | Gains | Gains |  |  |  |
| 131 | Leave | `PRINCIPAL_LEAVE_APPROVE` | Approve the Principal's own leave | Tenant |  | Has | Has |  |  | Locked for everyone |
| 132 | Leave | `LEAVE_BALANCE_CREDIT` | Credit yearly leave balances | Tenant |  | Gains | Gains | Gains | Gains |  |
| 133 | Leave | `LOSS_OF_PAY_CONFIRM` | Confirm flagged loss of pay | Tenant |  | Gains | Gains | Gains | Gains | Limits a lower role |
| 134 | Communication | `NOTICE_PUBLISH_INSTITUTION` | Publish an institution-wide notice directly | Tenant |  | Gains | Gains | Has | Has |  |
| 135 | Communication | `NOTICE_PUBLISH_UNIVERSITY` | Publish a university notice | Tenant |  | Gains | Gains |  |  |  |
| 136 | Communication | `NOTICE_PUBLISH_CLASS_DEPT` | Publish a class or department notice directly | Tenant |  | Gains | Gains | Gains | Gains |  |
| 137 | Communication | `NOTICE_SUBMIT` | Write a notice for Principal approval | Tenant |  | Gains | Gains | Gains | Gains |  |
| 138 | Communication | `NOTICE_APPROVE` | Approve or reject a notice | Tenant |  | Gains | Gains | Has | Has |  |
| 139 | Communication | `NOTICE_EMERGENCY` | Send an emergency notice | Tenant |  | Gains | Gains | Has | Has |  |
| 140 | Communication | `MESSAGE_SEND` | Send a private message | Tenant |  | Gains | Gains | Has | Has | Locked for everyone |
| 141 | Communication | `MESSAGE_OFFICE_HANDLE` | Handle messages sent to the office | Tenant |  | Gains | Gains | Gains | Gains |  |
| 142 | Communication | `CLASS_BROADCAST` | Send a one-way broadcast to a class | Tenant |  | Gains | Gains | Gains | Gains |  |
| 143 | Communication | `NOTIFICATION_OPTIONS_SET` | Tick which optional notifications the institution sends | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 144 | Communication | `DELIVERY_FAILURES_VIEW` | See failed SMS / email and wrong numbers | Tenant |  | Gains | Gains | Gains | Gains |  |
| 145 | Communication | `SMS_EMAIL_SETUP` | Set up SMS sender ID and templates (DLT), email and meeting tools | Tenant |  | Gains | Gains | Gains | Gains |  |
| 146 | Community and events | `COMMUNITY_POST_DIRECT` | Publish a community post directly | Tenant |  | Gains | Gains | Has | Has |  |
| 147 | Community and events | `COMMUNITY_POST_SUBMIT` | Write a community post for approval | Tenant |  | Gains | Gains | Gains | Gains |  |
| 148 | Community and events | `COMMUNITY_POST_APPROVE` | Approve or reject a community post | Tenant |  | Gains | Gains | Has | Has |  |
| 149 | Community and events | `COMMUNITY_LIKE_COMMENT` | Like and comment on posts | Tenant |  | Has | Has | Has | Has | Locked for everyone |
| 150 | Community and events | `COMMUNITY_COMMENT_HIDE` | Hide a comment | Tenant |  | Gains | Gains | Has | Has |  |
| 151 | Community and events | `EVENT_PROPOSE` | Propose an event | Tenant |  | Gains | Gains | Gains | Gains |  |
| 152 | Community and events | `EVENT_APPROVE` | Approve or reject an event | Tenant |  | Gains | Gains | Has | Has |  |
| 153 | Community and events | `EVENT_RSVP` | Register for an event | Tenant |  | Has | Has | Has | Has |  |
| 154 | Complaints | `COMPLAINT_RAISE` | Raise a complaint | Personal |  |  |  |  |  | Locked for everyone |
| 155 | Complaints | `COMPLAINT_ASSIGN` | Assign a complaint to an owner | Tenant |  | Gains | Gains | Has | Has |  |
| 156 | Complaints | `COMPLAINT_RESOLVE` | Resolve or escalate a complaint | Tenant |  | Gains | Gains | Has | Has |  |
| 157 | Complaints | `COMPLAINT_ESCALATED_VIEW` | Review escalated complaints | Tenant |  | Gains | Gains |  |  |  |
| 158 | Student notes | `NOTE_ADD` | Add a discipline or health note | Tenant |  | Gains | Gains | Has | Has |  |
| 159 | Student notes | `NOTE_CORRECT` | Add a correction to an existing note | Tenant |  | Gains | Gains | Has | Has | Locked for everyone |
| 160 | Student notes | `NOTE_VIEW_DISCIPLINE` | See discipline notes | Tenant |  | Gains | Gains | Has | Has |  |
| 161 | Student notes | `NOTE_VIEW_HEALTH` | See health notes | Tenant |  | Gains | Gains | Has | Has |  |
| 162 | Student notes | `HEALTH_NOTES_SWITCH` | Turn parents' view of health notes on or off | Tenant |  | Gains | Gains | Gains | Gains |  |
| 163 | Policies and documents | `POLICY_DRAFT` | Draft a university policy | Tenant |  | Gains | Gains | Has | Has |  |
| 164 | Policies and documents | `POLICY_PUBLISH` | Publish a policy | Tenant |  | Gains | Gains | Gains | Gains |  |
| 165 | Policies and documents | `POLICY_ACCEPT` | Read and accept a policy | Tenant |  | Gains | Has | Has | Has |  |
| 166 | Policies and documents | `DEPT_DOCUMENT_UPLOAD` | Upload department documents | Tenant |  | Gains | Gains | Gains |  |  |
| 167 | Staff records | `STAFF_RECORD_MANAGE` | Create and update staff profiles and service records | Tenant |  | Gains | Gains | Gains | Gains |  |
| 168 | Staff records | `STAFF_DOCUMENT_VERIFY` | Verify staff documents (degrees, ID) | Tenant |  | Gains | Gains | Gains | Gains |  |
| 169 | Staff records | `STAFF_RECORD_VIEW` | See staff records | Tenant |  | Has | Has | Has | Has |  |
| 170 | Risk monitoring | `RISK_THRESHOLDS_SET` | Change risk thresholds | Tenant |  | Gains | Gains | Gains | Gains |  |
| 171 | Risk monitoring | `RISK_LIST_VIEW` | See risk lists | Tenant |  | Has | Gains | Has | Has |  |
| 172 | Risk monitoring | `RISK_REMARK_ADD` | Add remarks to a flagged student | Tenant |  | Gains | Gains | Gains | Gains |  |
| 173 | Risk monitoring | `RISK_VIEW_OWN` | See own or own child's risk status | Personal |  |  |  |  |  |  |
| 174 | Reports and dashboards | `REPORTS_VIEW` | See fixed reports with filters | Tenant |  | Has | Has | Has | Has |  |
| 175 | Reports and dashboards | `BUYER_DASHBOARD_VIEW` | See the buyer dashboard (fees, attendance, results) | Tenant |  | Has | Has | Has | Has |  |
| 176 | Reports and dashboards | `USAGE_ANALYTICS_VIEW` | See usage analytics for own scope | Tenant |  | Has | Has | Has | Has |  |
| 177 | Reports and dashboards | `STATUTORY_EXPORT` | Export UDISE+, AISHE and affiliating-university files | Tenant |  | Gains | Gains | Gains | Gains |  |
| 178 | Reports and dashboards | `ID_CARD_EXPORT` | Export ID card data (photo + details) | Tenant |  | Gains | Gains | Gains | Gains |  |
| 179 | Data and records | `DATA_IMPORT` | Import students, staff, parents, fee dues or past results from templates | Tenant |  | Gains | Gains | Gains | Gains |  |
| 180 | Data and records | `BULK_CORRECT` | Correct many records at once | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 181 | Data and records | `STUDENT_SEARCH` | Search students by name, ID or phone | Tenant |  | Gains | Gains | Has | Has |  |
| 182 | Data and records | `STUDENT_RECORD_VIEW_OWN` | See own student record | Personal |  |  |  |  |  |  |
| 183 | Data and records | `CERTIFICATE_REQUEST` | Request a certificate, TC or record correction | Personal |  |  |  |  |  |  |
| 184 | Data and records | `CERTIFICATE_APPROVE` | Approve a certificate, TC or record correction | Tenant |  | Gains | Gains | Has | Has |  |
| 185 | Data and records | `CERTIFICATE_PREPARE` | Prepare the approved certificate or TC | Tenant |  | Gains | Gains | Gains |  |  |
| 186 | Governance and data protection | `AUDIT_LOG_VIEW` | See audit logs | Tenant |  | Has | Has | Has | Has | Locked for everyone |
| 187 | Governance and data protection | `AUDIT_LOG_EXPORT` | Export audit logs | Tenant |  | Has | Has | Has | Has |  |
| 188 | Governance and data protection | `AUDIT_FINDING_RECORD` | Record audit findings | Tenant |  | Gains | Gains | Gains |  |  |
| 189 | Governance and data protection | `COMPLIANCE_REPORT_MANAGE` | Prepare compliance reports and statutory returns (AISHE, UDISE+ and the like) | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 190 | Governance and data protection | `ACCREDITATION_EVIDENCE_MAP` | Upload accreditation documents and map evidence | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 191 | Governance and data protection | `GOVERNANCE_ITEM_APPROVE` | Approve or reject items in own governance area | Tenant |  | Has | Gains | Has | Has | Locked for everyone |
| 192 | Governance and data protection | `DATA_REQUEST_RAISE` | Ask for own personal data, a correction or deletion | Tenant |  | Has | Has | Has | Has |  |
| 193 | Governance and data protection | `DATA_REQUEST_HANDLE` | Handle personal-data requests | Tenant |  | Gains |  |  |  |  |
| 194 | Governance and data protection | `TENANT_EXPORT_REQUEST` | Request a full-tenant export | Both | Has | Gains | Gains | Gains | Gains |  |
| 195 | Governance and data protection | `TENANT_EXPORT_APPROVE` | Approve a full-tenant export | Platform | No |  |  |  |  | Named holder only |
| 196 | Governance and data protection | `TENANT_DELETION_REQUEST` | Request tenant deletion | Both | Has | Gains | Gains | Gains | Gains |  |
| 197 | Governance and data protection | `TENANT_DELETION_CONFIRM` | Confirm a tenant deletion request | Platform | Has |  |  |  |  |  |
| 198 | Governance and data protection | `TENANT_DELETION_APPROVE` | Approve tenant deletion | Platform | No |  |  |  |  | Locked; named holder only |
| 199 | Governance and data protection | `RETENTION_CHANGE_APPROVE` | Approve retention changes | Platform | No |  |  |  |  | Named holder only |
| 200 | Governance and data protection | `RBAC_MAPPING_CHECK` | Compare live permissions with the matrix and custom roles | Both | Gains | Gains | Gains | Gains |  |  |
| 201 | Platform | `PLAN_MANAGE` | Create plans and set prices | Platform | Has |  |  |  |  | Limits a lower role |
| 202 | Platform | `TENANT_CREATE` | Create a tenant (starts in Trial) | Platform | Has |  |  |  |  |  |
| 203 | Platform | `TENANT_ACTIVATE` | Activate a tenant after payment | Platform | Has |  |  |  |  |  |
| 204 | Platform | `TENANT_SUSPEND_ARCHIVE` | Suspend or archive a tenant | Platform | Has |  |  |  |  |  |
| 205 | Platform | `TENANT_SETTINGS` | Change a tenant's limits, features and branding within its plan | Platform | Has |  |  |  |  |  |
| 206 | Platform | `FEATURE_TOGGLE` | Switch modules and features on or off per plan or tenant | Platform | Has |  |  |  |  |  |
| 207 | Platform | `INVOICE_MANAGE` | Generate, send and record platform invoices | Platform | Has |  |  |  |  |  |
| 208 | Platform | `DISCOUNT_APPLY` | Give a platform discount | Platform | Has |  |  |  |  | Limits a lower role |
| 209 | Platform | `OVERDUE_FLAG` | Flag an overdue tenant for suspension review | Platform | Gains |  |  |  |  |  |
| 210 | Platform | `SUPPORT_TICKET_RAISE` | Raise a support ticket | Tenant |  | Has | Has | Has | Has |  |
| 211 | Platform | `SUPPORT_TICKET_MANAGE` | Triage, assign, escalate and close tickets | Platform | Has |  |  |  |  |  |
| 212 | Platform | `SUPPORT_ACCESS_GRANT` | Give Support read-only access for one ticket | Tenant |  | Gains | Gains | Gains | Gains |  |
| 213 | Platform | `SUPPORT_ACCESS_USE` | View tenant data under a granted ticket | Platform | Gains |  |  |  |  | Locked for everyone |
| 214 | Platform | `RELEASE_MANAGE` | Upload, test, deploy and roll back releases | Platform | Has |  |  |  |  | Locked for everyone |
| 215 | Platform | `INTEGRATION_TYPES_MANAGE` | Manage the list of allowed integration types | Platform | Has |  |  |  |  | Locked for everyone |
| 216 | Platform | `PLATFORM_AUDIT_VIEW` | See platform audit logs | Platform | Has |  |  |  |  | Locked for everyone |
| 217 | Platform | `USAGE_COUNTS_VIEW` | See built-in usage counts and revenue | Platform | Has |  |  |  |  |  |
| 218 | Platform | `SECURITY_POLICY_SET` | Set password, session and backup policy | Platform | Has |  |  |  |  |  |
| 219 | Platform | `BACKUP_STATUS_VIEW` | See backup status | Both | Has | Gains | Gains | Gains | Gains | Limits a lower role |
| 220 | Payroll | `SALARY_STRUCTURE_EDIT` | Set salary structures and deductions | Tenant |  | Gains | Gains | Gains | Gains |  |
| 221 | Payroll | `PAYROLL_GENERATE` | Generate monthly payroll | Tenant |  | Gains | Gains | Gains | Gains |  |
| 222 | Payroll | `PAYROLL_VERIFY` | Verify generated payroll | Tenant |  | Gains | Gains | Gains |  |  |
| 223 | Payroll | `PAYROLL_APPROVE_TOTALS` | Approve payroll totals | Tenant |  | Gains | Gains | Has | Has | Decision D |
| 224 | Payroll | `PAYSLIP_RELEASE` | Release payslips | Tenant |  | Gains | Gains | Gains | Gains |  |
| 225 | Payroll | `SALARY_INDIVIDUAL_VIEW` | See individual salaries | Tenant |  | Gains | Gains | Gains | Gains | Decision D |
| 226 | Payroll | `PAYROLL_TOTALS_VIEW` | See payroll totals for reconciliation | Tenant |  | Gains | Gains | Has | Has |  |
| 227 | Payroll | `OWN_SALARY_CHANGE_APPROVE` | Approve the payroll maker's own salary change | Tenant |  | Gains | Gains | Gains | Has | Locked for everyone |
| 228 | Library | `LIBRARY_SETTINGS_SET` | Set issue limits and fines per role | Tenant |  | Gains | Gains | Gains | Gains |  |
| 229 | Library | `CATALOG_MANAGE` | Add and update books and digital records; print barcode labels | Tenant |  | Gains | Gains | Gains | Gains |  |
| 230 | Library | `LIBRARY_CARD_ISSUE` | Issue library cards | Tenant |  | Gains | Gains | Gains | Gains |  |
| 231 | Library | `BOOK_ISSUE_RETURN` | Issue and return books | Tenant |  | Gains | Gains | Gains | Gains |  |
| 232 | Library | `RESERVATION_MANAGE` | Handle reservations | Tenant |  | Gains | Gains | Gains | Gains |  |
| 233 | Library | `FINE_COLLECT` | Collect library fines | Tenant |  | Gains | Gains | Gains | Gains |  |
| 234 | Library | `FINE_WAIVE` | Waive a library fine | Tenant |  | Gains | Gains | Gains | Gains | Limits a lower role |
| 235 | Library | `INVENTORY_AUDIT` | Mark books damaged or lost | Tenant |  | Gains | Gains | Gains | Gains | Limits a lower role |
| 236 | Library | `BOOK_SEARCH_RESERVE` | Search and reserve books | Tenant |  | Gains | Gains | Gains | Gains |  |
| 237 | Library | `LIBRARY_VIEW_OWN` | See issued books and fines | Tenant |  | Gains | Gains | Gains | Gains |  |
| 238 | Placement | `PLACEMENT_OFFICER_CREATE` | Create the Placement Officer account | Tenant |  | Gains | Gains | Gains |  |  |
| 239 | Placement | `PLACEMENT_MANAGE` | Manage companies, job posts, eligibility, shortlists, interviews, offers and Placed status | Tenant |  | Gains | Gains | Gains |  |  |
| 240 | Placement | `PLACEMENT_PROFILE_EDIT` | Keep a placement profile with resume | Personal |  |  |  |  |  |  |
| 241 | Placement | `JOB_APPLY` | Apply for a job | Personal |  |  |  |  |  |  |
| 242 | Placement | `OFFER_RESPOND` | Accept or decline an offer | Personal |  |  |  |  |  |  |
| 243 | Placement | `PLACEMENT_VIEW_CHILD` | See job posts and the child's applications and offers | Personal |  |  |  |  |  |  |
| 244 | Placement | `PLACEMENT_REPORT_VIEW` | See placement reports | Tenant |  | Has | Gains | Has |  |  |
| 245 | Admissions | `ADMISSION_FORM_CREATE` | Create the application form for a session | Tenant |  | Gains | Gains | Gains | Gains |  |
| 246 | Admissions | `APPLICATION_SUBMIT` | Apply for admission and pay the application fee | Personal |  |  |  |  |  |  |
| 247 | Admissions | `APPLICATION_VERIFY` | Verify documents and fees | Tenant |  | Gains | Gains | Gains |  |  |
| 248 | Admissions | `MERIT_LIST_GENERATE` | Generate the merit list | Tenant |  | Gains | Gains | Gains |  |  |
| 249 | Admissions | `MERIT_LIST_APPROVE` | Approve the final merit list | Tenant |  | No | No | Has | Has | Named holder only |
| 250 | Admissions | `SEAT_ALLOCATE` | Allocate seats and confirm admissions | Tenant |  | Gains | Gains | Gains |  |  |
| 251 | Admissions | `APPLICATION_FEE_VIEW` | See application-fee collection | Tenant |  | Gains | Gains | Gains | Gains |  |
| 252 | Hostel | `HOSTEL_MANAGE` | Manage blocks, rooms, allocation and vacancies | Tenant |  | Gains | Gains | Gains | Gains | Limits a lower role |
| 253 | Hostel | `HOSTEL_ATTENDANCE_MARK` | Mark hostel attendance | Tenant |  | Gains | Gains | Gains | Gains |  |
| 254 | Transport | `TRANSPORT_MANAGE` | Manage routes, vehicles, drivers and pickup points | Tenant |  | Gains | Gains | Gains | Gains |  |
| 255 | Transport | `DRIVER_ROUTE_VIEW` | See own route and student list | Tenant |  | Gains | Gains | Gains |  | Locked for everyone |
| 256 | Transport | `TRANSPORT_VIEW_OWN` | See route, driver contact and pickup point | Personal |  |  |  |  |  |  |
| 257 | Alumni | `GRADUATION_CONFIRM` | Confirm graduation (creates the alumni record) | Tenant |  | Gains | Gains | Gains |  | Decision E |
| 258 | Alumni | `ALUMNI_RECORD_MANAGE` | Manage alumni records and communications | Tenant |  | Gains | Gains | Gains |  |  |
| 259 | Research | `RESEARCH_RECORD_ADD` | Add research records and publications | Tenant |  | Gains | Gains | Gains |  |  |
| 260 | Research | `RESEARCH_RECORD_VERIFY` | Verify research records | Tenant |  | Gains | Gains | Gains |  |  |
| 261 | Research | `RESEARCH_GRANT_APPROVE` | Approve research and grant records | Tenant |  | Gains | Gains |  |  |  |
| 262 | API and integrations | `TENANT_INTEGRATION_MANAGE` | Manage the tenant's API keys and webhooks | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 263 | Exams | `EXAM_SCHEDULE_VIEW_OWN` | See own or own child's exam schedule and hall-ticket status | Personal |  |  |  |  |  |  |
| 264 | Timetable | `SYLLABUS_VIEW` | See the syllabus of own or own child's subjects | Tenant |  | Gains | Gains | Gains | Gains |  |
| 265 | Hostel | `HOSTEL_VIEW_OWN` | See own or own child's room, hostel attendance and mess fees | Personal |  |  |  |  |  |  |
| 266 | Leave | `SENIOR_LEAVE_APPROVE` | Approve the Registrar's or the Vice-Chancellor's own leave | Tenant |  | Has | Has |  |  | Locked for everyone |
| 267 | Platform | `PLATFORM_USER_MANAGE` | Create and deactivate platform staff accounts | Platform | Has |  |  |  |  | Locked for everyone |
| 268 | Users and roles | `TENANT_TOP_USERS_CREATE` | Create the Management, Data Protection Contact, Vice-Chancellor and Governing Body Approver accounts | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 269 | Institution setup | `PRINCIPAL_LEAVE_APPROVER_SET` | Set who approves the Principal's own leave | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 270 | Governance and data protection | `CONSENT_RECORD` | Record a parent's consent with its date | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 271 | Policies and documents | `UNIVERSITY_POLICY_APPROVE` | Approve or reject a university policy | Tenant |  | Gains | Has |  |  | Locked for everyone |
| 272 | Data and records | `STUDENT_RECORD_EDIT` | Edit an individual student record | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 273 | Data and records | `RECORD_CORRECTION_APPLY` | Apply an approved record correction | Tenant |  | Gains | Gains | Gains | Gains | Locked for everyone |
| 274 | Timetable | `SYLLABUS_MANAGE` | Create and maintain the syllabus of subjects | Tenant |  | Gains | Gains | Gains | Gains |  |
| 275 | Institution setup | `VC_LEAVE_APPROVER_SET` | Set who approves the Vice-Chancellor's own leave | Tenant |  | Gains | Gains |  |  | Locked for everyone |
| 276 | Reports and dashboards | `TENANT_OVERSIGHT_VIEW` | See every tenant area read-only (oversight) | Tenant |  | Has |  |  |  | Locked for everyone |
| 277 | Platform | `PLATFORM_REPORTS_VIEW` | See platform reports for own area | Platform | Gains |  |  |  |  | Locked for everyone |
| 278 | Communication | `INTEGRATION_SETTINGS_VIEW` | See SMS, email and meeting-tool settings and status (read-only) | Tenant |  | Gains | Gains | Gains |  | Locked for everyone |
