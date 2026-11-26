import api from "../../../services/axios";

export type IdCardTemplate = {
  templateId: string; institutionId: string; name: string; layout: "PORTRAIT" | "LANDSCAPE";
  backgroundImageRef: string | null; fieldsConfigJson: string; status: string; rowVersion: number;
};
export type IdCard = {
  issuanceId: string; institutionId: string; userId: string; cardNumber: string;
  issueDate: string; expiryDate: string | null; status: string; qrToken: string;
  fullName: string; profileCode: string; institutionName: string; layout: "PORTRAIT" | "LANDSCAPE";
};
export type IdCardReplacement = {
  replacementId: string; originalIssuanceId: string; reason: string;
  status: string; replacementIssuanceId: string | null;
};
export type CertificateTemplate = {
  certificateTemplateId: string; institutionId: string; type: string;
  name: string; templateHtml: string; status: string; rowVersion: number;
};
export type CertificateRequest = {
  certificateRequestId: string; institutionId: string; studentId: string;
  templateId: string; reason: string; status: string; duesJson: string; createdAt: string | null;
};
export type CertificateIssuance = {
  issuanceId: string; requestId: string; certificateNumber: string;
  issuedAt: string; revokedAt: string | null;
};
export type CertificatePrepared = {
  certificateIssuanceId: string; certificateNumber: string;
  verificationCode: string; verificationUrl: string; issuedAt: string;
};
export type CertificateVerification = {
  status: "VALID" | "REVOKED"; studentName: string; programName: string;
  certificateType: string; issuedAt: string; institutionName: string;
};

const root = (tenantId: string) => `/tenants/${tenantId}/documents`;

export const documentsApi = {
  idCardTemplates: (tenantId: string, institutionId: string) =>
    api.get<IdCardTemplate[]>(`${root(tenantId)}/id-cards/templates`, { params: { institutionId } }).then(r => r.data),
  createIdCardTemplate: (tenantId: string, institutionId: string, body: object) =>
    api.post<IdCardTemplate>(`${root(tenantId)}/id-cards/templates`, body, { params: { institutionId } }).then(r => r.data),
  updateIdCardTemplate: (tenantId: string, templateId: string, expectedVersion: number, body: object) =>
    api.put<IdCardTemplate>(`${root(tenantId)}/id-cards/templates/${templateId}`, body, { params: { expectedVersion } }).then(r => r.data),
  idCards: (tenantId: string, institutionId: string) =>
    api.get<IdCard[]>(`${root(tenantId)}/id-cards/issuances`, { params: { institutionId } }).then(r => r.data),
  issueIdCard: (tenantId: string, body: object) =>
    api.post<IdCard>(`${root(tenantId)}/id-cards/issuances`, body).then(r => r.data),
  requestReplacement: (tenantId: string, issuanceId: string, body: object) =>
    api.post<IdCardReplacement>(`${root(tenantId)}/id-cards/issuances/${issuanceId}/replacements`, body).then(r => r.data),
  replacementRequests: (tenantId: string, institutionId: string) =>
    api.get<IdCardReplacement[]>(`${root(tenantId)}/id-cards/replacements`, { params: { institutionId } }).then(r => r.data),
  decideReplacement: (tenantId: string, replacementId: string, body: object) =>
    api.post<IdCardReplacement>(`${root(tenantId)}/id-cards/replacements/${replacementId}/decision`, body).then(r => r.data),
  revokeIdCard: (tenantId: string, issuanceId: string, reason: string) =>
    api.post(`${root(tenantId)}/id-cards/issuances/${issuanceId}/revoke`, { reason }),
  idCardSvg: (tenantId: string, issuanceId: string) =>
    api.get<string>(`${root(tenantId)}/id-cards/issuances/${issuanceId}/print.svg`).then(r => r.data),
  idCardsPdf: (tenantId: string, institutionId: string, issuanceIds: string[]) =>
    api.post<Blob>(`${root(tenantId)}/id-cards/print/pdf`, { institutionId, issuanceIds }, { responseType: "blob" }).then(r => r.data),
  certificateTemplates: (tenantId: string, institutionId: string) =>
    api.get<CertificateTemplate[]>(`${root(tenantId)}/certificates/templates`, { params: { institutionId } }).then(r => r.data),
  createCertificateTemplate: (tenantId: string, institutionId: string, body: object) =>
    api.post<CertificateTemplate>(`${root(tenantId)}/certificates/templates`, body, { params: { institutionId } }).then(r => r.data),
  certificateRequests: (tenantId: string, institutionId: string) =>
    api.get<CertificateRequest[]>(`${root(tenantId)}/certificates/requests`, { params: { institutionId } }).then(r => r.data),
  requestCertificate: (tenantId: string, body: object) =>
    api.post<CertificateRequest>(`${root(tenantId)}/certificates/requests`, body).then(r => r.data),
  approveCertificate: (tenantId: string, requestId: string) =>
    api.post<CertificateRequest>(`${root(tenantId)}/certificates/requests/${requestId}/approve`).then(r => r.data),
  prepareCertificate: (tenantId: string, requestId: string) =>
    api.post<CertificatePrepared>(`${root(tenantId)}/certificates/requests/${requestId}/prepare`).then(r => r.data),
  overrideNoDues: (tenantId: string, requestId: string, reason: string) =>
    api.post(`${root(tenantId)}/certificates/requests/${requestId}/no-dues-override`, { reason }),
  certificateIssuances: (tenantId: string, institutionId: string) =>
    api.get<CertificateIssuance[]>(`${root(tenantId)}/certificates/issuances`, { params: { institutionId } }).then(r => r.data),
  revokeCertificate: (tenantId: string, issuanceId: string, reason: string) =>
    api.post(`${root(tenantId)}/certificates/issuances/${issuanceId}/revoke`, { reason }),
  printCertificate: (tenantId: string, issuanceId: string) =>
    api.get<string>(`${root(tenantId)}/certificates/issuances/${issuanceId}/print`).then(r => r.data),
  verifyCertificate: (code: string) =>
    api.get<CertificateVerification>(`/public/certificates/verify/${code}`).then(r => r.data),
};
