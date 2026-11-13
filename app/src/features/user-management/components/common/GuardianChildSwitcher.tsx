import { useEffect, useState } from "react";
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

  useEffect(() => {
    let active = true;
    api.get<GuardianChild[]>("/guardian/children")
      .then(({ data }) => {
        if (active) setChildren(data.filter((child) => child.status === "ACTIVE"));
      })
      .catch(() => { if (active) setError("Linked children could not be loaded."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  if (loading) return <p role="status">Loading linked children…</p>;
  if (error) return <p role="alert">{error}</p>;
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
