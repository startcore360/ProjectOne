/**
 * Platform Management Module
 *
 * Modularized following NyayTech design system & feature patterns:
 * - Common design primitives live in `features/platform/components/common/`
 * - Individual views and dialogs live in their respective subdirectories:
 *   - `overview/`
 *   - `tenants/`
 *   - `users/`
 *   - `plans/`
 *   - `support/`
 *   - `observability/`
 *   - `integrations/`
 */

export { PlatformOverviewPage } from "../components/overview/PlatformOverviewView";
export { TenantsPage } from "../components/tenants/TenantsView";
export { PlatformUsersPage } from "../components/users/PlatformUsersView";
export { PlatformRolesPage } from "../components/roles/RolesView";
export { PlatformRoleTemplatesPage } from "../components/roles/TemplatesView";
export { PlansPage } from "../components/plans/PlansView";
export { SupportAccessPage } from "../components/support/SupportAccessView";
export { AuditLogPage } from "../components/observability/AuditLogView";
export { UsageCountsPage } from "../components/observability/UsageCountsView";
export { BackupStatusPage } from "../components/observability/BackupStatusView";
export { IntegrationTypesPage } from "../components/integrations/IntegrationTypesView";
export { InvoicesPage } from "../components/billing/InvoicesView";
export { ReleasesPage } from "../components/release/ReleasesView";
export { ReportsPage } from "../components/reports/ReportsView";
export { SecurityPolicyPage } from "../components/security/SecurityPolicyView";
export { TicketsPage } from "../components/support/TicketsView";

// Re-export design system UI primitives for backwards compatibility
export {
  MetricCard,
  Metric,
  Panel,
  StatusBadge,
  Status,
  DataTable,
  SearchBox,
  Notice,
  SampleNote,
} from "../../../design-system/components";
