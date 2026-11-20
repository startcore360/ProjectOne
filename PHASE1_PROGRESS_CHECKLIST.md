# EDU 360 Phase 1: Feature Checklist and Progress

**Last updated:** 2026-11-13 | **Branch:** manoj_feature @ 90580ed (merged with sprint-2-development @ 7a0aedc; gates re-run by me on that commit) | **Source of stories:** Phase 1 backlog workbook and PDF (236 stories)
**Evidence basis:** checked against the code (controllers, services, routes, screens, tests), not against status trackers.

> **How to use this file:** tell Claude "update the tracker" and it re-checks the code and refreshes every table below.
> **Backend** = endpoint plus server-side permission check exist. **Frontend** = a screen or API service calls it. Yes = 1, Partial = 0.5, No = 0.
> These are structural scores. Rule-by-rule depth is only verified for the stories listed under "Confirmed gaps". Nothing is Accepted (only the Product Owner can accept).

## 1. Overall progress (236 stories)

| Layer | Score | % of Phase 1 |
|---|---:|---:|
| Backend | 72.5 of 236 | **30.7%** |
| Frontend | 69 of 236 | **29.2%** |
| Combined (average of both) | 70.8 of 236 | **30.0%** (upper bound) |
| Accepted by the Product Owner | 0 | 0.0% |

## 2. Progress by sprint

| Sprint | Dates | Stories | Backend % | Frontend % | Combined % |
|---|---|---:|---:|---:|---:|
| Sprint 1 | 3-9 Nov | 67 | 98.5% | 98.5% | 98.5% |
| Sprint 2 | 10-16 Nov | 23 | 8.7% | 0.0% | 4.3% |
| Sprint 3 | 17-23 Nov | 76 | 2.0% | 0.0% | 1.0% |
| Sprint 4 | 24-28 Nov | 70 | 4.3% | 4.3% | 4.3% |
| Sprint 5 (verification, UAT) | 29 Nov-3 Dec | n/a | not started | not started | not started |

## 3. Progress by module

| Sprint | Module | Stories | Backend % | Frontend % | Combined % |
|---|---|---:|---:|---:|---:|
| S1 | Sign-in and account | 7 | 92.9% | 92.9% | 92.9% |
| S1 | Users and roles | 17 | 100.0% | 100.0% | 100.0% |
| S1 | Institution setup | 22 | 97.7% | 97.7% | 97.7% |
| S2 | Timetable | 5 | 0.0% | 0.0% | 0.0% |
| S2 | Attendance | 10 | 20.0% | 0.0% | 10.0% |
| S3 | Exams | 20 | 0.0% | 0.0% | 0.0% |
| S3 | Results | 5 | 0.0% | 0.0% | 0.0% |
| S2 | Assignments and homework | 8 | 0.0% | 0.0% | 0.0% |
| S3 | Fees | 31 | 0.0% | 0.0% | 0.0% |
| S3 | Leave | 17 | 0.0% | 0.0% | 0.0% |
| S4 | Communication | 13 | 0.0% | 0.0% | 0.0% |
| S4 | Community and events | 8 | 0.0% | 0.0% | 0.0% |
| S4 | Complaints | 4 | 0.0% | 0.0% | 0.0% |
| S4 | Student notes | 5 | 0.0% | 0.0% | 0.0% |
| S4 | Policies and documents | 5 | 0.0% | 0.0% | 0.0% |
| S3 | Staff records | 3 | 50.0% | 0.0% | 25.0% |
| S4 | Risk monitoring | 4 | 0.0% | 0.0% | 0.0% |
| S4 | Reports and dashboards | 6 | 0.0% | 0.0% | 0.0% |
| S4 | Data and records | 9 | 0.0% | 0.0% | 0.0% |
| S4 | Governance and data protection | 16 | 18.8% | 18.8% | 18.8% |
| S1 | Platform | 21 | 100.0% | 100.0% | 100.0% |

## 4. Confirmed gaps (verified in code)

### Sprint 1
- [ ] `AUTH_LOGIN`: parent and applicant one-time-code login is only a disabled stub (returns 503, no sender, code storage or rate limits, no login option)
- [ ] `STRUCTURE_MANAGE`: still no PATCH or If-Match, no subject-group APIs; its 4 tests cover only `createDepartment`
- [x] `CALENDAR_LOCAL_SET`: exam day now exists as an event type (migration 30, backend and screen option); the conflict rule is not re-verified
- [x] `SUPPORT_TICKET_RAISE`, `SUPPORT_ACCESS_GRANT`: tenant Support page and `/support` route added
- [ ] All Sprint 1 tests are Mockito unit tests (no real database or row-level security); `*IT` tests are not part of `verify`
- [ ] Class teacher / mentor / HOD edge cases still not validated; student login own-data enforcement waits for endpoints
- [ ] Production tenant routing P1-P10 (`docker/PRODUCTION_TENANT_ROUTING.txt`)
- [ ] Remaining rule-by-rule verification of the other stories

### Sprint 2 (code state verified on sprint-2-development @ 7a0aedc; backend 892 tests, frontend 243 tests, lint and build pass, run by me)
- [x] Chunk 1 foundations: done and tested
- [~] Chunk 2 platform pieces: file service with owning-record policies, ClamAV scanner (off by default), in-app notifier, tenant permissions state, ActionGate, menu entries, self-service fix. The dependency CVE check and CI were not re-run by me
- [~] Chunk 3 People and live notifications, about half built:
  - [x] Student creation with enrolment (`POST /students`, `CreateStudentModal`), institution ID formats backend (migration 29), student-status ends enrolments
  - [x] Notifications: read state (migration 27), summary, mark-read and read-all endpoints, bell UI, plain WebSocket server and browser client
  - [x] STUDY_MATERIAL_VIEW action and grants (migration 28)
  - [~] Staff backend: profiles, assignments (including the end-assignment PATCH), service records, documents, verification
  - [ ] No automatic staff identity (`ensureStaffAssignment` missing) and no teacher lookup for the subject-allocation picker
  - [ ] No staff screens or routes; no ID-format fields in the institution forms; bulk student upload does not create enrolments
  - [ ] No `ACT_STAFF_*` tests; approval-count and permission-change live events are not published
- [~] Chunk 3b Sprint 1 repairs: support screens done, exam day done (backend); structure PATCH and subject groups not built; OTP login is a stub
- [ ] Chunks 4-8: timetable, syllabus, attendance, assignments, final sweep not started
- [ ] No Sprint 2 story (timetable, attendance, assignments) is built yet; the three staff stories are backend-only

### Before Phase 1 closes
- [ ] 1,112 action tests executed; 37 E2E scenarios; 404 vs 408 API operations reconciled
- [ ] Blocking decisions: OD-04, OD-07, OD-40, OD-03, OD-05a/b/c, OD-06

## 5. Story checklist (236 stories)

Status: `[x]` backend and frontend both present | `[~]` partly built | `[ ]` not started. Tests column applies to Sprint 1 only (the four named allow / deny / scope / guard tests).

### Sprint 1: Sign-in and account (7)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `AUTH_LOGIN`: 4/4 | Partial | Partial | 4/4 | parent / applicant one-time-code login missing |
| [x] | `AUTH_FIRST_LOGIN_RESET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `AUTH_PASSWORD_CHANGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `AUTH_DEVICES_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PROFILE_EDIT_OWN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLE_SWITCH`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SELF_SERVICE_VIEW`: 4/4 | Yes | Yes | 4/4 |  |

### Sprint 1: Users and roles (17)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [x] | `USER_VIEW`: differently named | Yes | Yes | differently named |  |
| [x] | `USER_CREATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `USER_BULK_UPLOAD`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `USER_EDIT`: differently named | Yes | Yes | differently named |  |
| [x] | `USER_DEACTIVATE`: differently named | Yes | Yes | differently named |  |
| [x] | `USER_PASSWORD_RESET_UNLOCK`: differently named | Yes | Yes | differently named |  |
| [x] | `ROLE_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLE_ASSIGN_NON_SENSITIVE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLE_CUSTOM_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `GUARDIAN_LINK`: differently named | Yes | Yes | differently named |  |
| [x] | `GUARDIAN_CHANGE_REQUEST`: differently named | Yes | Yes | differently named |  |
| [x] | `GUARDIAN_CHANGE_APPROVE`: 2/4 | Yes | Yes | 2/4 |  |
| [x] | `APPROVAL_DELEGATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `APPROVAL_BULK`: 2/4 | Yes | Yes | 2/4 |  |
| [x] | `TENANT_TOP_USERS_CREATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLLDOWN_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `ROLLDOWN_OVERRIDE_SET`: 4/4 | Yes | Yes | 4/4 |  |

### Sprint 1: Institution setup (22)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [x] | `INSTITUTION_CREATE_DRAFT`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INSTITUTION_ACTIVATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INSTITUTION_BLOCK_REQUEST`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INSTITUTION_BLOCK_APPROVE`: 4/4 | Yes | Yes | 4/4 |  |
| [~] | `STRUCTURE_MANAGE`: 4/4 | Partial | Partial | 4/4 | no PATCH / If-Match; subject groups missing; tests cover 1 operation |
| [x] | `PROGRAM_RULES_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `CALENDAR_MASTER_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `CALENDAR_LOCAL_SET`: 4/4 | Yes | Yes | 4/4 | exam day type added (migration 30, EXAM_DAY option); conflict rule not re-verified |
| [x] | `WEEKLY_OFF_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `GRADING_SCHEME_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `HOD_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SUBJECT_ALLOCATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `MENTOR_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `CLASS_TEACHER_ASSIGN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PROMOTION_RUN`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PROMOTION_APPROVE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `STUDENT_STATUS_CHANGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `STUDENT_LOGIN_SWITCH`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TERM_LOCK`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TERM_UNLOCK`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PRINCIPAL_LEAVE_APPROVER_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `VC_LEAVE_APPROVER_SET`: 4/4 | Yes | Yes | 4/4 |  |

### Sprint 2: Timetable (5)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `TIMETABLE_BUILD`: none | No | No | - |  |
| [ ] | `ONLINE_PERIOD_SET`: none | No | No | - |  |
| [ ] | `TIMETABLE_VIEW`: none | No | No | - |  |
| [ ] | `SYLLABUS_VIEW`: none | No | No | - |  |
| [ ] | `SYLLABUS_MANAGE`: none | No | No | - |  |

### Sprint 2: Attendance (10)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `ATTENDANCE_MARK`: none | No | No | - |  |
| [ ] | `ATTENDANCE_NOT_HELD`: none | No | No | - |  |
| [ ] | `SUBSTITUTE_ASSIGN`: none | No | No | - |  |
| [~] | `ATTENDANCE_CORRECTION_REQUEST`: none | Partial | No | - | approval routing only |
| [~] | `ATTENDANCE_CORRECTION_APPROVE`: none | Partial | No | - | approval routing only |
| [~] | `ATTENDANCE_BULK_UPLOAD`: none | Partial | No | - | approval routing only |
| [~] | `ATTENDANCE_BULK_UPLOAD_APPROVE`: none | Partial | No | - | approval routing only |
| [ ] | `ATTENDANCE_RULES_SET`: none | No | No | - |  |
| [ ] | `ATTENDANCE_VIEW`: none | No | No | - |  |
| [ ] | `ATTENDANCE_VIEW_OWN`: none | No | No | - |  |

### Sprint 3: Exams (20)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `EXAM_RULES_SET`: none | No | No | - |  |
| [ ] | `EXAM_PATTERN_SET`: none | No | No | - |  |
| [ ] | `EXAM_SCHEDULE_UNIVERSITY`: none | No | No | - |  |
| [ ] | `EXAM_SCHEDULE_INTERNAL`: none | No | No | - |  |
| [ ] | `EXAM_SCHEDULE_APPROVE`: none | No | No | - |  |
| [ ] | `HALL_TICKET_CHECKS_SET`: none | No | No | - |  |
| [ ] | `HALL_TICKET_GENERATE`: none | No | No | - |  |
| [ ] | `HALL_TICKET_OVERRIDE`: none | No | No | - |  |
| [ ] | `HALL_TICKET_DOWNLOAD`: none | No | No | - |  |
| [ ] | `MARKS_ENTER`: none | No | No | - |  |
| [ ] | `MARKS_EDIT_DRAFT`: none | No | No | - |  |
| [ ] | `MARKS_SUBMIT`: none | No | No | - |  |
| [ ] | `MARKS_EXTERNAL_UPLOAD`: none | No | No | - |  |
| [ ] | `MARKS_APPROVE_HOD`: none | No | No | - |  |
| [ ] | `MARKS_REVIEW_CLASS_TEACHER`: none | No | No | - |  |
| [ ] | `MARKS_APPROVE_PRINCIPAL`: none | No | No | - |  |
| [ ] | `SUPPLEMENTARY_EXAM_MANAGE`: none | No | No | - |  |
| [ ] | `SUPPLEMENTARY_REGISTER`: none | No | No | - |  |
| [ ] | `EXAM_VIEW`: none | No | No | - |  |
| [ ] | `EXAM_SCHEDULE_VIEW_OWN`: none | No | No | - |  |

### Sprint 3: Results (5)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `RESULT_PUBLISH`: none | No | No | - |  |
| [ ] | `RESULT_WITHDRAW`: none | No | No | - |  |
| [ ] | `RESULT_WITHDRAW_APPROVE`: none | No | No | - |  |
| [ ] | `RESULT_VIEW`: none | No | No | - |  |
| [ ] | `RESULT_VIEW_OWN`: none | No | No | - |  |

### Sprint 2: Assignments and homework (8)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `ASSIGNMENT_CREATE`: none | No | No | - |  |
| [ ] | `ASSIGNMENT_CLOSE`: none | No | No | - |  |
| [ ] | `STUDY_MATERIAL_UPLOAD`: none | No | No | - |  |
| [ ] | `ASSIGNMENT_SUBMIT`: none | No | No | - |  |
| [ ] | `ASSIGNMENT_EVALUATE`: none | No | No | - |  |
| [ ] | `HOMEWORK_STATUS_MARK`: none | No | No | - |  |
| [ ] | `ASSIGNMENT_VIEW_OWN`: none | No | No | - |  |
| [ ] | `HOMEWORK_COMPLIANCE_VIEW`: none | No | No | - |  |

### Sprint 3: Fees (31)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `FEE_POLICY_SET`: none | No | No | - |  |
| [ ] | `FEE_POLICY_DATA_ENTER`: none | No | No | - |  |
| [ ] | `FEE_STRUCTURE_CREATE`: none | No | No | - |  |
| [ ] | `FEE_STRUCTURE_APPROVE`: none | No | No | - |  |
| [ ] | `FEE_EXCEPTION_REQUEST`: none | No | No | - |  |
| [ ] | `FEE_EXCEPTION_APPROVE_FIRST`: none | No | No | - |  |
| [ ] | `FEE_EXCEPTION_APPROVE_PRINCIPAL`: none | No | No | - |  |
| [ ] | `INSTALLMENT_SET`: none | No | No | - |  |
| [ ] | `SCHOLARSHIP_MANAGE`: none | No | No | - |  |
| [ ] | `SCHOLARSHIP_LIST_APPROVE`: none | No | No | - |  |
| [ ] | `CONCESSION_REQUEST`: none | No | No | - |  |
| [ ] | `CONCESSION_APPROVE`: none | No | No | - |  |
| [ ] | `LATE_FEE_RULES_SET`: none | No | No | - |  |
| [ ] | `LATE_FEE_WAIVER_REQUEST`: none | No | No | - |  |
| [ ] | `LATE_FEE_WAIVER_APPROVE`: none | No | No | - |  |
| [ ] | `FEE_PAY_ONLINE`: none | No | No | - |  |
| [ ] | `PAYMENT_OFFLINE_RECORD`: none | No | No | - |  |
| [ ] | `PAYMENT_MARK_PAID_MANUAL`: none | No | No | - |  |
| [ ] | `RECEIPT_DOWNLOAD`: none | No | No | - |  |
| [ ] | `RECEIPT_CANCEL_REQUEST`: none | No | No | - |  |
| [ ] | `RECEIPT_CANCEL_APPROVE`: none | No | No | - |  |
| [ ] | `PERIOD_LOCK`: none | No | No | - |  |
| [ ] | `PERIOD_UNLOCK_REQUEST`: none | No | No | - |  |
| [ ] | `PERIOD_UNLOCK_APPROVE`: none | No | No | - |  |
| [ ] | `FEE_REMINDER_SEND`: none | No | No | - |  |
| [ ] | `NO_DUES_OVERRIDE`: none | No | No | - |  |
| [ ] | `LEDGER_VIEW`: none | No | No | - |  |
| [ ] | `DUES_STATUS_VIEW`: none | No | No | - |  |
| [ ] | `DAILY_COLLECTION_REPORT_VIEW`: none | No | No | - |  |
| [ ] | `TALLY_EXPORT`: none | No | No | - |  |
| [ ] | `FEE_VIEW_OWN`: none | No | No | - |  |

### Sprint 3: Leave (17)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `LEAVE_TYPES_SET`: none | No | No | - |  |
| [ ] | `STUDENT_LEAVE_APPLY`: none | No | No | - |  |
| [ ] | `STUDENT_LEAVE_APPROVE_MENTOR`: none | No | No | - |  |
| [ ] | `STUDENT_LEAVE_APPROVE_HOD`: none | No | No | - |  |
| [ ] | `STUDENT_LEAVE_APPROVE_CT`: none | No | No | - |  |
| [ ] | `STUDENT_LEAVE_APPROVE_PRINCIPAL`: none | No | No | - |  |
| [ ] | `STUDENT_LEAVE_VIEW`: none | No | No | - |  |
| [ ] | `LEAVE_CANCEL_OWN`: none | No | No | - |  |
| [ ] | `STAFF_LEAVE_APPLY`: none | No | No | - |  |
| [ ] | `STAFF_LEAVE_RECOMMEND_HOD`: none | No | No | - |  |
| [ ] | `STAFF_LEAVE_VERIFY_HR`: none | No | No | - |  |
| [ ] | `STAFF_LEAVE_APPROVE_PRINCIPAL`: none | No | No | - |  |
| [ ] | `STAFF_LEAVE_APPROVE_REGISTRAR`: none | No | No | - |  |
| [ ] | `PRINCIPAL_LEAVE_APPROVE`: none | No | No | - |  |
| [ ] | `LEAVE_BALANCE_CREDIT`: none | No | No | - |  |
| [ ] | `LOSS_OF_PAY_CONFIRM`: none | No | No | - |  |
| [ ] | `SENIOR_LEAVE_APPROVE`: none | No | No | - |  |

### Sprint 4: Communication (13)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `NOTICE_PUBLISH_INSTITUTION`: none | No | No | - |  |
| [ ] | `NOTICE_PUBLISH_UNIVERSITY`: none | No | No | - |  |
| [ ] | `NOTICE_PUBLISH_CLASS_DEPT`: none | No | No | - |  |
| [ ] | `NOTICE_SUBMIT`: none | No | No | - |  |
| [ ] | `NOTICE_APPROVE`: none | No | No | - |  |
| [ ] | `NOTICE_EMERGENCY`: none | No | No | - |  |
| [ ] | `MESSAGE_SEND`: none | No | No | - |  |
| [ ] | `MESSAGE_OFFICE_HANDLE`: none | No | No | - |  |
| [ ] | `CLASS_BROADCAST`: none | No | No | - |  |
| [ ] | `NOTIFICATION_OPTIONS_SET`: none | No | No | - |  |
| [ ] | `DELIVERY_FAILURES_VIEW`: none | No | No | - |  |
| [ ] | `SMS_EMAIL_SETUP`: none | No | No | - |  |
| [ ] | `INTEGRATION_SETTINGS_VIEW`: none | No | No | - |  |

### Sprint 4: Community and events (8)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `COMMUNITY_POST_DIRECT`: none | No | No | - |  |
| [ ] | `COMMUNITY_POST_SUBMIT`: none | No | No | - |  |
| [ ] | `COMMUNITY_POST_APPROVE`: none | No | No | - |  |
| [ ] | `COMMUNITY_LIKE_COMMENT`: none | No | No | - |  |
| [ ] | `COMMUNITY_COMMENT_HIDE`: none | No | No | - |  |
| [ ] | `EVENT_PROPOSE`: none | No | No | - |  |
| [ ] | `EVENT_APPROVE`: none | No | No | - |  |
| [ ] | `EVENT_RSVP`: none | No | No | - |  |

### Sprint 4: Complaints (4)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `COMPLAINT_RAISE`: none | No | No | - |  |
| [ ] | `COMPLAINT_ASSIGN`: none | No | No | - |  |
| [ ] | `COMPLAINT_RESOLVE`: none | No | No | - |  |
| [ ] | `COMPLAINT_ESCALATED_VIEW`: none | No | No | - |  |

### Sprint 4: Student notes (5)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `NOTE_ADD`: none | No | No | - |  |
| [ ] | `NOTE_CORRECT`: none | No | No | - |  |
| [ ] | `NOTE_VIEW_DISCIPLINE`: none | No | No | - |  |
| [ ] | `NOTE_VIEW_HEALTH`: none | No | No | - |  |
| [ ] | `HEALTH_NOTES_SWITCH`: none | No | No | - |  |

### Sprint 4: Policies and documents (5)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `POLICY_DRAFT`: none | No | No | - |  |
| [ ] | `POLICY_PUBLISH`: none | No | No | - |  |
| [ ] | `POLICY_ACCEPT`: none | No | No | - |  |
| [ ] | `DEPT_DOCUMENT_UPLOAD`: none | No | No | - |  |
| [ ] | `UNIVERSITY_POLICY_APPROVE`: none | No | No | - |  |

### Sprint 3: Staff records (3)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [~] | `STAFF_RECORD_MANAGE`: none | Partial | No | - | backend only: staff, assignments, service records, documents; no screens, no ACT tests, no auto staff identity |
| [~] | `STAFF_DOCUMENT_VERIFY`: none | Partial | No | - | backend only (verification endpoint); no screen, no ACT tests |
| [~] | `STAFF_RECORD_VIEW`: none | Partial | No | - | backend only (list and detail); no screens, no ACT tests |

### Sprint 4: Risk monitoring (4)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `RISK_THRESHOLDS_SET`: none | No | No | - |  |
| [ ] | `RISK_LIST_VIEW`: none | No | No | - |  |
| [ ] | `RISK_REMARK_ADD`: none | No | No | - |  |
| [ ] | `RISK_VIEW_OWN`: none | No | No | - |  |

### Sprint 4: Reports and dashboards (6)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `REPORTS_VIEW`: none | No | No | - |  |
| [ ] | `BUYER_DASHBOARD_VIEW`: none | No | No | - |  |
| [ ] | `USAGE_ANALYTICS_VIEW`: none | No | No | - |  |
| [ ] | `STATUTORY_EXPORT`: none | No | No | - |  |
| [ ] | `ID_CARD_EXPORT`: none | No | No | - |  |
| [ ] | `TENANT_OVERSIGHT_VIEW`: none | No | No | - |  |

### Sprint 4: Data and records (9)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `DATA_IMPORT`: none | No | No | - |  |
| [ ] | `BULK_CORRECT`: none | No | No | - |  |
| [ ] | `STUDENT_SEARCH`: none | No | No | - |  |
| [ ] | `STUDENT_RECORD_VIEW_OWN`: none | No | No | - |  |
| [ ] | `CERTIFICATE_REQUEST`: none | No | No | - |  |
| [ ] | `CERTIFICATE_APPROVE`: none | No | No | - |  |
| [ ] | `CERTIFICATE_PREPARE`: none | No | No | - |  |
| [ ] | `STUDENT_RECORD_EDIT`: none | No | No | - |  |
| [ ] | `RECORD_CORRECTION_APPLY`: none | No | No | - |  |

### Sprint 4: Governance and data protection (16)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [ ] | `AUDIT_LOG_VIEW`: none | No | No | - |  |
| [ ] | `AUDIT_LOG_EXPORT`: none | No | No | - |  |
| [ ] | `AUDIT_FINDING_RECORD`: none | No | No | - |  |
| [ ] | `COMPLIANCE_REPORT_MANAGE`: none | No | No | - |  |
| [ ] | `ACCREDITATION_EVIDENCE_MAP`: none | No | No | - |  |
| [ ] | `GOVERNANCE_ITEM_APPROVE`: none | No | No | - |  |
| [ ] | `DATA_REQUEST_RAISE`: none | No | No | - |  |
| [ ] | `DATA_REQUEST_HANDLE`: none | No | No | - |  |
| [~] | `TENANT_EXPORT_REQUEST`: none | Partial | Partial | - | platform side only |
| [~] | `TENANT_EXPORT_APPROVE`: none | Partial | Partial | - | platform side only |
| [~] | `TENANT_DELETION_REQUEST`: none | Partial | Partial | - | platform side only |
| [~] | `TENANT_DELETION_CONFIRM`: none | Partial | Partial | - | platform side only |
| [~] | `TENANT_DELETION_APPROVE`: none | Partial | Partial | - | platform side only |
| [~] | `RETENTION_CHANGE_APPROVE`: none | Partial | Partial | - | platform side only |
| [ ] | `RBAC_MAPPING_CHECK`: none | No | No | - |  |
| [ ] | `CONSENT_RECORD`: none | No | No | - |  |

### Sprint 1: Platform (21)

| Done | Story | Backend | Frontend | Tests | Note |
|:--:|---|:--:|:--:|:--:|---|
| [x] | `PLAN_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_CREATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_ACTIVATE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_SUSPEND_ARCHIVE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `TENANT_SETTINGS`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `FEATURE_TOGGLE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INVOICE_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `DISCOUNT_APPLY`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `OVERDUE_FLAG`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SUPPORT_TICKET_RAISE`: 4/4 | Yes | Yes | 4/4 | tenant Support page added (/support) |
| [x] | `SUPPORT_TICKET_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SUPPORT_ACCESS_GRANT`: 4/4 | Yes | Yes | 4/4 | tenant Support page added (/support) |
| [x] | `SUPPORT_ACCESS_USE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `RELEASE_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `INTEGRATION_TYPES_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PLATFORM_AUDIT_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `USAGE_COUNTS_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `SECURITY_POLICY_SET`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `BACKUP_STATUS_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PLATFORM_USER_MANAGE`: 4/4 | Yes | Yes | 4/4 |  |
| [x] | `PLATFORM_REPORTS_VIEW`: 4/4 | Yes | Yes | 4/4 |  |
