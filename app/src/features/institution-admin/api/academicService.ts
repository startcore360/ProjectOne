import api from "../../../services/axios";

export interface ProgramRuleSetItem {
  programRuleSetId: string;
  institutionId: string | null;
  programId: string | null;
  versionNo: number;
  rules: string;
  effectiveFrom: string;
  status: string;
  createdAt: string;
}

export interface GradingSchemeItem {
  gradingSchemeVersionId: string;
  institutionId: string | null;
  schemeName: string;
  versionNo: number;
  status: string;
  effectiveFrom: string;
  createdAt: string;
}

export interface SubjectAllocationItem {
  subjectAllocationId: string;
  institutionId: string;
  courseOfferingId: string;
  sectionId: string | null;
  subjectGroupId: string | null;
  staffAssignmentId: string;
  status: string;
  createdAt: string;
}

export interface PromotionRunItem {
  promotionRunId: string;
  institutionId: string;
  fromSessionId: string;
  toSessionId: string;
  status: string;
  approvalRequestId: string | null;
  executedAt: string | null;
  createdAt: string;
}

export interface StudentStatusItem {
  studentProfileId: string;
  institutionId: string | null;
  studentCode: string;
  status: string;
  statusReason: string | null;
  statusChangedAt: string | null;
}

export const academicService = {
  // Program rules
  listProgramRules: async (tenantId: string): Promise<ProgramRuleSetItem[]> => {
    const res = await api.get<ProgramRuleSetItem[]>(`/v1/tenants/${tenantId}/program-rule-sets`);
    return res.data;
  },
  createProgramRules: async (
    tenantId: string,
    req: { institutionId?: string; programId?: string; rules: string; effectiveFrom: string }
  ): Promise<ProgramRuleSetItem> => {
    const res = await api.post<ProgramRuleSetItem>(`/v1/tenants/${tenantId}/program-rule-sets`, req);
    return res.data;
  },

  // Grading schemes
  listGradingSchemes: async (tenantId: string, institutionId?: string): Promise<GradingSchemeItem[]> => {
    const params = institutionId ? `?institutionId=${institutionId}` : "";
    const res = await api.get<GradingSchemeItem[]>(`/v1/tenants/${tenantId}/grading-schemes${params}`);
    return res.data;
  },
  createGradingScheme: async (
    tenantId: string,
    req: { institutionId?: string; schemeName: string; effectiveFrom: string }
  ): Promise<GradingSchemeItem> => {
    const res = await api.post<GradingSchemeItem>(`/v1/tenants/${tenantId}/grading-schemes`, req);
    return res.data;
  },

  // Subject allocations
  listSubjectAllocations: async (tenantId: string, institutionId?: string): Promise<SubjectAllocationItem[]> => {
    const params = institutionId ? `?institutionId=${institutionId}` : "";
    const res = await api.get<SubjectAllocationItem[]>(`/v1/tenants/${tenantId}/subject-allocations${params}`);
    return res.data;
  },
  allocateSubject: async (
    tenantId: string,
    req: {
      institutionId: string;
      courseOfferingId: string;
      sectionId?: string;
      subjectGroupId?: string;
      staffAssignmentId: string;
    }
  ): Promise<SubjectAllocationItem> => {
    const res = await api.post<SubjectAllocationItem>(`/v1/tenants/${tenantId}/subject-allocations`, req);
    return res.data;
  },

  // Promotion runs
  createPromotionRun: async (
    tenantId: string,
    req: { institutionId: string; fromSessionId: string; toSessionId: string }
  ): Promise<PromotionRunItem> => {
    const res = await api.post<PromotionRunItem>(`/v1/tenants/${tenantId}/promotion-runs`, req);
    return res.data;
  },
  submitPromotionRun: async (tenantId: string, id: string): Promise<PromotionRunItem> => {
    const res = await api.post<PromotionRunItem>(`/v1/tenants/${tenantId}/promotion-runs/${id}/submission`);
    return res.data;
  },
  decidePromotionRun: async (
    tenantId: string,
    id: string,
    decision: { decision: string; comment?: string }
  ): Promise<PromotionRunItem> => {
    const res = await api.post<PromotionRunItem>(`/v1/tenants/${tenantId}/promotion-runs/${id}/decisions`, decision);
    return res.data;
  },

  // Student status change
  changeStudentStatus: async (
    tenantId: string,
    studentId: string,
    req: { status: string; reason: string }
  ): Promise<StudentStatusItem> => {
    const res = await api.post<StudentStatusItem>(`/v1/tenants/${tenantId}/students/${studentId}/status-changes`, req);
    return res.data;
  },
};
