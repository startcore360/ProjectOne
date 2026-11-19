import { useCallback, useEffect, useState } from "react";
import { Button, DataTable, EmptyState, ErrorState, Spinner } from "../../../../design-system/components";
import { usePlatformAccess } from "../../platformAccess";
import { platformDataProtectionService, type RbacMappingReportDto, type RbacMappingReportRowDto } from "../../api/platformDataProtectionService";

function ReportRows({ rows, emptyTitle }: { rows: RbacMappingReportRowDto[]; emptyTitle: string }) {
  return rows.length ? (
    <DataTable headers={["Scope", "Tenant", "Role", "Action", "Result", "Detail"]}>
      {rows.map((row, index) => (
        <tr key={`${row.tenantId ?? "platform"}-${row.roleCode}-${row.actionCode ?? row.result}-${index}`}>
          <td>{row.scope}</td>
          <td>{row.tenantName ?? "Platform"}</td>
          <td><strong>{row.roleName} ({row.roleCode})</strong></td>
          <td>{row.actionCode ?? "—"}</td>
          <td>{row.result}</td>
          <td>{row.detail}</td>
        </tr>
      ))}
    </DataTable>
  ) : <EmptyState title={emptyTitle} description="There are no records in this group." />;
}

export function RbacMappingCheckView() {
  const { can } = usePlatformAccess();
  const permitted = can("RBAC_MAPPING_CHECK");
  const [report, setReport] = useState<RbacMappingReportDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!permitted) return;
    setLoading(true);
    setError(null);
    try {
      const response = await platformDataProtectionService.getRbacMappingReport();
      setReport(response.data);
    } catch {
      setError("The RBAC mapping report could not be loaded. Check your access and retry.");
    } finally {
      setLoading(false);
    }
  }, [permitted]);

  useEffect(() => {
    if (permitted) void refresh();
  }, [permitted, refresh]);

  if (!permitted) return <section aria-label="RBAC mapping check"><p role="alert">You do not have permission to run the RBAC mapping check.</p></section>;

  return (
    <section aria-label="RBAC mapping check">
      <div className="data-protection-workspace__decision-actions">
        <Button variant="secondary" onClick={() => void refresh()} disabled={loading}>Refresh report</Button>
        <Button onClick={() => platformDataProtectionService.downloadRbacMappingReport()} disabled={loading || !report}>Export CSV</Button>
      </div>
      {loading && <div className="app-empty-inline"><Spinner label="Checking role permission mappings" /></div>}
      {error && <ErrorState title="RBAC mapping report unavailable" message={error} action={<Button variant="secondary" onClick={() => void refresh()} disabled={loading}>Retry</Button>} />}
      {report && !error && !loading && (
        <>
          <h2>Permission mismatches</h2>
          <ReportRows rows={report.mismatches} emptyTitle="No permission mismatches" />
          <h2>Custom roles without a base template</h2>
          <p>These roles are not classified as drift because no base_standard_role was recorded.</p>
          <ReportRows rows={report.noBaseTemplate} emptyTitle="No custom roles without a base template" />
          <h2>Skipped tenants</h2>
          {report.skippedTenants.length ? (
            <DataTable headers={["Tenant", "Tenant ID", "State"]}>
              {report.skippedTenants.map((tenant) => <tr key={tenant.tenantId}><td>{tenant.tenantName}</td><td>{tenant.tenantId}</td><td>{tenant.state}</td></tr>)}
            </DataTable>
          ) : <EmptyState title="No tenants skipped" description="Every tenant in the directory was eligible for checking." />}
        </>
      )}
    </section>
  );
}
