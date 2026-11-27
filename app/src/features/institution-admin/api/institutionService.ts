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

export type InstitutionIdFormats = {
  studentIdFormat: string;
  staffIdFormat: string | null;
  studentFormatLocked: boolean;
  staffFormatLocked: boolean;
};

export type InstitutionHierarchyNode = {
  institution: InstitutionItem;
  departments: DepartmentItem[];
};

export type InstitutionHierarchyResponse = {
  tenantId: string;
  tenantName: string;
  tenantType: string;
  institutions: InstitutionHierarchyNode[];
};

export type InstitutionPageResponse = {
  items: InstitutionItem[];
  total: number;
};

export type UpdateInstitutionPayload = {
  institutionName: string;
  addressLine?: string | null;
  city?: string | null;
  stateName?: string | null;
  postalCode?: string | null;
  principalInviteEmail?: string | null;
  externalUniversityMode?: boolean;
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
  studentIdFormat: string;
  staffIdFormat?: string;
};

export type DepartmentItem = {
  departmentId: string;
  institutionId: string;
  departmentCode: string;
  departmentName: string;
  hodStaffAssignmentId?: string;
  status: string;
  facultyCount?: number;
  studentCount?: number;
  rowVersion?: number;
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
  rowVersion?: number;
};

export type CreateProgramPayload = {
  departmentId: string;
  programCode: string;
  programName: string;
  durationYears: number;
  semestersPerYear?: number;
};

export type SchoolClassItem = {
  classId: string;
  institutionId: string;
  className: string;
  displayOrder: number;
  studentLoginEnabled: boolean;
  status: string;
  rowVersion?: number;
};

export type SectionItem = {
  sectionId: string;
  institutionId: string;
  academicSessionId: string;
  semesterId: string | null;
  classId: string | null;
  sectionName: string;
  status: string;
  capacity?: number | null;
  rowVersion?: number;
};

export type SubjectItem = {
  subjectId: string;
  institutionId: string;
  subjectCode: string;
  subjectName: string;
  credits: number;
  isElective: boolean;
  status: string;
};

export type CourseOfferingItem = {
  courseOfferingId: string;
  institutionId: string;
  subjectId: string;
  academicSessionId: string;
  semesterId: string | null;
  classId: string | null;
};

export type CreateSchoolClassPayload = { className: string; displayOrder: number };
export type CreateSectionPayload = { academicSessionId: string; classId?: string | null; semesterId?: string | null; sectionName: string; capacity?: number };

export type CurriculumVersionItem = {
  programCurriculumVersionId: string;
  institutionId: string;
  programId: string;
  versionNumber: number;
  curriculumJson: Record<string, unknown>;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string | null;
  createdBy: string;
};

export type CreateCurriculumVersionPayload = {
  curriculumJson: Record<string, unknown>;
  effectiveFrom: string;
  effectiveTo?: string | null;
};

export type ProgramYearItem = {
  programYearId: string;
  institutionId: string;
  programId: string;
  yearNumber: number;
  label: string;
};

export type CreateProgramYearPayload = { yearNumber: number; label: string };

export type SemesterItem = {
  semesterId: string;
  institutionId: string;
  programYearId: string;
  semesterNumber: number;
  label: string;
  startDate: string | null;
  endDate: string | null;
};

export type CreateSemesterPayload = {
  semesterNumber: number;
  label: string;
  startDate: string;
  endDate: string;
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

  async updateInstitution(
    tenantId: string,
    institutionId: string,
    rowVersion: number,
    payload: UpdateInstitutionPayload
  ): Promise<InstitutionItem> {
    const res = await api.put<InstitutionItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}`,
      payload,
      { headers: { "If-Match": `"${rowVersion}"` } }
    );
    return res.data;
  },

  async getHierarchy(tenantId: string): Promise<InstitutionHierarchyResponse> {
    const res = await api.get<InstitutionHierarchyResponse>(`/api/tenants/${tenantId}/institutions/hierarchy`);
    return res.data;
  },

  async createDraftInstitution(
    tenantId: string,
    request: CreateInstitutionRequest
  ): Promise<InstitutionItem> {
    const res = await api.post<InstitutionItem>(`/api/tenants/${tenantId}/institutions`, request);
    return res.data;
  },


  async getIdFormats(tenantId: string, institutionId: string): Promise<InstitutionIdFormats> {
    const res = await api.get<InstitutionIdFormats>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/id-formats`
    );
    return res.data;
  },

  async setIdFormats(tenantId: string, institutionId: string, payload: {
    studentIdFormat: string;
    staffIdFormat?: string;
  }): Promise<InstitutionIdFormats> {
    const res = await api.put<InstitutionIdFormats>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/id-formats`,
      payload
    );
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

  async updateDepartment(tenantId: string, institutionId: string, departmentId: string, rowVersion: number, payload: CreateDepartmentPayload): Promise<DepartmentItem> {
    const res = await api.put<DepartmentItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/departments/${departmentId}`,
      payload,
      { headers: { "If-Match": `"${rowVersion}"` } }
    );
    return res.data;
  },

  async archiveDepartment(tenantId: string, institutionId: string, departmentId: string, rowVersion: number): Promise<void> {
    await api.delete(`/api/tenants/${tenantId}/institutions/${institutionId}/departments/${departmentId}`,
      { headers: { "If-Match": `"${rowVersion}"` } });
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

  async updateProgram(tenantId: string, institutionId: string, programId: string, rowVersion: number, payload: CreateProgramPayload): Promise<ProgramItem> {
    const res = await api.put<ProgramItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/programs/${programId}`,
      payload,
      { headers: { "If-Match": `"${rowVersion}"` } }
    );
    return res.data;
  },

  async archiveProgram(tenantId: string, institutionId: string, programId: string, rowVersion: number): Promise<void> {
    await api.delete(`/api/tenants/${tenantId}/institutions/${institutionId}/programs/${programId}`,
      { headers: { "If-Match": `"${rowVersion}"` } });
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

  async listCurriculumVersions(tenantId: string, institutionId: string, programId: string): Promise<CurriculumVersionItem[]> {
    const res = await api.get<CurriculumVersionItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/programs/${programId}/curriculum-versions`
    );
    return res.data;
  },

  async createCurriculumVersion(tenantId: string, institutionId: string, programId: string, payload: CreateCurriculumVersionPayload): Promise<CurriculumVersionItem> {
    const res = await api.post<CurriculumVersionItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/programs/${programId}/curriculum-versions`,
      payload
    );
    return res.data;
  },

  async listProgramYears(tenantId: string, institutionId: string, programId: string): Promise<ProgramYearItem[]> {
    const res = await api.get<ProgramYearItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/programs/${programId}/years`
    );
    return res.data;
  },

  async createProgramYear(tenantId: string, institutionId: string, programId: string, payload: CreateProgramYearPayload): Promise<ProgramYearItem> {
    const res = await api.post<ProgramYearItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/programs/${programId}/years`,
      payload
    );
    return res.data;
  },

  async listSemesters(tenantId: string, institutionId: string, programYearId: string): Promise<SemesterItem[]> {
    const res = await api.get<SemesterItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/program-years/${programYearId}/semesters`
    );
    return res.data;
  },

  async createSemester(tenantId: string, institutionId: string, programYearId: string, payload: CreateSemesterPayload): Promise<SemesterItem> {
    const res = await api.post<SemesterItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/program-years/${programYearId}/semesters`,
      payload
    );
    return res.data;
  },

  async updateSchoolClass(tenantId: string, institutionId: string, classId: string, rowVersion: number, payload: CreateSchoolClassPayload): Promise<SchoolClassItem> {
    const res = await api.put<SchoolClassItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/classes/${classId}`,
      payload,
      { headers: { "If-Match": `"${rowVersion}"` } }
    );
    return res.data;
  },

  async archiveSchoolClass(tenantId: string, institutionId: string, classId: string, rowVersion: number): Promise<void> {
    await api.delete(`/api/tenants/${tenantId}/institutions/${institutionId}/classes/${classId}`,
      { headers: { "If-Match": `"${rowVersion}"` } });
  },

  async listSchoolClasses(tenantId: string, institutionId: string): Promise<SchoolClassItem[]> {
    const res = await api.get<SchoolClassItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/classes`
    );
    return res.data;
  },

  async createSchoolClass(
    tenantId: string,
    institutionId: string,
    payload: CreateSchoolClassPayload
  ): Promise<SchoolClassItem> {
    const res = await api.post<SchoolClassItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/classes`,
      payload
    );
    return res.data;
  },

  async updateSection(tenantId: string, institutionId: string, sectionId: string, rowVersion: number, payload: CreateSectionPayload & { semesterId?: string | null }): Promise<SectionItem> {
    const res = await api.put<SectionItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/sections/${sectionId}`,
      payload,
      { headers: { "If-Match": `"${rowVersion}"` } }
    );
    return res.data;
  },

  async archiveSection(tenantId: string, institutionId: string, sectionId: string, rowVersion: number): Promise<void> {
    await api.delete(`/api/tenants/${tenantId}/institutions/${institutionId}/sections/${sectionId}`,
      { headers: { "If-Match": `"${rowVersion}"` } });
  },

  async listSchoolClasses(tenantId: string, institutionId: string): Promise<SchoolClassItem[]> {
    const res = await api.get<SchoolClassItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/classes`
    );
    return res.data;
  },

  async listSubjects(tenantId: string, institutionId: string): Promise<SubjectItem[]> {
    const res = await api.get<SubjectItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/subjects`
    );
    return res.data;
  },

  async listCourseOfferings(tenantId: string, institutionId: string, academicSessionId?: string): Promise<CourseOfferingItem[]> {
    const res = await api.get<CourseOfferingItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/course-offerings`,
      { params: academicSessionId ? { academicSessionId } : {} }
    );
    return res.data;
  },

  async listSections(tenantId: string, institutionId: string): Promise<SectionItem[]> {
    const res = await api.get<SectionItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/sections`
    );
    return res.data;
  },

  async createSection(
    tenantId: string,
    institutionId: string,
    payload: CreateSectionPayload
  ): Promise<SectionItem> {
    const res = await api.post<SectionItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/sections`,
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
