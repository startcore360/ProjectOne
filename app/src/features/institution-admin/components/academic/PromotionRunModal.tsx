import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { institutionService, type InstitutionItem, type AcademicSessionItem } from "../../api/institutionService";

export interface PromotionRunModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: { institutionId: string; fromSessionId: string; toSessionId: string }) => Promise<void>;
  saving: boolean;
}

export function PromotionRunModal({ tenantId, isOpen, onClose, onSubmit, saving }: PromotionRunModalProps) {
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [institutionId, setInstitutionId] = useState("");
  const [sessions, setSessions] = useState<AcademicSessionItem[]>([]);
  const [fromSessionId, setFromSessionId] = useState("");
  const [toSessionId, setToSessionId] = useState("");
  const [loading, setLoading] = useState(false);
  const [lookupError, setLookupError] = useState("");
  const [modalError, setModalError] = useState("");

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    setLoading(true);
    setLookupError("");
    institutionService.listInstitutions(tenantId)
      .then((response) => {
        if (!cancelled) {
          setInstitutions(response.items ?? []);
          setInstitutionId((current) => current || response.items?.[0]?.institutionId || "");
        }
      })
      .catch((error: unknown) => { if (!cancelled) setLookupError(getApiErrorMessage(error)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [tenantId, isOpen]);

  useEffect(() => {
    if (!isOpen || !institutionId) {
      setSessions([]); setFromSessionId(""); setToSessionId("");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLookupError("");
    setFromSessionId(""); setToSessionId("");
    institutionService.listAcademicSessions(tenantId, institutionId)
      .then((items) => { if (!cancelled) setSessions(items.filter((item) => item.status !== "ARCHIVED")); })
      .catch((error: unknown) => { if (!cancelled) { setSessions([]); setLookupError(getApiErrorMessage(error)); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [tenantId, isOpen, institutionId]);

  const handleClose = () => { setModalError(""); onClose(); };
  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setModalError("");
    if (!institutionId || !fromSessionId || !toSessionId) {
      setModalError("Choose an institution and both academic sessions.");
      return;
    }
    if (fromSessionId === toSessionId) {
      setModalError("Choose two different academic sessions.");
      return;
    }
    try {
      await onSubmit({ institutionId, fromSessionId, toSessionId });
      setFromSessionId(""); setToSessionId("");
      handleClose();
    } catch (error: unknown) {
      setModalError(getApiErrorMessage(error));
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} kicker="YEAR-END PROMOTION" title="Initiate Promotion Run" titleId="prom-modal-title" size="sm">
      <form onSubmit={handleSubmit} className="app-form" noValidate>
        {modalError && <Notice tone="danger">{modalError}</Notice>}
        {lookupError && <Notice tone="danger">{lookupError}</Notice>}
        <SelectField id="prom-institution" label="Institution *" required value={institutionId} disabled={loading || institutions.length === 0}
          onChange={(event) => setInstitutionId(event.target.value)}>
          <option value="">{loading ? "Loading institutions…" : "Choose an institution"}</option>
          {institutions.map((item) => <option key={item.institutionId} value={item.institutionId}>{item.institutionName} · {item.institutionCode}</option>)}
        </SelectField>
        <SelectField id="prom-from-session" label="Promote students from *" required value={fromSessionId}
          disabled={loading || !institutionId || sessions.length === 0}
          onChange={(event) => setFromSessionId(event.target.value)}>
          <option value="">{loading ? "Loading sessions…" : "Choose current academic session"}</option>
          {sessions.map((item) => <option key={item.academicSessionId} value={item.academicSessionId}>{item.sessionLabel} ({item.startDate} – {item.endDate})</option>)}
        </SelectField>
        <SelectField id="prom-to-session" label="Promote students to *" required value={toSessionId}
          disabled={loading || !institutionId || sessions.length === 0}
          onChange={(event) => setToSessionId(event.target.value)}>
          <option value="">{loading ? "Loading sessions…" : "Choose next academic session"}</option>
          {sessions.filter((item) => item.academicSessionId !== fromSessionId).map((item) => <option key={item.academicSessionId} value={item.academicSessionId}>{item.sessionLabel} ({item.startDate} – {item.endDate})</option>)}
        </SelectField>
        {!loading && !lookupError && institutionId && sessions.length < 2 && <Notice tone="warning">At least two academic sessions are needed to start a promotion run.</Notice>}
        <footer className="app-modal__footer">
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>Cancel</Button>
          <Button type="submit" disabled={saving || loading || !institutionId || !fromSessionId || !toSessionId}>{saving ? "Saving…" : "Create Run"}</Button>
        </footer>
      </form>
    </Modal>
  );
}
export default PromotionRunModal;
