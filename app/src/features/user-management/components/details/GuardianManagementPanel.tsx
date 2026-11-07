import { useEffect, useState, type FormEvent } from "react";
import {
  Badge,
  Button,
  DataTable,
  Modal,
  Notice,
  Panel,
  TextField,
} from "../../../../design-system/components";
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
  const currentTenantId = tenantId || "00000000-0000-0000-0000-000000000001";
  const [links, setLinks] = useState<GuardianLinkItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);

  // Link modal state
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [fullName, setFullName] = useState("");
  const [mobile, setMobile] = useState("+91");
  const [email, setEmail] = useState("");
  const [relationship, setRelationship] = useState("FATHER");
  const [isPrimary, setIsPrimary] = useState(true);
  const [linking, setLinking] = useState(false);

  // Change request modal state
  const [changeModalItem, setChangeModalItem] = useState<GuardianLinkItem | null>(null);
  const [changeReason, setChangeReason] = useState("");
  const [requestingChange, setRequestingChange] = useState(false);

  // Decision review modal state
  const [decideModalItem, setDecideModalItem] = useState<GuardianLinkItem | null>(null);
  const [decisionApproved, setDecisionApproved] = useState(true);
  const [deciding, setDeciding] = useState(false);

  const fetchLinks = () => {
    setLoading(true);
    guardianService
      .listGuardianLinks(currentTenantId, studentId)
      .then((data) => setLinks(data || []))
      .catch(() => setLinks([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchLinks();
  }, [currentTenantId, studentId]);

  const handleLinkSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!fullName.trim() || !mobile.trim()) {
      setFeedback({ tone: "danger", message: "Full name and valid mobile number are required." });
      return;
    }
    setLinking(true);
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
        message: "Guardian linked successfully [GUARDIAN_LINK / Rules WF-21, WF-22, WF-24].",
      });
      setShowLinkModal(false);
      setFullName("");
      setMobile("+91");
      setEmail("");
      fetchLinks();
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setLinking(false);
    }
  };

  const handleRequestChangeSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!changeModalItem) return;
    if (!changeReason.trim()) {
      setFeedback({ tone: "danger", message: "Reason is required to request guardian change (Rule WF-23)." });
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
        message: "Guardian change requested with reason [GUARDIAN_CHANGE_REQUEST / Rule WF-23]. Sent to Principal for decision.",
      });
      setChangeModalItem(null);
      setChangeReason("");
      fetchLinks();
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setRequestingChange(false);
    }
  };

  const handleDecideSubmit = async (e: FormEvent) => {
    e.preventDefault();
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
          ? "Guardian change approved. Old guardian access blocked immediately [GUARDIAN_CHANGE_APPROVE / Rule WF-25]."
          : "Guardian change request rejected. Existing link preserved.",
      });
      setDecideModalItem(null);
      fetchLinks();
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setDeciding(false);
    }
  };

  return (
    <Panel
      title="Guardian linkages [WF-21..25]"
      description="Link parent or guardian profiles to this student (up to 3 guardians, exactly one primary). Changes require reason and Principal approval."
      action={
        <Button onClick={() => setShowLinkModal(true)} disabled={links.length >= 3}>
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

        {loading ? (
          <p style={{ fontSize: "0.875rem", color: "var(--color-text-muted)" }}>Loading guardian linkages...</p>
        ) : (
          <DataTable
            headers={["Guardian Name", "Relationship", "Primary?", "Status", "Actions"]}
            empty={links.length === 0}
            emptyMessage="No guardians linked yet. Click '+ Link Guardian' to add."
          >
            {links.map((link) => (
              <tr key={link.studentGuardianLinkId}>
                <td style={{ padding: "8px" }}>
                  <strong>{link.guardianName}</strong>
                </td>
                <td style={{ padding: "8px" }}>{link.relationship}</td>
                <td style={{ padding: "8px" }}>
                  {link.isPrimary ? (
                    <Badge tone="success">PRIMARY</Badge>
                  ) : (
                    <span style={{ color: "#999" }}>—</span>
                  )}
                </td>
                <td style={{ padding: "8px" }}>
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
                <td style={{ padding: "8px" }}>
                  <div style={{ display: "flex", gap: "8px" }}>
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
        kicker="GUARDIAN_LINK • Action #17 / Rules WF-21..24"
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
            <TextField
              id="guardian-mobile"
              label="Mobile Number *"
              required
              value={mobile}
              onChange={(e) => setMobile(e.target.value)}
              placeholder="+919876543210"
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
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "4px" }}>
              Relationship *
            </label>
            <select
              value={relationship}
              onChange={(e) => setRelationship(e.target.value)}
              style={{
                width: "100%",
                padding: "8px",
                border: "1px solid var(--color-border, #ccc)",
                borderRadius: "var(--radius-md, 4px)",
              }}
            >
              <option value="FATHER">Father</option>
              <option value="MOTHER">Mother</option>
              <option value="LEGAL_GUARDIAN">Legal Guardian</option>
              <option value="OTHER">Other</option>
            </select>
          </div>

          <div style={{ marginBottom: "var(--space-4)" }}>
            <label style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "0.875rem", cursor: "pointer" }}>
              <input
                type="checkbox"
                checked={isPrimary}
                onChange={(e) => setIsPrimary(e.target.checked)}
              />
              Designate as Primary Guardian [Rule WF-22: Exactly one primary required]
            </label>
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
        kicker="GUARDIAN_CHANGE_REQUEST • Action #18 / Rule WF-23"
        title="Request Change or Block"
        size="md"
      >
        <form onSubmit={handleRequestChangeSubmit}>
          <p style={{ fontSize: "0.875rem", color: "var(--color-text-secondary, #666)", marginBottom: "var(--space-3)" }}>
            Requesting change for guardian: <strong>{changeModalItem?.guardianName}</strong> ({changeModalItem?.relationship})
          </p>

          <div style={{ marginBottom: "var(--space-4)" }}>
            <label
              htmlFor="change-reason-input"
              style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "4px" }}
            >
              Reason for Change / Block *
            </label>
            <textarea
              id="change-reason-input"
              required
              rows={3}
              value={changeReason}
              onChange={(e) => setChangeReason(e.target.value)}
              placeholder="e.g. Change of custody court order, relocation, or parent request..."
              style={{
                width: "100%",
                padding: "8px",
                border: "1px solid var(--color-border, #ccc)",
                borderRadius: "var(--radius-md, 4px)",
              }}
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
        kicker="GUARDIAN_CHANGE_APPROVE • Action #19 / Rule WF-25"
        title="Review Guardian Change"
        size="md"
      >
        <form onSubmit={handleDecideSubmit}>
          <p style={{ fontSize: "0.875rem", color: "var(--color-text-secondary, #666)", marginBottom: "var(--space-3)" }}>
            Reviewing pending change request for: <strong>{decideModalItem?.guardianName}</strong>
          </p>

          <div style={{ marginBottom: "var(--space-4)" }}>
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "8px" }}>
              Principal Decision
            </label>
            <div style={{ display: "flex", gap: "16px" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "6px", cursor: "pointer" }}>
                <input
                  type="radio"
                  name="decision"
                  checked={decisionApproved}
                  onChange={() => setDecisionApproved(true)}
                />
                Approve (Block old guardian immediately [Rule WF-25])
              </label>
              <label style={{ display: "flex", alignItems: "center", gap: "6px", cursor: "pointer" }}>
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
