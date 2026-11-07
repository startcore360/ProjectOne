import { useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  DataTable,
  MetricCard,
  Notice,
  Panel,
  SearchBox,
  StatusBadge,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import {
  platformOpsService,
  type ReleaseItem,
  type CreateReleasePayload,
} from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

export function ReleasesView() {
  const { can } = usePlatformAccess();
  const canManageRelease = can("RELEASE_MANAGE");

  const [releases, setReleases] = useState<ReleaseItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  // Create Release Modal
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createDraft, setCreateDraft] = useState<CreateReleasePayload>({
    versionLabel: "v4.1.0",
    releaseNotes: "Includes support access, billing invoices, and tenant lifecycle updates.",
    isHotfix: false,
    schemaVersion: "V41__platform_updates",
  });

  // Edit Release Modal
  const [editingRelease, setEditingRelease] = useState<ReleaseItem | null>(null);
  const [editNotes, setEditNotes] = useState("");
  const [editHotfix, setEditHotfix] = useState(false);
  const [editSchema, setEditSchema] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);

  const loadReleases = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await platformOpsService.listReleases();
      setReleases(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load releases");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReleases();
  }, []);

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setError("");
    try {
      await platformOpsService.createRelease(createDraft);
      setMessage(`Release ${createDraft.versionLabel} created.`);
      setShowCreate(false);
      loadReleases();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create release");
    } finally {
      setCreating(false);
    }
  };

  const handleTransition = async (releaseId: string, versionLabel: string, targetStatus: string) => {
    try {
      await platformOpsService.transitionRelease(releaseId, targetStatus);
      setMessage(`Release ${versionLabel} transitioned to ${targetStatus}.`);
      loadReleases();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to transition release");
    }
  };

  const openEdit = (r: ReleaseItem) => {
    setEditingRelease(r);
    setEditNotes(r.releaseNotes);
    setEditHotfix(r.isHotfix);
    setEditSchema(r.schemaVersion);
  };

  const handleSaveEdit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editingRelease) return;
    setSavingEdit(true);
    setError("");
    try {
      await platformOpsService.updateRelease(editingRelease.releaseId, {
        releaseNotes: editNotes,
        isHotfix: editHotfix,
        schemaVersion: editSchema,
      });
      setMessage(`Release ${editingRelease.versionLabel} updated successfully.`);
      setEditingRelease(null);
      loadReleases();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update release");
    } finally {
      setSavingEdit(false);
    }
  };

  const filteredReleases = releases.filter((r) =>
    r.versionLabel.toLowerCase().includes(query.toLowerCase()) ||
    r.schemaVersion.toLowerCase().includes(query.toLowerCase()) ||
    r.releaseNotes.toLowerCase().includes(query.toLowerCase())
  );

  const deployed = releases.find((r) => r.status === "DEPLOYED");
  const hotfixCount = releases.filter((r) => r.isHotfix).length;
  const stagingCount = releases.filter((r) => r.status === "STAGING_TESTED").length;

  return (
    <div className="platform-page">
      <PageHeader
        title="Releases & Deployments"
        description="Version governance, database schema upgrades, staging validation, rollout deployment and hotfix tagging."
        actions={
          canManageRelease && (
            <Button onClick={() => setShowCreate(true)}>Create New Release</Button>
          )
        }
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && <Notice tone="danger">{error}</Notice>}

      <div className="kpi-grid">
        <MetricCard
          label="Active Version"
          value={deployed?.versionLabel ?? "None"}
          note={deployed?.deployedAt ? `Deployed ${new Date(deployed.deployedAt).toLocaleDateString()}` : "No live deployment"}
          mark="check-circle"
        />
        <MetricCard label="Total Releases" value={releases.length} note="Catalogue history" mark="book" />
        <MetricCard label="Staging Tested" value={stagingCount} note="Ready for rollout" mark="clock" />
        <MetricCard label="Hotfixes" value={hotfixCount} note="Emergency patches" mark="alert" />
      </div>

      <Panel title="Platform Releases" description="All tracked application and database versions">
        <div className="platform-toolbar" style={{ marginBottom: "16px" }}>
          <SearchBox
            value={query}
            onChange={(val) => setQuery(val)}
            placeholder="Search version label or schema..."
          />
        </div>

        {loading ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading releases..." />
          </div>
        ) : filteredReleases.length === 0 ? (
          <p className="platform-empty-inline">No releases recorded yet.</p>
        ) : (
          <DataTable headers={["Version", "Schema Migration", "Notes", "Status", "Actions"]}>
            {filteredReleases.map((r) => (
              <tr key={r.releaseId}>
                <td>
                  <strong>{r.versionLabel}</strong>
                  {r.isHotfix && (
                    <span style={{ marginLeft: "8px", color: "var(--color-status-danger-fg)", fontWeight: "bold", fontSize: "11px" }}>
                      [HOTFIX]
                    </span>
                  )}
                  <small className="platform-cell-sub">
                    Created {new Date(r.createdAt).toLocaleDateString()}
                  </small>
                </td>
                <td>
                  <code>{r.schemaVersion}</code>
                </td>
                <td style={{ maxWidth: "300px" }}>
                  <p style={{ margin: 0, fontSize: "var(--fs-body-sm)", whiteSpace: "normal" }}>
                    {r.releaseNotes}
                  </p>
                </td>
                <td>
                  <StatusBadge value={r.status} />
                </td>
                <td>
                  <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                    {canManageRelease && (
                      <Button size="sm" variant="secondary" onClick={() => openEdit(r)}>
                        Edit
                      </Button>
                    )}
                    {r.status === "UPLOADED" && canManageRelease && (
                      <Button size="sm" variant="secondary" onClick={() => handleTransition(r.releaseId, r.versionLabel, "STAGING_TESTED")}>
                        Pass Staging
                      </Button>
                    )}
                    {r.status === "STAGING_TESTED" && canManageRelease && (
                      <Button size="sm" variant="primary" onClick={() => handleTransition(r.releaseId, r.versionLabel, "DEPLOYED")}>
                        Deploy
                      </Button>
                    )}
                    {r.status === "DEPLOYED" && canManageRelease && (
                      <Button size="sm" variant="secondary" onClick={() => handleTransition(r.releaseId, r.versionLabel, "ROLLED_BACK")}>
                        Rollback
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>

      {/* Create Release Modal */}
      {showCreate && (
        <div className="platform-modal-backdrop">
          <div className="platform-modal" role="dialog" aria-label="Create Release">
            <header>
              <h2>Create Platform Release</h2>
              <p>Register a packaged system artifact and target database schema version.</p>
            </header>
            <form onSubmit={handleCreate}>
              <div style={{ display: "grid", gap: "12px" }}>
                <label className="field-label">
                  <span>Version Label</span>
                  <input
                    type="text"
                    className="platform-input"
                    value={createDraft.versionLabel}
                    onChange={(e) => setCreateDraft({ ...createDraft, versionLabel: e.target.value })}
                    required
                  />
                </label>
                <label className="field-label">
                  <span>Database Schema Version</span>
                  <input
                    type="text"
                    className="platform-input"
                    value={createDraft.schemaVersion}
                    onChange={(e) => setCreateDraft({ ...createDraft, schemaVersion: e.target.value })}
                    required
                  />
                </label>
                <label className="field-label">
                  <span>Release Notes</span>
                  <textarea
                    className="platform-input"
                    rows={4}
                    value={createDraft.releaseNotes}
                    onChange={(e) => setCreateDraft({ ...createDraft, releaseNotes: e.target.value })}
                    required
                  />
                </label>
                <label style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                  <input
                    type="checkbox"
                    checked={createDraft.isHotfix}
                    onChange={(e) => setCreateDraft({ ...createDraft, isHotfix: e.target.checked })}
                  />
                  <span>Mark as Emergency Hotfix</span>
                </label>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
                <Button variant="secondary" onClick={() => setShowCreate(false)} type="button">
                  Cancel
                </Button>
                <Button type="submit" disabled={creating}>
                  {creating ? "Creating..." : "Save Release"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Release Modal */}
      {editingRelease && (
        <div className="platform-modal-backdrop">
          <div className="platform-modal" role="dialog" aria-label="Edit Release">
            <header>
              <h2>Edit Release · {editingRelease.versionLabel}</h2>
              <p>Update release notes, schema version, or hotfix classification.</p>
            </header>
            <form onSubmit={handleSaveEdit}>
              <div style={{ display: "grid", gap: "12px" }}>
                <label className="field-label">
                  <span>Schema Version</span>
                  <input
                    type="text"
                    className="platform-input"
                    value={editSchema}
                    onChange={(e) => setEditSchema(e.target.value)}
                    required
                  />
                </label>
                <label className="field-label">
                  <span>Release Notes</span>
                  <textarea
                    className="platform-input"
                    rows={4}
                    value={editNotes}
                    onChange={(e) => setEditNotes(e.target.value)}
                    required
                  />
                </label>
                <label style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                  <input
                    type="checkbox"
                    checked={editHotfix}
                    onChange={(e) => setEditHotfix(e.target.checked)}
                  />
                  <span>Emergency Hotfix</span>
                </label>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
                <Button variant="secondary" onClick={() => setEditingRelease(null)} type="button">
                  Cancel
                </Button>
                <Button type="submit" disabled={savingEdit}>
                  {savingEdit ? "Saving..." : "Update Release"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export const ReleasesPage = ReleasesView;
