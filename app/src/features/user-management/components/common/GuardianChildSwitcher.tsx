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
  value: string;
  onChange: (studentProfileId: string) => void;
};

export function GuardianChildSwitcher({ value, onChange }: GuardianChildSwitcherProps) {
  const [children, setChildren] = useState<GuardianChild[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadChildren = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get<GuardianChild[]>("/guardian/children");
      setChildren(Array.isArray(data) ? data.filter((child) => child.status === "ACTIVE") : []);
    } catch {
      setError("Linked children could not be loaded. Check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadChildren();
  }, [loadChildren]);

  if (loading) return <p role="status">Loading linked children…</p>;
  if (error) return (
    <div>
      <p role="alert">{error}</p>
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
      <p>Institution details are available to authorised institutional staff.</p>
      <p>Progress, attendance, notices and academic records are not connected to this view yet.</p>
      <p>Child information is read-only.</p>
    </section>}
  </div>;
}
