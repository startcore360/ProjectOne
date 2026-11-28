# UI/UX Consistency & Performance Fix — Agent Execution Prompt

**Status:** Not started. This is a validated audit + an execution prompt for a separate agent to
run — written 2026-11-28, from reading the actual live code (not assumptions), in response to a
senior UI/UX + frontend audit request. None of the 8 items below have been implemented yet.
**Scope:** `app/` (frontend) and the small part of `api/` Item 1 touches.
**How to use this file:** hand its "Prompt for the executing agent" section (below) to the agent
that will do the work. The "Validated findings" section above it is supporting evidence — read it
first for context, but the prompt itself is self-contained.

---

## Validated findings (evidence behind each item)

### 1. Duplicate-field errors don't highlight the field
`api/src/main/java/com/edu360/api/identity/UserAccountService.java:86-96` throws
`DuplicateResourceException("A user with email '...' already exists...")` — the backend knows
it's the email (or mobile) but the exception carries no field name.
`api/src/main/java/com/edu360/api/exception/GlobalExceptionHandler.java`'s `handleDuplicate()`
turns it into `{code: "DUPLICATE_RESOURCE", message: "..."}` with no `fieldErrors` map. On the
frontend, `app/src/hooks/useFormErrors.ts`'s `handleApiError` only highlights a field red when
the backend sends `fieldErrors` (which only happens for `@Valid` 400s, never this 409). Result:
the message shows in a generic banner, no field turns red, no inline text under email/mobile.

### 2. Raw-UUID "ID" fields that should be pickers (19 confirmed instances)
`academic/pages/AttendanceRegisterPage.tsx:322,359,421,429,586` ·
`admissions/pages/AdmissionCyclesPage.tsx:17-19` ·
`admissions/pages/ApplicantPortalPage.tsx:34` ·
`admissions/pages/ApplicationScrutinyPage.tsx:17` ·
`admissions/pages/MeritListGenerationPage.tsx:23` ·
`institution-admin/pages/StaffRecordsPage.tsx:411-412` ·
`institution-admin/pages/TenantSupportPage.tsx:154` ·
`staff/pages/StaffAppraisalPage.tsx:145` ·
`t10/pages/MessageComposerPage.tsx:60` ·
`platform/components/observability/AuditLogView.tsx:164,168` ·
`support/SupportSessionDashboard.tsx:139`.
**Excluded, already correct as free text — do not touch:** any "National ID last 4 digits"/Aadhaar
field, `finance/pages/FeePaymentPortalPage.tsx:175` ("Payment ID" receipt lookup),
`institution-admin/components/institutions/CreateInstitutionModal.tsx:153,168` and
`InstitutionIdFormatsModal.tsx:77,87` ("ID format" is a config pattern string, not an entity
reference), `students/pages/StudentDirectoryPage.tsx:32` (a search box).
Correct existing pattern to copy: `user-management/components/common/ScopeFields.tsx` (used by
`CreateUserModal`) — resolves institution/department as a searchable picker, not free text.

### 3. Mobile/phone field — 12 instances, 8 different label phrasings for the same thing
"Phone", "Phone number" (×2), "Personal mobile (E.164)" (×2), "Primary contact mobile (+91
format)", "Mobile", "Mobile (E.164)", "Mobile Number *", "Mobile Number (Optional, E.164)",
"Mobile number". Files: `admissions/pages/ApplicantPortalPage.tsx:38`,
`institution-admin/pages/StaffRecordsPage.tsx:390,488`, `features/landing/LandingPage.tsx:1229,1415`,
`platform/components/tenants/CreateTenantModal.tsx:149`,
`features/profile/components/ProfileDetailsTab.tsx:182`,
`students/components/GuardianManagerModal.tsx:74`,
`user-management/components/details/GuardianManagementPanel.tsx:281`,
`user-management/components/users/CreateTopUserModal.tsx:107`,
`user-management/components/users/CreateUserModal.tsx:112` (best existing one — has `type="tel"`,
a clear hint, and a placeholder; use its wording as the reference), `user-management/pages/UserManagementPages.tsx:797`.

### 4. Hardcoded hex colors outside `tokens.css` — 368 total, concentrated
- `features/landing/LandingPage.css` — 201 (public marketing page; lower priority, tokenize only
  if time allows, and verify the public brand look afterward)
- `platform/components/billing/BillingEstimateShareModal.tsx` — 51, and
  `documents/pages/CertificateRequestPage.tsx` — some: **do not touch**, these render a
  printed/exported document whose colors are intentionally fixed, not theme-driven
- **Core design-system components — fix first, highest leverage:**
  `design-system/components/Toast.tsx` — 36, `design-system/components/ShareMenu.tsx` — 6,
  `design-system/components/MetricCard.tsx` — a few
- Page-level: `platform/components/reports/ReportsView.tsx` — 6,
  `support/SupportSessionBanner.tsx` — 3, `platform/components/tenants/TenantBrandingModal.tsx` — 3,
  `user-management/components/details/GuardianManagementPanel.tsx` — 2,
  `user-management/components/users/BulkUploadModal.tsx` — 1,
  `platform/components/landing/PlatformLandingCmsView.tsx` — 1,
  `academic/pages/TimetableGridView.tsx` — 1

### 5. Spacing-token sweep (~26 known spots)
`tokens.css:196-206` defines a real `--space-1` through `--space-16` scale, but ~26 places bypass
it with raw pixel values in inline `style={{}}`: `AcademicSessionDetailPage.tsx:38`,
`ClashWarningToast.tsx:4`, `CreatePlanModal.tsx:816,827`, and ~20 more in `LandingPage.tsx`,
`TenantsView.tsx`, `CreatedCredentialsModal.tsx`.

### 6. Nav bar icon/order inconsistency
`layout/AppLayout.tsx` reuses the `"shield"` icon for 3 conceptually different destinations:
Roles & permissions, Role Previews, and Data protection. "Self-service"/"My sessions" shift
between mid-list and bottom-of-list depending on which of the ~10 role nav arrays you're in, with
no stated placement rule.

### 7. Lighthouse performance/SEO/best-practices (~60% today)
- 4 Google Font families pulled from `fonts.googleapis.com` at multiple weights each (DM Sans,
  Manrope, Noto Sans Devanagari, Noto Sans Kannada) in `index.html`
- Inconsistent image `loading="lazy"` — present at `LandingPage.tsx:716`, missing at `:608` and `:996`
- Large per-route JS chunks from `npm run build`: `InstitutionSetupPages` 112KB,
  `UserManagementPages` 55KB, `TenantsView` 54KB, `PlansView` 59KB, `BillingEstimateShareModal` 36KB
  as its own chunk
- Only one `<meta name="description">`, no Open Graph tags, no canonical link

### Already fixed separately (do not redo)
The first-load splash and in-app lazy-route loading state were already redesigned in a prior
session: `index.html`, `design-system/components/AppLoader.tsx`, `routes/Routes.tsx`'s
`LazyRouteView`, `App.tsx`'s outer `Suspense`, and `main.tsx`'s landing-page chunk prefetch. Don't
touch these as part of this prompt's work.

---

## Prompt for the executing agent

```
EDU 360 — Frontend UI/UX Consistency & Performance Fix (validated audit, exact targets)

Before touching any file: follow CLAUDE.md's "Required workflow for every implementation
request" and "UI/UX standard" sections exactly — present the 9-heading implementation brief
and wait for approval before editing. Do each item below as its own brief/approval/build/test
cycle, not one giant diff. After each item: npm run lint, npm test, npm run build (frontend)
and/or ./mvnw test (backend), and report real output.

ITEM 1 — Duplicate-field errors don't highlight the field (backend + frontend)
  File: api/src/main/java/com/edu360/api/exception/DuplicateResourceException.java — add an
  optional `fieldName` constructor param.
  File: api/src/main/java/com/edu360/api/identity/UserAccountService.java:86-96 — the reference
  case: email check passes fieldName="email", mobile check passes fieldName="mobile".
  Repo-wide: grep `throw new DuplicateResourceException` (17 files found by this audit:
  GuardianLinkService, StudentCreationService, TopUserService, UserAccountService,
  InstitutionIdFormatService, InstitutionService, MasterCalendarService, StaffDirectoryService,
  PlatformUserManagementService, PlatformReleaseService, PlatformCustomRoleService,
  PlatformRoleTemplateService, PlatformSubscriptionService, TenantManagementService,
  RoleCustomService, KeycloakAdminClient) — add fieldName wherever the duplicate check is
  against one identifiable form field; skip it where there's no single field (e.g. a composite
  uniqueness rule).
  File: api/src/main/java/com/edu360/api/exception/GlobalExceptionHandler.java,
  handleDuplicate() — when ex.getFieldName() != null, emit `fieldErrors: { <fieldName>: message }`
  in the same shape handleValidation() already uses.
  Frontend: no change needed to useFormErrors.ts — it already reads fieldErrors. Verify
  CreateUserModal.tsx and every other form touching one of the 17 files above now shows the red
  inline error on the correct field, not just the top banner.
  Tests: one backend test per touched service (duplicate email -> fieldErrors.email present,
  not just a message), one frontend test asserting the specific field (not the banner) shows
  the error.

ITEM 2 — Raw-UUID fields that must become pickers (19 confirmed instances — do not touch the
  excluded list below, they're already correct)
  Fix these labels/fields, replacing the TextField with a searchable SelectField (or a new
  async-search combobox) backed by the matching list endpoint, following the existing correct
  pattern in app/src/features/user-management/components/common/ScopeFields.tsx:
    - academic/pages/AttendanceRegisterPage.tsx:322 (Institution ID), :359 (Section ID),
      :421 (Published timetable slot ID), :429 (Subject allocation ID), :586 (Department ID)
    - admissions/pages/AdmissionCyclesPage.tsx:17 (Institution ID), :18 (Academic session ID),
      :19 (Program ID)
    - admissions/pages/ApplicantPortalPage.tsx:34 (Institution ID)
    - admissions/pages/ApplicationScrutinyPage.tsx:17 (Institution ID)
    - admissions/pages/MeritListGenerationPage.tsx:23 (Admission cycle ID)
    - institution-admin/pages/StaffRecordsPage.tsx:411 (Institution ID), :412 (Department ID)
    - institution-admin/pages/TenantSupportPage.tsx:154 (Institution ID)
    - staff/pages/StaffAppraisalPage.tsx:145 (Academic year ID)
    - t10/pages/MessageComposerPage.tsx:60 (Recipient user ID) — picker should search by name,
      not show a UUID at all once resolved
    - platform/components/observability/AuditLogView.tsx:164 (Tenant ID), :168 (Actor user ID)
      — platform-side, picker searches tenant name / user display name
    - support/SupportSessionDashboard.tsx:139 (Tenant ID) — same, tenant name search
  EXCLUDE (already correct, do not change): any "National ID last 4 digits" / Aadhaar field,
  FeePaymentPortalPage.tsx:175 "Payment ID" (operator-typed receipt lookup),
  CreateInstitutionModal.tsx:153,168 and InstitutionIdFormatsModal.tsx:77,87 ("ID format" is a
  config pattern string, not an entity reference), StudentDirectoryPage.tsx:32 (search box).

ITEM 3 — One shared PhoneField, replacing 12 inconsistent instances
  Create design-system/components/PhoneField.tsx: country code + national number, type="tel",
  inputMode="tel", live format check, one consistent hint ("Include the country code, e.g.
  +919876543210" — copy CreateUserModal's existing wording, it's the best one today). Replace:
    - admissions/pages/ApplicantPortalPage.tsx:38 ("Phone")
    - institution-admin/pages/StaffRecordsPage.tsx:390,488 ("Personal mobile (E.164)" — drop
      "(E.164)" from what the user sees, that's an implementation detail)
    - features/landing/LandingPage.tsx:1229,1415 ("Phone number")
    - platform/components/tenants/CreateTenantModal.tsx:149 ("Primary contact mobile (+91 format)")
    - features/profile/components/ProfileDetailsTab.tsx:182 ("Mobile")
    - students/components/GuardianManagerModal.tsx:74 ("Mobile (E.164)")
    - user-management/components/details/GuardianManagementPanel.tsx:281 ("Mobile Number *")
    - user-management/components/users/CreateTopUserModal.tsx:107 ("Mobile Number (Optional, E.164)")
    - user-management/components/users/CreateUserModal.tsx:112 ("Mobile number" — this one is the
      reference for the hint wording, migrate it to the new shared component too)
    - user-management/pages/UserManagementPages.tsx:797 ("Mobile")
  Every one of these ends up with the identical label "Mobile number" and identical behavior.

ITEM 4 — Live inline validation while typing
  Add client-side format checks (email shape, phone shape, required non-empty) on
  blur/debounced-change, using useFormErrors.setFieldError — kept separate from
  clearFieldError's existing job of clearing a *server*-side error. Start with the forms Items
  1-3 touch, then sweep the rest of app/src/features form by form.

ITEM 5 — Hardcoded colors in CORE design-system components (fix first, highest leverage)
  These 3 files are shared by every page that uses them, so fixing them fixes consistency and
  dark-theme correctness app-wide in one move:
    - design-system/components/Toast.tsx — 36 hardcoded hex values, replace with --color-*
      tokens from tokens.css (check both themes after)
    - design-system/components/ShareMenu.tsx — 6
    - design-system/components/MetricCard.tsx — a few
  Then the page-level offenders: platform/components/reports/ReportsView.tsx (6),
  support/SupportSessionBanner.tsx (3), platform/components/tenants/TenantBrandingModal.tsx (3),
  user-management/components/details/GuardianManagementPanel.tsx (2),
  user-management/components/users/BulkUploadModal.tsx (1),
  platform/components/landing/PlatformLandingCmsView.tsx (1),
  academic/pages/TimetableGridView.tsx (1).
  DO NOT touch: documents/pages/CertificateRequestPage.tsx and
  platform/components/billing/BillingEstimateShareModal.tsx — these render a printed/exported
  document whose colors are intentionally fixed, not theme-driven. LandingPage.css (201 hits) is
  lower priority — it's the public marketing page's own brand palette; tokenize only if time
  allows, verify the public landing page still matches its intended brand look afterward (not
  the app's internal design-system look).

ITEM 6 — Spacing-token sweep (~26 known spots)
  Grep `padding:\s*[0-9]|margin:\s*[0-9]|gap:\s*[0-9]` across app/src/features and
  app/src/design-system, excluding anything already using var(--space-N). Known spots include
  AcademicSessionDetailPage.tsx:38, ClashWarningToast.tsx:4, CreatePlanModal.tsx:816,827, and
  ~20 more in LandingPage.tsx, TenantsView.tsx, CreatedCredentialsModal.tsx. Replace each with
  the matching --space-N token. Do not change intentional 0-resets (padding:0 button reset,
  margin:0 on a <p> that's deliberately tight).

ITEM 7 — Nav bar icon/order consistency (layout/AppLayout.tsx)
  Give Roles & permissions, Role Previews, and Data protection each their own icon — stop
  reusing "shield" for all three. Pick one placement rule for "Self-service"/"My sessions"
  (recommend: always last, under a visual divider) and apply it to all ~10 role nav arrays.

ITEM 8 — Lighthouse performance/SEO/best-practices
  - Self-host or subset the 4 Google Font families pulled from fonts.googleapis.com in
    index.html (DM Sans, Manrope, Noto Sans Devanagari, Noto Sans Kannada) — only weights
    actually used, and only load the Devanagari/Kannada subsets on pages that need them.
  - Add loading="lazy" + explicit width/height to LandingPage.tsx:608 and :996 (both missing it;
    :716 already has it correctly).
  - Run `npm run build`, inspect per-chunk gzip sizes; anything over ~40KB gzip
    (InstitutionSetupPages 112KB raw, UserManagementPages 55KB, TenantsView 54KB, PlansView
    59KB, BillingEstimateShareModal 36KB as its own chunk) — use rollup-plugin-visualizer to see
    what's pulling the weight and split further in vite.config.ts's manualChunks.
  - Add Open Graph tags + canonical link to index.html for the public landing page.
  - Run an actual Lighthouse report against a built preview and fix whatever specific
    Best Practices line items it names — this prompt can't know those without running it live.

Validate every item against CLAUDE.md's UI/UX standard (viewport sizes incl. short/laptop
heights, light AND dark theme, hover/focus/disabled/loading/error states, tokens-only
colors/fonts — re-grep for hardcoded hex after each item to confirm the count actually dropped).

Note: index.html, design-system/components/AppLoader.tsx, routes/Routes.tsx's LazyRouteView,
App.tsx, and main.tsx's landing-page chunk prefetch were already redesigned in a separate prior
session (the first-load splash and in-app lazy-route loading state). Do not redo or revert that
work as part of this prompt.
```
