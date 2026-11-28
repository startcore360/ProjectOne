import { useCallback, useState } from "react";
import { useParams } from "react-router-dom";
import { useAppSelector } from "../../../store/hooks";
import { getAcademicSessionDetail, setCurrentAcademicSession } from "../api/academicHierarchyApi";
import { DetailFrame } from "./DetailPageFrame";

export default function AcademicSessionDetailPage() {
  const { institutionId, academicSessionId } = useParams<{ institutionId: string; academicSessionId: string }>();
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [revision, setRevision] = useState(0);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState("");

  const load = useCallback(() => {
    if (!tenantId) return Promise.reject(new Error("Tenant context is unavailable"));
    if (!institutionId) return Promise.reject(new Error("Missing institution identifier"));
    if (!academicSessionId) return Promise.reject(new Error("Missing academic session identifier"));
    return getAcademicSessionDetail(tenantId, institutionId, academicSessionId);
  }, [tenantId, institutionId, academicSessionId]);

  const makeCurrent = async () => {
    if (!tenantId || !institutionId || !academicSessionId) return;
    setSaving(true);
    setFeedback("");
    try {
      await setCurrentAcademicSession(tenantId, institutionId, academicSessionId);
      setFeedback("This academic session is now current.");
      setRevision((value) => value + 1);
    } catch (cause) {
      setFeedback(cause instanceof Error ? cause.message : "Unable to set current academic session.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div style={{ display: "flex", gap: "var(--space-3)", alignItems: "center", marginBottom: "var(--space-3)" }}>
        <button type="button" onClick={makeCurrent} disabled={saving || !tenantId || !institutionId || !academicSessionId}>
          {saving ? "Saving…" : "Make current academic session"}
        </button>
        {feedback && <span role="status">{feedback}</span>}
      </div>
      <DetailFrame
        key={revision}
        title="Academic session details"
        backTo="/university/setup/calendar-events"
        backLabel="Academic calendar"
        load={load}
        fields={[
          "academicSessionId", "sessionLabel", "academicYear", "startDate", "endDate",
          "status", "current", "institutionId", "rowVersion",
        ]}
      />
    </>
  );
}
