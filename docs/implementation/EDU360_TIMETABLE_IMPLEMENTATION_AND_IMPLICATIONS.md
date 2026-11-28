# EDU 360 — Timetable Management Implementation and Implications

**Repository:** `startcore360/ProjectOne`  
**Branch:** `sprint-3-development`  
**Tracking status:** In progress  
**Last updated:** 2026-11-29  
**Purpose:** Maintain a commit-by-commit implementation, requirements, implications, and verification record for Timetable Management. This document is updated with each related implementation commit.

## 1. Source baseline

Primary product source: `EDU360_V4.0_01_Master_Product_Specification.md`.

Relevant source requirements inspected:

- **§8.3 — Timetable and online classes:** hand-built timetable in v1; allocate faculty and rooms; publish by class, faculty, and room; support online periods and meeting links.
- **§8.3 rule [MD-05]:** teacher or room double-booking shows a warning, not a block.
- **§8.3 change [PQ-30, PQ-31]:** changes to a published timetable retain history and notify affected people; historical timetable and attendance records must not be silently rewritten.
- **§26.7 — Timetable actions:** `TIMETABLE_BUILD` and `ONLINE_PERIOD_SET` are assigned to College Admin, School Admin, HOD, and Department Coordinator within their own scope. `TIMETABLE_VIEW` includes faculty, students, parents, HOD, coordinators, admins, Principal (College), School Principal, and Vice Principals within their specified visibility scope.

The approved role interpretation for this implementation is: Admin, HOD, and Department Coordinator receive builder controls where the backend grants `TIMETABLE_BUILD`; Principal roles receive read-only timetable oversight unless separately granted a builder action by the authoritative permission model. The UI must not elevate permissions.

## 2. Current implementation scope

The current work is based on the existing timetable UI and backend contracts:

- Frontend: `app/src/features/academic/pages/TimetableGridView.tsx` and its route/page integration.
- Backend: `api/src/main/java/com/edu360/api/academic/controller/TimetableController.java` and `api/src/main/java/com/edu360/api/academic/service/TimetableService.java`.
- Database: `sql/65_v41_timetable_gist_constraints.sql` and `sql/65t_tests_timetable_gist_constraints.sql`.

Existing API operations inspected include timetable slot listing, slot creation, draft-slot movement, per-slot publishing, room listing/creation, meeting-link creation, and meeting-link access.

## 3. Confirmed implementation decisions

1. **Permission-aware experience:** builder controls are gated by `TIMETABLE_BUILD`; structure and subject-allocation lookups are only attempted when the current user has the corresponding permissions.
2. **Principal oversight:** Principal roles without `TIMETABLE_BUILD` remain read-only; no frontend permission bypass is introduced.
3. **Institution/session selection:** the UI uses institution and academic-session selectors rather than asking users to enter raw identifiers.
4. **Resource selection:** the builder uses existing room and subject-allocation data where the current user is authorized to retrieve it.
5. **Conflict feedback:** known conflicting allocation/room choices are marked unavailable in the dropdown UI with an explanatory label. This is a usability aid; backend and database validation remain authoritative.
6. **No fabricated bulk actions:** the UI must not claim publish-by-class/faculty/room, version history, or notification delivery unless supported by verified backend contracts and behaviour.

## 4. Important specification / database implication

There is a material policy mismatch that must remain visible until resolved by the product owner or authoritative specification decision:

- V4.0 §8.3 [MD-05] says teacher/room double-booking produces a **warning, not a block**.
- The current migration `sql/65_v41_timetable_gist_constraints.sql` defines PostgreSQL GiST exclusion constraints named `ex_timetable_teacher_no_overlap` and `ex_timetable_room_no_overlap` for overlapping date/time ranges on active draft/published timetable slots.
- The corresponding SQL test `sql/65t_tests_timetable_gist_constraints.sql` explicitly expects those exclusion constraints to exist.

**Implication:** the current database design can reject a save that the product prose describes as warning-only. The frontend must not imply that a warning can override a database constraint. Until the policy is reconciled, conflict feedback should explain when a save is blocked by the current database rule. Do not remove or weaken database constraints as an incidental UI change.

A separate class/allocation duplicate check in the dropdown is not equivalent to the teacher/room double-booking rule. Keep these conflict categories distinct in both code and documentation.

## 5. Published timetable history and notifications

The service source inspected shows that a published slot is immutable through the current move/update operation: attempts to move a non-draft slot produce `PUBLISHED_SLOT_IMMUTABLE` and instruct the caller to supersede and republish. This is consistent with avoiding silent edits, but it does **not by itself prove** that a complete supersede/version workflow or affected-user notifications are implemented.

Status: **Needs further backend and schema inspection.** Do not mark history retention or notifications complete until the supersede path, audit/version data, and notification dispatch are verified end to end.

## 6. Commit log

| Commit | Change recorded | Verification status |
| --- | --- | --- |
| `9e09c174aa581bf5750970c769d21981206db4a7` | Initial timetable UI rewrite. | Code committed; tests/build not run in this environment. |
| `cae26e32e0977f4126cc04f455dd84b415766fae` | Load academic structure data conditionally based on permission. | Code committed; tests/build not run. |
| `96ec32c064f90502ac28f6444653b480309fdf41` | Add timetable grid styling. | Code committed; visual/runtime verification pending. |
| `b5862d0361e59491e5efe5c518d0ad7c2ae1e908` | Add role-aware filters and conflict warning flow. | Code committed; tests/build not run. |
| `c17ebff91217d3b26f067691791ff7d19ab9a0bc` | Add loading/empty states and fallback when allocation lookup is restricted. | Code committed; tests/build not run. |
| `82874a3c57e1311f7b11c477d63b14eb2b5e4382` | Clarify current database-enforced conflict behaviour in UI feedback. | Code committed; tests/build not run. |
| `021a7ba7b9381c68f2247614c98168265999ee0c` | Disable allocation/room options with conflict explanations. | Code committed; browser accessibility/interaction verification pending. |
| `639bf981934dbf72d622d5dba5e3a1796a810736` | Fetch broader authorized timetable slot data for conflict dropdown checks. | Branch HEAD confirmed; tests/build not run. |

## 7. Verification register

| Area | Current evidence | Status |
| --- | --- | --- |
| Product requirement baseline | V4.0 §8.3 and §26.7 inspected. | Reviewed |
| Role and permission model | UI gating and backend authorization paths inspected; full role matrix still needs final cross-check. | Partially verified |
| Institution/session/section option loading | Frontend implementation inspected. | Code-reviewed; runtime pending |
| Allocation and room conflict dropdowns | Frontend implementation inspected; known class/allocation and room overlap checks present. | Partially verified |
| Teacher conflict dropdown explanation | Teacher identity is derived in the database from the subject allocation's staff assignment; frontend allocation DTO does not itself establish a reliable teacher-profile mapping. | Not yet verified in UI |
| Database conflict enforcement | GiST migration and SQL assertions inspected. | Confirmed in source; runtime DB test pending |
| Published-slot immutability | Service rejects moving published slots. | Confirmed in inspected service path |
| Supersede/version history | Not yet traced end to end. | Open |
| Affected-user notifications | Not yet traced end to end. | Open |
| Publish by class/faculty/room | Existing per-slot publish endpoint inspected; bulk/group publish capability not yet established. | Open |
| Frontend/backend tests and build | Not executable through the GitHub file connector used for these edits. | Not run |
| Full V4.0 acceptance validation | Pending completion of implementation and test execution. | Open |

## 8. Open work

- Trace and document the complete published-slot supersede/version workflow and notification dispatch.
- Confirm whether grouped publishing is supported by an existing API; do not add fake controls.
- Complete teacher conflict UX only if an authorized, reliable teacher identity mapping can be used without broadening access.
- Reconcile the V4.0 warning-not-block rule with the current database exclusion constraints through an explicit product/architecture decision.
- Add or update focused frontend/backend tests where the repository test setup permits.
- Run the project test suites, lint, and build in an execution environment; record exact commands and outcomes.
- Complete a final V4.0 traceability pass before declaring the module complete.

## 9. Change-control rule for this document

For every subsequent Timetable Management code commit, update this file in the same work cycle with:

- commit SHA and concise change summary;
- affected files, API contracts, permission or schema implications;
- tests/commands actually run and their exact outcomes;
- remaining risks or open questions.

A commit is not considered verified merely because GitHub accepted the file update. Distinguish source inspection, automated test execution, and live runtime validation.
