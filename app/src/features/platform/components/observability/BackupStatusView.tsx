import { useEffect, useState } from "react";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Button, Panel, DataTable, Notice } from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { formatTimestamp, readErrorMessage, type FetchStatus } from "../common/types";
import api from "../../../../services/axios";

export type BackupStatusEntry = {
  tenantId: string;
  lastDailyBackupAt: string | null;
  lastBackupOutcome: string | null;
  earliestRestorableAt: string | null;
  latestRestorableAt: string | null;
  lastRestoreTestAt: string | null;
  lastRestoreTestOutcome: string | null;
  stale: boolean;
};

export type BackupStatus = {
  wiredToRealBackupSystem: boolean;
  note: string;
  tenants: BackupStatusEntry[];
};

export function BackupStatusView() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [data, setData] = useState<BackupStatus | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let active = true;
    // "loading" is set by the initial state and by the Retry handler, not here.
    api
      .get("/platform/backup-status")
      .then((res) => {
        if (!active) return;
        setData(res.data);
        setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setErrorMessage(readErrorMessage(err, "Could not load backup status."));
        setStatus("error");
      });
    return () => {
      active = false;
    };
  }, [retryCount]);

  return (
    <div className="app-page">
      <PageHeader title="Backup status" description="Per-tenant daily backup and restore-test status." />
      {status === "error" && (
        <ErrorState
          message={`Could not load backup status: ${errorMessage}`}
          action={<Button variant="secondary" onClick={() => { setStatus("loading"); setRetryCount((c) => c + 1); }}>Retry</Button>}
        />
      )}
      {data && !data.wiredToRealBackupSystem && (
        <Notice tone="info">
          <strong>Not connected to a real backup system.</strong> {data.note}
        </Notice>
      )}
      <Panel title="Tenant backup status" description="platform.tenant_backup_status">
        {status === "loading" ? (
          <div className="app-empty-inline">
            <Spinner label="Loading backup status" />
          </div>
        ) : (
          <DataTable
            headers={[
              "Tenant",
              "Last backup",
              "Backup outcome",
              "Restorable window",
              "Last restore test",
              "Restore test outcome",
              "Backup freshness",
            ]}
            empty={!data?.tenants.length}
            emptyMessage={
              status === "error" ? "Backup status could not be loaded." : "No backup status rows recorded yet."
            }
          >
            {(data?.tenants ?? []).map((t) => (
              <tr key={t.tenantId}>
                <td>{t.tenantId}</td>
                <td>{formatTimestamp(t.lastDailyBackupAt)}</td>
                <td>{t.lastBackupOutcome ?? "—"}</td>
                <td>
                  {t.earliestRestorableAt && t.latestRestorableAt
                    ? `${formatTimestamp(t.earliestRestorableAt)} – ${formatTimestamp(t.latestRestorableAt)}`
                    : "—"}
                </td>
                <td>{formatTimestamp(t.lastRestoreTestAt)}</td>
                <td>{t.lastRestoreTestOutcome ?? "—"}</td>
                <td>{t.stale ? "Stale" : "Current"}</td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>
    </div>
  );
}

// Export alias
export const BackupStatusPage = BackupStatusView;
