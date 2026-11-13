import api from "../../../services/axios";

export type StaffProfile = {
  id: string;
  userAccountId: string | null;
  staffCode: string;
  fullName: string;
  dateOfBirth?: string | null;
  personalEmail?: string | null;
  personalMobile?: string | null;
  nationalIdLast4?: string | null;
  photoFileId?: string | null;
  employmentStatus: "ACTIVE" | "INACTIVE" | "LEFT" | string;
  joinedOn?: string | null;
  leftOn?: string | null;
  version: number;
};

export type StaffAssignment = {
  id: string;
  staffProfileId: string;
  institutionId: string | null;
  departmentId: string | null;
  designation: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  status: "ACTIVE" | "ENDED" | string;
  version: number;
};

export type StaffDocument = {
  id: string;
  staffProfileId: string;
  documentType: "DEGREE" | "ID_PROOF" | "OTHER" | string;
  fileId: string;
  verificationStatus: "UPLOADED" | "VERIFIED" | "REJECTED" | string;
  verifiedBy?: string | null;
  verifiedAt?: string | null;
  createdAt: string;
  createdBy: string;
  version: number;
};

export type StaffServiceRecord = {
  id: string;
  staffProfileId: string;
  eventType: string;
  effectiveOn: string;
  details: string;
  staffAssignmentId?: string | null;
  documentFileId?: string | null;
  createdAt: string;
};

export type StaffProfilePayload = {
  staffCode?: string;
  fullName: string;
  officialEmail?: string;
  initialRoleCode?: string;
  dateOfBirth?: string;
  personalEmail?: string;
  personalMobile?: string;
  nationalIdLast4?: string;
  employmentStatus?: string;
  joinedOn?: string;
  leftOn?: string;
};

export type StaffProfileUpdate = Partial<StaffProfilePayload>;

export type StaffAssignmentPayload = {
  institutionId?: string;
  departmentId?: string;
  designation: string;
  effectiveFrom: string;
  effectiveTo?: string;
};

export type StaffServiceRecordPayload = {
  eventType: "JOINED" | "DESIGNATION_CHANGE" | "TRANSFER" | "CONFIRMATION" | "LEAVE_OF_ABSENCE" | "LEFT" | "OTHER";
  effectiveOn: string;
  details: string;
  staffAssignmentId?: string;
  documentFileId?: string;
};

export type StaffOnboardingResponse = {
  staff: StaffProfile;
  login: {
    userAccountId: string;
    email: string;
    status: string;
    temporaryPassword: string;
    initialRole: string | null;
  };
  personalEmailVerificationRequired: boolean;
};

export type StaffListResponse = {
  items: StaffProfile[];
  page: { limit: number; nextCursor: string | null; hasMore: boolean; totalCount: number | null };
};

export type StaffFileUpload = {
  fileId: string;
  originalFilename: string;
  scanStatus: string;
  status: string;
};

export const staffService = {
  async list(tenantId: string, query = "") {
    const response = await api.get<StaffListResponse>(`/api/tenants/${tenantId}/staff`, {
      params: { q: query || undefined, limit: 100 },
    });
    return response.data;
  },

  async get(tenantId: string, id: string) {
    const response = await api.get<StaffProfile>(`/api/tenants/${tenantId}/staff/${id}`);
    return { data: response.data, etag: response.headers.etag as string | undefined };
  },

  async create(tenantId: string, payload: StaffProfilePayload) {
    const response = await api.post<StaffOnboardingResponse>(`/api/tenants/${tenantId}/staff`, payload);
    return response.data;
  },

  async update(tenantId: string, id: string, etag: string, payload: StaffProfileUpdate) {
    const response = await api.patch<StaffProfile>(`/api/tenants/${tenantId}/staff/${id}`, payload, {
      headers: { "If-Match": etag },
    });
    return { data: response.data, etag: response.headers.etag as string | undefined };
  },

  async listAssignments(tenantId: string, staffId: string) {
    const response = await api.get<StaffAssignment[]>(`/api/tenants/${tenantId}/staff/${staffId}/assignments`);
    return response.data;
  },

  async addAssignment(tenantId: string, staffId: string, payload: StaffAssignmentPayload) {
    const response = await api.post<StaffAssignment>(`/api/tenants/${tenantId}/staff/${staffId}/assignments`, payload);
    return response.data;
  },

  async endAssignment(tenantId: string, staffId: string, assignmentId: string, etag: string, effectiveTo: string) {
    const response = await api.patch<StaffAssignment>(
      `/api/tenants/${tenantId}/staff/${staffId}/assignments/${assignmentId}`,
      { effectiveTo },
      { headers: { "If-Match": etag } },
    );
    return response.data;
  },

  async listDocuments(tenantId: string, staffId: string) {
    const response = await api.get<StaffDocument[]>(`/api/tenants/${tenantId}/staff/${staffId}/documents`);
    return response.data;
  },

  async attachDocument(tenantId: string, staffId: string, payload: { documentType: string; fileId: string }) {
    const response = await api.post<StaffDocument>(`/api/tenants/${tenantId}/staff/${staffId}/documents`, payload);
    return response.data;
  },

  async verifyDocument(tenantId: string, documentId: string, payload: { verificationStatus: "VERIFIED" | "REJECTED"; remark?: string }) {
    const response = await api.post<StaffDocument>(
      `/api/tenants/${tenantId}/staff-documents/${documentId}/verification`,
      payload,
    );
    return response.data;
  },

  async listServiceRecords(tenantId: string, staffId: string) {
    const response = await api.get<StaffServiceRecord[]>(`/api/tenants/${tenantId}/staff/${staffId}/service-records`);
    return response.data;
  },

  async addServiceRecord(tenantId: string, staffId: string, payload: StaffServiceRecordPayload) {
    const response = await api.post<StaffServiceRecord>(`/api/tenants/${tenantId}/staff/${staffId}/service-records`, payload);
    return response.data;
  },

  async uploadDocument(tenantId: string, file: File, institutionId?: string) {
    const form = new FormData();
    form.append("file", file);
    form.append("purpose", "STAFF_DOCUMENT");
    if (institutionId) form.append("institutionId", institutionId);
    const response = await api.post<StaffFileUpload>(`/api/tenants/${tenantId}/files`, form);
    return response.data;
  },
};
