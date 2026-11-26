# T10 implementation contract — library dues, notices, dispatch, transport and analytics

## Library fine policy

Migration 74 creates `app.library_fine_policy`, keyed by `(tenant_id, institution_id)`.
The configuration fields are `grace_period_days`, `calculation_mode` (`FLAT_DAILY_AMOUNT`), `daily_amount`, `day_basis` (`CALENDAR_DAYS` or `WORKING_DAYS`), `working_days` (ISO weekdays 1–7), `fine_cap`, and `is_placeholder_default`.

**The INR 5/day, INR 500 cap, zero-grace, calendar-day seed is a placeholder only. It is not an approved fixed business rule.** Each institution can change its own policy. The calculation function is `app.fn_library_fine_amount(tenant_id, institution_id, due_date)`; overdue fines accrue dynamically in the library-dues view and are assessed on return. Partial payment and returned-lost-book workflows are out of scope. The database permits only `amount_paid = 0` or `amount_paid = amount`.

Borrow limits are enforced server-side using the borrower's actual account and active role: Student 3, Faculty (Professor / Associate Professor / Assistant Professor) 10. The borrower role supplied by the client is not trusted.

## T9 No-Dues read contract

Migration 74 publishes `app.v_student_library_dues_flag` as a read-only, `security_invoker` view. Columns:
- `tenant_id uuid`
- `institution_id uuid`
- `student_id uuid` — borrower user identifier
- `has_library_dues boolean` — true for unreturned/overdue/lost circulation or an outstanding fine
- `outstanding_fine_balance numeric(12,2)` — outstanding assessed fines plus dynamically accrued overdue fines for open circulations

Underlying tables retain tenant and institution RLS. T9 should query this view under the student's tenant/institution context and treat no row as no library activity/dues. This is a read contract, not a write API.

## T10 endpoints and pages added

- `NoticeBulletinController`: publish/list notices and record required acknowledgments; role targets are persisted in `app.notice_target`.
- `CommunicationDispatchController`: queue private multi-channel dispatches and expose dispatch history.
- `LibraryController`: catalog, issue/return, reservations, per-institution fine policy and full fine payment.
- `TransportController`: route/vehicle/stop creation, route listing and allocation; capacity is enforced by a database trigger.
- `InstitutionalReportController` / `ExecutiveAnalyticsService`: dashboard metrics are read through the separate `edu_report` datasource. The report transaction sets tenant and institution RLS GUCs locally; OLTP stores only report definition/execution metadata.
- React pages: Notice Bulletin, Message Composer, Library Catalog, Transport Routes and Executive Dashboard. Routes are guarded by tenant action codes.

## Runtime configuration and delivery boundary

Configure `app.edu-report.datasource.url`, `app.edu-report.datasource.username` and `app.edu-report.datasource.password` to connect as PostgreSQL role `edu_report`. The pool is marked read-only and sets `default_transaction_read_only=on`. Do not reuse the OLTP `edu_app` credential.

Spring environment-variable equivalents are `APP_EDU_REPORT_DATASOURCE_URL`, `APP_EDU_REPORT_DATASOURCE_USERNAME` and `APP_EDU_REPORT_DATASOURCE_PASSWORD`. The reporting username is deliberately required to be exactly `edu_report`.

For provider deployment, configure `APP_DISPATCH_PROVIDERS_PUSH_URL` / `APP_DISPATCH_PROVIDERS_PUSH_TOKEN`, `APP_DISPATCH_PROVIDERS_SMS_URL` / `APP_DISPATCH_PROVIDERS_SMS_TOKEN`, and `APP_DISPATCH_PROVIDERS_EMAIL_URL` / `APP_DISPATCH_PROVIDERS_EMAIL_TOKEN`. The worker polling interval is `APP_DISPATCH_WORKER_FIXED_DELAY_MS` (default 5000). Tokens must be supplied by the deployment secret manager; never commit them to the repository.

The generic HTTP adapter treats a successful provider HTTP response as accepted/sent, not proof of handset or mailbox delivery. A provider delivery receipt must update the dispatch through the authenticated outcome integration before the status can truthfully be reported as `DELIVERED`. No live vendor credentials or provider-specific receipt webhook are included in this repository change.

Dispatch attempts are persisted in fallback order (In-app → Push → SMS → Email). Only the first attempt is queued; later attempts wait. `DispatchWorker` polls ACTIVE/TRIAL tenants, claims one queued attempt using `FOR UPDATE SKIP LOCKED`, and releases the database transaction before making provider HTTP calls. A five-minute lease recovers claims after process interruption; attempt counts are retained. Success cancels remaining fallback attempts; failure queues the next attempt. In-app delivery writes to the existing `app.notification` inbox using a deterministic notification UUID.

PUSH, SMS and EMAIL use a configurable JSON-over-HTTP gateway adapter. Set Spring Boot environment variables `APP_DISPATCH_PROVIDERS_PUSH_URL` / `APP_DISPATCH_PROVIDERS_PUSH_TOKEN`, `APP_DISPATCH_PROVIDERS_SMS_URL` / `APP_DISPATCH_PROVIDERS_SMS_TOKEN`, and `APP_DISPATCH_PROVIDERS_EMAIL_URL` / `APP_DISPATCH_PROVIDERS_EMAIL_TOKEN` as deployment secrets. Set `APP_DISPATCH_WORKER_FIXED_DELAY_MS` to override polling. Keep all values in a deployment secret manager; never commit provider URLs or tokens. The gateway must accept `X-Idempotency-Key` (the dispatch UUID), a bearer token, and a JSON payload containing `tenantId`, `institutionId`, `recipientUserId`, `to`, and `body`; a JSON `messageId` response is stored as the carrier ID. No vendor-specific account or credential is bundled. An unconfigured gateway fails that attempt and advances fallback; configure at least one usable channel. HTTP 2xx means accepted/sent, not delivery receipt. SMS usage counts only `SENT` or `DELIVERED` rows. `SMS_SENT` metering counts only dispatch rows marked `SENT` or `DELIVERED`; queued and failed rows are not counted. Do not interpret a queued dispatch as delivered.

PostgreSQL integration tests are opt-in via `EDU360_T10_JDBC_URL` and `EDU360_REPORT_JDBC_URL` (with corresponding `_USER` and `_PASSWORD` variables). CI provisions disposable PostgreSQL 16, applies baseline schema plus migrations 73/74, and executes library, reporting-role and dispatch-provider tests. This does not prove end-to-end delivery through real gateways; that requires deployment-owned provider credentials and authenticated delivery-receipt integration.
