import { TextField } from "../../../../design-system/components";
import type { ActingScope, ScopeType } from "../../api/userManagementService";

export interface ScopeFieldsProps {
  scope: ActingScope;
  onChange: (next: ActingScope) => void;
  idPrefix: string;
}

export function ScopeFields({ scope, onChange, idPrefix }: ScopeFieldsProps) {
  return (
    <div className="platform-form-grid">
      <label className="platform-form-field">
        Acting scope
        <select
          value={scope.scopeType}
          onChange={(e) => onChange({ ...scope, scopeType: e.target.value as ScopeType })}
        >
          <option value="TENANT">Tenant (university-wide)</option>
          <option value="INSTITUTION">Institution</option>
          <option value="DEPARTMENT">Department</option>
        </select>
      </label>
      {scope.scopeType !== "TENANT" && (
        <TextField
          id={`${idPrefix}-institution-id`}
          label="Institution ID"
          required
          value={scope.institutionId ?? ""}
          onChange={(e) => onChange({ ...scope, institutionId: e.target.value })}
          placeholder="UUID of the institution"
        />
      )}
      {scope.scopeType === "DEPARTMENT" && (
        <TextField
          id={`${idPrefix}-department-id`}
          label="Department ID"
          required
          value={scope.departmentId ?? ""}
          onChange={(e) => onChange({ ...scope, departmentId: e.target.value })}
          placeholder="UUID of the department"
        />
      )}
    </div>
  );
}

export default ScopeFields;
