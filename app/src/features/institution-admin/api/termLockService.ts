import api from "../../../services/axios";

export type TermLockItem = {
  termLockId: string;
  tenantId: string;
  institutionId: string;
  academicSessionId: string;
  semesterId?: string;
  lockState: "LOCKED" | "TEMP_UNLOCKED";
  unlockUntil?: string;
  createdAt: string;
  updatedAt: string;
  rowVersion: number;
};

export type LockTermRequest = {
  academicSessionId: string;
  semesterId?: string;
  notes?: string;
};

export type UnlockTermRequest = {
  reason: string;
  unlockDurationHours?: number;
};

export const termLockService = {
  async listTermLocks(tenantId: string, institutionId: string): Promise<TermLockItem[]> {
    const res = await api.get<TermLockItem[]>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/term-locks`
    );
    return res.data;
  },

  async lockTerm(
    tenantId: string,
    institutionId: string,
    request: LockTermRequest
  ): Promise<TermLockItem> {
    const res = await api.post<TermLockItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/term-locks`,
      request
    );
    return res.data;
  },

  async unlockTerm(
    tenantId: string,
    institutionId: string,
    termLockId: string,
    request: UnlockTermRequest
  ): Promise<TermLockItem> {
    const res = await api.post<TermLockItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/term-locks/${termLockId}/unlocks`,
      request
    );
    return res.data;
  },
};
