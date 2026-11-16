# EDU 360 — Sprint 1 Gap Validation

**Checked on:** 7 November 2026, on branch `manoj_feature`, including uncommitted work.

**Compared against:**
- `EDU360_Sprint1_Implementation_Prompt.md` (the 67 stories and exit criteria)
- The `sql/` schema, for the tables and rules behind the findings
- `sprint1_full_audit_matrix.md` (the audit being validated)

- The V4.0 package (D1 to D10), the spec summary, the governance log and the role addendum. **Section 16 lists what was read, what was not, and the corrections this led to.** Some sheets and sections were not read.

---

## 1. The short answer

**As a senior tester, my result is: not ready for acceptance. Sprint 1 is not close to finished, and the audit makes it look further along than it is.**

The audit says 25 of 67 stories are complete. We found 5 where the code holds up under testing, and even those fall short of the sprint's Definition of Done.

| Epic | Built | Partial | Missing |
|---|---:|---:|---:|
| Sign-in and account (7) | 0 | 6 | 1 |
| Users and roles (17) | 0 | 12 | 5 |
| Institution setup (22) | 5 | 9 | 8 |
| Platform (21) | 0 | 8 | 13 |
| **Total (67)** | **5** | **35** | **27** |

Against the API specification (D5), 119 of the 176 Sprint 1 operations are missing, and none of the other 57 fully conforms (Section 16).

What the words mean here:
- **Built:** the API and a screen both exist, and the main rules are in place.
- **Partial:** some of it exists, but a rule, screen or API is missing, or a serious defect was found.
- **Missing:** nothing usable exists yet.

**No story fully meets the Definition of Done yet.** It requires audit evidence with the active role and scope, and the audit log cannot store those (Gap 4). "Built" therefore does not mean "ready to accept".

**The bugs you reported are confirmed.** See Section 11 (internal server errors), Section 12 (inline errors), Section 13 (loaders and fake data) and Section 14 (negative test cases).

### Product Owner decisions, 7 November 2026

| # | Decision | What it means for this report |
|---|---|---|
| PO-1 | A University Admin can manage all colleges belonging to that university, under the approved scope and permission rules. | Settles the scope question for this role. The exact-match permission check and `StructureService`'s institution-only check must change. Scope for other roles still follows the documents. |
| PO-2 | Adopt the D5 error format and apply it across the APIs. | Settles OD-74 for this work. Sections 11 and 12 build on the D5 format. |
| PO-3 | A user can hold multiple roles at the same scope. Permissions are never combined, each role's permissions stay separate, role identity and scope are preserved, and one role never silently replaces another. Each role is tested independently. | Goes with D3 K-010 and the database index. Differs from Addendum rule 10 as coded, so the code that ends the other role and denies "ambiguous" assignments must change. It also makes D01 (active role ignored) more urgent. |
| PO-4 | Verify through Docker and live testing where possible, run the available tests, and report actual results. | Approved. Scheduled for Phase 2; the results in this report are still from code and old logs. |

### Current status of this review

**Phase 1 (specification and code review) is done for Sprint 1, with stated limits; see Section 17.** The documents were not read end to end. The counts and defects in this report have **not** been re-verified yet; that is Phase 2. Code changed on 7 November after the counts were made, so some statuses are already out of date (Section 17.5).

### The ten things that matter most

1. **Staff may see no college or school data in the real database.** Every tenant login gets an empty institution scope (Gap 1).
2. **Normal actions and bad input return 500.** Audit write failures roll back real work, and common bad requests aren't handled (Section 11).
3. **Scope gaps let one college's admin act on another's users.** An IT Admin could reset the Vice-Chancellor's password and receive the new one (T01, T02).
4. **Accounts are created that can never log in.** If Keycloak fails, the app still reports success (T10). Creating a tenant doesn't create its first admin (T09).
5. **Sample data is shown as real.** The Tenants page and Overview show fake tenants and counts (Section 13).
6. **Admins cannot reach their own main screens.** The Institutions and Roles pages always send them back to the dashboard (Gap 2).
7. **Only 1 of 28 forms shows errors at the field.** Everywhere else you see "Validation failed" with no field named (Section 12).
8. **Login is not protected as the spec requires.** There is no two-step login, no 15-minute lockout, and passwords can be 6 characters (Gap 3).
9. **The audit trail cannot answer "who did this, in which role?"** Seven actions write no audit entry at all (Gap 4, T26).
10. **There is no approvals inbox.** Stories that depend on approval either do not exist or skip the approval step (Gap 5).

### What is good

- **The structure is sound.** Tenant routing, Keycloak token checks and the action evaluator are real and carefully written.
- **Most actions check permission on the server,** but not all (T19), and the check often doesn't confirm the target is in scope (T01, T02).
- **Several negative cases pass.** Self-deactivation, changing your own role, duplicates, plan limits and end-before-start dates are refused (Section 14).
- **Tests pass today.** Backend unit tests are at 253 of 253 and frontend tests at 77 of 77.
- **The SQL gate in CI is real.** The job runs `sql/run_all.sh` against Postgres 16.

---

## 2. How this was checked, and what was not

**Run on 7 November 2026:**

| Check | Result |
|---|---|
| Backend `mvnw test` | **253 of 253 pass.** An earlier run the same day failed to compile because of `LeaveApproverService`; that has since been fixed. |
| Frontend `npm test` | 77 of 77 pass |
| Frontend `npm run build` | Passes, with a warning that the bundle is large (550 kB) |
| Frontend `npm run lint` | **Fails: 4 errors, 2 warnings.** CI runs lint, so the frontend CI job will fail. |
| `npm audit --audit-level=high` | Passes: 7 moderate issues, none high |

**Not run, so not proven:**
- **The app was not used live.** The Docker stack was down, so UI findings come from reading the code, not from screenshots. Dark-mode contrast and keyboard behaviour are unchecked.
- **The two `*IT` integration tests were not run.** They are the only tests that use real row-level security (RLS) and real Keycloak tokens, and `mvn verify` skips them.
- **`sql/run_all.sh` was not run.** The committed `sql/run_results.txt` holds only a header line, so it does not prove the SQL tests passed.
- **The app was not compared visually with the prototype (D10).** Its code and text were read, but it was not viewed in a browser.
- **Passing unit tests prove less than they seem to.** They use mocks and H2, so they cannot catch RLS failures, database rejections or most of the 500s in this report.

**How to read test results:** every "actual" result comes from reading the code, or from `logs/backend*.log` (5–6 November), not from clicking through the app. Each is labelled where it matters. All must be retested live once the stack is up.

Development is still going on, so some details may already have changed.

---

## 3. The serious gaps, with real-world scenarios

The scenarios use an imaginary customer, Northbridge University.

### Gap 1 — Staff may see no institution data

**Spec:** Every request carries tenant and institution context, set on the database session (Foundation item 5, OD-03).

**Today:** Tenant context is set, but institution scope is always `NONE` with an empty list. No production code passes real institution IDs. The code comment says this waits for Foundation item 7, which the audit marks as completed.

**Real-world scenario 1:** Anita is the College Admin at Northbridge College of Science. She logs in and opens departments. The database policy also covers `user_role_assignment`, so her college-level role is hidden and the permission check denies her, even though the role was correctly given.

**Real-world scenario 2:** Ravi, a University Admin with a tenant-wide role, creates a college. The permission check passes, then the database refuses the new row and he gets a 500 (Section 11, E02).

**Why tests miss it:** Unit tests mock the database, so RLS never runs. Failing closed is safe, but it means the feature does not work.

**Status:** Confirmed by code, not yet seen live. This should be the first thing tested once the stack is up.

**Fix:** D4 §2.2 sets the scope from the active role: `ALL` for tenant-wide roles such as University Admin, Vice-Chancellor, Registrar and Management oversight, and a list of institution IDs for everyone else. D4 also requires these settings per transaction, not per session (Section 16). Add an integration test on real Postgres.

**Warning:** Fix T01 and T02 in the same change. Today the empty scope happens to block college-level admins from reaching those holes. Fixing Gap 1 alone would open them.

**Evidence:** `identity/TenantUserLookup.java:35-41`, `tenant/TenantRoutingDataSource.java:245`, `sql/03_bindings.sql:235`

---

### Gap 2 — Admins cannot reach their own screens

**Spec:** A role-aware shell with navigation by role (Foundation item 10). Screens must be reachable by the roles that use them.

**Today, there are four separate problems:**

| Problem | Effect |
|---|---|
| `UniversityAdminRoute` always redirects to `/dashboard` | Institutions, University Users, Roles and Missing Roles can't be opened by anyone |
| `AcademicStructurePage` has no route | Departments and programs can't be opened at all |
| Tenant users' menu has only "Overview" and "My sessions" | User management and setup pages work only if you type the web address |
| `PlatformAdminRoute` allows only `PLATFORM_ADMIN` | Product Owner, Billing, Support and Release staff can't open the platform console |

**Real-world scenario 1:** Ravi, the University Admin, must create a new college. He clicks "Institutions" and lands back on the dashboard with no message. He cannot create, activate or block any college. The screen exists and its tests pass, because the tests open the page directly and never go through the route gate.

**Real-world scenario 2:** Only the Product Owner may manage platform staff, but the Product Owner cannot open the platform console. The Platform Admin can open that page, but the server refuses them. **So nobody can use the Platform Users screen.**

**Fix:** D9 P2 says to build navigation from the actions the server allows, never from the role name. So the backend must give the browser the user's allowed actions. A page the user can't open shows a neutral "You don't have access to this" state and logs the attempt, instead of redirecting. The server stays the real security check.

**Evidence:** `app/src/routes/Routes.tsx:58-65,78-85`, `app/src/layout/AppLayout.tsx:29-32`

---

### Gap 3 — Login is not protected as the spec requires

**Spec (AUTH_LOGIN, AUTH_PASSWORD_CHANGE):**
- Two-step login for platform roles, Admins, Principals, Accountants, HR, Payroll, Management, Vice-Chancellor, Registrar and other senior roles.
- Locked for 15 minutes after 5 wrong passwords.
- At least 8 characters, common passwords blocked, forced change every 120 days.

**Today:**

| Rule | Status |
|---|---|
| Two-step login | **Not enforced.** Only a stored `mfa_required` flag and code comments. |
| 15-minute lockout | **Not configured** in the Keycloak realm files. The code can only clear a lockout. |
| Minimum 8 characters | **6** in both the API and the screen |
| Common-password block | **Missing** |
| Forced change at 120 days | **Missing** (the audit wrongly says it exists) |
| Student first password expires in 7 days | **Missing** |
| Student ID, parent OTP, applicant OTP login | **Missing.** Email and password only. |

**Real-world scenario 1:** The Finance Director's password leaks in a phishing email. The attacker signs in with the password alone, with no second step. A bot can also keep guessing, because nothing locks the account.

**Real-world scenario 2:** A parent at the gate wants to see their child's attendance. They only have a mobile number, but the login page asks for an email. They cannot get in.

**Real-world scenario 3:** A Class 8 student gets a printed first-login password. Three months later it is still valid in an unclaimed account, because the 7-day expiry doesn't exist.

**Fix:** Set the password policy and brute-force protection in Keycloak. Require a one-time code step for the listed roles. Change 6 to 8 in both places. Add the other login types as separate stories.

**Evidence:** `docker/keycloak/realm-export-*.json`, `auth/dto/ChangePasswordRequest.java:16`, `app/src/features/auth/pages/ChangePasswordPage.tsx:26`

---

### Gap 4 — The audit trail cannot answer "who did this?"

**Spec (Foundation item 9, OD-09):** Every create, edit, delete, approval, export, login, denied attempt and sensitive view is logged with actor, active role, scope and correlation ID, and is append-only.

**Today:**
- `AuditLogService.record` takes only event type, user ID, email, IP and details.
- The user ID is a `Long`, but real user IDs are UUIDs. New code passes `null` and puts the ID in the email field.
- There is no active role, scope or correlation ID, and nothing in the database makes the table append-only.
- The Platform Audit screen reads `platform.audit_outbox`, and nothing writes to that table, so it stays empty.

**Real-world scenario 1:** A student's marks are found changed. The Principal asks who did it. Dr Rao holds two roles, HOD and Professor. The log cannot show which role was active, or which college the action was for.

**Real-world scenario 2:** A dishonest staff member with database access deletes the log entry. Nothing stops it or reveals it.

**Real-world scenario 3:** The Product Owner opens the Audit Log during an incident and sees nothing. An empty screen looks like "no activity", which is misleading.

**Fix:** Move to the schema's audit design with UUID actor, active role, scope and correlation ID. Add the append-only trigger. Write platform actions to the platform outbox.

**Evidence:** `audit/AuditLogService.java:36`, `platformops/PlatformAuditLogController.java:24-26`

---

### Gap 5 — There is no approvals inbox

**Spec:** Approvals go through `approvals/inbox`, `approval-requests/{id}` and `decisions`. Delegation and bulk approval sit on top of this.

**Today:** None of these endpoints or screens exist. Activation and block approval are direct calls.

**Real-world scenario:** The University Admin requests that a college be blocked. The Vice-Chancellor has nowhere to see pending requests and would need a separate tool or a phone call. If the VC goes on leave, there is no delegation, so requests just wait. When a VC is deactivated, the rule says pending approvals move to the new holder, but there are none to move.

**Affects:** #19, #20, #21, #26, #28, #40, and the "pending approvals move" rule in #12. Role roll-down (#23, #24) also needs somewhere to route approvals.

**Fix:** Build one shared approvals module, then connect each story to it.

---

### Gap 6 — Forms ask for IDs that admins cannot know

**Spec:** Screens must be usable by the roles that own them, and the prompt requires a proper front-end standard.

**Today:** 13 or more inputs ask for raw IDs such as `3f2a9c1e-...`: Institution ID, Department ID, Section ID, Class ID, Academic Session ID and "Staff assignment ID". No screen shows these IDs to copy.

**Real-world scenario:** A College Admin must make Dr Sharma the HOD of Physics. The form asks for "Department ID" and "Staff assignment ID". The admin knows the words "Physics" and "Dr Sharma", not those codes, so they must ring IT, who need database access. Everyone assigning a mentor or class teacher hits the same wall.

**Fix:** Replace each with a dropdown or a search by name. For staff, show name, designation and department.

**Evidence:** `app/src/features/institution-admin/pages/InstitutionSetupPages.tsx:73,191-194,245-248,311-314,359,685,740,878,1035`

---

### Gap 7 — Academic structure is mostly unbuilt

**Spec (#29 STRUCTURE_MANAGE):** About 40 operations: departments, programs, years, semesters, classes, sections, subjects, course offerings and subject groups, each with create and edit.

**Today:** Only list and create for departments and programs. The audit's "Years, Semesters, Sections tabs" have no API behind them. There is no edit anywhere, and the page itself has no route (Gap 2).

**Real-world scenario:** Northbridge is setting up B.Tech for the new year. They create the program, then need Year 1, Semester 1 and Section A. They can't. They also spelled "Compter Science" wrong and have no way to correct it.

**Why it matters:** HOD, mentor and class-teacher assignment all need sections and departments to exist first, and later sprints (timetable, attendance, marks) sit on top of this.

**Evidence:** `institution/StructureController.java:35-79`

---

### Gap 8 — Rules that were skipped in "completed" stories

| Story | Spec rule | Today | Real-world effect |
|---|---|---|---|
| #49 Tenant activate | **Product Owner only** (D1 §11.1 step 9, D2 row 203, D3) | Platform Admin is also allowed | Staff could activate a customer who has not paid. **The code is wrong.** |
| #4 Devices | See and log out own devices; one phone + one computer | Can only end the current session | A teacher who lost a phone cannot sign it out from a laptop |
| #5 Profile | New parent phone verified by code, old number alerted | Not done | Someone could change a parent's number silently and receive the child's alerts |
| #48 Tenant create | Own database by default; shared on small plans | Only shared is possible | A customer promised a dedicated database cannot get one |
| #48 Plans | Real plan list | Hardcoded fallback plans with prices appear if loading fails | Staff could pick a plan and price that isn't real, with no warning |
| #66 Platform users | Create and **deactivate** | No PATCH, so no deactivation | A former employee's platform account can't be turned off from the app |
| #65 Backup status | IT sees own tenant; platform sees all | Platform only, flagged as not wired to a real backup system | A status screen could reassure people about backups nobody is checking |
| #63 Usage | Tenant usage and reports | Only the combined count | Can't see one customer's usage for billing |
| #45, #46 Leave approver | Management notified on every change; `dpc-leave-approver`; a screen | API exists; no notification, no DPC endpoint, **no screen** | Activation is refused because the approver isn't set, and **no screen exists to set it** |
| #25 Institution | Edit a draft | No PATCH | A typo in a draft college can't be fixed |

---

### Gap 9 — Things the prompt requires that are absent everywhere

| Requirement | Today |
|---|---|
| Optimistic locking (`row_version` / `If-Match`) | Not implemented. The column exists in the schema. **Scenario:** two admins edit the same user, and the second silently overwrites the first. |
| Idempotency | Not implemented. **Scenario:** a slow network makes someone click Create twice, and it runs twice. |
| Spec API paths | Only 6 controllers have `/v1/...` paths. Others use `/api/...` or different names, such as `/api/me/sessions` instead of `/me/devices`. |
| Signed 408-operation list | Does not exist |
| End-to-end tests | **None exist.** The audit says E2E-001, 002 and 020 are "verified". |
| Scope covers narrower scope | The evaluator matches exact scope only, by design. **Scenario:** a University Admin with a tenant-wide role may be refused for a college action. **Decided for University Admin by PO-1.** |

---

## 4. Where the audit document itself is wrong

**Overstated:**

| Audit says | Actually |
|---|---|
| Summary: 6 partial, 29 pending | Its own table has 10 partial, 25 pending |
| OD-09 audit trail completed with role and correlation ID | Those fields don't exist |
| Two-step login and 15-minute lockout done | Not configured |
| 120-day password tracking | Doesn't exist |
| Evaluator has scope hierarchy | Exact match only |
| Calls `platformPermissionService.assertAllowed(...)` | That method doesn't exist; it is `authorize` |
| E2E scenarios verified | No such tests exist |
| API master list completed | Doesn't exist |
| #19 Guardian approval "backend + tests complete" | No guardian module, nothing to approve |
| #60 Release "partial" | No endpoint and no permission entry |
| Exit 6: no placeholder reported as done | #65 is a declared placeholder |
| #25, #26, #27, #29, #66 screens complete | Screens exist, but users can't reach them (Gap 2) |
| #14, #15 "Assign Role modal" | No screen calls `assignRole`. The service function exists but is unused. |
| #48 tenant creation complete | Saves the first admin's details only in the audit text and creates no account (T09) |
| Frontend quality gate clean | Lint fails with 4 errors |

**Understated (better than the audit says):**

| Audit says | Actually |
|---|---|
| #45, #46 PUT endpoints pending | Built, with a go-live check |
| #61, #62, #63, #65 "UI pending" | Screens and routes exist. The audit's "quick wins" are already done. |
| 118 and 242 backend tests | 253 pass today |

---

## 5. Walking through it as a real user

| Step | What a person expects | What happens | Verdict |
|---|---|---|---|
| Platform Admin creates a tenant | Pick type, plan, database, enter admin details; first admin can log in | Tenant is saved, only shared database, **no admin account is created** | **Broken** |
| Product Owner activates after payment | Open console, click Activate | Product Owner can't open the console | **Broken** |
| University Admin signs in first time | Forced password change | Works, but 6 characters allowed and no two-step | Weak |
| Admin creates VC and top users | A clear menu item | Exists, but no menu link to Users | **Hidden** |
| Admin creates a college | Click Institutions | Sent back to dashboard | **Broken** |
| Admin sets Principal's leave approver | A screen | No screen | **Blocked** |
| VC approves the college | Inbox with a request | No inbox | **Missing** |
| Admin builds departments and sections | Setup screens | No route; sections have no API | **Blocked** |
| Admin assigns HOD | Choose from a list | Paste two long IDs | **Unusable** |
| Admin adds students with guardians | Admission-style form | No student or guardian form | **Missing** |
| Teacher or student signs in | Dashboard for their role | Mostly placeholder content | Early |

**Is the UI perfect? No.** The visual base is decent (design system, theme toggle, modals, notices), but a new customer cannot complete onboarding through the screens today.

### UI problems to fix

1. **Silent redirects.** Being sent to the dashboard with no message feels like a bug. Show "You don't have access to this page".
2. **Missing states.** Dashboard, Login and Change Password lack loading and error states. The prompt requires loading, empty, error, denied and read-only on every screen.
3. **Password screen.** No rules shown, no show/hide toggle, no strength hint.
4. **Role field.** The placeholder suggests `TEACHER, STUDENT`, which aren't in the 71-role catalogue. Use a list from `GET /roles`, filtered to what this user may assign.
5. **Next-step guidance.** After creating a tenant or college, nothing says what to do next. A setup checklist fed by `go-live-check` would help a lot.
6. **Hardcoded colours.** 37 inline colour values bypass the design tokens and could break in dark mode. Some may be false matches.
7. **Very large pages.** Four files run 900 to 1,500 lines, which makes bugs and reviews harder.

---

## 6. Data that should be collected

**Read this first:** the required field list is still an open decision (V4-OD-01, DR-20). Student ID format is also open (OD-12). The prototype (D10) collects far less than the tables below: its student and teacher wizard asks only for first name, last name, email, optional mobile, institution, role, and class or subject.

So only these are backed directly by the screens or workflows:
- **Guardian:** full name, relationship, mobile, primary (D10)
- **Tenant create:** start date, retention period, backup retention (D1 §11.1 step 8, D7 F02_T03). **The app doesn't collect these.**
- **Department create:** department name, HOD email (D7 F03_I05)
- **Plan:** name, price, limits, pricing type, modules (D7 F02_T01)
- **Deactivate user:** a required reason, up to 200 characters (D5, D10). **The app doesn't send one.**

The other fields below exist in the schema, so they are reasonable suggestions, but they need a decision before anyone builds them.

### Create user: student

The `POST /students` path doesn't exist. From `app.student_profile`:

| Field | Why a school needs it |
|---|---|
| Student code | Student ID is how college students log in |
| Full name | Official record, may differ from display name |
| Date of birth | Identifying the student, and reports |
| Admission date | Academic record |
| Institution | Which college or school they belong to |
| Photo | ID cards and recognition |

### Create user: staff

From `app.staff_profile` and `app.staff_assignment`:

| Field | Why |
|---|---|
| Staff code | Employee ID |
| Date of birth, personal email, personal mobile | HR records and a way to reach them after leaving work email |
| Joining date | Service record |
| Institution and department | Needed for HOD rule: "must be a faculty member of that department" |
| Designation | Lists should show "Dr Sharma, Associate Professor" |
| Effective from and to | Assignments have dates |

### Guardian (no form exists)

From `app.guardian_profile` and `app.student_guardian_link`, and the GUARDIAN_LINK rule:

| Field | Why |
|---|---|
| Full name, mobile, email | Mobile is how parents log in and how duplicates are matched |
| Relationship | Father, mother, guardian |
| Primary guardian flag | The rule allows up to 3 guardians, one primary |

### Create tenant

Already collected: name, type, plan, subdomain, isolation, admin details. Also in `platform.tenant`:

| Field | Why |
|---|---|
| Time zone, data region | Correct dates; where data is stored |
| Trial end date | The rule says a trial ends read-only until payment |
| User, storage, SMS and API limits | "Change limits within plan" (#51) |
| Logo and colours, custom domain | Branding rule (#51). Could go in tenant settings instead. |
| Analytics opt-out | Rule PV-07 |

### Create institution

Already collected: type, code, name, address, city, state, PIN, principal email, external university mode. **Bug:** `under_university` is always saved as false (`InstitutionService.java:103`). A college under a university is treated as standalone, so choosing Registrar or Vice-Chancellor as leave approver is wrongly refused (T20).

### Needs a Product Owner decision first

These are common in real schools, but they are **not in the schema or the rules we checked**. We should not add them without a decision.

| Field | Where | Why a school might want it |
|---|---|---|
| Home address, emergency contact | Student and staff | Safety, and required by many schools |
| Blood group, gender, category | Student | Medical and government reporting |
| Board and UDISE or affiliation code | Institution | Government and board reporting |
| Institution phone and logo | Institution | Letterheads and contact pages |
| GSTIN and billing contact | Tenant | Invoices. The rule says no tax in v1, so this may wait. |

---

## 7. All 67 stories

Key: ✅ Built · 🟡 Partial · ❌ Missing. "Audit" shows what the audit said: Done, BE (backend only), Part or Pend.

### Sign-in and account

| # | Story | Audit | Real | What's missing |
|---|---|---|---|---|
| 1 | AUTH_LOGIN | Part | 🟡 | Two-step, lockout, Student ID and OTP logins |
| 2 | AUTH_FIRST_LOGIN_RESET | Part | 🟡 | 7-day expiry |
| 3 | AUTH_PASSWORD_CHANGE | Part | 🟡 | 8 characters, common-password block, 120 days |
| 4 | AUTH_DEVICES_MANAGE | Done | 🟡 | Revoke other devices, device limit, push subscriptions |
| 5 | PROFILE_EDIT_OWN | Done | 🟡 | Parent phone verification and alert |
| 6 | ROLE_SWITCH | Done | 🟡 | Permission check ignores the active role; kept only in memory; not in audit entries |
| 7 | SELF_SERVICE_VIEW | Pend | ❌ | Everything |

### Users and roles

| # | Story | Audit | Real | What's missing |
|---|---|---|---|---|
| 8 | USER_VIEW | Done | 🟡 | Lists every user in the tenant, ignoring scope (T02); no menu link; guardian-link view |
| 9 | USER_CREATE | Done | 🟡 | `POST /students` and student fields |
| 10 | USER_BULK_UPLOAD | Done | 🟡 | `jobs/{id}` endpoint; temp passwords discarded so imported users can't log in (T13); no file limit; T12, T15 |
| 11 | USER_EDIT | Done | 🟡 | Scope (T02); email not validated or synced to Keycloak (T23); no audit entry; optimistic locking |
| 12 | USER_DEACTIVATE | Done | 🟡 | Moving pending approvals |
| 13 | USER_PASSWORD_RESET_UNLOCK | Done | 🟡 | Target's scope not checked (T01); unlock destroys the user's password (T11) |
| 14 | ROLE_ASSIGN | Done | 🟡 | **No screen** calls it; `GET roles/{id}` |
| 15 | ROLE_ASSIGN_NON_SENSITIVE | BE | 🟡 | Rule checks the role's name, not its rights, so it can be bypassed; **no screen** calls it |
| 16 | ROLE_CUSTOM_MANAGE | Part | 🟡 | Create and edit APIs |
| 17 | GUARDIAN_LINK | Pend | ❌ | Everything |
| 18 | GUARDIAN_CHANGE_REQUEST | Pend | ❌ | Everything |
| 19 | GUARDIAN_CHANGE_APPROVE | BE | ❌ | Everything except a generic self-approval rule |
| 20 | APPROVAL_DELEGATE | Pend | ❌ | Everything |
| 21 | APPROVAL_BULK | Pend | ❌ | Everything |
| 22 | TENANT_TOP_USERS_CREATE | Done | 🟡 | Keycloak failure still reports success (T10); two-step is only a comment |
| 23 | ROLLDOWN_VIEW | Part | 🟡 | API and screen (database ready) |
| 24 | ROLLDOWN_OVERRIDE_SET | Part | 🟡 | API and screen (database ready) |

### Institution setup

| # | Story | Audit | Real | What's missing |
|---|---|---|---|---|
| 25 | INSTITUTION_CREATE_DRAFT | Done | 🟡 | Screen unreachable; no draft edit |
| 26 | INSTITUTION_ACTIVATE | Done | 🟡 | Screen unreachable; no approvals inbox |
| 27 | INSTITUTION_BLOCK_REQUEST | Done | 🟡 | Screen unreachable (API is done) |
| 28 | INSTITUTION_BLOCK_APPROVE | BE | 🟡 | Approvals inbox and screen |
| 29 | STRUCTURE_MANAGE | Done | 🟡 | About 30+ operations; no route |
| 30 | PROGRAM_RULES_SET | Pend | ❌ | Everything |
| 31 | CALENDAR_MASTER_SET | Done | 🟡 | Audit event rejected, so saves return 500 (E04); session edit; mid-year notice |
| 32 | CALENDAR_LOCAL_SET | Done | 🟡 | No exam-day block; unknown session gives 500 (T06); blank title saved (T07); no audit entry |
| 33 | WEEKLY_OFF_SET | Done | ✅ | No audit entry; ID input |
| 34 | GRADING_SCHEME_SET | Pend | ❌ | Everything |
| 35 | HOD_ASSIGN | Done | ✅ | No audit entry; ID inputs |
| 36 | SUBJECT_ALLOCATE | Pend | ❌ | Everything |
| 37 | MENTOR_ASSIGN | Done | ✅ | No audit entry; ID inputs |
| 38 | CLASS_TEACHER_ASSIGN | Done | ✅ | No audit entry; ID inputs |
| 39 | PROMOTION_RUN | Pend | ❌ | Everything |
| 40 | PROMOTION_APPROVE | Pend | ❌ | Everything |
| 41 | STUDENT_STATUS_CHANGE | Pend | ❌ | Everything |
| 42 | STUDENT_LOGIN_SWITCH | Done | ✅ | No audit entry; ID input |
| 43 | TERM_LOCK | Pend | ❌ | Everything |
| 44 | TERM_UNLOCK | Pend | ❌ | Everything |
| 45 | PRINCIPAL_LEAVE_APPROVER_SET | Part | 🟡 | Screen, notification, DPC endpoint |
| 46 | VC_LEAVE_APPROVER_SET | Part | 🟡 | Screen, notification, DPC endpoint |

### Platform

| # | Story | Audit | Real | What's missing |
|---|---|---|---|---|
| 47 | PLAN_MANAGE | Part | 🟡 | Create, edit, entitlements |
| 48 | TENANT_CREATE | Done | 🟡 | No first admin created (T09); realm failure ignored (E14); dedicated database; fallback plans |
| 49 | TENANT_ACTIVATE | Done | 🟡 | Platform Admin allowed; Product Owner can't open the screen |
| 50 | TENANT_SUSPEND_ARCHIVE | Pend | ❌ | Everything |
| 51 | TENANT_SETTINGS | Pend | ❌ | Everything |
| 52 | FEATURE_TOGGLE | Pend | ❌ | Everything |
| 53 | INVOICE_MANAGE | Pend | ❌ | Everything |
| 54 | DISCOUNT_APPLY | Pend | ❌ | Everything |
| 55 | OVERDUE_FLAG | Pend | ❌ | Everything |
| 56 | SUPPORT_TICKET_RAISE | Pend | ❌ | Everything |
| 57 | SUPPORT_TICKET_MANAGE | Pend | ❌ | Everything |
| 58 | SUPPORT_ACCESS_GRANT | Pend | ❌ | Everything |
| 59 | SUPPORT_ACCESS_USE | Pend | ❌ | Everything |
| 60 | RELEASE_MANAGE | Part | ❌ | Everything |
| 61 | INTEGRATION_TYPES_MANAGE | BE | 🟡 | Product Owner can't open the screen |
| 62 | PLATFORM_AUDIT_VIEW | BE | 🟡 | Nothing writes the data, so it's empty; incidents |
| 63 | USAGE_COUNTS_VIEW | BE | 🟡 | Tenant usage and reports |
| 64 | SECURITY_POLICY_SET | Pend | ❌ | Everything |
| 65 | BACKUP_STATUS_VIEW | BE | 🟡 | Tenant endpoint for IT; not real data |
| 66 | PLATFORM_USER_MANAGE | Done | 🟡 | Deactivation; nobody can use the screen |
| 67 | PLATFORM_REPORTS_VIEW | Pend | ❌ | Everything |

---

## 8. Recommended order of work

| # | Work | Why now |
|---|---|---|
| 1 | Fix the 4 lint errors | CI is red, which blocks everything else |
| 2 | Start the stack; run the `*IT` tests and `run_all.sh`; retest this report live | Proves what really works |
| 3 | Add the missing error handlers (E01, E08–E13) in the D5 error format (PO-2); write audit to `app.audit_outbox` (E04–E06) | Removes most 500s; the handlers are a small change with a big benefit |
| 4 | Fix institution scope (Gap 1) **together with** target-scope checks (T01, T02, T19) | Fixing only the first opens a security hole |
| 5 | Stop false success: Keycloak failures, first admin, unlock (T09, T10, T11, T13) | Accounts must actually work |
| 6 | Remove sample and fake data from real screens (Section 13) | People must be able to trust what they see |
| 7 | Fix role-based routes and menus (Gap 2); build the Assign Role screen | Makes built features reachable |
| 8 | Inline errors everywhere, plus consistent loaders and states (Sections 12, 13) | Fix once in shared components, then roll out |
| 9 | Login security (Gap 3) | Must be right before real users |
| 10 | Audit trail, including the seven actions with no entry (Gap 4, T26) | Every story's Definition of Done needs it |
| 11 | Replace ID inputs and the role field (Gap 6); add the leave-approver screen | Lets admins actually finish setup |
| 12 | Academic structure (Gap 7) | Later sprints depend on it |
| 13 | Approvals module (Gap 5) | Many stories wait on it |
| 14 | Students and guardians, then remaining stories epic by epic | Fills in the rest |
| 15 | End-to-end tests and the API master list; run `*IT` tests in CI | Needed for the exit criteria, and to stop these bugs coming back |

Each item still follows the `CLAUDE.md` process: implementation brief first, approval, then build.

---

## 9. Decisions needed

| # | Question | Recommendation |
|---|---|---|
| 1 | **Temporary passwords** are returned by the API for user creation, top users, platform users and password reset. Keep them? | Keep only for printed student first-login passwords, which the documents define. Nothing authorises showing staff passwords to an admin, and D5's responses contain none. Use invitations for others. Never log them. |
| 2 | **Two-step method:** authenticator app or SMS code? | Authenticator app (TOTP) first: built into Keycloak and has no SMS cost. |
| 3 | **Can a tenant-wide role act on a college below it?** | **Closed for University Admin by PO-1: yes, all colleges of that university.** Whether other tenant-wide roles work the same way follows D4 §2.2 and the permission rules, and is checked in Phase 1. |
| 4 | **Should Platform Admin be able to activate tenants?** | **Closed: no.** Product Owner only. Fix the code. |
| 5 | **Dedicated databases:** the documents say own database by default, and the code offers only shared. Is that OK for now? | This is a deviation, so it needs a recorded Product Owner decision. |
| 6 | **Extra fields in Section 6** (address, emergency contact, board code, GSTIN, date of birth, staff code). | Decide with V4-OD-01. None of these is in the prototype. Address and emergency contact seem the most needed. |
| 7 | **Approvals:** build one shared module first? | Yes. It avoids redoing #26 and #28 later. |
| 8 | **May we start Docker and test in a browser?** | **Closed by PO-4: yes.** Testing happens in Phase 2. |
| 9 | **First admin on tenant creation (T09).** | **Closed: invite the first admin.** D1 §11.1 step 8, §16 and D7 F02_T05. |
| 10 | **Unlock (T11):** keep the user's password, or force a new one? | Keep it. Clear the lock only, which matches "unlock before the 15-minute lockout ends". |
| 11 | **Sample data on platform screens:** remove all of it now? | Yes. Show real data, or a clear "not built yet" message. |
| 12 | **Error format (OD-74).** | **Closed by PO-2: adopt D5's format.** Record it in the governance log. |
| 13 | **Two roles at the same scope.** | **Closed by PO-3: allowed, with permissions never merged.** Record it in the governance log, since it differs from Addendum rule 10 as coded. |
| 14 | **Reactivating a user.** D10 offers Reactivate; a code comment says deactivation is permanent. | Check D3 and decide. |

---

## 10. Housekeeping risks

- **Work isn't committed.** About 176 files are changed or new. A lost laptop or a bad merge could lose the sprint's work.
- **`.gitignore` hides documents.** `*.md` and `/docs/` are ignored, so this file, `CLAUDE.md` and any new doc won't reach the repository without `git add -f` or a rule change.
- **`mvn verify` skips RLS tests.** A green build says nothing about tenant isolation, so a CI job should run the `*IT` tests.
- **One integration test can delete dev data.** `TenantIssuerAuthenticationManagerResolverIT.deleteAnyExistingTenantForRealm` deletes the tenant, its users and identity links for the realm it uses, in the shared dev database. Whether those deletes are rolled back hasn't been checked, so check before running it.
- **`app/.env.example` breaks local tenant login** if copied as-is, because it sets `VITE_API_BASE_URL` to `localhost`.

---

## 11. Internal server errors (HTTP 500)

**Why you see so many:** `GlobalExceptionHandler` has a catch-all (line 95) that turns anything it doesn't recognise into a 500 with "An unexpected error occurred". The user gets no useful message, and many of these are really the user's input or a missing page.

**From the logs:** 13 of the 15 logged 500s came from a web address missing from the running backend, for example `GET /api/platform/platform-users`. The screen was calling an endpoint that the running build didn't have. These logs are from 5–6 November and predate some current code.

### Normal actions that fail

| ID | Severity | Steps | Expected | Actual | Cause | Confidence |
|---|---|---|---|---|---|---|
| E01 | Blocker | Call any address that doesn't exist | 404 | **500** | `NoResourceFoundException` isn't handled | Seen in log |
| E02 | Blocker | Tenant admin creates an institution, department, program, session, calendar event or weekly-off rule | Created | **500**, database refuses the row | Scope is always `NONE` (Gap 1) | By code |
| E03 | Critical | Call `POST /api/platform/tenants` with `ISOLATED` | 400 or 422 | **500** (`ck_tenant_r2`) | The screen disables this option; the API doesn't check it | Seen in log |
| E04 | Critical | Create an academic session or master calendar event; set a leave approver; activate a tenant | Saved | **500**, change rolled back | These event types aren't allowed by the audit table's check rule | By code; same failure in log for older types |
| E05 | Critical | Any audited action | Saved, with a valid audit row | Audit write fails, so **500** and the whole action is rolled back | Audit writes to the legacy `public.audit_logs`, which rejects these entries. `AuditLogService` catches the error, but the shared transaction has already failed. | By code |
| E06 | Critical | Any audited action on a fresh database | Saved | **500** | `audit_logs` isn't in `sql/`; `patch_audit_logs_constraint.sql` isn't run by `dev-up.ps1`; the failed grant is hidden | By code |
| E07 | Major | Activate a tenant when the audit write fails | Clear result | **500**, though the tenant may be active | Two databases' transactions aren't joined | Likely |

**Real-world scenario:** Ravi sets up the 2026-27 academic session and clicks Save. He sees "An unexpected error occurred" and nothing is saved. The session was valid; only the audit entry was refused. He retries several times and then calls support.

### Bad input that should get a clear message

| ID | Steps | Should return | Actually returns |
|---|---|---|---|
| E08 | Put a bad ID in the web address, such as `/api/tenants/abc/users` | 400 | **500** |
| E09 | Send malformed JSON, an empty body, or an invalid option such as `userType: "X"` | 400 | **500** |
| E10 | Wrong method; wrong content type; missing required parameter | 405; 415; 400 | **500** |
| E11 | Upload a profile photo over 2 MB | 413 "File too large, max 2 MB" | **500** |
| E12 | Two people create the same code or email at once; any database rule failure | 409 or 400 | **500** (no handler for `DataIntegrityViolationException`) |
| E13 | Log in while Keycloak is down | 503 "Sign-in is unavailable" | **500** |

### Half-finished actions

| ID | Steps | What goes wrong |
|---|---|---|
| E14 | Create a tenant and retry after a failure, or with Keycloak failing | The realm is created before the save, so retries leave orphan realms. If Keycloak fails, the tenant is still saved, and nobody can log in. |
| E15 | Create a user when the database save fails | The Keycloak account is left behind, and a retry may conflict. (Likely) |
| E16 | Use an `ISOLATED` tenant whose secret is missing | **500** instead of a clear error |

**Fix for most of these:** add handlers to `GlobalExceptionHandler`, using the codes D5 defines (`RESOURCE_NOT_FOUND`, `MALFORMED_JSON`, `VALIDATION_FAILED`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `PAYLOAD_TOO_LARGE`, `DUPLICATE_RESOURCE`, `DEPENDENCY_UNAVAILABLE`). Treat Keycloak failure as a failure.

**Audit:** write to the design's `app.audit_outbox` **in the same transaction** as the change (D4 §2.5). If the audit write fails, the action should fail too. The problem to fix is that these writes are invalid, not that they share a transaction.

**Correction:** an earlier version of this report said to use a separate transaction and add `audit_logs` to the SQL bundle. Both go against the design.

---

## 12. Inline field errors

**Result: 28 forms checked. Only 1, Profile, shows errors under the field.** No form moves the cursor to the first bad field. `aria-invalid` is only on Profile, so screen readers aren't told about errors elsewhere.

**Why you can't tell which field failed:**
1. **Field details are thrown away.** On a 400 the backend sends `fieldErrors` with one message per field. `ApiHelper.normalizeError`, `readErrorMessage` and the page-level catches read only `error`, which says "Validation failed".
2. **The shared input can't show an error.** `TextField` has no error prop, `aria-invalid` or `aria-describedby`.
3. **The helper is unused.** `normalizeFormErrors` exists but nothing calls it. `ApiErrorResponse` also declares `errors`, while the server sends `fieldErrors`.

| Form | Client check | Inline error | Server field errors used | What the user sees |
|---|---|---|---|---|
| Login | Blank only | No | No | One banner |
| Change password | Blank, length, match | No | No | One banner, such as "Fill in all three fields." |
| Profile | Mobile format | **Yes** | **Yes** | Message under the field |
| Create user, Edit user, Create-user check | Browser only | No | No | "Validation failed" |
| Bulk upload | File or text present | Row table only | No | "Validation failed" for form errors |
| Top user | Browser only | No | No | "Validation failed" |
| Create tenant, Activate tenant | Browser only | No | No | Server text, or raw "Request failed with status code 500" |
| Platform user | Browser only | No | No | Same as above |
| Integration type create, edit | Not confirmed | No | No | Same as above |
| Create institution | Code and name, one banner | No | No | "Validation failed" |
| Block request; block decision | Length; none | No | No | Banner |
| Principal and VC leave approver | Browser only | No | No | Banner |
| HOD, Mentor, Class Teacher, Student login | ID format, one banner | No | No | Banner |
| Local calendar event, Academic session, Master event | Blank, dates, one banner | No | No | Banner |
| Weekly off; Department; Program | Blank or browser only | No | No | Banner |

### Rules the server enforces but the screen doesn't check or show

| Field | Server rule | Screen |
|---|---|---|
| Tenant name | 2–200 characters | Required only |
| Subdomain | A fixed pattern | Rule not shown |
| Tenant mobile | `+` then 8–15 digits | No check, label says "+91 format" |
| Department code | Capitals, digits, `_`, `-`, 2–20 | Required only, so `cse` fails with "Validation failed" |
| Program code | Capitals, digits, `_`, `.`, `-`, 2–20 | Required only |
| Department / program name | 2–150 / 2–200 characters | Required only |
| Session label | 4–20 characters | Blank check only |
| Aadhaar last 4 (user) | 4 digits | Length limit only |
| Postal code | 3–12 characters | Rule not shown |

### Rules that are wrong on both sides

| Field | Problem |
|---|---|
| New password | Minimum 6 on screen and server; the spec says 8 |
| Tenant Aadhaar last 4 | Server accepts letters, though it should be digits |
| Calendar event title | Server allows blank; only the screen checks it |
| Postal code | Allows 3–12 characters, not a 6-digit PIN. Confirm whether non-Indian codes are needed. |

### Test cases

| ID | Severity | Steps | Expected | Actual |
|---|---|---|---|---|
| F01 | High | Create Tenant with mobile `98765` | Error under Mobile | Banner: "Validation failed" |
| F02 | High | Create Department with code `cse` | Error under Code, showing the format | Banner: "Validation failed" |
| F03 | Medium | Change password with current password blank | Error on that field | "Fill in all three fields." |
| F04 | Medium | Academic session with label `A` | Error under Label | Banner: "Validation failed" |
| F05 | Medium | Submit a form with several bad fields | All marked, cursor on the first | One banner at best |
| F06 | Medium | Use a screen reader on a bad field | Error is announced | Nothing, except on Profile |
| F07 | Medium | Network off, Create Tenant | Friendly message | Raw "Network Error" |
| F08 | Low | Look for required markers across forms | Consistent | Only on `TextField`; raw inputs have none |

**Fix:** follow D9 P4 and C2.
- On submit, show an error summary at the top, move focus to it, link each item to its field, and show the same message under the field.
- Validate on blur and on submit. Mark Required or Optional in text. Never clear what the user typed.
- Use D9's wording, such as "Enter [field name]." and "We couldn't save your changes. Your entries are still here…".
- Add error support to `TextField`; the `aria-invalid` CSS already exists. Read field errors in one shared helper, using the D5 format (PO-2).
- Phone: D9 defaults to +91 with 10-digit validation, while the app asks for `+` and country code.

---

## 13. Loaders, screen states and fake data

### Fake data shown as real

| ID | Severity | Steps | Expected | Actual | Evidence |
|---|---|---|---|---|---|
| L01 | Critical | Open Tenants with the API failing | Error and retry | Sample tenants shown as real, error hidden | `PlatformPages.tsx:16-20,175` |
| L02 | Critical | Open Tenants with zero tenants | Empty state | Sample tenants stay | `PlatformPages.tsx:161` |
| L03 | Critical | Click Activate on sample `TEN-0003` | Not possible | Request sent with a fake ID | `PlatformPages.tsx` |
| L04 | Critical | Open Platform Overview | Real counts | Always sample numbers | `PlatformPages.tsx:104-105` |
| L05 | High | Plans API fails, then create a tenant | Error, submit blocked | Fake plan and price can be submitted | `PlatformPages.tsx:215,363-365` |
| L06 | High | Open Plans or Support | Real data, or "not available yet" | Static data, including ticket `SUP-2026-00398` | `PlatformPages.tsx:607,612` |
| L07 | High | Roles API fails | Error | Static role list shown | `InstitutionAdminPages.tsx:1133` |

**Real-world scenario:** The Product Owner checks the Overview before a customer meeting and quotes the tenant count. That number is sample data. On a day the API fails, the Tenants page still looks healthy, so nobody knows anything is wrong.

### Loaders

| Where | Today | Should be |
|---|---|---|
| App start | **Blank white page, no timeout** | Full-page loader, then an error with retry |
| Platform, Profile, Sessions, Users | Spinner | OK |
| Tenants list | Spinner only when empty, and sample data keeps it from being empty | Spinner or skeleton on every load |
| Institutions, Setup, Structure | Plain "Loading…" text | Spinner |
| Role switch | **None, failures hidden** | Loading state; on failure, a message and the old role restored |
| Form submit buttons | Busy state and double-click protection | OK, keep |
| Skeleton loaders; request timeout | None; `axios` has no timeout | Add a timeout with a friendly message |

There are also two different Button components, one using `loading` and the other `busy`. Pick one.

### Screen states

| Page | Empty | Error + retry | Permission denied | Success message |
|---|---|---|---|---|
| Login | n/a | Banner | No | Redirect |
| Change password | n/a | Banner | No | **Redirect, no message** |
| Dashboard | Placeholder "—" | n/a | n/a | n/a |
| Profile | n/a | Yes | No | Yes |
| Sessions | Yes | Yes | No | Redirect |
| Tenants | Yes | **Hidden** | No | Yes |
| Platform users, Audit, Usage, Backup, Integrations | Yes | Banner, no retry | Message only | Yes |
| Institutions | Yes | Yes | No | Banner |
| HOD, Mentor, Class Teacher, Student login | n/a | Banner, no retry | **Yes** | Banner |
| Calendar, Weekly-off, Structure | Yes | Banner, no retry | Partial | Banner |
| User list and detail | Yes | Yes | Partial | Banner |
| University Users, Missing Roles | **Static prototype text** | n/a | n/a | n/a |

### Other UI test cases

| ID | Severity | Steps | Expected | Actual |
|---|---|---|---|---|
| L08 | High | Force a 500 on Tenant or Platform forms | Friendly message | Raw "Request failed with status code 500" |
| L09 | Medium | Open Calendar, Weekly-off or Structure | Data loads | Empty until an ID is pasted and "Load" is clicked |
| L10 | Medium | Fail a list load on a Platform page | Retry button | Must reload the page |
| L11 | Medium | Click outside a half-filled modal | Warning, or stays open | Closes, and typed data can be lost |
| L12 | Low | Press Escape or Tab in a modal | Closes; focus stays inside | No Escape handler; no focus-trap code found |
| L13 | Low | Switch to dark mode | Readable everywhere | 49 hardcoded colours and 203 inline styles may break it. **Not viewed.** |

---

## 14. Negative and permission test cases

"Actual" comes from reading the code. Tests marked Critical must be repeated live.

### Failed

| ID | Story | Severity | Scenario | Expected | Actual | Evidence |
|---|---|---|---|---|---|---|
| T01 | #13 | **Critical** | IT Admin at School A resets the Vice-Chancellor's password | Refused | Scope comes from the request; the target isn't checked. **New password is returned to the caller.** | `UserPasswordResetUnlockController.java:82-90` |
| T02 | #8, 11, 12 | **Critical** | College A admin lists, edits or deactivates a College B user | 404 | List uses `findAll()` filtered only by tenant. Edit and deactivate have the same gap. | `UserViewController.java:80` |
| T09 | #48 | **Critical** | Create a tenant with first admin details | Admin account exists | Details are only written to the audit text and echoed back | `TenantManagementService.java:150-165` |
| T10 | #9, 22, 48 | **Critical** | Keycloak fails during creation | Error; nothing saved | Failure ignored, random ID stored, success shown with a password that can't work | `UserAccountService.java:121-124` |
| T11 | #13 | High | Unlock a locked user | Same password works again | Password replaced with a random value nobody sees, so the user stays locked out until a reset | `UserPasswordResetUnlockController.java:~121` |
| T13 | #10 | High | Import users successfully | Users can log in | Temp passwords are discarded | `UserBulkUploadService.java:447` |
| T12 | #10 | High | One row fails during import | Other rows import | Risk of total rollback and a 500; Keycloak users stay behind (inferred) | `UserBulkUploadService.java:447` |
| T17 | #26 | High | Activate a blocked or archived institution | Refused | Only `ACTIVE` is stopped in this code; database gate not checked | `InstitutionService.java:153` |
| T18 | #28 | High | Approve a block with no request | Refused | Requests are only audited, not stored; any institution can be blocked | `InstitutionService.java:281` |
| T19 | #25 | High | A student lists institutions | 403 | No permission check on list or get; RLS hides rows today only because of Gap 1 | `InstitutionService.java:197,210` |
| T20 | #45 | High | University college chooses Registrar or VC as approver | Allowed | Refused, because `under_university` is always false | `InstitutionService.java:103`, `LeaveApproverService.java:82` |
| T23 | #11 | High | Edit email to a duplicate or invalid value | 400 or 409 | No validation or duplicate check; Keycloak not updated, so login stays on the old email | `UserEditController.java` |
| T26 | #11, 32, 33, 35, 37, 38, 42 | High | "Every change logged" | Audit entry | **No audit calls at all in these seven.** Denied attempts aren't logged either. | Files checked directly |
| T06 | #32 | High | Unknown academic session ID in the request body | 422 `REFERENCE_NOT_FOUND` (D5) | Not checked; database refuses it, giving a 500 | `CalendarEventService.java:60` |
| T07 | #32 | Medium | Blank title | 400 | Saved | `CalendarEventRequest.java:23` |
| T15 | #10 | Medium | CSV name `=HYPERLINK(...)` | Rejected or neutralised | Not checked; dangerous if exported to Excel | `UserBulkUploadService.java` |
| T16 | #10 | Medium | Very large CSV | Size or row limit | No limit; whole file held in memory | `UserBulkUploadService.java:144` |
| T21 | Scope | Medium | `tenantId` in the address belongs to another tenant | 404, per the prompt | 403, which reveals the tenant exists | `UserRequestSupport.java:32` |
| T22 | #9 | Medium | INSTITUTION scope with no ID | 400 | Silently becomes tenant scope | `UserAccountService.java:208` |
| T24 | #5 | Medium | `.exe` renamed with an image type | 400 | Only the declared type is checked | `ProfileService.java:94` |
| T25 | #12 | Medium | Deactivate the last admin | Refused or warned | No check, so a tenant can lose its only admin | Not implemented |
| T28 | #31, 32 | Medium | Holiday on an exam day; mid-year date change | Blocked; notified | Neither happens, though the audit text says "notified" | `MasterCalendarService.java:162` |
| T14 | #10 | Low | Row with 2 errors | Total counts rows | Counts errors | `UserBulkUploadService.java:287` |
| T29 | #35, 37, 38 | Low | Target isn't faculty | Likely 422; D5 also has a "wrong role" row that says 403, so confirm | 403 | `HodAssignmentService.java:64` |
| T30 | #48 | Low | Use an inactive plan | Refused | Plan status isn't checked | `TenantManagementService.java` |

**Real-world scenario (T01):** A School IT Support person wants access to the Vice-Chancellor's account. They call the password reset API for the VC's user ID, with their own school as the scope. The check passes because it looks only at their own permission. The API returns the VC's new temporary password. Today Gap 1 happens to block college-level roles from this, so it must be fixed together with Gap 1.

**Real-world scenario (T11):** A teacher mistypes a password five times. The admin clicks Unlock and tells them to try again. Their correct password no longer works, and nobody knows the new one.

### Passed

| Case | Evidence |
|---|---|
| Can't deactivate yourself | `UserDeactivationController.java:90` |
| Can't change your own role | `RoleAssignmentService.java:79` |
| Can't give yourself a top role | `TopUserService.java:102` |
| Duplicate email or institution code returns 409 (when not simultaneous) | Service checks |
| Plan limits for users and institutions | Enforced |
| End date before start date | Refused in calendar, session and weekly-off |
| School can't have departments | `StructureService` |
| Block request needs a reason | `BlockInstitutionRequest` |
| Aadhaar masked in CSV handling | `UserBulkUploadService` |
| XSS strings | Stored as typed, escaped by React on screen. Recheck when exports are built. |

### Other risks

- **Simultaneous requests:** Checks run before inserts, with no locking. Only one `@Version` exists in the code, so two edits overwrite each other. A simultaneous role assignment can leave two active roles, and the evaluator then denies that user.
- **Email uniqueness:** `ux_user_account_1` covers staff and governance users (`01_schema.sql:7959`), so a simultaneous duplicate gets a 500 (E12). Students and guardians aren't covered.
- **Pagination:** Only tenants and platform audit paginate. Users, institutions, departments, programs and import errors return everything, which will be slow at university size.

---

## 15. How good are the existing tests?

| Question | Finding |
|---|---|
| Do stories have ALLOW, DENY, SCOPE and GUARD tests? | 11 stories have all four named: #6, 9, 10, 14, 15, 22, 25, 26, 27, 28, 48. |
| Equivalent tests without the names | #8, 11, 12, 13 |
| Missing a cross-institution SCOPE test | #32, 33, 35, 37, 38, 42 |
| No named four-action set | #31, 45, 46, 66 |
| Do tests use a real database? | **No.** All `*Test` classes use mocks or plain logic. |
| Which tests use real Postgres? | Only 2 `*IT` classes, and `mvn verify` skips them |
| Designed action tests (D6) | 260 `ACT-` tests for 65 stories. **44 IDs appear in test code** (the 11 stories above). Some appear only in comments, and not every ID was checked against a real assertion. |
| End-to-end tests | None. **0 of the prompt's 12 scenarios** (E2E-001, 002, 005, 006, 007, 010, 020, 026, 033, 035, 036, 037) appear in test code. |
| Do frontend tests go through routes? | The unreachable pages pass because tests open them directly (Gap 2) |

**So 253 passing tests and these defects are both true.** Many of these defects come from how the app, database and Keycloak work together, and no test runs them together.

**Tests to add first:**
1. Integration tests on real Postgres: tenant admin creates an institution; college admin reads departments; College A can't reach College B.
2. A controller-level test for each error handler in Section 11.
3. Route-guard tests: each role reaches its own pages.
4. Keycloak failure tests: nothing is saved and an error is returned.
5. A form test for each of the 28 forms: bad input shows a message under the field.

---

## 16. Validation against the documents

### What was read

This table covers the first document check. Section 17.1 has the wider Phase 1 reading and replaces it.

| Document | Read | Not read |
|---|---|---|
| Sprint 1 prompt | All | — |
| Spec summary, governance log, role addendum, traceability update | All | — |
| D1 Product spec | V4-2.1, V4-5, V4-11.1–2, V4-19.1–2; §6.1–6.5, §7.0, §7.1 (rows), §7.13–14, §8.1, 8.2, 8.20, §11.0–11.2, §12.3, §26.1–26.3 | Everything else; §26 row by row |
| D2 Governance register | 12 Sprint 1 action rows, selected open decisions | Other rows |
| D3 Roles | Screens, SoD, custom roles, lifecycle, permission codes, legend, roll-down rules | **Master Matrix cells, Roll-down Map rows,** change logs |
| D4 Database | §2.2–2.5, audit tables | Other sections |
| D5 API | OpenAPI by script (all paths, operations, schemas); error sections | §7 idempotency scopes; response shapes compared for 9 operations only |
| D6 QA | Phase 1 Action Tests, E2E sheets | Endpoint Inventory, API-DB Field Map |
| D7 Workflow | Flows 01, 02, 03, 04, 15, 16, plus keyword searches | Most of the 3,125 nodes |
| D8 Security | Report §1, §3, §7, Appendix A | **The D8 spreadsheet** (could not be opened) |
| D9 Design | C2, C5, C10, P2, P4, P5, part of P12, microcopy | Token tables |
| D10 Prototype | Roles, navigation, decision register, sign-in, user wizard, guardian, deactivate, tenant, institutions, academic | Other pages; not viewed in a browser |
| `sql/` | Audit, policy and index lines; action and role rows | Not run. `app.role_action` seeding not checked. |

### API result against D5

Of 176 Sprint 1 operations, matched by method and path only:

| Result | Count |
|---|---:|
| Matches the `/v1` path | 34 |
| Same path, `/api` prefix only | 17 |
| Different path, close equivalent | 6 |
| **Missing** | **119** |

**None of the 57 fully conforms,** because all use the wrong error body. Also:

| Item | D5 | Code |
|---|---|---|
| Error body | `{error:{code,message,status,correlationId,auditId,retryable,details,...}}` | `{timestamp,status,error}` plus `fieldErrors` |
| `If-Match` | Required on 46 Sprint 1 operations | Not read anywhere |
| `Idempotency-Key` | Required on 12, optional on 23 | Not read anywhere |
| Deactivate user | Reason required | Institution ID, no reason |
| Password reset, unlock | Empty body | Institution ID required |
| User import | `sourceFileId` | Raw CSV text |
| Mentor, class teacher | `effectiveFrom` required | Missing |
| Calendar event | `sourceLevel` required | Missing |
| Create-user responses | No password | Password returned |

30 code routes aren't in D5, including all `/api/auth/*` routes.

### Screens against D3 and D7

| Screen | Status |
|---|---|
| RP-01 Roles | Exists, unreachable, static fallback |
| RP-02 Role detail, RP-03 Custom role, RP-04 Assign roles, RP-05 User access, RP-07 Delegations, RP-08 Access review | **Missing** |
| RP-06 Missing roles | Static and unreachable |
| Two-step code (F01_A01) | Missing |
| Blocked tenant, read-only trial, module disabled (F01_E02, R01, E04) | **Missing states** |
| Create user (F16) | D10 has a three-step wizard; the app has one form |
| Sign-in | D10 says "School email or student ID"; the app is email only |

**University Admin menu in D10:** Overview, Institutions, Academic structure, Users & roles, Roles & permissions, Missing roles, Academic calendar, Fee policy, Reports. The app has 4 of these.

### New defects from the documents

| ID | Severity | Rule | Today | Reference |
|---|---|---|---|---|
| D01 | **Critical** | One active role per request | Permission check receives only tenant and user. The active role is kept in memory, lost on restart and not shared between servers. **Switching roles doesn't change permissions.** | D3 CR-11; `RbacPrincipal.java:12`, `UserMeService.java:65` |
| D02 | **High** | IT may assign only roles with no approval, finance, HR or exam rights | Checks the role's name, so a custom role named `OFFICE_HELPER` with fee rights passes | D3 CR-05; `RoleAssignmentService.java:416-438` |
| D03 | **High** | Database session settings are per transaction, "never session-wide" | Session-wide `SET` on each connection borrow, with a code comment defending it | D4 §2.2, §2.4 (SEC-04) |
| D04 | High | Idle logout: 15 minutes for Admin, finance, HR; 60 for teachers, parents, students | 30 minutes for everyone | D1 §11.0 |
| D05 | High | Tenant setup steps recorded and undone on failure; first admin invited | Not done | D4 §2.4, D1 §11.2, D7 F02_E02 |
| D06 | High | Don't finalise deactivation without an eligible successor | No check | D7 F16_R06 |
| D07 | Medium | Permission changes apply within 1 minute; a new device logs out the old one | Not done or not confirmed | D1 §8.1, §11.0 |
| D08 | Medium | Rights and approver changes notify Principal, Management or GBA | No notifications | D1 §6.3, §7.1 |
| D09 | Medium | Audit stores old and new values, masks sensitive fields, alerts the Admin on repeated denials | Not done | D1 §7.14, D4 |
| D10 | Medium | Warn at 90% of users, block at 100%; 30-day trial, then read-only | Limit block exists; warning and read-only not confirmed | D1 §7.13 |
| D11 | Medium | Failures show a code and an audit ID; error reference such as `ERR-7F3A21` | Neither | D7 F18_E03, D9 P5 |
| D12 | Medium | Partial data, version conflict, offline banner, four kinds of empty state | Not built | D9 P5 |
| D13 | Medium | Skeletons for regions, spinner with text for actions, progress bar for imports | No skeletons or progress bars | D9 C10 |
| D14 | Medium | On role switch, clear cached data and announce the new context | Not confirmed | D9 P2 |
| D15 | Medium | Warn about unsaved changes on long forms | None | D9 C2, P4 |
| D16 | Medium | WCAG 2.2 AA | Gaps in F06 and L12 | D8 SEC-V4-15 |

**To check later:** whether the token's tenant matches the subdomain and path (D4 §2.4), self-hosted fonts (V4-F-16), and whether `edu_app` may read `platform.*` (D4 §2.3 says no; `LOCAL_DEV.md` says read-only).

### Findings the documents confirm

| Finding | Reference |
|---|---|
| Gap 1 institution scope | D4 §2.2, governance log OD-03 |
| Gap 2 reachability and denied state | D3 RP-01, RP-06; D9 P2 |
| Gap 3 login rules (26 two-step roles in the catalogue) | D1 §8.1, §11.0, V4-11.2; D3 Role Catalogue; D8 §3.2 says "designed, not demonstrated" |
| Gap 4 audit design | D1 §7.14, D3 lifecycle, D4 §2.5, `02_rules.sql:35` |
| Gap 5 approvals and chains | D1 §7.1, D3 SoD |
| T01, T02, T19 scope | D3 action map; D8 SEC-V4-01 (Critical, Open) |
| T09 first admin | D1 §11.1, §16; D7 F02_T05 |
| T21 foreign tenant gets 404 | D1 V4-11.2, D5 |
| T24 upload inspection; T16 and pagination | D8 SEC-V4-10, SEC-V4-12 |
| E14 safe retry and cleanup | D1 §11.2, D7 F02_E02 |
| Sections 12 and 13 | D9 C2, C5, C10, P2, P4, P5 |

**Approval chains:** new institution and block both go from University Admin to Vice-Chancellor (D1 §7.1). In a standalone tenant, the Principal approves activation by roll-down (D1 V4-19.1).

### Corrections made to this report

| Earlier statement | Now |
|---|---|
| E05: action should succeed, audit in its own transaction | Audit is in the same transaction (D4 §2.5) |
| Add `audit_logs` to the SQL bundle | Removed; move to `app.audit_outbox` |
| No unique rule on email | Exists for staff and governance |
| T06 expects 404 | 422 `REFERENCE_NOT_FOUND` |
| #6 Role switch is Built | Partial (D01) |
| #15 API rule is in place | Can be bypassed (D02) |
| Gap 2 fix uses the role | Use the actions the server allows (D9 P2) |
| Section 6 fields read as requirements | Mostly suggestions; the field list is open |
| Temporary passwords in three places | Four, including platform users |

### Conflicts in the documents (not resolved here)

| Topic | One source | Other source |
|---|---|---|
| Scope inheritance **(decided for University Admin by PO-1)** | D1 §26.1 rule 2 and Addendum 8–9: never inherit | D1 §12.3, V4-19.1, `ALL` in `p_institution` |
| Roles at one scope **(decided by PO-3)** | D3 K-010, `ux_user_role_assignment_1`: one per role and scope | Addendum rule 10 as coded: one per scope |
| Decision status | D2 and D8: OD-03, 09, 10, 80 and (D2) 134 open | Governance log: approved 2026-11-04, and declares itself authoritative |
| Audit store | D1 §7.14: write-once separate store | OD-09: database trigger, hash chain deferred |
| SQL evidence | Governance log: 97/97 on 2026-11-04 | `sql/run_results.txt`: header only. D2: 79/79. |
| Operation count | OpenAPI YAML: 408 operations, 313 paths | D5 text: 404 on 310. Sprint 1 tags 176; the prompt names 172. |
| Login API | D5, F-35: Keycloak with `recordIdentityEvent` | Code: `/api/auth/*`. OD-80's approval doesn't address this. |
| Tenant activation path | Prompt: `POST .../activation` | D7 F02_T10: `PATCH .../status` |
| Who creates tenants | D1, D3: Product Owner and Platform Admin | D10: Platform Admin only (no Product Owner persona) |
| Leave approver choices | Story 21 lists Management, Registrar or VC | Same story, changed in 3.3: Management, otherwise GBA; under a university also Registrar or VC |
| Action name | D1 §11.0: `USER_ROLE_ASSIGN` | D2, D3, prompt: `ROLE_ASSIGN` |
| Reactivation | D10 offers Reactivate | Code comment: deactivation is permanent |
| Aadhaar last 4 | In app forms | Not in D10; source R2-34 not traced |
| Dark theme | D9 and summary: status differs (proposed or beta) | `CLAUDE.md`: check both themes |
| "No application exists" | D8 §1, §7, spec summary | Code now exists; these statements are outdated |

**Evidence the release gates are still unmet:** D8 lists pen test, adversarial tests and UAT. None has evidence, which supports the "not ready" verdict.

---

## 17. Phase 1 review results

**Method:** documents and source code were read. No tests, live calls or database queries were run, and no source code was changed. Nothing here confirms or refutes a defect; that is Phase 2.

### 17.1 What was read and not read

| Document | Read | Not read |
|---|---|---|
| Sprint 1 prompt | All | — |
| Spec summary, governance log, role addendum, traceability update | All | — |
| D1 | Part I V4-2.1, V4-4, V4-5, V4-11.1–2, V4-15, V4-19.1–2. Part II §5.2, §5.2.1, §6.1–6.5, §7.0, §7.13, §7.14, §8.1, §8.2, §8.20, §11.0, §11.1, §12.3, §12.12, §26.1. §7.1 by keyword. Samples of §13.1, §25.3, §26.4–5, §26.24. | **§14; most of §12 and §13; §11.2–11.6 in this pass;** §2, §6.6, §10 matrix, V4-19.3 map rows, rest of §26 |
| D2 | All 67 action rows; open decisions by filter; V4.0 decisions and notes; source conflicts | Role x Action, Actions per Role, GAP Data, Audit Findings, Constraint Catalogue, history |
| D3 | All governance sheets, screens, permission codes, legend. By script: Template Action Map (1,851 rows), Role Catalogue (71), Roll-down Map (counts and samples) | Master Matrix beyond headers and University Admin; change logs, history, baselines, area profiles |
| D4 | §2, §4.5–4.7, §4.26, §6 K-001–K-030, §10.1, §10.3, §11.2, §16.4 | **Most of 8,300 lines,** including most of the §5 dictionary, §7, §13, §14 |
| `sql/` | All of `08_v40_rules`; parts of `01`, `02`, `03`, `10`; titles of all 97 tests | Other tables' rules; test bodies. **Not run.** |
| D5 | §0–3, §6.1, §6.3–6.5, §7, §10, §13. OpenAPI: 408 operations by script | §9, most of §11, §12, Appendix A; each Sprint 1 request schema |
| D6 | Headers, Phase 1 Action Tests, E2E, DB Test Execution, Roll-down Requirements | Endpoint Inventory and API-DB Field Map (sampled only) |
| D7 | 133 of 3,125 nodes in full (F01–04, F14–16, F18); keyword search of all | Action catalogue, role journeys and approval chains only sampled; module map, other flows |
| D8 | Report §3.1–3.2, §7, Appendix A. Spreadsheet: controls, verification, findings | Other spreadsheet rows; Appendices B–E |
| D9 | Colour, spacing, type, C1, C2, C5, C7, C9, C10, C13, C15, P1–P5, P7, P12, P13 | Motion, icons, charts, calendar, P6, P8–P11 |
| D10 | Roles, navigation, decision register, and Sprint 1 pages | Non-Sprint 1 pages. **Not viewed in a browser.** |
| Code | Backend and frontend inventory as on disk on 7 November | Line-by-line logic; edits made after the review |

**Authority order (D1 V4-2.1):** Product Owner decisions, then D1 Part II with D2 and D3, then D4 and D5, then D7, D9 and D10 as visual aids.

### 17.2 Effect of your decisions

| Decision | What the documents say | What stays limited or still needs recording |
|---|---|---|
| **PO-1 University Admin** | Supported by D1 §12.3, §8.2 and D2. Widens **scope, not actions**. | Never enters marks, marks attendance, processes payroll or takes fee payments. Approvals stay with the chain. Custom roles are university-office only (CR-02). `HOD_ASSIGN` stays with College Admin. D4 §2.2 lists other tenant-wide roles for `ALL`, but this decision covers only University Admin. |
| **PO-2 Error format** | D5 §6: `{error:{code,message,status,correlationId,auditId?,retryable,timestamp,details?,retryAfterSeconds?,context?}}`, with 89 codes and field errors in `details[]`. | D2, D5 and the YAML still mark OD-74 open, so record it in the governance log. D5 has small internal gaps, such as a test expecting `INVALID_TYPE`, which isn't in the catalogue. |
| **PO-3 Multiple roles** | Supported by D1 V4-4, §5.2, §5.2.1; D3 K-010, CR-11; D4 `ux_user_role_assignment_1`; D5; D6 E2E-005 ("permissions are not unioned"). **No schema change needed.** | Addendum rule 10 should be updated: many held, one active per request. One active role means one token or session context, chosen by assignment ID and validated by the server. Nothing carries over, and every switch is audited with both contexts. No SQL test proves two roles at one scope. |
| **PO-4 Live testing** | D8 says API authorization, two-step login and lockout are "designed, not executed". | Scheduled for Phase 2. |

### 17.3 Standards now established for the work ahead

**Database (D4)**
- Settings are per transaction with `set_config(name, value, true)`. Session-wide `SET` is forbidden, and a lint must fail the build on it.
- `app.institution_scope` is `ALL` for tenant-wide roles and empty otherwise. `app.institution_ids` comes from the active role's assignments.
- `edu_app` gets nothing in `platform.*` (DB-59). `edu_platform` gets `platform` and `ref` only.
- `app.audit_outbox` needs `active_role_code` for user events, `correlation_id` and `payload_hash`, in the same transaction as the change. `edu_app` has SELECT and INSERT only.
- `app.role_action` isn't seeded by any SQL file. Tenant provisioning must copy the 65 tenant-side roles (D4 §11.2, step P04), and also invite the first Admin (P07).

**API (D5)**
- `/v1/tenants/{tenantId}`, `/v1/platform`, `/v1/internal`, `/v1/webhooks`.
- Token tenant must equal subdomain and path tenant. `tenantId` never appears in bodies.
- Out-of-scope records return 404; foreign IDs in a body return 422; denials return 403 and write a DENIED audit event.
- `If-Match` on all PATCH and state changes (46 Sprint 1 operations): missing gives 400, stale gives 409.
- `Idempotency-Key`: 12 required and 23 optional in Sprint 1.
- Pagination: `limit` 1–200, default 50, with `cursor`, returning `{items, page}`.
- `X-Correlation-Id` on every response. Slow operations return 202 with `JobRef`.

**UI (D9, D7)**
- Navigation comes from the actions the server allows, never the role name. Permission states: Allowed, Conditional (disabled with a reason), Read-only, Denied (not rendered).
- Forms: Required or Optional in text; error summary at the top with focus and links; the same message inline; values kept. Phone is +91 and 10 digits.
- States: skeleton for regions, spinner with text for actions, progress for jobs. Four kinds of empty state, plus partial data, error with reference, denied, read-only, stale and offline.
- Buttons keep their label, show a spinner and ignore repeat clicks. Dialogs use native `dialog`, trap focus, close on Escape and return focus. Toasts are never used for form errors.
- Dark theme: Beta; follow the device and remember the user's choice.

**Screens without a D10 design:** two-step code, first-login change, devices, top users, leave approvers, go-live readiness, plan edit, feature toggles, tenant settings, suspend and archive, invoices, discount, overdue, tickets, releases, security policy, program rules, subject allocation, promotion, term lock, student status, student login, weekly off, delegation, bulk approval, guardian change. D10 covers 8 personas of 71, and no rule defines menus for the rest. These screens follow D7 and D9 patterns and need a proposal in each brief.

### 17.4 Code inventory on 7 November (counted, not run)

| Item | Count or finding |
|---|---|
| Backend routes | **84**, up from 73 |
| Entities | 34: 23 `app`, 7 `platform`, 3 `ref`, plus legacy `audit_logs` |
| Backend tests | 43 classes, 279 `@Test`: 13 on real Postgres, 251 with no real database (classified by text search), 14 MockMvc, 1 Spring context |
| Frontend tests | 9 files, 85 tests by count. **None for route guards, login, change password or dashboard.** |
| Uncommitted | 152 `api/` entries, plus frontend work |
| Stubs | `sha256-placeholder` in imports; "failed or stubbed" Keycloak warnings; backup status not wired; file scan placeholder |
| Hardcoded UI | 57 hex values, 232 inline styles |

**Backend build, 7 November 01:54:** the local backend **failed to compile** at startup. `RoleCustomService.java:70,135` call `authorize` with 3 arguments instead of 4. This is uncommitted, in-progress work. Later edits may have fixed it.

**To check first in Phase 2:**
1. Does the backend compile now?
2. `RoleAssignmentController:42` and `RoleCustomController:35` both map `GET .../roles`, which normally stops Spring from starting.
3. Four frontend services start paths with `/api/...` on a base address already ending in `/api`, which may produce `/api/api/...`.
4. Do tenant lookups rely on the `edu_app` grant on `platform.*` in `provision-edu-app.ps1:94`, which D4 forbids?
5. Is `app.role_action` populated for the dev tenants?
6. Does `TenantIssuerAuthenticationManagerResolverIT` really delete data, or roll back?

### 17.5 Report statuses that are likely out of date

These will be re-checked in Phase 2. The Section 7 table hasn't been changed yet.

| Story | Report says | Code now appears to have |
|---|---|---|
| #16 ROLE_CUSTOM_MANAGE | No create or edit API | `RoleCustomController`: list, get, create, retirement |
| #45, #46 Leave approver | No screen | A screen exists, behind the route that always redirects |
| #47 PLAN_MANAGE | Only `GET /plans` | Plan create and update |
| #50 TENANT_SUSPEND_ARCHIVE | Missing | Suspension, archive, reactivation API and UI |
| #51 TENANT_SETTINGS | Missing | Settings GET and PATCH, and UI |

Also out of date: "73 endpoints", the 253 and 77 test counts, and the audit `PlatformPermissionService` map, which now has 10 action codes.

### 17.6 Open decisions that can block Sprint 1

| Decision | Blocks |
|---|---|
| OD-75, OD-76, OD-78, OD-119, PTD-61, PTD-63 | Marked "before any API coding" in D5 §11.1 |
| OD-80 auth fronting, TD-11 | `/api/auth/*` versus Keycloak with identity events |
| OD-12, OD-16, V4-OD-01 (DR-19, DR-20) | Student ID format; student, staff and guardian fields; bulk upload |
| OD-17 | Delegation "same-or-higher" ranking (#20) |
| OD-15 | Ticket categories (#56) |
| OD-42, OD-70, OD-73 | Usage counts and billing usage (#53, #63) |
| OD-65 | Archiving an institution |
| OD-68, OD-19 | Promotion batches; detained students (#39) |
| OD-69 | Program rule fields (#30) |
| OD-72 | Bulk approval design (#21) |
| OD-79 | Support grant marks (#59) |
| OD-135 | Who sets the DPC leave approver (#45) |
| OD-53, OD-60 | Field encryption; migration tool |
| OD-39 | Idempotency retention |
| V4-OD-02 | Notification templates are empty, which affects every "notified" rule |
| V4-OD-03 | Proposed Section 21 checks 25–31 |
| OD-62 | Deferred by the Product Owner, so don't build it |
| Scope of PO-1 | Whether VC, Registrar and Management oversight also act on institutions |
| "Own institution" for University Admin | `USER_DEACTIVATE` and `USER_PASSWORD_RESET_UNLOCK`: how they apply under PO-1 |

**Not all of these block the first fixes.** Which ones do will be set out in the Phase 3 plan.

### 17.7 More conflicts found (not resolved)

| Topic | One source | Other source |
|---|---|---|
| Send-back | D9 P3: "Return for changes" | D9 C1, D10 DR-10, D7: approve or reject only |
| Create-tenant fields | D7, D1: start date, retention | D10 omits them |
| Role assignment timing | D10: at next sign-in | D7, D1: logged out at once, effective within 1 minute |
| Who may assign roles | D10: University Admin only | Prompt, D3: University, College and School Admin |
| Path and token mismatch | D5 §2, §10: 404 | D5: host and token mismatch is 401 |
| `edu_app` on `platform.*` | D4, DB-59: nothing | `provision-edu-app.ps1`, `LOCAL_DEV.md`: read-only |
| Table and key counts | D4: 206 tables, 690 foreign keys | D8: 208 tables, 696 foreign keys |
| D7 field lists | 36 nodes are open by design | D10 and the prompt give some fields |

**Evidence that doesn't exist yet:** D6 marks all 260 action tests as Planned. The 97 SQL tests are recorded as passed on the spec package only. Of the 12 exit-criteria E2E scenarios, none has run in full. D8 notes 27 of 70 integrity rules have no executed test, and its Sprint 1 risks are all still open, including 3 Critical: SEC-V4-01, SEC-V4-02 and SEC-V4-04.
