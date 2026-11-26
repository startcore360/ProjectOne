import api from "../../../services/axios";

export type GrievanceCase = {
  grievanceCaseId: string; caseNumber: string; institutionId: string; category: string;
  details: string; accusedUserId: string | null; anonymousSubmission: boolean; status: string;
  assignedToUserId: string | null; nextHearingAt: string | null; resolutionNotes: string | null;
  submittedAt: string;
};
export type GrievanceReceipt = { caseNumber: string; status: string; submittedAt: string };
export type Evidence = { grievanceEvidenceId?: string; evidenceId?: string; fileId: string; uploadedByUserId: string; createdAt: string };
export type GovernanceAction = {
  actionId: string; actionType: string; actionNotes: string; actionAt: string;
  performedByUserId: string; noticeFileId: string | null; minutesFileId: string | null; penalty: string | null;
};
export type GrievanceHearing = {
  hearingId: string; hearingAt: string; hearingNotes: string; noticeFileId: string | null;
  minutesFileId: string | null; minutesNotes: string | null; status: string; updatedAt: string;
};
export type DisciplinaryIncident = {
  incidentId: string; institutionId: string; studentId: string; studentName: string;
  incidentDate: string; severity: string; description: string; actionTaken: string | null;
  status: string; createdAt: string;
};

const grievanceRoot = (tenantId: string) => `/tenants/${tenantId}/governance/grievances`;
const disciplinaryRoot = (tenantId: string) => `/tenants/${tenantId}/governance/disciplinary-incidents`;

export const governanceApi = {
  submitGrievance: (tenantId: string, body: object) =>
    api.post<GrievanceReceipt>(`${grievanceRoot(tenantId)}/submissions`, body).then(r => r.data),
  grievances: (tenantId: string, institutionId: string) =>
    api.get<GrievanceCase[]>(`${grievanceRoot(tenantId)}/cases`, { params: { institutionId } }).then(r => r.data),
  grievance: (tenantId: string, caseId: string) =>
    api.get<GrievanceCase>(`${grievanceRoot(tenantId)}/cases/${caseId}`).then(r => r.data),
  grievanceActions: (tenantId: string, caseId: string) =>
    api.get<GovernanceAction[]>(`${grievanceRoot(tenantId)}/cases/${caseId}/actions`).then(r => r.data),
  grievanceHearings: (tenantId: string, caseId: string) =>
    api.get<GrievanceHearing[]>(`${grievanceRoot(tenantId)}/cases/${caseId}/hearings`).then(r => r.data),
  assignGrievance: (tenantId: string, caseId: string, body: object) =>
    api.post<GrievanceCase>(`${grievanceRoot(tenantId)}/cases/${caseId}/assign`, body).then(r => r.data),
  scheduleHearing: (tenantId: string, caseId: string, body: object) =>
    api.post<GrievanceCase>(`${grievanceRoot(tenantId)}/cases/${caseId}/hearing`, body).then(r => r.data),
  recordMinutes: (tenantId: string, caseId: string, body: object) =>
    api.post(`${grievanceRoot(tenantId)}/cases/${caseId}/minutes`, body),
  issuePenalty: (tenantId: string, caseId: string, body: object) =>
    api.post(`${grievanceRoot(tenantId)}/cases/${caseId}/penalty`, body),
  resolveGrievance: (tenantId: string, caseId: string, body: object) =>
    api.post<GrievanceCase>(`${grievanceRoot(tenantId)}/cases/${caseId}/resolve`, body).then(r => r.data),
  uploadGrievanceEvidence: (tenantId: string, caseId: string, file: File) => {
    const data = new FormData(); data.append("file", file);
    return api.post<{ fileId: string }>(`${grievanceRoot(tenantId)}/cases/${caseId}/evidence`, data).then(r => r.data);
  },
  grievanceEvidence: (tenantId: string, caseId: string) =>
    api.get<Evidence[]>(`${grievanceRoot(tenantId)}/cases/${caseId}/evidence`).then(r => r.data),
  createIncident: (tenantId: string, body: object) =>
    api.post<DisciplinaryIncident>(disciplinaryRoot(tenantId), body).then(r => r.data),
  incidents: (tenantId: string, institutionId: string) =>
    api.get<DisciplinaryIncident[]>(disciplinaryRoot(tenantId), { params: { institutionId } }).then(r => r.data),
  incident: (tenantId: string, incidentId: string) =>
    api.get<DisciplinaryIncident>(`${disciplinaryRoot(tenantId)}/${incidentId}`).then(r => r.data),
  incidentActions: (tenantId: string, incidentId: string) =>
    api.get<GovernanceAction[]>(`${disciplinaryRoot(tenantId)}/${incidentId}/actions`).then(r => r.data),
  updateIncident: (tenantId: string, incidentId: string, body: object) =>
    api.patch<DisciplinaryIncident>(`${disciplinaryRoot(tenantId)}/${incidentId}`, body).then(r => r.data),
  uploadIncidentEvidence: (tenantId: string, incidentId: string, file: File) => {
    const data = new FormData(); data.append("file", file);
    return api.post<{ fileId: string }>(`${disciplinaryRoot(tenantId)}/${incidentId}/evidence`, data).then(r => r.data);
  },
  incidentEvidence: (tenantId: string, incidentId: string) =>
    api.get<Evidence[]>(`${disciplinaryRoot(tenantId)}/${incidentId}/evidence`).then(r => r.data),
  incidentAction: (tenantId: string, incidentId: string, body: object) =>
    api.post(`${disciplinaryRoot(tenantId)}/${incidentId}/actions`, body),
};
