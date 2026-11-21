# Chat T1 — Foundation, Term Lock Engine & Roll-Down Infrastructure

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-3-development`.

## Your mission

Build the architectural security and governance foundations for the tenant application: implement the **Master Roll-Down Engine (V4-PD-01)**, build the **Academic Term Lock and Emergency 72-Hour Unlock Engine (Actions 33–41)**, resolve the **Native Form Control UI Defects (Number Stepper & Dark Mode Selects)**, and establish shared tenant interceptors for Row-Level Security GUC propagation and audit outbox recording. **You go first.** Chats T2–T10 rely on your security guards and roll-down resolution service.

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word. That file is the master contract. The notes below provide exhaustive implementation details.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T1-S1** | `ACT-001`..`ACT-007` | Verify and harden M01 (Account & Sign-in): MFA enforcement, session concurrency limits (SES-01), failed login cooling-off period (5 attempts → 30 min lockout), and password complexity policies. | §7.1 |
| **T1-S2** | `RD-01`, `RD-02` | Implement the Master Roll-Down Engine (`V4-PD-01`, Rules `RD-G1` to `RD-G8`, `DB-80` to `DB-97`): recursive 6-hop resolution walking `app.role_rolldown_override` and `ref.role_rolldown_default` (182 seeded defaults). Enforce the Management Revocation Rule (`RD-G8`). | §8.3 |
| **T1-S3** | `ACT-033`..`ACT-041` | Build the Academic Term Lock Engine (`is_term_locked()`, `DB-42`): Term Lock API, status immutability, blocking attendance and marks updates on locked terms with HTTP 409 `TERM_LOCKED`. | §7.3 |
| **T1-S4** | `ACT-038`..`ACT-041` | Build Emergency Term Unlock API (`POST /api/academic/terms/{id}/unlock`): requires minimum 20-character rationale, auto-relocks after 72 hours via scheduled background task, writes `app.audit_outbox`. | §7.3 |
| **T1-S5** | UI Polish | Fix Native Number Stepper dark background defect (Image 1) and Dark Mode Select Dropdown styling (Image 2) across all tenant and platform forms in `app/src/index.css` and form components. | §17.1 |
| **T1-S6** | Shared Guard | Implement shared `TenantSecurityInterceptor` and Spring `TenantContext` verifying `app.tenant_id`, `app.institution_id`, and `app.institution_scope` on every Hikari connection checkout. | §4.1 |

---

### Detailed Technical Specifications

#### Story T1-S1: Account & Authentication Hardening (M01)
- **Backend:**
  - Audit `AuthenticationService.java` and `TokenProvider.java`.
  - Ensure password policy enforces 12+ characters, upper, lower, digit, symbol.
  - Implement `fn_session_limit`: SES-01 rule allows multiple devices, but exactly 1 active session per browser client.
  - Rate-limit login failures: 5 failed attempts locks user account for 30 minutes; write audit outbox `ACCOUNT_LOCKED`.
- **Frontend:**
  - Ensure `app/src/features/auth/SignInForm.tsx` renders the 7 screen states.
- **Tests:** `ACT-AUTH-LOCKOUT-ALLOW`, `ACT-AUTH-SESSION-CONCURRENCY-IT`.

#### Story T1-S2: Master Roll-Down Engine (V4-PD-01)
- **Database:**
  - Verify `ref.role_rolldown_default` (182 seeded default rows across 52 roles).
  - Verify `app.role_rolldown_override` (tenant_id, target_role_code, standin_role_code, is_withheld, updated_by).
  - Stored function `fn_rolldown_resolve(p_tenant_id uuid, p_role_code varchar)` returning `TABLE(resolved_role varchar, resolution_type varchar)`.
  - **Rule RD-G8 (Management Revocation Trigger):** `trg_revoke_gba_rolldown` on `app.role_assignment`. When an active role assignment for `MANAGEMENT` is inserted or activated for a tenant, automatically deactivate any stand-in authority held by `GOVERNING_BODY_APPROVER`.
- **Backend:**
  - Service `RoleRolldownService.java` providing `resolveEffectiveRole(UUID tenantId, String requestedRole)`.
  - Controller `RoleRolldownController.java` (`GET /api/roles/rolldown/effective`, `PUT /api/roles/rolldown/overrides`).
- **Tests:** `ACT-ROLLDOWN-RESOLVE-ALLOW`, `DB-80-TO-DB-97-ROLLDOWN-IT`.

#### Story T1-S3 & T1-S4: Academic Term Lock & Emergency 72h Unlock Engine
- **Database:**
  - Ensure column `is_locked boolean NOT NULL DEFAULT false` and `locked_at timestamptz` on `app.academic_term`.
  - Create table `app.term_unlock_log` (`id uuid`, `tenant_id uuid`, `term_id uuid`, `unlocked_by_user_id uuid`, `reason text`, `unlocked_at timestamptz`, `relocked_at timestamptz`, `is_active boolean`).
  - Stored function `is_term_locked(p_term_id uuid)` returning `boolean`.
- **Backend:**
  - `AcademicTermController.java`:
    - `POST /api/academic/terms/{id}/lock` (Principal / Management only; sets `is_locked = true`).
    - `POST /api/academic/terms/{id}/unlock` (`TermUnlockRequest` body: `reason` min 20 chars).
  - Scheduled worker `TermRelockScheduler.java` checking every hour for unlocks older than 72 hours and resetting `is_locked = true`.
- **Frontend:**
  - `AcademicCalendarView.tsx`: Render Lock/Unlock modal with mandatory reason textarea; display "Term Locked: Records are immutable" banner on locked terms.
- **Tests:** `ACT-TERM_LOCK-ALLOW`, `ACT-TERM_LOCK-DENY`, `DB-42-TERM-LOCK-IT`.

#### Story T1-S5: Native Form Control & Dark Mode UI Polish
- **CSS (`app/src/index.css`):**
  - Add native number stepper reset:
    ```css
    input[type=number]::-webkit-inner-spin-button,
    input[type=number]::-webkit-outer-spin-button {
      opacity: 0.8;
      background: transparent;
      filter: invert(0.5);
    }
    input[type=number] {
      -moz-appearance: textfield;
    }
    ```
  - Add native select dark mode fix:
    ```css
    select {
      color-scheme: light dark;
    }
    .dark select {
      color-scheme: dark;
      background-color: #0f172a;
      color: #f8fafc;
    }
    ```

---

## Database migrations you own

Use **only** numbers **55** and **56**. Each needs `NNr_` rollback and `NNt_` test files, registered in `sql/run_all.sh` and `docker/postgres/provision-edu-app.ps1`.
- `55_v41_term_lock_engine.sql`: `app.term_unlock_log`, trigger `trg_term_lock_immutability`, function `is_term_locked()`.
- `56_v41_rolldown_management_revocation.sql`: Trigger `trg_revoke_gba_rolldown` and `fn_rolldown_resolve()` hardening.

---

## Files you own

- **Backend:**
  - `com.edu360.api.security.TenantSecurityInterceptor`
  - `com.edu360.api.security.TenantContext`
  - `com.edu360.api.academic.service.RoleRolldownService`
  - `com.edu360.api.academic.controller.RoleRolldownController`
  - `com.edu360.api.academic.service.AcademicTermLockService`
  - `com.edu360.api.academic.scheduler.TermRelockScheduler`
- **Frontend:**
  - `app/src/index.css`
  - `app/src/components/ui/Input.tsx`
  - `app/src/components/ui/Select.tsx`
  - `app/src/features/academic/components/TermLockModal.tsx`
  - `app/src/features/academic/components/TermUnlockModal.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/academic/AcademicTermLockIT.java`
  - `api/src/test/java/com/edu360/api/academic/RoleRolldownIT.java`
  - `app/src/features/academic/__tests__/TermLockModal.test.tsx`

---

## Depends on / others depend on you

- **You depend on nobody.** You go first.
- **Chats T2 through T10 depend on you** for the Term Lock function `is_term_locked()`, the roll-down resolution helper, and the form styling fixes.

---

## Verification you must run and show before pushing

1. Backend tests: `cd api && .\mvnw.cmd test -Dtest=AcademicTermLockIT,RoleRolldownIT`
2. Frontend tests: `cd app && npm test -- TermLockModal`
3. Frontend build & lint: `npm run lint && npm run build`
4. SQL migration check: `./sql/run_all.sh`
