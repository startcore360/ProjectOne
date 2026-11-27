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
