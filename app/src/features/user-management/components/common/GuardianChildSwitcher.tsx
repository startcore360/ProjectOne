import { useCallback, useEffect, useState } from "react";
import { Badge, Button, EmptyState, ErrorState, Panel, SelectField, Spinner } from "../../../../design-system/components";
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
      const activeChildren = Array.isArray(data)
        ? data.filter((child) => child.status === "ACTIVE")
        : [];
      setChildren(activeChildren);
    } catch {
      setError("Linked children could not be loaded. Check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadChildren();
  }, [loadChildren]);

  useEffect(() => {
    if (!loading && !error && !value && children.length > 0) {
      onChange(children[0].studentProfileId);
    }
  }, [children, error, loading, onChange, value]);

  if (loading) return <Panel title="Linked children"><Spinner label="Loading linked children…" /></Panel>;
  if (error) return (
    <ErrorState
      title="Could not load linked children"
      message={error}
      action={<Button variant="secondary" onClick={() => void loadChildren()}>Retry</Button>}
    />
  );
  if (children.length === 0) return (
    <EmptyState
      title="No active linked children"
      description="When an institution links a student profile to your account, it will appear here."
      icon="users"
      variant="page"
    />
  );

  const selected = children.find((child) => child.studentProfileId === value);
  return (
    <div className="guardian-dashboard">
      <Panel title="Your linked children" description="Choose a child to view the information currently shared with your account.">
        <div className="guardian-dashboard__selector">
          <SelectField
            id="guardian-child-select"
            label="Child"
            value={selected?.studentProfileId ?? ""}
            onChange={(event) => onChange(event.target.value)}
            options={children.map((child) => ({
              value: child.studentProfileId,
              label: child.studentCode,
            }))}
          />
          <span className="guardian-dashboard__count">{children.length} active {children.length === 1 ? "child" : "children"}</span>
        </div>
      </Panel>
      {selected && (
        <Panel title="Selected child" description="Read-only profile information available to your guardian account.">
          <div className="guardian-child-card">
            <div className="guardian-child-card__avatar" aria-hidden="true">{selected.studentCode.slice(0, 1).toUpperCase()}</div>
            <div className="guardian-child-card__identity">
              <span className="guardian-child-card__eyebrow">STUDENT PROFILE</span>
              <h2>{selected.studentCode}</h2>
              <p>Student code</p>
            </div>
            <Badge tone="success">{selected.status === "ACTIVE" ? "Active" : selected.status}</Badge>
          </div>
          <Notice tone="info">
            Academic progress, attendance, fee details and notices are not yet available through the guardian dashboard. This screen shows only the linked student code and status returned by the current service.
          </Notice>
        </Panel>
      )}
    </div>
  );
}
