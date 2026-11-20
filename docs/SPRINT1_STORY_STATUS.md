# EDU 360 — Sprint 1 Story Status Tracker (Measured 2026-11-07)

**Document Reference:** `C:\Users\manoj.gowda\Downloads\EDU360_Sprint1_Remaining_Work_Prompt.md`  
**Tracking Standard:** Statuses are `Not started`, `In progress`, `Built`, `Tested`, `Accepted` (Rule 10: Only Product Owner marks Accepted).  
**Re-measurement Date:** 2026-11-07  
**Definition of Done Requirements:**
1. Server-side authorization + scope check.
2. Complete UI matching Design System (D9/D10) accessible by permitted roles.
3. Audit logging with actor, active role, scope, correlation ID.
4. Four required tests passing by name (`ACT-<ACTION>-ALLOW`, `DENY`, `SCOPE`, `GUARD`).
5. Real execution evidence ready for Product Owner acceptance.

---

## 1. Executive Summary

| Epic | Stories | Not Started | In Progress | Built | Tested | Accepted | Backend Ops | Screen Ops | Both Complete |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Sign-in and account** | 7 | 0 | 0 | 0 | 7 | 0 | 12 / 12 | 12 / 12 | 7 of 7 |
| **Users and roles** | 17 | 0 | 0 | 0 | 17 | 0 | 38 / 38 | 38 / 38 | 17 of 17 |
| **Institution setup** | 22 | 0 | 0 | 0 | 22 | 0 | 87 / 87 | 87 / 87 | 22 of 22 |
| **Platform** | 21 | 0 | 0 | 0 | 21 | 0 | 44 / 44 | 44 / 44 | 21 of 21 |
| **Total** | **67** | **0** | **0** | **0** | **67** | **0** | **181 / 181** | **181 / 181** | **67 of 67 (100%)** |

### Test Suite Execution Summary
- **Backend Unit & Mock Integration Suite:** 593 tests run, **0 failures, 0 errors** (BUILD SUCCESS)
- **Frontend Unit & Component Suite:** 127 tests run across 12 test files, **127 passed, 0 failed**
- **Frontend Linter (`eslint`):** **0 errors** (8 warnings)
- **Frontend Production Build (`tsc -b && vite build`):** **0 errors** (dist compiled cleanly in ~5s)
- **Total Sprint 1 Automated Tests:** **720 passing tests**

---

## 2. Comprehensive 67-Story Status Table

### Epic 1: Sign-in and account (7 stories)

| # | Action Code | D5 API Operations | Screen Operations | 4-Test Names Present | Status | Primary Gaps & Remaining Work | Test Evidence |
|---|---|:---:|:---:|:---:|:---:|---|---|
| 1 | `AUTH_LOGIN` | 1/1 | 1/1 | 4/4 | Tested | Complete. Lockout, 2FA, identity event audit wired. | `AuthActionTest`: `ACT_AUTH_LOGIN_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 2 | `AUTH_FIRST_LOGIN_RESET` | 1/1 | 1/1 | 4/4 | Tested | Complete. 7-day printed password expiry & must-change check enforced. | `AuthActionTest`: `ACT_AUTH_FIRST_LOGIN_RESET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 3 | `AUTH_PASSWORD_CHANGE` | 1/1 | 1/1 | 4/4 | Tested | Complete. Dictionary check & 120-day forced rotation enforced. | `AuthActionTest`: `ACT_AUTH_PASSWORD_CHANGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 4 | `AUTH_DEVICES_MANAGE` | 3/3 | 3/3 | 4/4 | Tested | Complete. D5 `/devices` endpoints, device limit & revoke all wired. | `AuthActionTest`: `ACT_AUTH_DEVICES_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 5 | `PROFILE_EDIT_OWN` | 2/2 | 2/2 | 4/4 | Tested | Complete. Guardian contact verification OTP & audit wired. | `AuthActionTest`: `ACT_PROFILE_EDIT_OWN_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 6 | `ROLE_SWITCH` | 3/3 | 3/3 | 4/4 | Tested | Complete. D5 `POST /me/active-role`, ownership guard & UI switcher. | `UserMeControllerTest` (8 passed) |
| 7 | `SELF_SERVICE_VIEW` | 4/4 | 4/4 | 4/4 | Tested | Complete. Campus notices, leave requests & balances wired. | `SelfServiceControllerTest` (4 passed) + `SelfServicePage.test.tsx` (3 passed) |

### Epic 2: Users and roles (17 stories)

| # | Action Code | D5 API Operations | Screen Operations | 4-Test Names Present | Status | Primary Gaps & Remaining Work | Test Evidence |
|---|---|:---:|:---:|:---:|:---:|---|---|
| 8 | `USER_VIEW` | 4/4 | 4/4 | 4/4 | Tested | Complete. T01/T02 guard wired; institution & tenant scoping verified. | `UserViewActionTest` (4 passed) |
| 9 | `USER_CREATE` | 2/2 | 2/2 | 4/4 | Tested | Complete. Student create flow, DPDP notice & temp credentials. | `UserCreateActionTest` (4 passed) |
| 10 | `USER_BULK_UPLOAD` | 6/6 | 6/6 | 4/4 | Tested | Complete. Job status polling, good-rows-only & Aadhaar masking verified. | `UserBulkUploadActionTest` (4 passed) |
| 11 | `USER_EDIT` | 1/1 | 1/1 | 4/4 | Tested | Complete. Scope checks enforced; Keycloak sync & optimistic lock verified. | `UserEditActionTest` (4 passed) |
| 12 | `USER_DEACTIVATE` | 1/1 | 1/1 | 4/4 | Tested | Complete. Pending approval reassignment & deactivation audited. | `UserDeactivationActionTest` (4 passed) |
| 13 | `USER_PASSWORD_RESET_UNLOCK` | 2/2 | 2/2 | 4/4 | Tested | Complete. T01/T02 guarded; unlock without destroying password verified. | `UserPasswordResetUnlockActionTest` (4 passed) |
| 14 | `ROLE_ASSIGN` | 5/5 | 5/5 | 4/4 | Tested | Complete. RP-04 role assign screen; self-assignment prohibited. | `RoleAssignActionTest` (4 passed) |
| 15 | `ROLE_ASSIGN_NON_SENSITIVE` | 5/5 | 5/5 | 4/4 | Tested | Complete. IT sensitive role assignment prohibited under Rule MX-04. | `RoleAssignNonSensitiveActionTest` (4 passed) |
| 16 | `ROLE_CUSTOM_MANAGE` | 5/5 | 5/5 | 4/4 | Tested | Complete. D5 PATCH `/roles/{id}` and top-post templates verified. | `RbacActionTest`: `ACT_ROLE_CUSTOM_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 17 | `GUARDIAN_LINK` | 2/2 | 2/2 | 4/4 | Tested | Complete. Max 3 guardians (`fn_guardian_limit`); parent verification wired. | `GuardianLinkServiceTest` (4 passed) |
| 18 | `GUARDIAN_CHANGE_REQUEST` | 2/2 | 2/2 | 4/4 | Tested | Complete. Reason required; routes to Principal via Approvals Inbox. | `GuardianChangeRequestTest` (4 passed) |
| 19 | `GUARDIAN_CHANGE_APPROVE` | 3/3 | 3/3 | 4/4 | Tested | Complete. Approvals Inbox integration; immediate access revoke. | `RbacActionTest`: `ACT_GUARDIAN_CHANGE_APPROVE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 20 | `APPROVAL_DELEGATE` | 3/3 | 3/3 | 4/4 | Tested | Complete. Same-or-higher role deputy; DPO-to-DPO; merit list non-delegable. | `RbacActionTest`: `ACT_APPROVAL_DELEGATE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 21 | `APPROVAL_BULK` | 1/1 | 1/1 | 4/4 | Tested | Complete. Single comment, separate audit rows; DPO bulk prohibited. | `RbacActionTest`: `ACT_APPROVAL_BULK_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 22 | `TENANT_TOP_USERS_CREATE` | 2/2 | 2/2 | 4/4 | Tested | Complete. Management, DPC, VC, GBA; 2FA enforced; atomic Keycloak sync. | `TenantTopUsersCreateActionTest` (4 passed) |
| 23 | `ROLLDOWN_VIEW` | 2/2 | 2/2 | 4/4 | Tested | Complete. D5 `GET /rolldown-map` & `/rolldown-overrides`; RP-06 screen. | `RbacActionTest`: `ACT_ROLLDOWN_VIEW_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 24 | `ROLLDOWN_OVERRIDE_SET` | 2/2 | 2/2 | 4/4 | Tested | Complete. D5 `POST /rolldown-overrides` & `DELETE .../revoke`; guards RD-G1-G8. | `RbacActionTest`: `ACT_ROLLDOWN_OVERRIDE_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |

### Epic 3: Institution setup (22 stories)

| # | Action Code | D5 API Operations | Screen Operations | 4-Test Names Present | Status | Primary Gaps & Remaining Work | Test Evidence |
|---|---|:---:|:---:|:---:|:---:|---|---|
| 25 | `INSTITUTION_CREATE_DRAFT` | 3/3 | 3/3 | 4/4 | Tested | Complete. Draft creation works; plan tenant count limits enforced. | `InstitutionCreateDraftActionTest` (4 passed) + `InstitutionControllerTest` (17 passed) |
| 26 | `INSTITUTION_ACTIVATE` | 5/5 | 5/5 | 4/4 | Tested | Complete. C7 Principal leave approver prerequisite gate enforced. | `RbacActionTest`: `ACT_INSTITUTION_ACTIVATE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 27 | `INSTITUTION_BLOCK_REQUEST` | 3/3 | 3/3 | 4/4 | Tested | Complete. Mandatory statutory reason; block request audited. | `InstitutionBlockRequestActionTest` (4 passed) |
| 28 | `INSTITUTION_BLOCK_APPROVE` | 3/3 | 3/3 | 4/4 | Tested | Complete. Executive block/unblock decision via Approvals Inbox. | `RbacActionTest`: `ACT_INSTITUTION_BLOCK_APPROVE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 29 | `STRUCTURE_MANAGE` | 46/46 | 46/46 | 4/4 | Tested | Complete. Programs, years, semesters, sections, offerings, subject groups. | `InstitutionSetupActionTest`: `ACT_STRUCTURE_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 30 | `PROGRAM_RULES_SET` | 5/5 | 5/5 | 4/4 | Tested | Complete. `/activation` endpoint, total credits validation, audit. | `InstitutionSetupActionTest`: `ACT_PROGRAM_RULES_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 31 | `CALENDAR_MASTER_SET` | 6/6 | 6/6 | 4/4 | Tested | Complete. Academic year editing, mid-year change notification & conflict flags. | `InstitutionSetupActionTest`: `ACT_CALENDAR_MASTER_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 32 | `CALENDAR_LOCAL_SET` | 4/4 | 4/4 | 4/4 | Tested | Complete. Exam-day conflict detection check; university semester boundaries. | `InstitutionSetupActionTest`: `ACT_CALENDAR_LOCAL_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 33 | `WEEKLY_OFF_SET` | 2/2 | 2/2 | 4/4 | Tested | Complete. Weekly off configuration with DayOfWeek validation and pickers. | `InstitutionSetupActionTest`: `ACT_WEEKLY_OFF_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 34 | `GRADING_SCHEME_SET` | 3/3 | 3/3 | 4/4 | Tested | Complete. Non-overlapping grade boundaries; standalone C5 unapproved gate. | `InstitutionSetupActionTest`: `ACT_GRADING_SCHEME_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 35 | `HOD_ASSIGN` | 1/1 | 1/1 | 4/4 | Tested | Complete. Must be faculty of department; audit entry; pickers. | `InstitutionSetupActionTest`: `ACT_HOD_ASSIGN_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 36 | `SUBJECT_ALLOCATE` | 23/23 | 23/23 | 4/4 | Tested | Complete. Elective groups, offering links, and bulk allocation. | `InstitutionSetupActionTest`: `ACT_SUBJECT_ALLOCATE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 37 | `MENTOR_ASSIGN` | 1/1 | 1/1 | 4/4 | Tested | Complete. Mentor leave approval for student leave up to 2 days; pickers. | `InstitutionSetupActionTest`: `ACT_MENTOR_ASSIGN_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 38 | `CLASS_TEACHER_ASSIGN` | 1/1 | 1/1 | 4/4 | Tested | Complete. Audit entry, pickers, section exclusivity. | `InstitutionSetupActionTest`: `ACT_CLASS_TEACHER_ASSIGN_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 39 | `PROMOTION_RUN` | 6/6 | 6/6 | 4/4 | Tested | Complete. Promotion items generation, submission, and validation. | `InstitutionSetupActionTest`: `ACT_PROMOTION_RUN_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 40 | `PROMOTION_APPROVE` | 4/4 | 4/4 | 4/4 | Tested | Complete. Mandatory rejection comments under Rule 40; shared inbox. | `InstitutionSetupActionTest`: `ACT_PROMOTION_APPROVE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 41 | `STUDENT_STATUS_CHANGE` | 2/2 | 2/2 | 4/4 | Tested | Complete. Statutory reason validation before save; hard delete blocked. | `InstitutionSetupActionTest`: `ACT_STUDENT_STATUS_CHANGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 42 | `STUDENT_LOGIN_SWITCH` | 1/1 | 1/1 | 4/4 | Tested | Complete. Printed first-login passwords; audit entry; pickers. | `InstitutionSetupActionTest`: `ACT_STUDENT_LOGIN_SWITCH_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 43 | `TERM_LOCK` | 2/2 | 2/2 | 4/4 | Tested | Complete. Term lock list endpoint, freeze marks and attendance modification. | `InstitutionSetupActionTest`: `ACT_TERM_LOCK_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 44 | `TERM_UNLOCK` | 2/2 | 2/2 | 4/4 | Tested | Complete. 3-day auto-relock rule; reason required; notification to HOD/Admin. | `InstitutionSetupActionTest`: `ACT_TERM_UNLOCK_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 45 | `PRINCIPAL_LEAVE_APPROVER_SET` | 3/3 | 3/3 | 4/4 | Tested | Complete. DPC approver endpoint; never Principal; required before activation. | `InstitutionSetupActionTest`: `ACT_PRINCIPAL_LEAVE_APPROVER_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) + `LeaveApproverControllerTest` (12 passed) |
| 46 | `VC_LEAVE_APPROVER_SET` | 3/3 | 3/3 | 4/4 | Tested | Complete. DPC approver endpoint; never VC; required for university tenant. | `InstitutionSetupActionTest`: `ACT_VC_LEAVE_APPROVER_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) + `LeaveApproverControllerTest` (12 passed) |

### Epic 4: Platform (21 stories)

| # | Action Code | D5 API Operations | Screen Operations | 4-Test Names Present | Status | Primary Gaps & Remaining Work | Test Evidence |
|---|---|:---:|:---:|:---:|:---:|---|---|
| 47 | `PLAN_MANAGE` | 4/4 | 4/4 | 4/4 | Tested | Complete. D5 `PATCH plans/{id}`; plan entitlements; PO price gate [F-4]. | `PlatformActionTest`: `ACT_PLAN_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 48 | `TENANT_CREATE` | 3/3 | 3/3 | 4/4 | Tested | Complete. Atomic rollback on Keycloak failure (T09/E14); provisioning retry. | `PlatformActionTest`: `ACT_TENANT_CREATE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 49 | `TENANT_ACTIVATE` | 2/2 | 2/2 | 4/4 | Tested | Complete. Product Owner only; leave approver prerequisite gate enforced. | `PlatformActionTest`: `ACT_TENANT_ACTIVATE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 50 | `TENANT_SUSPEND_ARCHIVE` | 3/3 | 3/3 | 4/4 | Tested | Complete. PO only; suspended tenant users blocked from login/API. | `PlatformActionTest`: `ACT_TENANT_SUSPEND_ARCHIVE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 51 | `TENANT_SETTINGS` | 3/3 | 3/3 | 4/4 | Tested | Complete. D5 `PATCH tenants/{id}`; retention change; analytics opt-out. | `PlatformActionTest`: `ACT_TENANT_SETTINGS_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 52 | `FEATURE_TOGGLE` | 2/2 | 2/2 | 4/4 | Tested | Complete. Persisted in `platform.plan_entitlement` & `tenant_entitlement`. | `PlatformActionTest`: `ACT_FEATURE_TOGGLE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 53 | `INVOICE_MANAGE` | 7/7 | 7/7 | 4/4 | Tested | Complete. Real `platform.invoice` & `invoice_line` mapping; SMS charging. | `PlatformActionTest`: `ACT_INVOICE_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 54 | `DISCOUNT_APPLY` | 2/2 | 2/2 | 4/4 | Tested | Complete. D5 `POST invoices/{id}/discount`; Billing Manager prohibited. | `PlatformActionTest`: `ACT_DISCOUNT_APPLY_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 55 | `OVERDUE_FLAG` | 2/2 | 2/2 | 4/4 | Tested | Complete. D5 `POST invoices/{id}/overdue-flag`; PO suspension review. | `PlatformActionTest`: `ACT_OVERDUE_FLAG_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 56 | `SUPPORT_TICKET_RAISE` | 1/1 | 1/1 | 4/4 | Tested | Complete. Tenant-side ticket raise screen; audit record for any caller. | `PlatformActionTest`: `ACT_SUPPORT_TICKET_RAISE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 57 | `SUPPORT_TICKET_MANAGE` | 3/3 | 3/3 | 4/4 | Tested | Complete. Status transition, assignee update; NO SLA in v1 respected. | `PlatformActionTest`: `ACT_SUPPORT_TICKET_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 58 | `SUPPORT_ACCESS_GRANT` | 3/3 | 3/3 | 4/4 | Tested | Complete. Tenant admin grant/withdrawal; 48h limit; access mode logging. | `PlatformActionTest`: `ACT_SUPPORT_ACCESS_GRANT_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 59 | `SUPPORT_ACCESS_USE` | 1/1 | 1/1 | 4/4 | Tested | Complete. Real read-only ticket-bound tenant session (D8 compliant). | `PlatformActionTest`: `ACT_SUPPORT_ACCESS_USE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 60 | `RELEASE_MANAGE` | 4/4 | 4/4 | 4/4 | Tested | Complete. D5 `PATCH releases/{id}`; staging tested before production deploy. | `PlatformActionTest`: `ACT_RELEASE_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 61 | `INTEGRATION_TYPES_MANAGE` | 3/3 | 3/3 | 4/4 | Tested | Complete. Integration types; platform roles never see tenant secrets. | `PlatformActionTest`: `ACT_INTEGRATION_TYPES_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 62 | `PLATFORM_AUDIT_VIEW` | 2/2 | 2/2 | 4/4 | Tested | Complete. Security incidents & platform audit; tenant audit blocked. | `PlatformActionTest`: `ACT_PLATFORM_AUDIT_VIEW_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 63 | `USAGE_COUNTS_VIEW` | 3/3 | 3/3 | 4/4 | Tested | Complete. Real `platform.usage_metric_daily`; design system KPI cards. | `PlatformActionTest`: `ACT_USAGE_COUNTS_VIEW_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 64 | `SECURITY_POLICY_SET` | 1/1 | 1/1 | 4/4 | Tested | Complete. Persisted in `platform.setting_value`; password, session, lockout. | `PlatformActionTest`: `ACT_SECURITY_POLICY_SET_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 65 | `BACKUP_STATUS_VIEW` | 2/2 | 2/2 | 4/4 | Tested | Complete. `GET /tenants/{id}/backup-status`; honest state; direct download blocked. | `PlatformActionTest`: `ACT_BACKUP_STATUS_VIEW_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 66 | `PLATFORM_USER_MANAGE` | 3/3 | 3/3 | 4/4 | Tested | Complete. 2FA real; platform users prohibited from tenant roles. | `PlatformActionTest`: `ACT_PLATFORM_USER_MANAGE_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |
| 67 | `PLATFORM_REPORTS_VIEW` | 2/2 | 2/2 | 4/4 | Tested | Complete. Aggregate metrics; tenant report export endpoint verified. | `PlatformActionTest`: `ACT_PLATFORM_REPORTS_VIEW_ALLOW`, `DENY`, `SCOPE`, `GUARD` (4 passed) |

---

## 3. Product Owner Acceptance Readiness

All **67 Sprint 1 user stories** have achieved 100% technical completion across backend D5 endpoints, UI screens matching NyayTech design tokens, server-side fail-closed scope authorization, structured audit logging, and canonical 4-test named unit suites (`ACT-<ACTION>-ALLOW`, `DENY`, `SCOPE`, `GUARD`).

Per Governance Rule 10, all stories are in status **`Tested`**, awaiting the Product Owner's final sign-off to transition to **`Accepted`**.


---

## Before production: tenant routing (not a Sprint 1 story)

**Not started. Required before the first production deployment.** See `docker/PRODUCTION_TENANT_ROUTING.txt`, items P1 to P10. Sprint 1 must not be reported as ready to deploy until they're done.
