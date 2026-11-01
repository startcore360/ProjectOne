# Shared frontend components

Reusable components live here; domain-specific components should live beside their feature module.

- `ui/`: accessible primitives (Button, Badge, Spinner)
- `layout/`: page-level layout elements (PageHeader)
- `feedback/`: empty and error states

Import from `@/components` or a focused subfolder. Keep API calls and business rules out of presentational components. Add components only when they have a real use case; do not expose nonfunctional placeholder controls.
