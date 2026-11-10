import api from "../../../services/axios";

export type InstitutionType = "COLLEGE" | "SCHOOL";
export type InstitutionStatus = "DRAFT" | "ACTIVE" | "BLOCKED" | "ARCHIVED";

export type InstitutionItem = {
  tenantId: string;
  institutionId: string;
  institutionType: InstitutionType;
  institutionCode: string;
  institutionName: string;
  status: InstitutionStatus;
  externalUniversityMode: boolean;
  underUniversity: boolean;
  addressLine?: string;
  city?: string;
  stateName?: string;
  postalCode?: string;
  principalInviteEmail?: string;
  activatedAt?: string;
  createdAt: string;
  updatedAt: string;
  rowVersion: number;
};

export type InstitutionPageResponse = {
  items: InstitutionItem[];
  total: number;
};

export type CreateInstitutionRequest = {
  institutionType: InstitutionType;
  institutionCode: string;
  institutionName: string;
  externalUniversityMode?: boolean;
  addressLine?: string;
  city?: string;
  stateName?: string;
  postalCode?: string;
  principalInviteEmail?: string;
};

export type DepartmentItem = {
  departmentId: string;
  institutionId: string;
  departmentCode: string;
  departmentName: string;
  hodStaffAssignmentId?: string;
  status: string;
};

export type CreateDepartmentPayload = {
  departmentCode: string;
  departmentName: string;
};

export type ProgramItem = {
  programId: string;
  institutionId: string;
  departmentId: string;
  programCode: string;
  programName: string;
  durationYears: number;
  semestersPerYear: number;
  status: string;
};

export type CreateProgramPayload = {
  departmentId: string;
  programCode: string;
  programName: string;
  durationYears: number;
  semestersPerYear?: number;
};

export type AcademicSessionItem = {
  academicSessionId: string;
  tenantId: string;
  institutionId?: string | null;
  sessionLabel: string;
  startDate: string;
  endDate: string;
  academicYear: number;
  status: string;
  rowVersion: number;
};

export type CreateAcademicSessionPayload = {
  institutionId?: string | null;
  sessionLabel: string;
  startDate: string;
  endDate: string;
  academicYear: number;
  status?: string;
};

export type MasterCalendarEventItem = {
  academicCalendarEventId: string;
  tenantId: string;
  academicSessionId: string;
  institutionId?: string | null;
  sourceLevel: string;
  eventType: string;
  title: string;
  startDate: string;
  endDate: string;
  rowVersion: number;
};

export type CreateMasterCalendarEventPayload = {
  academicSessionId: string;
  eventType: string;
  title: string;
  startDate: string;
  endDate: string;
};

export const institutionService = {
  async listInstitutions(tenantId: string): Promise<InstitutionPageResponse> {
    const res = await api.get<InstitutionPageResponse>(`/api/tenants/${tenantId}/institutions`);
    return res.data;
  },

  async createDraftInstitution(
    tenantId: string,
    request: CreateInstitutionRequest
  ): Promise<InstitutionItem> {
    const res = await api.post<InstitutionItem>(`/api/tenants/${tenantId}/institutions`, request);
    return res.data;
  },

  async activateInstitution(tenantId: string, institutionId: string): Promise<InstitutionItem> {
    const res = await api.post<InstitutionItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/activation`
    );
    return res.data;
  },

  async requestBlock(
    tenantId: string,
    institutionId: string,
    reason: string
  ): Promise<{ institutionId: string; message: string; status: InstitutionStatus | "REQUESTED" | string }> {
    const res = await api.post<{ institutionId: string; message: string; status: InstitutionStatus | "REQUESTED" | string }>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/block-requests`,
      { reason }
    );
    return res.data;
  },

  async decideBlock(
    tenantId: string,
    institutionId: string,
    approved: boolean,
    decisionNotes?: string
  ): Promise<{ institutionId: string; message: string; status: InstitutionStatus | "REQUESTED" | string }> {
    const res = await api.post<{ institutionId: string; message: string; status: InstitutionStatus | "REQUESTED" | string }>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/block-decisions`,
      { approved, decisionNotes }
    );
    return res.data;
  },

  async listDepartments(tenantId: string, institutionId: string): Promise<DepartmentItem[]> {
    const res = await api.get<DepartmentItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/departments`
    );
    return res.data;
  },

  async createDepartment(
    tenantId: string,
    institutionId: string,
    payload: CreateDepartmentPayload
  ): Promise<DepartmentItem> {
    const res = await api.post<DepartmentItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/departments`,
      payload
    );
    return res.data;
  },

  async listPrograms(tenantId: string, institutionId: string): Promise<ProgramItem[]> {
    const res = await api.get<ProgramItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/programs`
    );
    return res.data;
  },

  async createProgram(
    tenantId: string,
    institutionId: string,
    payload: CreateProgramPayload
  ): Promise<ProgramItem> {
    const res = await api.post<ProgramItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/programs`,
      payload
    );
    return res.data;
  },

  async listAcademicSessions(tenantId: string, institutionId?: string): Promise<AcademicSessionItem[]> {
    const res = await api.get<AcademicSessionItem[]>(
      `/api/tenants/${tenantId}/academic-sessions`,
      { params: institutionId ? { institutionId } : {} }
    );
    return res.data;
  },

  async createAcademicSession(
    tenantId: string,
    payload: CreateAcademicSessionPayload
  ): Promise<AcademicSessionItem> {
    const res = await api.post<AcademicSessionItem>(
      `/api/tenants/${tenantId}/academic-sessions`,
      payload
    );
    return res.data;
  },

  async listMasterCalendarEvents(tenantId: string, academicSessionId?: string): Promise<MasterCalendarEventItem[]> {
    const res = await api.get<MasterCalendarEventItem[]>(
      `/api/tenants/${tenantId}/master-calendar-events`,
      { params: academicSessionId ? { academicSessionId } : {} }
    );
    return res.data;
  },

  async createMasterCalendarEvent(
    tenantId: string,
    payload: CreateMasterCalendarEventPayload
  ): Promise<MasterCalendarEventItem> {
    const res = await api.post<MasterCalendarEventItem>(
      `/api/tenants/${tenantId}/master-calendar-events`,
      payload
    );
    return res.data;
  },
};
