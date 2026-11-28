import { useCallback, useEffect, useState } from "react";
import {
  PageHeader,
  Button,
  EmptyState,
  TextField,
  TextAreaField,
  SelectField,
  CheckboxField,
  Notice,
  Spinner,
  Panel,
  Badge,
} from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api, type Notice as NoticeType } from "../api/t10Api";
import {
  institutionService,
  type InstitutionItem,
} from "../../institution-admin/api/institutionService";

export default function NoticeBulletinPage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const activeInstitution = useAppSelector(
    (s) => s.tenantPermissions.current?.institutionId ?? ""
  );
  const actions = useAppSelector(
    (s) => s.tenantPermissions.current?.allowedActions ?? []
  );
  const canPublish = [
    "NOTICE_PUBLISH_INSTITUTION",
    "NOTICE_PUBLISH_CLASS_DEPT",
    "NOTICE_PUBLISH_UNIVERSITY",
  ].some((action) => actions.includes(action));
  const canPublishUrgent = actions.includes("NOTICE_EMERGENCY");

  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [selectedInstitution, setSelectedInstitution] = useState(activeInstitution);
  const [items, setItems] = useState<NoticeType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [targetRolesText, setTargetRolesText] = useState("");
  const [targetKind, setTargetKind] = useState<"ROLE" | "INSTITUTION">("ROLE");
  const [urgent, setUrgent] = useState(false);
  const [requiresAcknowledgment, setRequiresAcknowledgment] = useState(false);
  const [busy, setBusy] = useState(false);
  const [successMsg, setSuccessMsg] = useState("");

  useEffect(() => {
    if (!tenant) return;
    void institutionService
      .listInstitutions(tenant)
      .then((res) => {
        const list = res?.items ?? [];
        setInstitutions(list);
        if (!selectedInstitution && list.length > 0) {
          setSelectedInstitution(activeInstitution || list[0].institutionId);
        }
      })
      .catch(() => {});
  }, [tenant, activeInstitution, selectedInstitution]);

  const effectiveInstitution = selectedInstitution || activeInstitution;

  const load = useCallback(async () => {
    if (!tenant || !effectiveInstitution) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    setItems([]);
    try {
      setItems(await t10Api.notices(tenant, effectiveInstitution));
    } catch {
      setError("Notices could not be loaded for this institution.");
    } finally {
      setLoading(false);
    }
  }, [tenant, effectiveInstitution]);

  useEffect(() => {
    void load();
  }, [load]);

  const publish = async () => {
    if (!tenant || !effectiveInstitution || !title.trim() || !body.trim()) return;
    setBusy(true);
    setError("");
    setSuccessMsg("");
    try {
      const targetRoles = targetRolesText
        .split(",")
        .map((role) => role.trim().toUpperCase())
        .filter(Boolean);
      await t10Api.publishNotice(tenant, {
        institutionId: effectiveInstitution,
        title: title.trim(),
        body: body.trim(),
        targetRoles,
        targetKind,
        targetId: targetKind === "INSTITUTION" ? effectiveInstitution : null,
        urgent,
        requiresAcknowledgment,
      });
      setTitle("");
      setBody("");
      setTargetRolesText("");
      setUrgent(false);
      setRequiresAcknowledgment(false);
      setSuccessMsg("Notice published successfully.");
      await load();
    } catch {
      setError(
        "Notice could not be published. Check your permission, target roles and institution scope."
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="app-page">
      <PageHeader
        title="Notice Board & Circulars"
        description="Publish role-targeted circulars, urgent alerts and notices requiring acknowledgment."
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => void load()}
            disabled={loading || !effectiveInstitution}
          >
            Refresh notices
          </Button>
        }
      />

      {institutions.length > 1 && (
        <Panel
          title="Institution Context"
          description="Select an institution to filter circulars and notices."
        >
          <div style={{ maxWidth: "420px" }}>
            <SelectField
              id="notice-inst-select"
              label="Selected Institution"
              value={effectiveInstitution}
              onChange={(e) => setSelectedInstitution(e.target.value)}
              options={institutions.map((i) => ({
                value: i.institutionId,
                label: i.institutionName,
              }))}
            />
          </div>
        </Panel>
      )}

      {error && <Notice tone="danger">{error}</Notice>}
      {successMsg && <Notice tone="success">{successMsg}</Notice>}

      {canPublish && (
        <Panel
          title="Publish a notice"
          description="Draft and distribute a formal circular or urgent alert to target roles."
        >
          <form
            className="app-form"
            style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}
            onSubmit={(e) => {
              e.preventDefault();
              void publish();
            }}
          >
            <TextField
              id="notice-title"
              label="Title *"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              maxLength={160}
              required
              placeholder="e.g. End-of-term examinations schedule announcement"
            />
            <TextAreaField
              id="notice-body"
              label="Message *"
              value={body}
              onChange={(event) => setBody(event.target.value)}
              maxLength={4000}
              rows={4}
              required
              placeholder="Enter full notice text..."
            />
            <div className="app-form-grid">
              <SelectField
                id="notice-target-kind"
                label="Target scope"
                value={targetKind}
                onChange={(event) =>
                  setTargetKind(event.target.value as "ROLE" | "INSTITUTION")
                }
                options={[
                  { value: "ROLE", label: "Role-scoped" },
                  { value: "INSTITUTION", label: "Entire institution" },
                ]}
              />
              <TextField
                id="notice-target-roles"
                label="Target role codes (comma-separated; blank targets all)"
                value={targetRolesText}
                onChange={(event) => setTargetRolesText(event.target.value)}
                placeholder="STUDENT, CLASS_TEACHER, PRINCIPAL_COLLEGE"
              />
            </div>
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: "var(--space-4)",
                margin: "var(--space-2) 0",
              }}
            >
              {canPublishUrgent && (
                <CheckboxField
                  id="notice-urgent"
                  label="Urgent alert"
                  checked={urgent}
                  onChange={(event) => setUrgent(event.target.checked)}
                />
              )}
              <CheckboxField
                id="notice-ack"
                label="Require read acknowledgment"
                checked={requiresAcknowledgment}
                onChange={(event) => setRequiresAcknowledgment(event.target.checked)}
              />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <Button
                type="submit"
                disabled={
                  busy || !title.trim() || !body.trim() || !effectiveInstitution
                }
                busy={busy}
              >
                Publish notice
              </Button>
            </div>
          </form>
        </Panel>
      )}

      <Panel
        title="Published notices"
        description="Active circulars and bulletins for this institution."
      >
        {loading ? (
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              padding: "var(--space-6)",
            }}
          >
            <Spinner />
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            title="No published notices"
            description="Published notices will appear here."
            icon="file-text"
          />
        ) : (
          <div className="app-stack">
            {items.map((notice) => (
              <article
                className="app-card app-card--notice"
                key={notice.notice_id}
                style={{ padding: "var(--space-4)", marginBottom: "var(--space-3)" }}
              >
                <div
                  className="app-card__header"
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: "var(--space-2)",
                  }}
                >
                  <h3 className="app-card__title" style={{ margin: 0 }}>
                    {notice.title}
                  </h3>
                  <div
                    className="app-card__badges"
                    style={{ display: "flex", gap: "var(--space-2)" }}
                  >
                    {notice.is_urgent && <Badge tone="danger">Urgent</Badge>}
                    {notice.published_at && (
                      <Badge tone="neutral">
                        {new Date(notice.published_at).toLocaleDateString()}
                      </Badge>
                    )}
                  </div>
                </div>
                <p
                  className="app-card__body"
                  style={{
                    margin: "0 0 var(--space-3) 0",
                    color: "var(--color-text)",
                    whiteSpace: "pre-wrap",
                  }}
                >
                  {notice.body}
                </p>
                {notice.requires_acknowledgment && !notice.acknowledged && (
                  <div style={{ marginTop: "var(--space-2)" }}>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={async () => {
                        try {
                          await t10Api.acknowledge(
                            tenant,
                            effectiveInstitution,
                            notice.notice_id
                          );
                          await load();
                        } catch {
                          setError(
                            "Notice acknowledgment could not be saved. Please try again."
                          );
                        }
                      }}
                    >
                      Acknowledge notice
                    </Button>
                  </div>
                )}
                {notice.requires_acknowledgment && notice.acknowledged && (
                  <Notice tone="success" style={{ marginTop: "var(--space-2)" }}>
                    Read acknowledgment recorded.
                  </Notice>
                )}
              </article>
            ))}
          </div>
        )}
      </Panel>
    </main>
  );
}
