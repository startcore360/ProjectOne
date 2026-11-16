# EDU 360 — Requirements Traceability and Implementation Gap Update

**Covers:** the seven governance decisions in `docs/GOVERNANCE_DECISION_LOG.md` (2026-11-04).

**Identifier convention used here:** existing identifiers only — decision IDs (OD-xx,
SOURCE-GAP) from D2's governance register, action codes (e.g. `USER_CREATE`) from D2/D3's Action
List, and epic/story names from the Sprint 1 story list supplied separately (the project has no
in-repo sprint-planning file; the 67-story breakdown was supplied as an external prompt document
and is treated as the current authoritative Sprint 1 scope, consistent with
`docs/V4.0_SPEC_SUMMARY.md`'s own statement that sprint planning isn't part of the V4.0 package).
No new numbering scheme is introduced.

**Traceability matrix location note:** D6 (`docs/EDU360_V4.0_Final/06_Requirements_Traceability_and_QA/*.xlsx`)
is the original, signed-off traceability matrix for the full V4.0 package's 278 actions against
408 API operations. This document is a **targeted update** covering only what changed with this
round's seven decisions — it does not reproduce or re-derive the full matrix, and does not edit
the original xlsx in place (per governance restriction #8, preserve historical
decisions/traceability).

---

## A. Traceability: decision → requirement → story → test → status

| Decision | Requirement/rule affected | Epic / Story | Acceptance criteria (per story's existing ALLOW/DENY/SCOPE/GUARD pattern) | Test IDs | Implementation status |
|---|---|---|---|---|---|
| OD-03 | Institution-level RLS (PTD-I1) | Foundation item 5 (tenant/institution context); Institution setup epic (all 22 stories' "Scope: Own institution/college/school"); Users & roles `USER_VIEW`/`ROLE_ASSIGN*` | SCOPE tests for every affected story must fail closed across institution boundaries, not just tenant boundaries | DB-side: already covered by `sql/04_tests.sql`/`sql/09_tests_v40.sql` (RLS/`p_institution` tests, passing). App-side: no `ACT-*-SCOPE` tests exist yet for institution boundaries specifically | **Not implemented** (app-side GUC wiring missing) |
| OD-10 / OD-80 | Keycloak realm-per-tenant + `user_identity_link` | Sign-in epic story 1 (`AUTH_LOGIN`); Foundation item 6 (Identity) | Login resolves correct tenant/platform realm from hostname; identity link correctly maps Keycloak subject to `UserAccount` | `TenantIssuerAuthenticationManagerResolverIT` (7/7 passing) | **Mostly implemented** — core linkage done; per-role-type login rules and two-step login not yet built |
| OD-09 | Append-only audit, full event taxonomy, actor/role/scope/correlation-id | Cross-cutting — the GUARD test of every one of the 67 stories | Every create/edit/delete/approval/export/login/denied-attempt/sensitive-view produces an immutable audit row with actor, active role, scope, correlation id | None yet beyond `LOGIN_SUCCESS`/`LOGIN_FAILURE`/`LOGOUT`/`ACCOUNT_CREATED`/`PASSWORD_CHANGED` coverage (no dedicated audit tests found) | **Partially implemented** |
| OD-01 | PostgreSQL 16 | Foundation item 3 (SQL runner + CI) | CI's `database-migration-validation` job runs against `postgres:16` and passes | `sql/run_all.sh` → `SUMMARY \| PASS \| 97` (re-verified live 2026-11-04) | **Implemented and verified** |
| OD-62 | Role-template update propagation | Users & roles story 9 (`ROLE_CUSTOM_MANAGE`) | N/A — explicitly deferred; no acceptance criteria exist until un-deferred | None (correctly none) | **Deferred — not implemented, not to be implemented** |
| OD-134 | `USER_CREATE` covers single-student creation | Users & roles story 2 (`USER_CREATE`) | `POST /v1/tenants/{tenantId}/students` authorised under the `USER_CREATE` action code, not a new one | `ACT-USER_CREATE-ALLOW/DENY/SCOPE/GUARD` (to be written when the story is built) | **Not implemented** (decision only fixes the action code to use) |
| SOURCE-GAP | 18 critical role/permission rules (`docs/ROLE_PERMISSION_MANAGEMENT_RULES_ADDENDUM.md`) | Users & roles story 9 (`ROLE_CUSTOM_MANAGE`) directly; binding design input to Foundation item 7 (71-role/280-action model) | Foundation item 7's data model must satisfy all 18 rules (ownership hierarchy, lifecycle states, no hard delete, self-elevation ban, etc.) before any `ROLE_*` story is accepted | None yet — Foundation item 7 not started | **Documentation only — no code** |

---

## B. Implementation Gap Register (this round's items only)

| Area | Already implemented & verified | Partially implemented | Not implemented | Blocked by dependency | Deferred by PO decision |
|---|---|---|---|---|---|
| Tenant-level RLS | ✅ `app.tenant_id` GUC via `TenantRoutingDataSource` | | | | |
| Institution-level RLS (OD-03) | | | ✅ app-side wiring | blocked on Foundation item 5 brief approval | |
| SQL bundle + DB tests (OD-01) | ✅ 97/97 PASS, re-verified live 2026-11-04 | | | | |
| Keycloak identity linkage (OD-10/OD-80) | ✅ realm resolution, `UserIdentityLink` | ✅ two-step login, per-role-type login rules not built | | | |
| Audit append-only + full taxonomy (OD-09) | | ✅ basic audit log exists (5 event types) | ✅ append-only trigger, correlation id, remaining event types | blocked on Foundation item 7 (need a role to log `activeRole` against) | |
| `USER_CREATE` / student creation (OD-134) | | | ✅ endpoint + action evaluator | blocked on Foundation item 7 | |
| Role-template update propagation (OD-62) | | | | | ✅ deferred until `ROLE_CUSTOM_MANAGE` exists |
| Role/Permission Management Rules (SOURCE-GAP) | | | ✅ no code yet | blocked on Foundation item 7 brief | |

**Explicitly not marking anything "complete" on the basis of documentation alone** — per
governance restriction #7, every "implemented" status above is backed by either a passing test
run shown in this conversation or an explicit "not implemented" where no such evidence exists.

---

## C. Epic/Sprint planning impact

No change to the approved Sprint 1 scope (67 stories, 4 epics) is made here — per governance
restriction, planning changes are recommended separately, not applied silently. What this round's
decisions do is:

1. **Unblock** Foundation item 5 (tenant/institution context) to proceed to its implementation
   brief, now that OD-03 is approved.
2. **Unblock** Foundation item 9 (audit) to be scoped, now that OD-09 is approved — recommended to
   sequence after Foundation item 7 (role model), not before, since audit's `activeRole`/`scope`
   fields need a role to log against (see gap register above). This is a sequencing
   recommendation, not a scope change.
3. **Add a binding design constraint** to Foundation item 7 (the 18 rules in
   `docs/ROLE_PERMISSION_MANAGEMENT_RULES_ADDENDUM.md`) — no story count change, but Foundation
   item 7's acceptance criteria now explicitly include satisfying those 18 rules.
4. **Fix the action code** for Users & roles story 2 (`USER_CREATE`, not a new `STUDENT_CREATE`) —
   no scope change, just removes an ambiguity the story list already left open.
5. **Confirm no new work** from OD-62 (deferred) or OD-01 (already satisfied).

No stories are added, removed, or resequenced beyond the sequencing note in (2) above.
