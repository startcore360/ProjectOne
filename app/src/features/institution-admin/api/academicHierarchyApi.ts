import api from "../../../services/axios";

export type AcademicDetail = Record<string, unknown>;

const tenantBase = (tenantId: string): string => {
  if (!tenantId?.trim()) throw new Error("Tenant context required");
  return `/api/tenants/${encodeURIComponent(tenantId)}`;
};

const institutionBase = (tenantId: string, institutionId: string): string => {
  if (!institutionId?.trim()) throw new Error("Institution context required");
  return `${tenantBase(tenantId)}/institutions/${encodeURIComponent(institutionId)}`;
};

async function getDetail<T extends AcademicDetail>(url: string): Promise<T> {
  const response = await api.get<T>(url);
  return response.data;
}

export const getInstitutionDetail = (tenantId: string, institutionId: string) =>
  getDetail(`${tenantBase(tenantId)}/institutions/${encodeURIComponent(institutionId)}`);

export const getDepartmentDetail = (tenantId: string, institutionId: string, departmentId: string) =>
  getDetail(`${institutionBase(tenantId, institutionId)}/departments/${encodeURIComponent(departmentId)}`);

export const getProgramDetail = (tenantId: string, institutionId: string, programId: string) =>
  getDetail(`${institutionBase(tenantId, institutionId)}/programs/${encodeURIComponent(programId)}`);

export const getSectionDetail = (tenantId: string, institutionId: string, sectionId: string) =>
  getDetail(`${institutionBase(tenantId, institutionId)}/sections/${encodeURIComponent(sectionId)}`);

export const getAcademicSessionDetail = (tenantId: string, institutionId: string, academicSessionId: string) =>
  getDetail(`${institutionBase(tenantId, institutionId)}/academic-sessions/${encodeURIComponent(academicSessionId)}`);

export const setCurrentAcademicSession = (
  tenantId: string,
  institutionId: string,
  academicSessionId: string,
) => api.post(
  `${institutionBase(tenantId, institutionId)}/academic-sessions/${encodeURIComponent(academicSessionId)}/current`,
).then((response) => response.data as AcademicDetail);
