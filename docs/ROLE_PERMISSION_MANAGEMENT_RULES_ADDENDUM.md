# EDU 360 — Role and Permission Management Rules (Addendum, approved for next revision)

**Status:** Approved for the next requirements revision by Product Owner decision, 2026-11-04
(see `docs/GOVERNANCE_DECISION_LOG.md`, decision SOURCE-GAP).

**What this document is:** this closes the previously-open gap noted in
`docs/V4.0_SPEC_SUMMARY.md` §5 and §2 — the "Role and Permission Management Rules" source
document referenced by one open High-severity security finding was never supplied to this
repository. The Product Owner has now approved its content for incorporation into the next
revision of D1 (Master Product Specification) and D3 (Master Roles & Permission Specification).

**What this document is not:** it is not retroactive V3.3 functionality, and it is not a complete
transcription of the full 12-section source. The Product Owner's approval message gave this
document's **section structure** (12 named sections) and a **consolidated list of critical rules**
drawn from across those sections — it did not supply the full prose of each section. Per the
governance restriction "do not invent product behaviour," this addendum records exactly what was
supplied: the 12 section titles (as a next-revision table of contents) and the critical rules
verbatim. **Where a section's full text has not been supplied, that is stated explicitly below —
it is not filled in or paraphrased.**

---

## 1. Section structure (approved as the next-revision table of contents for this topic)

1. Organisational hierarchy and role ownership.
2. Standard roles and custom roles.
3. Institution-level and university-level administrative roles.
4. Role creation and permission configuration.
5. Permission model.
6. Multiple institutional assignments.
7. Mandatory security and approval restrictions.
8. University control over institution roles.
9. Role lifecycle and change management.
10. Role Management functionality.
11. Required operational behaviour.
12. Final acceptance criteria.

**Full prose for each section has not been supplied to this repository.** Only the critical rules
below (§2 of this document) were given, cutting across these 12 sections. Do not treat any
section above as having content beyond what §2 records — if a future revision supplies the full
section text, it replaces this note, it does not get reconstructed from the rules list.

---

## 2. Critical rules (approved verbatim, consolidated across the 12 sections)

These are binding requirements for the next revision of D1/D3 and for the design of Foundation
item 7 (the 71-role/280-action authorisation model). Each is recorded exactly as approved — none
are paraphrased or extended:

1. Role ownership must follow organisational hierarchy.
2. Lower-level administrators cannot manage higher-level-owned roles.
3. Standard roles are suggested templates, not compulsory roles.
4. Custom roles can use only approved Action List permissions.
5. Role authority depends on permissions and organisational scope, not its name.
6. Role creation must validate the administrator's delegated authority.
7. Role, permission, organisational scope and approval authority remain separate concepts.
8. Multiple institutional assignments must remain separately scoped.
9. Permissions from separate assignments must not be combined to expand access.
10. Only one role is active within a given assignment and access context.
11. Self-elevation and self-approval are prohibited.
12. Mandatory approval chains cannot be bypassed.
13. Tenant and institution isolation must remain enforced.
14. Platform-reserved restrictions cannot be overridden by tenant administrators.
15. Every protected operation requires server-side authorisation.
16. Role lifecycle must support Draft, Active, Inactive and Archived.
17. Roles with historical assignments or records must not be hard-deleted.
18. Role changes and assignments must be auditable.

**Source-of-truth constraint (unchanged):** the existing Action List and Permission Matrix
(D2/D3) remain the sources of truth for available action codes and role-action mappings. This
addendum does not create new permission codes or alter existing permission mappings — any action
code referenced elsewhere in planning (e.g. `ROLE_CUSTOM_MANAGE`, `USER_CREATE`) is the one
already catalogued in D2/D3, not a new one introduced here.

---

## 3. How these rules map onto rule #10 above and existing V4.0 concepts

A few of the 18 rules restate or sharpen mechanisms already described in
`docs/V4.0_SPEC_SUMMARY.md` §2 rather than introducing new ones — recorded here so the two don't
read as independent requirements that happen to agree:

- Rule 10 ("only one role active within a given assignment and access context") is the same
  constraint already described as "a user can hold multiple role assignments, but only one is
  active per request" (D1 Part I) and implemented as the Sign-in epic's `ROLE_SWITCH` story.
- Rules 11–12 (no self-elevation/self-approval, approval chains can't be bypassed) restate
  `GUARD_NO_SELF_APPROVAL` and the Delegation & SoD rules already in §2.
- Rule 13 (tenant/institution isolation) restates `GUARD_PLATFORM_ISOLATION` plus OD-03 (this
  round's institution-RLS decision — see `docs/GOVERNANCE_DECISION_LOG.md`).
- Rule 14 (platform-reserved restrictions) restates the platform/tenant role separation already
  enforced by `PlatformPrincipal`/`TenantPrincipal` being distinct Java types.
- Rule 15 (server-side authorisation for every protected operation) restates `GUARD_ROLE_SCOPE_STATE`.
- Rules 16–17 (Draft/Active/Inactive/Archived lifecycle, no hard delete) restate the "Role
  lifecycle & audit" mechanism already named in §2, and generalise the SQL bundle's existing
  soft-delete-only convention (D4) to the role model specifically.

What's genuinely new in this addendum, not previously stated elsewhere in the summarised package:
rules 1–2 (ownership follows organisational hierarchy; lower-level admins can't manage
higher-level-owned roles) and rule 6 (role creation must validate the creating administrator's
own delegated authority, not just their role name) — these are concrete, checkable constraints
that Foundation item 7's data model and action evaluator must satisfy and that were not
explicit in the previously-summarised D1–D10 package.

---

## 4. Implementation status

**Not implemented.** No role_definition/role_assignment model exists in `api/` yet (see
`docs/GOVERNANCE_DECISION_LOG.md`, SOURCE-GAP entry, and the Section 2 baseline review from
2026-11-04). This document is a design input for the Foundation item 7 implementation brief, to
be presented and approved per `CLAUDE.md`'s workflow before any code is written against it.
