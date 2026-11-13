import { useCallback, useEffect, useState } from "react";
import { Button } from "../../../../design-system/components";
import api from "../../../../services/axios";

export type GuardianChild = {
  studentProfileId: string;
  institutionId: string;
  studentCode: string;
  status: string;
};

type GuardianChildSwitcherProps = {
  tenantId: string;
  value: string;
  onChange: (studentProfileId: string) => void;
};

export function GuardianChildSwitcher({ tenantId, value, onChange }: GuardianChildSwitcherProps) {
  const [children, setChildren] = useState<GuardianChild[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadChildren = useCallback(async () => {
    if (!tenantId) {
      setError("Your account is not linked to an institution. Contact your administrator.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get<GuardianChild[]>(`/tenants/${tenantId}/guardian/children`);
      setChildren(Array.isArray(data) ? data.filter((child) => child.status === "ACTIVE") : []);
    } catch {
      setError("Linked children could not be loaded. Check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    void loadChildren();
  }, [loadChildren]);

  if (loading) return <p role="status">Loading linked children…</p>;
  if (error) return (
    <div role="alert">
      <p>{error}</p>
      <Button variant="secondary" onClick={() => void loadChildren()}>Retry</Button>
    </div>
  );
  if (children.length === 0) return <p>No active linked children are available.</p>;

  const selected = children.find((child) => child.studentProfileId === value);
  return <div>
    <label>
      <span>Child</span>
      <select aria-label="Choose child" value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="" disabled>Select a child</option>
        {children.map((child) => <option key={child.studentProfileId} value={child.studentProfileId}>{child.studentCode}</option>)}
      </select>
    </label>
    {selected && <section aria-label="Selected child details">
      <p>Student code: {selected.studentCode}</p>
      <p>Status: {selected.status}</p>
      <p>Only the linked student code and status are currently available in this view.</p>
      <p>Academic progress, attendance, notices, fees and academic records are not yet provided by the guardian dashboard service.</p>
      <p>Child information is read-only.</p>
    </section>}
  </div>;
}
