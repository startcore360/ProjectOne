import api from "../../../services/axios";

export interface RolldownMapEntry {
  missingRoleCode: string;
  context: string;
  permissionKind: string;
  preference: number;
  standInRoleCode: string;
  note: string | null;
  hasOverride: boolean;
  overrideMode: string | null;
  overrideTargetRoleCode: string | null;
}

export interface RolldownOverride {
  roleRolldownOverrideId: string;
  institutionId: string | null;
  missingRoleCode: string;
  permissionKind: string;
  actionCode: string | null;
  mode: string;
  targetRoleCode: string | null;
  reason: string;
  setByUserId: string;
  setByRole: string;
  status: string;
  effectiveFrom: string;
  createdAt: string;
}

export interface CreateRolldownOverrideRequest {
  institutionId?: string;
  missingRoleCode: string;
  permissionKind: string;
  actionCode?: string;
  mode: string;
  targetRoleCode?: string;
  reason: string;
}

export const rolldownService = {
  getRolldownMap: async (tenantId: string, institutionId?: string, context?: string): Promise<RolldownMapEntry[]> => {
    const params = new URLSearchParams();
    if (institutionId) params.append("institutionId", institutionId);
    if (context) params.append("context", context);
    const res = await api.get<RolldownMapEntry[]>(`/v1/tenants/${tenantId}/rolldown/map?${params.toString()}`);
    return res.data;
  },
  getOverrides: async (tenantId: string, institutionId?: string): Promise<RolldownOverride[]> => {
    const params = new URLSearchParams();
    if (institutionId) params.append("institutionId", institutionId);
    const res = await api.get<RolldownOverride[]>(`/v1/tenants/${tenantId}/rolldown/overrides?${params.toString()}`);
    return res.data;
  },
  createOverride: async (
    tenantId: string,
    activeRole: string,
    req: CreateRolldownOverrideRequest
  ): Promise<RolldownOverride> => {
    const res = await api.post<RolldownOverride>(`/v1/tenants/${tenantId}/rolldown/overrides`, req, {
      headers: { "X-Active-Role": activeRole },
    });
    return res.data;
  },
  revokeOverride: async (tenantId: string, overrideId: string): Promise<RolldownOverride> => {
    const res = await api.post<RolldownOverride>(`/v1/tenants/${tenantId}/rolldown/overrides/${overrideId}/revocation`);
    return res.data;
  },
};
