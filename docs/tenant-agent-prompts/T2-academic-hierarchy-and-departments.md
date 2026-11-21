# Chat T2 — Academic Structure, Institutional Hierarchy & Department Setup

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-3-development`.

## Your mission

Deliver the complete institutional hierarchy and academic catalog for Level 2 (University), Level 3 (College), and Level 4 (School/Department) institutions (**Module 3: Actions 22–32**). You own university groups, standalone degree colleges, K-12 schools, academic years, semesters, departments, programs, courses, classes, and sections.

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T2-S1** | `ACT-022`, `ACT-023` | Institution registration and multi-institution management: College/School creation under a University Group or Standalone Tenant. Strict institution scoping. | §7.3 |
| **T2-S2** | `ACT-024`, `ACT-025` | Academic Year & Calendar Terms: define academic calendars, terms, non-overlapping constraints, and term dates. | §7.3 |
| **T2-S3** | `ACT-026`, `ACT-027` | Department and Faculty Center management: create, update, and assign Heads of Department (HOD). | §7.3 |
| **T2-S4** | `ACT-028`, `ACT-029` | Programs, Degrees & Curriculum Catalog: degree types (UG, PG, Diploma, K-12 Grade), credit structures, and syllabus versions. | §7.3 |
| **T2-S5** | `ACT-030`..`ACT-032` | Classes, Batches & Section Management: Section provisioning (A, B, C), room capacity bindings, and Class Teacher / Mentor assignment. | §7.3 |

---

### Detailed Technical Specifications

#### Story T2-S1: Multi-Institution Hierarchy Engine (M03)
- **Database Tables:** `app.institution` (`id`, `tenant_id`, `parent_institution_id`, `institution_type` [UNIVERSITY, COLLEGE, SCHOOL], `code`, `name`, `status`, `settings_json`).
- **Backend:**
  - `InstitutionController.java`: `GET /api/institutions`, `POST /api/institutions`, `GET /api/institutions/{id}`, `PUT /api/institutions/{id}`.
  - Validate that standalone colleges/schools have `parent_institution_id = NULL`, while university colleges link to the university institution.
  - Enforce `RESTRICTIVE` RLS policy `p_institution` ensuring college users see only their institution records.
  - **Decision PO-T06 (Delegated Autonomy with Executive Override):**
    - Constituent colleges operate autonomously for daily academic schedules and internal evaluations.
    - Implement `POST /api/institutions/{id}/override` allowing University officers (`VICE_CHANCELLOR`, `REGISTRAR`, `MANAGEMENT`) to execute an executive override on any affiliated institution's decision.
    - Requires mandatory `overrideReason` (min 20 characters) and records an immutable audit record in `app.audit_outbox` with before/after state payloads.
- **Frontend:**
  - `InstitutionHierarchyView.tsx`: Interactive tree viewer rendering University $\rightarrow$ Constituent Colleges $\rightarrow$ Departments.
  - Add "Executive Override" action button visible strictly to University executive roles, triggering an override confirmation modal with mandatory rationale input.

#### Story T2-S2: Academic Calendar & Term Configuration
- **Database Tables:** `app.academic_year` (`id`, `tenant_id`, `institution_id`, `name`, `start_date`, `end_date`, `is_current`), `app.term` (`id`, `academic_year_id`, `name`, `term_number`, `start_date`, `end_date`, `is_locked`).
- **Business Logic:**
  - Exclusion constraint `EXCLUDE USING gist (institution_id WITH =, daterange(start_date, end_date, '[]') WITH &&)` prevents overlapping academic years.
  - Stored procedure `fn_set_current_academic_year(p_year_id uuid)` atomically clears previous current year and sets new current year.

#### Story T2-S3: Departments & Faculty Setup
- **Database Tables:** `app.department` (`id`, `tenant_id`, `institution_id`, `code`, `name`, `hod_user_id`, `status`).
- **Backend:** `DepartmentController.java` (`GET /api/departments`, `POST /api/departments`, `PUT /api/departments/{id}/hod`).
- **Frontend:** `DepartmentListView.tsx` with department cards, faculty counts, student counts, and HOD assignment modal.

#### Story T2-S4 & T2-S5: Programs, Batches & Class Sections
- **Database Tables:** `app.program` (`id`, `department_id`, `code`, `name`, `degree_level`, `duration_years`, `total_credits`), `app.batch` (`id`, `program_id`, `name`, `admission_year`), `app.class_section` (`id`, `batch_id`, `term_id`, `section_name`, `capacity`, `class_teacher_user_id`).
- **Backend:** Full CRUD controllers with pagination, sorting, and optimistic concurrency (`If-Match`).

---

## Database migrations you own

Use **only** numbers **57** and **58**.
- `57_v41_academic_hierarchy_constraints.sql`: Exclusion constraints on `app.academic_year` and `app.term`.
- `58_v41_department_program_section_schema.sql`: Indexes and triggers for department and section capacity tracking.

---

## Files you own

- **Backend:**
  - `com.edu360.api.academic.controller.InstitutionController`
  - `com.edu360.api.academic.controller.AcademicYearController`
  - `com.edu360.api.academic.controller.DepartmentController`
  - `com.edu360.api.academic.controller.ProgramController`
  - `com.edu360.api.academic.controller.ClassSectionController`
  - `com.edu360.api.academic.service.AcademicHierarchyService`
- **Frontend:**
  - `app/src/features/academic/pages/InstitutionHierarchyPage.tsx`
  - `app/src/features/academic/pages/AcademicCalendarPage.tsx`
  - `app/src/features/academic/pages/DepartmentManagementPage.tsx`
  - `app/src/features/academic/pages/ProgramCatalogPage.tsx`
  - `app/src/features/academic/pages/SectionRosterPage.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/academic/AcademicHierarchyIT.java`
  - `app/src/features/academic/__tests__/AcademicCalendarPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T1 for `TenantSecurityInterceptor` and basic schema bindings.
- **Chats T3, T4, T5, T6 depend on you** for institutions, departments, programs, and class sections.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=AcademicHierarchyIT`
2. `cd app && npm test -- AcademicCalendarPage`
3. `npm run lint && npm run build`
