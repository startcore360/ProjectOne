# EDU 360 — Project Implementation and Implications Register

**Repository:** `startcore360/ProjectOne`  
**Branch:** `sprint-3-development`  
**Current baseline:** `ac3ad964214a1b2e8005e10b6fa5f1ba30200f54`  
**Status:** In progress — project-wide implementation record  
**Last updated:** 2026-11-29

> This is the project-wide engineering and validation log. It records changes already made, the implications that must be checked, and the remaining implementation/verification programme. A feature is not considered complete merely because its UI or API code exists. Each status distinguishes implementation from test execution and end-to-end validation.

## 1. Purpose and operating rules

This register is the consolidated record for implementation against the EDU 360 V4.0 source package. It is intended to support the user's final validation after the implementation work is complete.

For every implementation commit, update this file in the same work cycle with:
- commit SHA and purpose;
- affected files/modules and API/database contracts;
- requirement, role, permission, security, workflow and UX implications;
- tests/build/lint commands actually executed and their exact results;
- outstanding risks, decisions and follow-up work.

Use these status meanings consistently:
- **Implemented:** code change has been committed.
- **Source-reviewed:** relevant code/specification has been inspected.
- **Tested:** automated test command actually ran and passed.
- **Runtime-verified:** behaviour was exercised in a running application/environment.
- **Open:** incomplete, unresolved, or not yet evidenced.

Never report tests, builds, or runtime checks as passed unless they were actually run.

## 2. Project baseline and source set

The implementation baseline is the V4.0 package supplied for this project, including:
- Master Product Specification;
- Product Governance and Action Register;
- Master Roles and Permission Specification;
- Database and Technical Architecture;
- SQL schema/migrations;
- API and Integration Specification and OpenAPI document;
- Requirements Traceability and QA;
- Unified Workflow and UX Specification;
- Security, Compliance and Final Validation materials;
- Master Design System and Interactive Prototype.

The project work must be checked against these sources together. If sources disagree, record the exact conflict and its impact; do not silently choose a convenient interpretation or bypass backend authorization.

Repository areas reviewed in the work to date include frontend pages/components and routes, academic APIs/services/controllers, SQL constraints, and existing tests. This register is not a substitute for a complete V4.0 traceability audit.

## 3. Work completed to date

### 3.1 Academic Structure / Institution Setup

**Relevant files**
- `app/src/features/institution-admin/pages/InstitutionSetupPages.tsx`
- `app/src/design-system/components.css`
- `app/src/features/institution-admin/pages/InstitutionSetupPages.test.tsx`

**Changes recorded**
- Corrected stale institution-selection closure behaviour.
- Refined page presentation and interaction states, including empty states and header actions.
- Updated related component styling and tests.

**Commits**
- `cdb3a527013cea2a0a6ceed4c47264d4d3405075`
- `15dfaf8a1cec0aca67883cdd251628d3a3820caa`
- `ff3033febdb80ec8bf9d2daa61ec1c5178f9ff17`

**Implications / validation still required**
- Revalidate institution/session/department/course/section relationships against the V4.0 structure model.
- Confirm tenant/institution scoping and all structure-management action checks in backend APIs.
- Run the updated page tests and frontend build; previous work record says these were not run.
- Exercise switching between institutions and verify dependent data resets/reloads correctly.

**Status:** UI changes committed; automated/runtime verification open.

### 3.2 Principal and Admin Dashboards

**Changes recorded**
- Added role-specific dashboard summary API usage and quick links.
- Avoided fabricated KPI values where authoritative data was not available.
- Added safeguards for multi-institution tenant summary behaviour.

**Commit**
- `a32ce4bebfbf7b6c699a4f82d233e9231bb1b6e4`

**Implications / validation still required**
- Verify each dashboard card uses an authorized, correctly scoped source.
- Check Principal, College Admin, School Admin and other applicable roles separately.
- Confirm multi-institution aggregation does not leak or mix institution data.
- Validate loading, empty, partial-data and API-failure states.
- Run dashboard tests and frontend/backend builds.

**Status:** implementation committed; tests/build not run in the recorded work.

### 3.3 Approval Inbox

**Relevant feature**
- Approval inbox UI, route `/approvals`, live inbox operations and action handling.

**Changes recorded**
- Added live inbox retrieval, search, refresh and request details.
- Added approval-chain visibility.
- Added approve and reject operations.
- Required a rejection remark.
- Did not add a misleading Forward action: backend `FORWARDED` status did not establish a real reassignment operation.

**Commit**
- `5972c74a46f550cb80c5a8c5862ff859e3c30d61`

**Implications / validation still required**
- Confirm the action register and backend policy for every approver/delegate role.
- Validate approval-chain ordering, delegation, escalation and tenant scope.
- Confirm rejection remark validation server-side, not only in the UI.
- Confirm idempotency/concurrency handling and audit history.
- Verify notification dispatch for approval/rejection and downstream workflow transitions.
- Run frontend/backend tests and exercise the complete approval lifecycle.

**Status:** implementation committed; tests/build and full workflow verification open.

### 3.5 Attendance Management — first UI slice

**Approved direction**
- Teacher marking and HOD review are separate experiences.
- Teacher marking is limited to the selected class/subject/session; complete-day or cross-subject history does not belong in the marking roster.
- HOD review is read-only and must remain inside the active department scope. College correction approvals belong to the HOD; school correction approvals belong to the School Principal or Vice-Principal.

**Changes committed**
- Added `AttendanceOversightPage.tsx` and a dedicated `/academic/attendance/oversight` route gated by `ATTENDANCE_CORRECTION_APPROVE`.
- HOD can select institution, section and date, load the existing register, select a session and review student statuses and session-level counts.
- API authorization remains authoritative for department scope; an out-of-scope section request is surfaced as an error.
- Added responsive layout styles for the HOD review workspace.
- Refocused the teacher register around the current class/session, renamed raw-ID labels, removed medical-exemption detail and threshold configuration from the marking task, added roll/ID as a separate column, and added submitted-session read-only handling.
- Removed the old date-wide register preload from the teacher marking form because the current register API returns every session for a date without a timetable-slot filter; merging those rows could silently put one period's marks into another period. The form now starts a clean session-specific draft. The backend still needs natural idempotency and a slot-scoped register/read endpoint before duplicate-safe resume and submitted-state locking can be delivered. Updated the related test labels.

**Commits**
- `04fcb567ed02e0c666797e14e74dc601134668ca` — add HOD attendance review workspace.
- `5a2c3cecf5d136f97172685129d2d765df1e5431` — add HOD oversight route and permission gate.
- `59e21403c61fd57989f1aac085e00ab7dc1ec79a` — add oversight layout styles.
- `436a78ead74683fd485028b10eb8f07f1843b0e3` — focus teacher register on the current session.
- `a9eed90b0f3a12a3c21ac8a9c608a4a364eefe9` — correct roster columns and bulk controls.
- `477819c3f5688a73b2950bdf0a0b51c48eee2d72` — type submitted state and use local attendance date.
- `4ea6c2b83acaf9e725bf302522c730c0f738decf` — align teacher register test labels.
- `5b21caa9c540660237981de785136232a155a513` — repair submitted-state JSX condition.

**Source-derived requirements**
- V4.0 §8.4: college period marking; school twice-daily marking; canonical statuses Present, Absent, On approved leave, Not held; teacher assignment restriction; correction approval by HOD in college and School Principal/Vice-Principal in school; immutable original/new correction audit; term-lock refusal.
- Master Design System: compact roster, explicit per-student radio selection, mark-all-present then exceptions, submit only when every student is marked, visible loading/empty/error states.

**Open implementation gaps — do not treat this module as complete**
- The existing `AttendanceController` does not expose the correction-request/approval, attendance-summary, bulk-upload, substitute-assignment or sync-conflict operations specified in OpenAPI.
- The existing `AttendanceService` authorizes the attendance action and department/institution scope but does not visibly enforce the assigned-teacher / substitute-for-date rule at the service layer. This is a blocking authorization gap to resolve before relying on teacher marking for production.
- Current HOD screen reviews one selected section/date using the existing register API. It is not yet a complete department dashboard, defaulter/risk list, or correction inbox.
- The existing register response returns all sessions for a section/date without a timetable-slot filter or enough period metadata for a safe teacher-side session resume. The teacher form therefore does not preload those rows. The current save flow creates a session and writes entries separately; natural idempotency, slot-scoped reads, submitted-state lifecycle, term-lock UI and preservation of correction history need backend/API alignment.
- Teacher register still needs its dedicated UX refinement and separation of threshold/rules configuration from the marking task.
- No tests, frontend build, backend build or runtime checks were executed for these commits.

**Status:** First teacher-register UX refinement and HOD read-only review slice committed; authorization, correction workflow, department-wide analytics, API lifecycle and full V4.0 attendance coverage remain open.

### 3.4 Timetable Management

**Related detailed register:** `docs/implementation/EDU360_TIMETABLE_IMPLEMENTATION_AND_IMPLICATIONS.md`

**Changes recorded**
- Reworked timetable grid and builder experience.
- Added institution/session selection rather than raw identifier entry.
- Made structure/allocation lookups conditional on the relevant permissions.
- Added role-aware views and read-only Principal oversight where the role lacks `TIMETABLE_BUILD`.
- Added loading, empty and restricted-data fallback states.
- Added room/allocation conflict feedback and disabled known conflicting dropdown choices.
- Expanded authorized timetable slot lookup for conflict checks.
- Kept backend/database validation authoritative.

**Commits**
- `9e09c174aa581bf5750970c769d21981206db4a7`
- `cae26e32e0977f4126cc04f455dd84b415766fae`
- `96ec32c064f90502ac28f6444653b480309fdf41`
- `b5862d0361e59491e5efe5c518d0ad7c2ae1e908`
- `c17ebff91217d3b26f067691791ff7d19ab9a0bc`
- `82874a3c57e1311f7b11c477d63b14eb2b5e4382`
- `021a7ba7b9381c68f2247614c98168265999ee0c`
- `639bf981934dbf72d622d5dba5e3a1796a810736`

**Important implications**
- V4.0 timetable prose describes teacher/room double-booking as warning-not-block, while current SQL GiST exclusion constraints enforce a hard block. This is a product/architecture decision requiring reconciliation; do not weaken database constraints as an incidental UI change.
- Current UI conflict checks do not yet establish a reliable teacher identity mapping from the subject allocation DTO to the timetable teacher profile.
- Existing per-slot publish support does not prove grouped publishing by class/faculty/room.
- Published-slot immutability does not alone prove a complete supersede/version history or notification workflow.
- Principal read-only behaviour must remain aligned with the authoritative action register; never grant builder controls from frontend assumptions.

**Status:** committed UI work; several backend lifecycle and conflict-policy items remain open. See detailed timetable register.

## 4. Cross-cutting engineering implications

These checks apply to every module, not only the pages already changed.

### 4.1 Authorization and role boundaries
- Treat the Master Roles and Permission Specification and Action Register as authoritative.
- Verify UI visibility and backend enforcement independently.
- Check action-level permission, tenant/institution/department scope, ownership and record-level visibility.
- Never infer permission from a role label alone when an explicit action grant exists.
- Verify read-only roles cannot mutate data by calling APIs directly.

### 4.2 Tenant isolation and data scope
- Trace tenant and institution IDs from route/session context through controller, service, repository and database query.
- Test cross-tenant and cross-institution access denial.
- Review aggregate dashboards, search, exports, bulk actions and background jobs for scope leakage.

### 4.3 API and integration contracts
- Compare frontend service calls with the API Integration Specification and OpenAPI definitions.
- Check request/response DTO shape, validation, error codes, pagination, idempotency and backward compatibility.
- Verify external integrations use configured credentials, secure secret storage, retry/error handling and audit logging.
- Do not show a successful UI state before the server confirms success.

### 4.4 Database and data lifecycle
- Review schema constraints, indexes, foreign keys, transaction boundaries and migration ordering.
- Confirm immutable/history-bearing records are not silently overwritten.
- Check soft delete/archive semantics, retention, audit events and referential integrity.
- For every workflow, verify rollback and partial-failure behaviour.

### 4.5 Security, privacy and compliance
- Review authentication/session handling, authorization, sensitive data exposure, file upload/download, exports and logs.
- Check encryption and secret management against the supplied security specification.
- Verify audit trails for sensitive reads/writes and privileged actions.
- Check consent, retention, deletion and data minimization requirements where applicable.
- Treat the security/compliance validation workbook/report as acceptance criteria, not optional documentation.

### 4.6 UX and design-system consistency
- Align page layouts, controls, typography, spacing, status treatment and responsive behaviour with the Master Design System and Unified Workflow/UX Specification.
- Provide loading, empty, error, permission-denied and success states.
- Ensure keyboard access, semantic labels, focus handling and accessible conflict explanations.
- Do not rely solely on browser-specific native tooltip behaviour for critical information.
- Keep user-facing labels consistent with domain terminology.

### 4.7 Observability and operations
- Verify structured logs, correlation IDs, actionable errors and operational metrics.
- Avoid logging credentials, tokens or unnecessary personal data.
- Check scheduled/background processing, retries, dead-letter handling and operational recovery where applicable.

## 5. Remaining implementation programme

The following is the work programme to be completed and refined against the V4.0 requirements traceability matrix. It is a planning register, not a claim that every listed module is currently absent. Existing repository functionality must be inspected before changing it.

| Workstream | Planned implementation/review | Required implications and acceptance checks | Status |
|---|---|---|---|
| Academic Structure | Finish structure setup validation and dependent selectors | Hierarchy integrity, institution scope, action permissions, lifecycle rules, tests | In progress / verify |
| Dashboards | Complete role-specific data and navigation | KPI provenance, scoped aggregation, role visibility, empty/error states | In progress / verify |
| Approval Inbox | Complete workflow lifecycle and audit | Delegation, escalation, decision history, concurrency, notifications | In progress / verify |
| Timetable Management | Finish conflicts, publish lifecycle, versioning and notifications | Warning-vs-block policy, teacher/room/class conflict semantics, immutable history, audience publishing | In progress |
| Attendance Management | Inspect and implement attendance capture, correction and review flows | Session/timetable linkage, attendance permissions, correction audit, lock/close rules, parent/student visibility | Not started in this work log |
| Teacher / Faculty Workspace | Inspect teacher-facing classes, schedule, attendance and academic actions | Assignment scope, faculty ownership, privacy, workload and action permissions | Not started in this work log |
| Student Workspace | Inspect student timetable, attendance, learning and academic views | Own-record access, guardian visibility, privacy, term/session scoping | Not started in this work log |
| Parent / Guardian Workspace | Inspect linked-child views and communication | Verified guardian-child relationship, consent, restricted data visibility | Not started in this work log |
| Assessment / Examination | Inspect assessment planning, marks entry, moderation and publication | Grade calculations, approval controls, audit, published-result immutability | Not started in this work log |
| Fees / Finance | Inspect fee structures, invoices, payments, concessions and reconciliation | Financial permissions, payment integration, idempotency, audit and reconciliation | Not started in this work log |
| Admissions / Enrollment | Inspect application, review, offer and enrollment lifecycle | PII security, document handling, duplicate detection, decision audit | Not started in this work log |
| Communication / Notifications | Verify in-app, email, SMS or configured channels and templates | Consent, delivery status, retries, preference management, event triggers | Cross-cutting review pending |
| Reports / Exports | Inspect report catalogue, filters, export and print workflows | Row-level scope, sensitive fields, export audit, large dataset handling | Cross-cutting review pending |
| Integrations | Verify configured external services and callbacks | Secret handling, signature verification, retries, rate limits, failure recovery | Cross-cutting review pending |
| Security / Compliance | Complete control-by-control validation from V4.0 artifacts | Threat surface, access tests, audit evidence, retention, encryption and sign-off | Final gate |
| Design System / UX | Review all implemented screens against system and prototype | Responsive behaviour, accessibility, consistency, interaction states | Continuous |
| QA / Release | Execute traceability, regression, integration and release checks | Automated suites, migrations, build artifacts, environment config, smoke tests | Final gate |

The module list above is a workstream map for planning. Exact module boundaries, feature scope and sequence must be confirmed against the supplied V4.0 specification and requirements traceability workbook before implementation.

## 6. Validation and evidence register

| Validation area | Evidence required | Current status |
|---|---|---|
| Requirements traceability | Each requirement mapped to implementation, API, DB, permission and test evidence | Full pass pending |
| Roles and permissions | Role/action matrix tests plus direct API denial tests | Partial source review; full pass pending |
| Tenant isolation | Cross-tenant and cross-institution negative tests | Full pass pending |
| Frontend unit/component tests | Test command and output for changed modules | Not run for recorded work |
| Backend unit/integration tests | Test command and output, including DB-backed cases | Not run for recorded work |
| Lint/type checking | Exact command and clean output | Not run for recorded work |
| Frontend production build | Exact command and artifact/result | Not run for recorded work |
| Backend build/package | Exact command and artifact/result | Not run for recorded work |
| Database migrations | Apply migrations on clean and upgrade paths; run schema assertions | Pending |
| API contract | OpenAPI/DTO conformance and error handling | Partial source inspection; full pass pending |
| Browser/runtime QA | Role-based workflows, responsive layout, accessibility and error states | Pending |
| Security/compliance | Evidence against V4.0 security checklist and final validation report | Pending |
| Release readiness | Regression, smoke tests, migration plan, rollback and operational checks | Pending |

**Execution limitation recorded:** prior edits were made through the GitHub repository connector, which can read and write repository files but does not execute the project's shell-based test/build commands. Therefore those checks must be run in an execution-capable environment before they can be marked tested.

## 7. Risks and decisions requiring explicit resolution

1. **Timetable clash policy:** reconcile the V4.0 warning-not-block wording with database exclusion constraints.
2. **Timetable publication lifecycle:** verify publish-by-class/faculty/room, supersede/version history and notification dispatch.
3. **Role/action alignment:** validate every changed screen against the full action register, not only role names.
4. **Test evidence gap:** implementation commits to date have not been accompanied by recorded successful test/build runs.
5. **Full requirements coverage:** no project-wide V4.0 traceability pass has yet been completed.
6. **Runtime confidence:** source inspection and GitHub commits do not establish browser behaviour, deployment correctness or production readiness.

Each decision must be resolved from the authoritative V4.0 source or escalated for an explicit product/architecture decision. Record the decision, owner/approval evidence if available, and affected code/schema/tests.

## 8. Commit-by-commit change log

### Previously completed work
| Commit | Area | Summary |
|---|---|---|
| `cdb3a527013cea2a0a6ceed4c47264d4d3405075` | Academic Structure | Fix institution selection closure behaviour and related setup UI work. |
| `15dfaf8a1cec0aca67883cdd251628d3a3820caa` | Academic Structure | Refine setup page UX/styling and interaction states. |
| `ff3033febdb80ec8bf9d2daa61ec1c5178f9ff17` | Academic Structure | Further setup page refinements and tests. |
| `a32ce4bebfbf7b6c699a4f82d233e9231bb1b6e4` | Dashboards | Role-specific dashboard summaries and quick links; avoid fabricated KPIs and protect multi-institution summaries. |
| `5972c74a46f550cb80c5a8c5862ff859e3c30d61` | Approval Inbox | Live approval inbox, details, approval chain, approve/reject and rejection remark requirement; no unsupported Forward action. |
| `9e09c174aa581bf5750970c769d21981206db4a7` | Timetable | Initial timetable UI rewrite. |
| `cae26e32e0977f4126cc04f455dd84b415766fae` | Timetable | Conditional structure-data loading based on permission. |
| `96ec32c064f90502ac28f6444653b480309fdf41` | Timetable | Timetable grid styling. |
| `b5862d0361e59491e5efe5c518d0ad7c2ae1e908` | Timetable | Role-aware filters and clash-warning flow. |
| `c17ebff91217d3b26f067691791ff7d19ab9a0bc` | Timetable | Loading/empty states and restricted allocation lookup fallback. |
| `82874a3c57e1311f7b11c477d63b14eb2b5e4382` | Timetable | Clarify current database-enforced conflict behaviour. |
| `021a7ba7b9381c68f2247614c98168265999ee0c` | Timetable | Disable known conflicting allocation/room choices with explanations. |
| `639bf981934dbf72d622d5dba5e3a1796a810736` | Timetable | Check dropdown conflicts against all authorized institution timetable slots. |
| `ac3ad964214a1b2e8005e10b6fa5f1ba30200f54` | Documentation | Add detailed Timetable Management implementation/implications register. |

| `04fcb567ed02e0c666797e14e74dc601134668ca` | Attendance | Add HOD attendance review workspace. |
| `5a2c3cecf5d136f97172685129d2d765df1e5431` | Attendance | Add HOD oversight route and permission gate. |
| `59e21403c61fd57989f1aac085e00ab7dc1ec79a` | Attendance | Add HOD oversight layout styles. |
| `436a78ead74683fd485028b10eb8f07f1843b0e3` | Attendance | Refocus teacher marking on current class/session; remove unrelated rules configuration and sensitive medical detail. |
| `a9eed90b0f3a12a3c21ac8a9c608a4a364eefe9` | Attendance | Correct teacher roster columns and bulk controls. |
| `477819c3f5688a73b2950bdf0a0b51c48eee2d72` | Attendance | Add submitted-session state and local-date handling. |
| `4ea6c2b83acaf9e725bf302522c730c0f738decf` | Attendance | Align teacher register test labels. |
| `5b21caa9c540660237981de785136232a155a513` | Attendance | Repair submitted-state JSX condition. |
| `d20c8b223210aa20c357f0ab182881921c67772b` | Attendance | Remove stray roster header escape. |
| `614f22c5737de0f0669c0d39ced5f116fe5b5ddf` | Attendance | Avoid mixing date-wide register rows from different periods in the teacher marking form. |
| `1c1181c2a5269121e6e7e5e4ebfc5418ba8be04a` | Documentation | Record first teacher/HOD Attendance UI slice and open risks. |

### Future commits
Append one row per code commit and include the new SHA, module, summary and verification state. When a documentation update is made in a separate commit, identify the code commit it documents.

## 9. Final completion criteria

The project should only be presented for final user validation when:
- all in-scope V4.0 requirements have a traceable implementation or a documented approved deferral;
- all relevant action/role and tenant-scope checks are verified in both UI and backend;
- database constraints, lifecycle and audit behaviour match the approved product decisions;
- required API and integration flows have been exercised;
- tests, lint/type checks and builds have actual recorded outcomes;
- security/compliance checks have evidence;
- key workflows have been exercised in a running environment for each relevant role;
- unresolved issues and known limitations are listed clearly.

The final handover must include a summary of completed work, outstanding exceptions, commit references, test/build evidence, and a validation checklist for the user.
