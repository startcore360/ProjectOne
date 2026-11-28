import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  Badge,
  Button,
  CheckboxField,
  DataTable,
  Modal,
  Notice,
  Panel,
  PhoneField,
  ErrorState,
  SelectField,
  SkeletonTable,
  TextAreaField,
  TextField,
} from "../../../../design-system/components";
import { useFormErrors } from "../../../../hooks";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  guardianService,
  type GuardianLinkItem,
} from "../../api/guardianService";

export interface GuardianManagementPanelProps {
  tenantId?: string;
  studentId: string;
  institutionId?: string;
}

export function GuardianManagementPanel({
  tenantId,
  studentId,
  institutionId,
}: GuardianManagementPanelProps) {
  const currentTenantId = tenantId ?? "";
  const [links, setLinks] = useState<GuardianLinkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);

  // Link modal state
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [fullName, setFullName] = useState("");
  const [mobile, setMobile] = useState("+91");
  const [email, setEmail] = useState("");
  const [relationship, setRelationship] = useState("FATHER");
  const [isPrimary, setIsPrimary] = useState(true);
  const [linking, setLinking] = useState(false);
  const { fieldErrors, setFieldError, clearFieldError, clearAll, handleApiError } = useFormErrors();

  // Change request modal state
  const [changeModalItem, setChangeModalItem] = useState<GuardianLinkItem | null>(null);
  const [changeReason, setChangeReason] = useState("");
  const [requestingChange, setRequestingChange] = useState(false);

  // Decision review modal state
  const [decideModalItem, setDecideModalItem] = useState<GuardianLinkItem | null>(null);
  const [decisionApproved, setDecisionApproved] = useState(true);
  const [deciding, setDeciding] = useState(false);

  const fetchLinks = useCallback(async () => {
    if (!currentTenantId || !studentId) {
      setLoadError("A valid tenant and student context are required to load guardian links.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setLoadError("");
    try {
      const data = await guardianService.listGuardianLinks(currentTenantId, studentId);
      setLinks(data || []);
    } catch (err) {
      setLoadError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [currentTenantId, studentId]);

  useEffect(() => {
    void fetchLinks();
  }, [fetchLinks]);

  const handleLinkSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!currentTenantId) return;
    if (!fullName.trim() || !mobile.trim()) {
      setFeedback({ tone: "danger", message: "Full name and valid mobile number are required." });
      return;
    }
    setLinking(true);
    clearAll();
    try {
      await guardianService.linkGuardian(
        currentTenantId,
        studentId,
        {
          fullName: fullName.trim(),
          mobile: mobile.trim(),
          email: email.trim() || undefined,
          relationship,
          isPrimary,
        },
        institutionId
      );
      setFeedback({
        tone: "success",
        message: "Guardian linked successfully.",
      });
      setShowLinkModal(false);
      setFullName("");
      setMobile("+91");
      setEmail("");
      void fetchLinks();
    } catch (err: unknown) {
      handleApiError(err);
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setLinking(false);
    }
  };

  const handleRequestChangeSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!currentTenantId) return;
    if (!changeModalItem) return;
    if (!changeReason.trim()) {
      setFeedback({ tone: "danger", message: "Enter a reason for the guardian change request." });
      return;
    }
    setRequestingChange(true);
    try {
      await guardianService.requestGuardianChange(
        currentTenantId,
        changeModalItem.studentGuardianLinkId,
        { reason: changeReason.trim() },
        institutionId
      );
      setFeedback({
        tone: "success",
        message: "Guardian change request submitted to the Principal for review.",
      });
      setChangeModalItem(null);
      setChangeReason("");
      void fetchLinks();
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setRequestingChange(false);
    }
  };

  const handleDecideSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!currentTenantId) return;
    if (!decideModalItem) return;
    setDeciding(true);
    try {
      await guardianService.decideGuardianChange(
        currentTenantId,
        decideModalItem.studentGuardianLinkId,
        { approved: decisionApproved },
        institutionId
      );
      setFeedback({
        tone: "success",
        message: decisionApproved
          ? "Guardian change approved. The previous guardian's access has been blocked."
          : "Guardian change request rejected. Existing link preserved.",
      });
      setDecideModalItem(null);
      void fetchLinks();
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setDeciding(false);
    }
  };

  return (
    <Panel
      title="Guardian linkages"
      description="Link parent or guardian profiles to this student (up to 3 guardians, exactly one primary). Changes require reason and Principal approval."
      action={
        <Button onClick={() => setShowLinkModal(true)} disabled={!currentTenantId || links.length >= 3}>
          + Link Guardian
        </Button>
      }
    >
      <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
        {feedback && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <Notice tone={feedback.tone}>{feedback.message}</Notice>
          </div>
        )}

        {!currentTenantId ? (
          <ErrorState title="Tenant context unavailable" message="Guardian links cannot be loaded or changed without a tenant identity." />
        ) : loadError ? (
          <ErrorState title="Could not load guardian links" message={loadError} action={<Button variant="secondary" onClick={() => void fetchLinks()}>Retry</Button>} />
        ) : loading ? (
          <div role="status" aria-busy="true" aria-label="Loading guardian linkages">
            <SkeletonTable columns={5} rows={3} />
          </div>
        ) : (
          <DataTable
            headers={["Guardian Name", "Relationship", "Primary?", "Status", "Actions"]}
            empty={links.length === 0}
            emptyMessage="No guardians linked yet. Click '+ Link Guardian' to add."
          >
            {links.map((link) => (
              <tr key={link.studentGuardianLinkId}>
                <td style={{ padding: "var(--space-2)" }}>
                  <strong>{link.guardianName}</strong>
                </td>
                <td style={{ padding: "var(--space-2)" }}>{link.relationship}</td>
                <td style={{ padding: "var(--space-2)" }}>
                  {link.isPrimary ? (
                    <Badge tone="success">PRIMARY</Badge>
                  ) : (
                    <span style={{ color: "var(--color-text-muted)" }}>—</span>
                  )}
                </td>
                <td style={{ padding: "var(--space-2)" }}>
                  {link.changeApprovalRequestId ? (
                    <Badge tone="warning">Change Pending</Badge>
                  ) : (
                    <Badge
                      tone={link.status === "ACTIVE" ? "success" : "danger"}
                    >
                      {link.status}
                    </Badge>
                  )}
                </td>
                <td style={{ padding: "var(--space-2)" }}>
                  <div style={{ display: "flex", gap: "var(--space-2)" }}>
                    {link.changeApprovalRequestId ? (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setDecideModalItem(link)}
                      >
                        Review Decision
                      </Button>
                    ) : (
                      link.status === "ACTIVE" && (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => setChangeModalItem(link)}
                        >
                          Request Change
                        </Button>
                      )
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </div>

      {/* Link Guardian Modal */}
      <Modal
        isOpen={showLinkModal}
        onClose={() => setShowLinkModal(false)}
        kicker="GUARDIAN LINK"
        title="Link Parent or Guardian"
        size="md"
      >
        <form onSubmit={handleLinkSubmit}>
          <div style={{ marginBottom: "var(--space-3)" }}>
            <TextField
              id="guardian-fullname"
              label="Full Name *"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="e.g. Ramesh Sharma"
            />
          </div>

          <div style={{ marginBottom: "var(--space-3)" }}>
            <PhoneField
              id="guardian-mobile"
              label="Mobile number"
              required
              value={mobile}
              onChange={(e) => {
                setMobile(e.target.value);
                clearFieldError("mobile");
              }}
              fieldName="mobile"
              error={fieldErrors.mobile}
              setFieldError={setFieldError}
              clearFieldError={clearFieldError}
            />
          </div>

          <div style={{ marginBottom: "var(--space-3)" }}>
            <TextField
              id="guardian-email"
              label="Email Address (Optional)"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="ramesh.sharma@example.com"
            />
          </div>

          <div style={{ marginBottom: "var(--space-3)" }}>
            <SelectField
              id="guardian-link-relationship"
              label="Relationship *"
              required
              value={relationship}
              onChange={(e) => setRelationship(e.target.value)}
            >
              <option value="FATHER">Father</option>
              <option value="MOTHER">Mother</option>
              <option value="LEGAL_GUARDIAN">Legal Guardian</option>
              <option value="OTHER">Other</option>
            </SelectField>
          </div>

          <div style={{ marginBottom: "var(--space-4)" }}>
            <CheckboxField
              id="guardian-link-primary"
              label="Designate as primary guardian"
              checked={isPrimary}
              onChange={(e) => setIsPrimary(e.target.checked)}
            />
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
            <Button variant="secondary" onClick={() => setShowLinkModal(false)} disabled={linking}>
              Cancel
            </Button>
            <Button type="submit" busy={linking}>
              Link Guardian
            </Button>
          </div>
        </form>
      </Modal>

      {/* Request Guardian Change Modal */}
      <Modal
        isOpen={!!changeModalItem}
        onClose={() => setChangeModalItem(null)}
        kicker="GUARDIAN CHANGE REQUEST"
        title="Request Change or Block"
        size="md"
      >
        <form onSubmit={handleRequestChangeSubmit}>
          <p style={{ fontSize: "0.875rem", color: "var(--color-text-muted)", marginBottom: "var(--space-3)" }}>
            Requesting change for guardian: <strong>{changeModalItem?.guardianName}</strong> ({changeModalItem?.relationship})
          </p>

          <div style={{ marginBottom: "var(--space-4)" }}>
            <TextAreaField
              id="change-reason-input"
              label="Reason for Change / Block *"
              required
              rows={3}
              value={changeReason}
              onChange={(e) => setChangeReason(e.target.value)}
              placeholder="e.g. Change of custody court order, relocation, or parent request..."
            />
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
            <Button variant="secondary" onClick={() => setChangeModalItem(null)} disabled={requestingChange}>
              Cancel
            </Button>
            <Button type="submit" busy={requestingChange}>
              Submit to Principal
            </Button>
          </div>
        </form>
      </Modal>

      {/* Decide Guardian Change Modal */}
      <Modal
        isOpen={!!decideModalItem}
        onClose={() => setDecideModalItem(null)}
        kicker="GUARDIAN CHANGE REVIEW"
        title="Review Guardian Change"
        size="md"
      >
        <form onSubmit={handleDecideSubmit}>
          <p style={{ fontSize: "0.875rem", color: "var(--color-text-muted)", marginBottom: "var(--space-3)" }}>
            Reviewing pending change request for: <strong>{decideModalItem?.guardianName}</strong>
          </p>

          <div style={{ marginBottom: "var(--space-4)" }}>
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-2)" }}>
              Principal Decision
            </label>
            <div style={{ display: "flex", gap: "var(--space-4)" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "var(--space-1)", cursor: "pointer" }}>
                <input
                  type="radio"
                  name="decision"
                  checked={decisionApproved}
                  onChange={() => setDecisionApproved(true)}
                />
                Approve and block previous guardian access
              </label>
              <label style={{ display: "flex", alignItems: "center", gap: "var(--space-1)", cursor: "pointer" }}>
                <input
                  type="radio"
                  name="decision"
                  checked={!decisionApproved}
                  onChange={() => setDecisionApproved(false)}
                />
                Reject Request
              </label>
            </div>
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
            <Button variant="secondary" onClick={() => setDecideModalItem(null)} disabled={deciding}>
              Cancel
            </Button>
            <Button type="submit" busy={deciding}>
              Approve & Block
            </Button>
          </div>
        </form>
      </Modal>
    </Panel>
  );
}

export default GuardianManagementPanel;
