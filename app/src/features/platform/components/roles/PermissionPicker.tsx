import { Badge } from "../../../../design-system/components";
import type { PlatformRole } from "../../api/platformUserService";
import { baseLimitReason, type AccessMode, type ActionCatalogItem } from "../../api/platformRoleService";

export interface PermissionPickerProps {
  catalog: ActionCatalogItem[];
  base: PlatformRole;
  selected: Record<string, AccessMode>;
  onChange: (next: Record<string, AccessMode>) => void;
  showAccessMode?: boolean;
  readOnly?: boolean;
}

export function PermissionPicker({
  catalog,
  base,
  selected,
  onChange,
  showAccessMode = true,
  readOnly = false,
}: PermissionPickerProps) {
  const modules = [...new Set(catalog.map((item) => item.moduleName))];

  const toggle = (actionCode: string, checked: boolean) => {
    const next = { ...selected };
    if (checked) next[actionCode] = "SHARED";
    else delete next[actionCode];
    onChange(next);
  };

  return (
    <div className="platform-form-field" style={{ maxHeight: "min(36vh, 320px)", overflowY: "auto" }}>
      {modules.map((moduleName) => (
        <fieldset key={moduleName} style={{ border: 0, margin: 0, padding: 0, minWidth: 0 }}>
          <legend className="platform-field-hint" style={{ padding: "var(--space-2) 0" }}>
            {moduleName}
          </legend>
          {catalog
            .filter((item) => item.moduleName === moduleName)
            .map((item) => {
              const lockedReason = item.lockedReason ?? baseLimitReason(item.actionCode, base);
              const isOn = item.actionCode in selected;
              return (
                <div
                  key={item.actionCode}
                  style={{
                    display: "flex",
                    flexWrap: "wrap",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: "var(--space-2)",
                    padding: "var(--space-2) 0",
                    borderTop: "1px solid var(--color-border)",
                  }}
                >
                  <label style={{ display: "flex", gap: "var(--space-2)", flex: "1 1 240px", minWidth: 0 }}>
                    <input
                      type="checkbox"
                      checked={isOn}
                      disabled={readOnly || !!lockedReason}
                      onChange={(e) => toggle(item.actionCode, e.target.checked)}
                    />
                    <span>
                      {item.description}
                      {item.sensitive && (
                        <>
                          {" "}
                          <Badge tone="warning">Sensitive</Badge>
                        </>
                      )}
                      <small className="platform-cell-sub">{lockedReason ?? item.actionCode}</small>
                    </span>
                  </label>
                  {lockedReason ? (
                    <Badge tone="neutral">Locked</Badge>
                  ) : (
                    showAccessMode &&
                    isOn && (
                      <select
                        aria-label={`Who does ${item.description}`}
                        value={selected[item.actionCode]}
                        disabled={readOnly}
                        onChange={(e) => onChange({ ...selected, [item.actionCode]: e.target.value as AccessMode })}
                      >
                        <option value="SHARED">Shared with me</option>
                        <option value="HANDED_OVER">Only this person</option>
                      </select>
                    )
                  )}
                </div>
              );
            })}
        </fieldset>
      ))}
    </div>
  );
}
