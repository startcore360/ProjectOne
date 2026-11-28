import api from "../../../services/axios";

export type StudentRow = {
  studentProfileId: string; studentCode: string; admissionId?: string; fullName: string;
  preferredName?: string; status: string; institutionId: string; departmentId?: string;
  programId?: string; batchId?: string; sectionId?: string; rollNumber?: string; photoFileId?: string;
};
export type StudentNote = { student_note_id: string; note_type: string; body: string; created_at: string; added_by_user_id: string; delete_after?: string };
export type GuardianLink = { studentGuardianLinkId: string; guardianProfileId: string; guardianName: string; guardianMobile: string; guardianEmail?: string; relationship: string; isPrimary: boolean; status: string; contactAuthorized?: boolean; emergencyContact?: boolean };
export type LegalHold = { legalHoldId: string; reason: string; placedAt: string; releasedAt?: string; active: boolean };
export type StudentPage = { items: StudentRow[]; nextCursor?: string; hasMore: boolean };
export const studentApi = {
  list: (tenantId: string, params: Record<string,string>) => api.get<StudentPage>(`/tenants/${tenantId}/students`, { params }),
  get: (tenantId: string, id: string) => api.get<Record<string,unknown>>(`/tenants/${tenantId}/students/${id}/profile`),
  update: (tenantId: string,id: string,body: Record<string,unknown>) => api.patch(`/tenants/${tenantId}/students/${id}/profile`,body),
  changeStatus: (tenantId:string,id:string,body:{status:string;reason:string}) => api.post(`/tenants/${tenantId}/students/${id}/status-changes`,body),
  linkGuardian: (tenantId:string,id:string,body:{fullName:string;mobile:string;email?:string;relationship:string;isPrimary:boolean}) => api.post<{studentGuardianLinkId:string}>(`/tenants/${tenantId}/students/${id}/guardian-links`,body),
  guardians: (tenantId: string,id: string) => api.get<GuardianLink[]>(`/tenants/${tenantId}/students/${id}/guardian-links`),
  notes: (tenantId: string,id: string) => api.get<StudentNote[]>(`/tenants/${tenantId}/students/${id}/notes`),
  addNote: (tenantId: string,id: string,body: {category:string;body:string;correctsNoteId?:string;deleteAfter?:string}) => api.post(`/tenants/${tenantId}/students/${id}/notes`,body),
  holds: (tenantId: string,id: string) => api.get<LegalHold[]>(`/tenants/${tenantId}/students/${id}/legal-holds`),
  guardianPreferencesList: (tenantId:string,studentId:string) => api.get<Array<{studentGuardianLinkId:string;contactAuthorized:boolean;emergencyContact:boolean;primary:boolean}>>(`/tenants/${tenantId}/students/${studentId}/guardian-preferences`),
  guardianPreferences: (tenantId:string,studentId:string,linkId:string,body:{contactAuthorized:boolean;emergencyContact:boolean;primary:boolean}) => api.patch(`/tenants/${tenantId}/students/${studentId}/guardian-links/${linkId}/preferences`,body),
};