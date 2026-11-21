# Chat T10 — Communications, Library, Transport & Executive Analytics

You are a senior full-stack developer (Java 21 / Spring Boot 3.4.3, React 19 / TypeScript 5.8, PostgreSQL 16) working on EDU 360. Work in the repository on branch `sprint-3-development`.

## Your mission

Deliver Communications, Circulars, Library Operations, Transport Tracking, and Institutional Executive Dashboards (**Modules 17, 18, 19, 20, 21: Actions 196–234**). You own the digital notice board with role targeting, multi-channel messaging (in-app, push, SMS, email), library book circulation with overdue fine calculation, bus route stop allocations, and executive analytics reports executing over the read-only `edu_report` database connection pool.

---

## Your stories

Your acceptance criteria are **every bullet, rule, and table cell for these story IDs in `docs/TENANT_COMPLETION_PLAN.md`**, word for word.

| ID | Action Code(s) | What | Plan Section |
|---|---|---|---|
| **T10-S1** | `ACT-196`..`ACT-203` | Notice Board & Circulars: role-scoped bulletin posts (All, Faculty, Students, Parents), mandatory read acknowledgments, and urgent alert popups. | §7.17 |
| **T10-S2** | `ACT-204`..`ACT-213` | Communications & Message Dispatch: multi-channel campaign composer, template substitution (`{{student_name}}`), carrier delivery logs, and SMS usage tracking. | §7.18 |
| **T10-S3** | `ACT-214`..`ACT-221` | Basic Library Operations: book catalog, ISBN search, copy tracking, check-out/check-in circulation, reservation queues, and dynamic overdue fine accrual. | §7.19 |
| **T10-S4** | `ACT-222`..`ACT-228` | Basic Transport Tracking: bus routes, pickup stops, vehicle capacity limits, driver assignments, passenger manifests, and transport fee linkage. | §7.20 |
| **T10-S5** | `ACT-229`..`ACT-234` | Executive Dashboards & Analytics Reports: attendance trends, fee collection KPIs, enrollment ratios, and statutory reports running over `edu_report` connection. | §7.21 |

---

### Detailed Technical Specifications

#### Story T10-S1 & T10-S2: Notices & Communications (M17 & M18)
- **Database Tables:**
  - `app.notice_bulletin` (`id`, `tenant_id`, `institution_id`, `title`, `body`, `target_roles_json`, `is_urgent`, `published_at`, `expires_at`).
  - `app.notice_acknowledgment` (`id`, `notice_id`, `user_id`, `acknowledged_at`).
  - `app.communication_dispatch` (`id`, `tenant_id`, `channel` [IN_APP, EMAIL, SMS], `recipient_user_id`, `phone`, `email`, `message_body`, `carrier_message_id`, `status` [QUEUED, SENT, DELIVERED, FAILED], `sent_at`).
- **Carrier Usage Tracking:**
  - For every carrier SMS sent, record usage metric in `platform.tenant_usage_monthly` (metric code `SMS_SENT`) for monthly consolidated billing.

#### Story T10-S3: Library Operations (M19)
- **Database Tables:**
  - `app.library_book` (`id`, `tenant_id`, `institution_id`, `isbn`, `title`, `author`, `category`, `total_copies`, `available_copies`).
  - `app.book_circulation` (`id`, `book_id`, `borrower_user_id`, `issued_at`, `due_date`, `returned_at`, `fine_amount`, `status` [ISSUED, RETURNED, OVERDUE, LOST]).
- **Business Logic:**
  - Maximum active borrows: Student $\le$ 3 books; Faculty $\le$ 10 books.
  - Nightly job calculates overdue fine for days past `due_date`.
  - Exposes `hasUnreturnedBooks(studentId)` for Chat T9's No-Dues check.

#### Story T10-S4: Transport Tracking (M20)
- **Database Tables:**
  - `app.transport_route` (`id`, `tenant_id`, `institution_id`, `route_name`, `vehicle_number`, `driver_name`, `driver_phone`, `total_capacity`).
  - `app.route_stop` (`id`, `route_id`, `stop_name`, `pickup_time`, `drop_time`, `fee_amount`).
  - `app.transport_allocation` (`id`, `route_id`, `stop_id`, `passenger_user_id`, `status` [ACTIVE, CANCELLED]).
- **Constraint:** Allocation count cannot exceed `transport_route.total_capacity`.

#### Story T10-S5: Institutional Dashboards & Reporting (M21)
- **Architecture:**
  - All report queries execute exclusively on the secondary `edu_report` database connection pool (`ReportJpaConfig.java`) to ensure analytical queries never lock OLTP tables.
- **Frontend:**
  - `ExecutiveDashboardView.tsx`: Interactive widgets rendering student attendance rates, daily fee collection totals, active enrollment counts, and compliance status.

---

## Database migrations you own

Use **only** numbers **73** and **74**.
- `73_v41_notices_and_communications.sql`: `app.notice_bulletin`, `app.communication_dispatch`.
- `74_v41_library_and_transport_schema.sql`: `app.library_book`, `app.book_circulation`, `app.transport_route`, `app.transport_allocation`.

---

## Files you own

- **Backend:**
  - `com.edu360.api.communication.controller.NoticeBulletinController`
  - `com.edu360.api.communication.controller.CommunicationDispatchController`
  - `com.edu360.api.facility.controller.LibraryController`
  - `com.edu360.api.facility.controller.TransportController`
  - `com.edu360.api.report.controller.InstitutionalReportController`
  - `com.edu360.api.report.service.ExecutiveAnalyticsService`
- **Frontend:**
  - `app/src/features/communication/pages/NoticeBulletinPage.tsx`
  - `app/src/features/communication/pages/MessageComposerPage.tsx`
  - `app/src/features/facilities/pages/LibraryCatalogPage.tsx`
  - `app/src/features/facilities/pages/TransportRoutePage.tsx`
  - `app/src/features/reports/pages/ExecutiveDashboardPage.tsx`
- **Tests:**
  - `api/src/test/java/com/edu360/api/report/ExecutiveAnalyticsReportIT.java`
  - `api/src/test/java/com/edu360/api/facility/LibraryOverdueFineIT.java`
  - `app/src/features/reports/__tests__/ExecutiveDashboardPage.test.tsx`

---

## Depends on / others depend on you

- **You depend on:** Chat T4 for students/users and Chat T8 for recording SMS billable overages.
- **Chat T9 depends on you:** Your `app.book_circulation` is checked during No-Dues certificate verification.

---

## Verification you must run and show before pushing

1. `cd api && .\mvnw.cmd test -Dtest=ExecutiveAnalyticsReportIT,LibraryOverdueFineIT`
2. `cd app && npm test -- ExecutiveDashboardPage`
3. `npm run lint && npm run build`
