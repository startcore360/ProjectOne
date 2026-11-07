import { useEffect, useState } from "react";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Panel, DataTable, Notice } from "../../../../design-system/components";
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

  useEffect(() => {
    let active = true;
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
  }, []);

  return (
    <div className="platform-page">
      <PageHeader title="Backup status" description="Per-tenant daily backup and restore-test status." />
      {status === "error" && <Notice tone="danger">Could not load backup status: {errorMessage}</Notice>}
      {data && !data.wiredToRealBackupSystem && (
        <Notice tone="info">
          <strong>Not connected to a real backup system.</strong> {data.note}
        </Notice>
      )}
      <Panel title="Tenant backup status" description="platform.tenant_backup_status">
        {status === "loading" ? (
          <div className="platform-empty-inline">
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
