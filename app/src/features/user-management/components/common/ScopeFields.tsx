import { useEffect, useState } from "react";
import { SelectField } from "../../../../design-system/components";
import { institutionService, type DepartmentItem, type InstitutionItem } from "../../../institution-admin/api/institutionService";
import type { ActingScope, ScopeType } from "../../api/userManagementService";

export interface ScopeFieldsProps {
  tenantId: string;
  scope: ActingScope;
  onChange: (next: ActingScope) => void;
  idPrefix: string;
}

export function ScopeFields({ tenantId, scope, onChange, idPrefix }: ScopeFieldsProps) {
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [departments, setDepartments] = useState<DepartmentItem[]>([]);
  const [institutionsLoading, setInstitutionsLoading] = useState(false);
  const [departmentsLoading, setDepartmentsLoading] = useState(false);
  const [lookupError, setLookupError] = useState("");

  useEffect(() => {
    if (!tenantId || scope.scopeType === "TENANT") {
      setInstitutions([]);
      return;
    }
    let active = true;
    setInstitutionsLoading(true);
    setLookupError("");
    institutionService.listInstitutions(tenantId)
      .then((response) => {
        if (active) setInstitutions(response.items ?? []);
      })
      .catch(() => {
        if (active) {
          setInstitutions([]);
          setLookupError("Institutions could not be loaded. Check your access and try again.");
        }
      })
      .finally(() => {
        if (active) setInstitutionsLoading(false);
      });
    return () => { active = false; };
  }, [tenantId, scope.scopeType]);

  useEffect(() => {
    if (!tenantId || scope.scopeType !== "DEPARTMENT" || !scope.institutionId) {
      setDepartments([]);
      return;
    }
    let active = true;
    setDepartmentsLoading(true);
    setLookupError("");
    institutionService.listDepartments(tenantId, scope.institutionId)
      .then((rows) => {
        if (active) setDepartments(Array.isArray(rows) ? rows : []);
      })
      .catch(() => {
        if (active) {
          setDepartments([]);
          setLookupError("Departments could not be loaded for this institution. Check your access and try again.");
        }
      })
      .finally(() => {
        if (active) setDepartmentsLoading(false);
      });
    return () => { active = false; };
  }, [tenantId, scope.scopeType, scope.institutionId]);

  const handleScopeTypeChange = (scopeType: ScopeType) => {
    setLookupError("");
    onChange({ scopeType });
  };

  const handleInstitutionChange = (institutionId: string) => {
    setLookupError("");
    setDepartments([]);
    onChange({ ...scope, institutionId, departmentId: undefined });
  };

  return (
    <div className="app-form-grid">
      <SelectField
        id={`${idPrefix}-scope-type`}
        label="Acting scope"
        value={scope.scopeType}
        onChange={(e) => handleScopeTypeChange(e.target.value as ScopeType)}
      >
        <option value="TENANT">Tenant (university-wide)</option>
        <option value="INSTITUTION">Institution</option>
        <option value="DEPARTMENT">Department</option>
      </SelectField>
      {scope.scopeType !== "TENANT" && (
        <SelectField
          id={`${idPrefix}-institution-id`}
          label="Institution"
          required
          value={scope.institutionId ?? ""}
          disabled={institutionsLoading || institutions.length === 0}
          onChange={(e) => handleInstitutionChange(e.target.value)}
        >
          <option value="">
            {institutionsLoading ? "Loading institutions…" : "Choose an institution"}
          </option>
          {institutions.map((institution) => (
            <option key={institution.institutionId} value={institution.institutionId}>
              {institution.institutionName} · {institution.institutionCode}
            </option>
          ))}
        </SelectField>
      )}
      {scope.scopeType === "DEPARTMENT" && (
        <SelectField
          id={`${idPrefix}-department-id`}
          label="Department"
          required
          value={scope.departmentId ?? ""}
          disabled={!scope.institutionId || departmentsLoading || departments.length === 0}
          onChange={(e) => onChange({ ...scope, departmentId: e.target.value || undefined })}
        >
          <option value="">
            {!scope.institutionId
              ? "Choose an institution first"
              : departmentsLoading
                ? "Loading departments…"
                : "Choose a department"}
          </option>
          {departments.map((department) => (
            <option key={department.departmentId} value={department.departmentId}>
              {department.departmentName} · {department.departmentCode}
            </option>
          ))}
        </SelectField>
      )}
      {lookupError && <p className="app-note" role="alert">{lookupError}</p>}
      {!lookupError && scope.scopeType !== "TENANT" && !institutionsLoading && institutions.length === 0 && (
        <p className="app-note" role="status">No institutions are available in your current access scope.</p>
      )}
      {!lookupError && scope.scopeType === "DEPARTMENT" && scope.institutionId && !departmentsLoading && departments.length === 0 && (
        <p className="app-note" role="status">No departments are available for this institution.</p>
      )}
    </div>
  );
}

export default ScopeFields;
