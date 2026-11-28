# EDU 360 — End-to-End Flow Reference

## Purpose of this document set

This is a plain-English, end-to-end walk-through of EDU 360, split by who is using it:
platform staff, tenant/institution admins, teaching staff, students, and
parents/guardians. For each audience it says two different things, kept clearly apart:

1. **What is actually built in the code today** — real endpoints, real files, real database
   tables, checked by opening the files, not guessed from the spec.
2. **What the V4.0 target spec plans** — the bigger design the company wants to build
   towards, which is mostly not built yet.

It also explains, for each audience, exactly what the most senior role can and cannot do,
and how every other role in that audience differs from it.

## How to read the marks

- ✅ **Built today** — this exists in the code right now. A file path and line number is
  given so you can open it yourself.
- 🔜 **V4.0 target, not built** — this is only in the spec documents. Nothing in `api/` or
  `app/` does this yet.
- ⚠️ **Partly built / in progress** — some of it exists, but it is incomplete, or only
  wired for a narrow case.

Every spec fact is tagged with where it came from, for example "D1 §7.1" (Deliverable 1,
section 7.1) or "D3 Role Catalogue." The master index for all of this is
`docs/V4.0_SPEC_SUMMARY.md` — these files point back to it rather than repeating its whole
content.

## Important ground rules (carried over from `docs/V4.0_SPEC_SUMMARY.md` and this repo's
`CLAUDE.md`, and followed everywhere in this document set)

1. The spec package describes a target multi-tenant SaaS with 71 roles, a 278-action
   permission matrix, a 206-table database, and a 408-operation API. The real code today
   has about 70 working REST endpoints (per this repo's own `CLAUDE.md`), a handful of
   backend roles actually wired through `app.role_action`, and a frontend with a growing
   but still partial set of screens. The gap between spec and code is large — this
   document never blurs that line.
2. Authorization in the backend is **imperative code**, not an annotation. Tenant-side
   checks go through `PermissionEvaluatorService.authorize(...)`
   (`api/src/main/java/com/edu360/api/rbac/PermissionEvaluatorService.java`). Platform-side
   checks go through `PlatformPermissionService.authorize(...)`
   (`api/src/main/java/com/edu360/api/platformops/PlatformPermissionService.java`). Neither
   uses `@PreAuthorize`.
3. Two known numeric disagreements inside the spec package itself, repeated here whenever
   they are relevant: the governance log says **79/79** SQL tests passed, while the SQL
   bundle's own `run_results.txt` says **97/97**; the governance log says **206 logical
   tables**, while the executed database catalogue reports **208 parent tables**. Both
   numbers are quoted; neither is silently picked as "the" answer.
4. The V4.0 spec package's own blunt self-description ("a single-tenant monolith with 2
   roles, 18 endpoints") is now stale — the real repository has moved well past that
   starting point, but it is still nowhere near V4.0 scale. Treat every "what exists
   today" claim in these files as checked against the actual source, not against that old
   paragraph.

## Files in this set

- [`platform.md`](./platform.md) — the platform side: platform realm, Product Owner,
  Platform Admin, Billing/Support/Release Managers, Data Protection Officer, tenant
  lifecycle, platform screens.
- [`tenant-admin.md`](./tenant-admin.md) — the tenant/institution admin side: Management,
  University Admin, Principal/College Admin, HOD, admissions, fees, academic structure.
- [`staff.md`](./staff.md) — the teaching/support staff side: HR, leave, appraisals,
  timetable, attendance, marks/grading, communications as a staff member sees them.
- [`student.md`](./student.md) — the student side: self-service, academics, fees, library,
  complaints as a student sees them.
- [`parent.md`](./parent.md) — the parent/guardian side: self-service, fees,
  communications, attendance visibility as a parent/guardian sees them.

Each file follows the same structure: landing/sign-in/dashboard, what exists in code today,
what the V4.0 spec plans, and a role-restriction section covering the most senior role in
full detail plus a shorter delta for every other role in that audience.

## Client Pitch Overview

*This section is the plain-business-language entry point into the whole document set, for
presenting EDU 360 to a non-technical client deciding whether to buy it. Each of the five
audience files above now ends with its own "Client Pitch Summary" section — the bullets,
speaker notes, role tables, and roadmap for that audience — immediately followed by a
"Developer detail" write-up of the same features for anyone taking over the codebase. This
overview ties those five together into one story.*

**Title / one-line pitch:** EDU 360 — one platform for every school, college and
university. One secure login per institution, one system for admissions, attendance,
exams, fees, staff and parents.

**Who this is for — five audiences, one system:**
- **Platform Operator** — EDU 360's own team, who run the SaaS business itself: onboarding
  schools, billing, support, release management. They never see a school's actual student
  data except through a supervised, time-limited support session. See
  [`platform.md`](./platform.md#client-pitch-summary).
- **Institution Admin** — the school/college's own leadership and administrative roles
  (Management, University Admin, Vice Chancellor, Principal, Head of Department, and the
  teams under them): they run the institution's structure, staff, fees and records. See
  [`tenant-admin.md`](./tenant-admin.md#client-pitch-summary).
- **Staff** — teachers and support staff who deliver the actual day-to-day work: marking
  attendance, running exams, entering marks, applying for leave. See
  [`staff.md`](./staff.md#client-pitch-summary).
- **Student** — the learner's own account: their record, their fees, their results, their
  own requests. See [`student.md`](./student.md#client-pitch-summary).
- **Parent/Guardian** — a parent's own account, linked to their child, with its own
  protected approval process for changing who is linked. See
  [`parent.md`](./parent.md#client-pitch-summary).

Think of these as five circles, one inside the next: the Platform Operator runs the whole
service from outside; inside each institution, Admins run operations, Staff deliver
teaching, and Students and Parents each get a narrow, personal view. Everyone sees only
what their role needs.

**How sign-in works, in plain terms:** every school/college gets its own private web
address (e.g. "yourschool.edu360.com"). That address — not a dropdown, not a form field —
is what tells the system which school's data to show, so logging in at the wrong address
simply shows nothing, and one school's records are never visible to another school's
users. Passwords are checked by a dedicated identity service (Keycloak) that EDU 360 never
stores passwords outside of, and every subsequent action is re-checked, every time, against
that same identity.

**Closing summary — what's live now vs. what's coming:**
- **Live today, across all five audiences:** sign-in and strict tenant isolation,
  institution structure and staff management, student records, fee structures and online
  payments, attendance, exams and marks entry, leave requests, confidential grievances,
  guardian linking and change approval, platform tenant lifecycle and billing, and
  time-limited supervised platform support access.
- **Coming next:** richer per-role dashboards across every audience, automatic stand-in
  authority when a post is vacant, a stronger role-match check on publishing results, a
  fuller self-service surface for students and parents, and phone-verified guardian
  profile editing. See each audience file's "Coming next / planned" list for specifics.

**Differentiators, in plain terms:**
- **Strict role-based security** — no one, at any level including the most senior roles,
  can do more than their specific role allows; this is enforced by the system itself, not
  by a policy document.
- **Complete data isolation between schools** — every institution's data is walled off
  from every other institution's, enforced at the database level, not just hidden in the
  user interface.
- **Built-in approval workflows** — no one can approve their own request anywhere in the
  system, at any level, from a platform executive down to a department head.
