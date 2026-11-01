# Frontend component organization

This directory contains reusable, domain-independent components.

- `ui/`: accessible primitives such as Button, Badge and Spinner.
- `feedback/`: empty and error states.
- `layout/`: reusable layout elements such as PageHeader.

Feature-specific UI belongs under `src/features/<feature>/`. For example, authentication pages live in `src/features/auth/` and dashboard pages live in `src/features/dashboard/`.

Keep API calls and business rules out of presentational components. Add components only for real use cases; do not expose nonfunctional placeholder controls.
