# EDU 360 repository structure

This document records the current repository layout after the feature-first frontend organization update and distinguishes implemented folders from the longer-term V4.0 architecture target.

## Top-level layout

```text
ProjectOne/
├── app/                         # FRONTEND — React + Vite + TypeScript
├── api/                         # BACKEND — Spring Boot REST API (current monolith)
├── docs/                        # Project architecture and engineering documentation
└── .gitignore
```

## Frontend — `app/`

Technology: React, TypeScript, Vite, Tailwind CSS, React Router, Redux Toolkit.

```text
app/
├── public/                      # Static public assets
├── src/
│   ├── App.tsx                  # Application composition; delegates route definitions
│   ├── main.tsx                 # Browser entry point, providers and bootstrap
│   ├── main.css                 # Global stylesheet entry point
│   ├── assets/                  # Imported images and static assets
│   ├── design-system/           # EDU 360 Stillwater design foundations
│   │   ├── tokens.css           # Design tokens
│   │   └── components.css       # Shared component styling
│   ├── components/              # Reusable, domain-independent components
│   │   ├── ui/                  # Button, Badge, Spinner and primitives
│   │   ├── feedback/             # EmptyState, ErrorState and feedback patterns
│   │   └── layout/              # Shared layout elements such as PageHeader
│   ├── features/                # Feature/domain-owned frontend code
│   │   ├── auth/
│   │   │   └── pages/
│   │   │       └── LoginPage.tsx
│   │   └── dashboard/
│   │       └── pages/
│   │           └── DashboardPage.tsx
│   ├── layout/                  # Application shell and authentication layouts
│   │   ├── AppLayout.tsx
│   │   └── AuthLayout.tsx
│   ├── routes/                  # Route table and route guards
│   │   ├── Routes.tsx
│   │   ├── PrivateRoute.tsx
│   │   └── PublicRoute.tsx
│   ├── services/                # HTTP client, session lifecycle and API modules
│   │   ├── modules/
│   │   │   └── authService.tsx
│   │   └── ...                  # axios, interceptors, auth bootstrap, logout
│   ├── store/                   # Redux store, typed hooks and slices
│   │   └── slices/
│   ├── hooks/                   # Shared React hooks
│   ├── lib/                     # Small shared library helpers
│   ├── utils/                   # Shared utility functions
│   └── test/                    # Frontend test setup
├── package.json
├── vite.config.ts
├── vitest.config.ts
└── tailwind.config.ts
```

### Frontend ownership rules

- Put feature-specific pages, components, hooks and feature-only logic under `src/features/<feature>/`.
- Put genuinely reusable, domain-independent components under `src/components/`.
- Keep route registration in `src/routes/Routes.tsx`; `App.tsx` should compose the route module rather than declare a second route table.
- Keep the existing `layout/`, `services/` and `store/` directories as the canonical locations. Do not create parallel `layouts/`, `api/` or `app/store/` folders.
- Add a feature directory when that feature has real implementation files. Avoid empty placeholder folders.

## Backend — `api/` (current implementation)

Technology: Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven.

```text
api/
├── pom.xml
├── mvnw / mvnw.cmd
└── src/
    ├── main/
    │   ├── java/com/edu360/api/
    │   │   ├── ApiApplication.java
    │   │   ├── auth/              # Authentication controller, service and DTOs
    │   │   ├── attendance/        # Attendance entity, controller, repository, service, DTOs
    │   │   ├── course/            # Course entity, controller, repository, service, DTOs
    │   │   ├── performance/       # Performance controller, service and DTOs
    │   │   ├── score/             # Score entity, controller, repository, service, DTOs
    │   │   ├── user/              # User and role model/repository
    │   │   ├── config/            # Security, JWT and OpenAPI configuration
    │   │   └── exception/         # Shared API exception handling
    │   └── resources/
    │       ├── application.properties
    │       ├── application-dev.properties
    │       └── application-prod.properties
    └── test/java/com/edu360/api/
```

The backend remains one Spring Boot application. Its feature packages are logical module boundaries, not independently deployable services. Preserve existing endpoints, DTOs, persistence behavior and authentication while improving internal organization.

## Longer-term V4.0 backend direction — target, not yet implemented

The V4.0 architecture calls for separately deployable services. A possible service-aligned target is:

```text
backend-platform/                 # FUTURE BACKEND SERVICE PLATFORM
├── gateway/
├── services/
│   ├── platform-tenant/
│   ├── access-rbac/
│   ├── users-records/
│   ├── academic/
│   ├── attendance/
│   ├── examinations-results/
│   ├── finance/
│   ├── leave/
│   ├── communication/
│   └── reporting/
├── shared/                       # Only deliberately shared contracts/libraries
└── integrations/
```

This is a migration destination, not the current repository tree. Do not create empty service projects or move production code into them until service boundaries, API contracts, data ownership, authentication, deployment and migration sequencing are agreed and tested.

## What changed in this update

1. Added feature-owned page locations for authentication and dashboard.
2. Updated the central route table to import pages from their feature modules.
3. Simplified `App.tsx` so it delegates routing to `routes/Routes.tsx`; this removes the duplicate route declaration.
4. Removed the old duplicate page files after the route table was switched to the feature-owned versions.
5. Preserved the existing application shell, auth guards, API services, Redux store, backend packages and API contracts.
6. Added this document to make the frontend/backend boundary and current-versus-target backend status explicit.

## Verification status

The repository files and route imports were reviewed through the GitHub repository. A local frontend build, frontend tests and Maven verification have not been run as part of this GitHub-only update. Run `npm run build` and `npm test` from `app/`, and `./mvnw clean verify` from `api/`, before merging.
