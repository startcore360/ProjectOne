import api from "../../../services/axios";

export type PlatformRole =
  | "PRODUCT_OWNER"
  | "PLATFORM_ADMIN"
  | "BILLING_MANAGER"
  | "SUPPORT_MANAGER"
  | "RELEASE_MANAGER"
  | "DATA_PROTECTION_OFFICER";

export type PlatformUserItem = {
  platformUserId: string;
  fullName: string;
  email: string;
  platformRole: PlatformRole;
  platformRoleId?: string | null;
  customRoleName?: string | null;
  status: "ACTIVE" | "INACTIVE";
  mfaRequired: boolean;
  lastLoginAt?: string;
  createdAt: string;
  tempPassword?: string;
};

// A standard role or a custom one, never both.
export type PlatformRoleChoice = { platformRole?: PlatformRole; platformRoleId?: string };

// Role pickers hold "std:<role>" or "custom:<id>" as one select value.
export function roleChoicePayload(choice: string): PlatformRoleChoice {
  return choice.startsWith("custom:")
    ? { platformRoleId: choice.slice("custom:".length) }
    : { platformRole: choice.slice("std:".length) as PlatformRole };
}

export type PlatformUserCreateRequest = PlatformRoleChoice & {
  fullName: string;
  email: string;
};

export type PlatformUserUpdateRequest = PlatformRoleChoice & {
  fullName?: string;
  status?: "ACTIVE" | "INACTIVE";
};

export const platformUserService = {
  getPlatformUsers: () => api.get<PlatformUserItem[]>("/platform/platform-users"),
  createPlatformUser: (data: PlatformUserCreateRequest) =>
    api.post<PlatformUserItem>("/platform/platform-users", data),
  updatePlatformUser: (id: string, data: PlatformUserUpdateRequest) =>
    api.patch<PlatformUserItem>(`/platform/platform-users/${id}`, data),
};
