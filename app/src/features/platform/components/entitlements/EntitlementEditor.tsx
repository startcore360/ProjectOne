import { useCallback, useEffect, useMemo, useState } from "react";
import { Badge, Button, Modal, Notice, Spinner } from "../../../../design-system/components";
import api from "../../../../services/axios";
import { readErrorMessage } from "../common/types";

type EditorFeature = { featureCode: string; key: string; description: string; provisional: boolean; enabled: boolean; planEnabled: boolean; source: string | null };
type EditorModule = { moduleCode: string; moduleName: string; phase: number; enabled: boolean; planEnabled: boolean; source: string | null; requires: string[]; features: EditorFeature[] };

export interface EntitlementEditorProps {
  isOpen: boolean;
  target: "plan" | "tenant";
  targetId: string;
  targetName: string;
  isProductOwner: boolean;
  onClose: () => void;
  onSaved: (message: string) => void;
}

// E2-S2 (PD-11): switch modules and features on or off for a plan, or as a tenant override. The
// server enforces every rule; this screen explains them up front so a switch rarely bounces back.
export function EntitlementEditor({ isOpen, target, targetId, targetName, isProductOwner, onClose, onSaved }: EntitlementEditorProps) {
  const [modules, setModules] = useState<EditorModule[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [changes, setChanges] = useState<Record<string, boolean>>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [openModule, setOpenModule] = useState<string | null>(null);

  const base = target === "plan" ? `/platform/plans/${targetId}` : `/platform/tenants/${targetId}`;

  const load = useCallback(async () => {
    try {
      const { data } = await api.get<{ modules: EditorModule[] }>(`${base}/entitlement-editor`);
      setModules(data.modules);
      setLoadError("");
    } catch (err) {
      setLoadError(readErrorMessage(err, "Could not load modules and features."));
    } finally {
      setLoading(false);
    }
  }, [base]);

  useEffect(() => {
    if (isOpen) void load();
  }, [isOpen, load]);

  const value = useCallback(
    (key: string, saved: boolean) => (key in changes ? changes[key] : saved),
    [changes],
  );
  const moduleOn = useMemo(() => {
    const map: Record<string, boolean> = {};
    modules.forEach((m) => { map[m.moduleCode] = value(m.moduleCode, m.enabled); });
    return map;
  }, [modules, value]);

  /** Why a switch can't be turned on/off right now, or null. Mirrors the server's rules. */
  const blockedReason = (key: string, turnOn: boolean, planEnabled: boolean, module: EditorModule, isFeature: boolean): string | null => {
    if (turnOn && target === "tenant" && !planEnabled && !isProductOwner) return "Beyond the plan - Product Owner only.";
    if (isFeature && turnOn && !moduleOn[module.moduleCode]) return `Turn on ${module.moduleName} first.`;
    if (!isFeature && turnOn) {
      const missing = module.requires.filter((m) => !moduleOn[m]);
      if (missing.length) return `Needs ${missing.join(", ")} on first.`;
    }
    if (!isFeature && !turnOn) {
      const dependants = modules.filter((m) => m.requires.includes(key) && moduleOn[m.moduleCode]).map((m) => m.moduleName);
      if (dependants.length) return `${dependants.join(", ")} still need this module.`;
    }
    return null;
  };

  const toggle = (key: string, saved: boolean, next: boolean) =>
    setChanges((prev) => {
      const copy = { ...prev };
      if (next === saved) delete copy[key];
      else copy[key] = next;
      return copy;
    });

  const save = async () => {
    setSaving(true);
    setSaveError("");
    try {
      await api.put(`${base}/entitlements`, { features: changes });
      onSaved(`Modules and features of "${targetName}" saved.`);
    } catch (err) {
      setSaveError(readErrorMessage(err, "Could not save. Nothing was changed."));
    } finally {
      setSaving(false);
    }
  };

  const changedCount = Object.keys(changes).length;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker={target === "plan" ? "PLAN ENTITLEMENTS" : "TENANT FEATURES"}
      title={`Modules and features: ${targetName}`}
      titleId="entitlement-editor-title"
      description={
        target === "plan"
          ? "What every tenant on this plan gets. Phase 2 and 3 modules can be switched on but are not built yet."
          : "This tenant's switches. 'Override' means it differs from its plan. Going beyond the plan is Product Owner only."
      }
      size="lg"
      footer={
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "var(--space-2)", width: "100%" }}>
          <span className="app-cell-sub">{changedCount ? `${changedCount} unsaved change${changedCount === 1 ? "" : "s"}` : "No changes"}</span>
          <div style={{ display: "flex", gap: "var(--space-2)" }}>
            <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
            <Button onClick={() => void save()} busy={saving} disabled={!changedCount}>Save</Button>
          </div>
        </div>
      }
    >
      {loading ? (
        <Spinner label="Loading modules" />
      ) : loadError ? (
        <Notice tone="danger">{loadError}</Notice>
      ) : modules.length === 0 ? (
        <Notice tone="info">No modules are available.</Notice>
      ) : (
        <div style={{ display: "grid", gap: "var(--space-2)" }}>
          {saveError && <Notice tone="danger">{saveError}</Notice>}
          {modules.map((m) => {
            const on = moduleOn[m.moduleCode];
            const reason = blockedReason(m.moduleCode, !on, m.planEnabled, m, false);
            const expanded = openModule === m.moduleCode;
            return (
              <div key={m.moduleCode} style={{ border: "1px solid var(--color-border-divider)", borderRadius: "var(--radius-md)", padding: "var(--space-3)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", flexWrap: "wrap" }}>
                  <input
                    type="checkbox"
                    id={`ent-${m.moduleCode}`}
                    checked={on}
                    disabled={!!reason}
                    title={reason ?? undefined}
                    onChange={(e) => toggle(m.moduleCode, m.enabled, e.target.checked)}
                  />
                  <label htmlFor={`ent-${m.moduleCode}`}><strong>{m.moduleName}</strong></label>
                  {m.phase > 1 && <Badge tone="neutral">Phase {m.phase}</Badge>}
                  {target === "tenant" && m.source === "OVERRIDE" && <Badge tone="info">Override</Badge>}
                  {m.features.length > 0 && (
                    <Button variant="tertiary" size="sm" onClick={() => setOpenModule(expanded ? null : m.moduleCode)} aria-expanded={expanded}>
                      {expanded ? "Hide features" : `${m.features.length} features`}
                    </Button>
                  )}
                  {reason && <small className="app-cell-sub">{reason}</small>}
                </div>
                {expanded && (
                  <ul style={{ listStyle: "none", margin: "var(--space-2) 0 0", paddingLeft: "var(--space-5)", display: "grid", gap: "var(--space-1)" }}>
                    {m.features.map((f) => {
                      const fOn = value(f.key, f.enabled);
                      const fReason = blockedReason(f.key, !fOn, f.planEnabled, m, true);
                      return (
                        <li key={f.key} style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", flexWrap: "wrap" }}>
                          <input type="checkbox" id={`ent-${f.key}`} checked={fOn} disabled={!!fReason} title={fReason ?? undefined}
                            onChange={(e) => toggle(f.key, f.enabled, e.target.checked)} />
                          <label htmlFor={`ent-${f.key}`}>{f.description}</label>
                          {f.provisional && <Badge tone="neutral">Provisional</Badge>}
                          {target === "tenant" && f.source === "OVERRIDE" && <Badge tone="info">Override</Badge>}
                          {fReason && <small className="app-cell-sub">{fReason}</small>}
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
