import { useState, type FormEvent } from "react";
import { Button, Modal, Notice } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { userManagementService, type ActingScope } from "../../api/userManagementService";
import { ScopeFields } from "../common/ScopeFields";

export interface CreateUserCheckModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
}

export function CreateUserCheckModal({ tenantId, isOpen, onClose }: CreateUserCheckModalProps) {
  const [scope, setScope] = useState<ActingScope>({ scopeType: "TENANT" });
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null);

  const runCheck = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setResult(null);
    try {
      const { data } = await userManagementService.checkCreateUserAuthorization(tenantId, scope);
      setResult({ ok: true, message: data.message });
    } catch (error) {
      setResult({ ok: false, message: getApiErrorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="USER_CREATE"
      title="Check if I can create a user"
      titleId="create-check-title"
      description="This only checks whether you are authorized to create a user in the chosen scope. It does not create an account - the backend endpoint behind this check is a proof-of-integration authorization check only."
    >
      <form onSubmit={runCheck}>
        <ScopeFields scope={scope} onChange={setScope} idPrefix="create-check" />
        {result && (
          <div style={{ marginTop: "12px" }}>
            <Notice tone={result.ok ? "success" : "danger"}>
              {result.message}
            </Notice>
          </div>
        )}
        <div className="platform-modal-note" style={{ marginTop: "12px" }}>
          No account is created by this dialog, regardless of the result shown above.
        </div>
        <footer style={{ marginTop: "20px", display: "flex", justifyContent: "flex-end", gap: "8px" }}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Close
          </Button>
          <Button type="submit" busy={busy}>
            Check authorization
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default CreateUserCheckModal;
