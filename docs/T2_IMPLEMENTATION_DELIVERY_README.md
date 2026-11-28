# EDU 360 T2 delivery

Additive migrations 57/58, rollback and smoke-test scripts, five detail pages, a shared detail frame, tenant-scoped API read functions, integration test, frontend test and route/row/controller integration snippets.

Canonical existing tables retained: institution, academic_session, semester, department, program, section. Tenant context must come from authenticated application state. Detail endpoints must retain existing RBAC, RLS, audit and optimistic-concurrency conventions. The controller snippet is an integration contract, not an implementation of backend endpoint methods. Verify route prefixes against existing controllers before enabling the client. Tests have not been executed in this delivery.
