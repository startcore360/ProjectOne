import api from "../../../services/axios";
import type { PlatformRole } from "./platformUserService";

export type AccessMode = "SHARED" | "HANDED_OVER";
export type CustomRoleStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";

export type RoleActionItem = { actionCode: string; accessMode: AccessMode };

export type PlatformRoleItem = {
  platformRoleId: string;
  roleName: string;
  basePlatformRole: PlatformRole;
  sourceTemplateId: string | null;
  status: CustomRoleStatus;
  actions: RoleActionItem[];
  holders: number;
};

export type RoleTemplateItem = {
  platformRoleTemplateId: string;
  templateName: string;
  description: string | null;
  basePlatformRole: PlatformRole;
  standard: boolean;
  locked: boolean;
  status: "ACTIVE" | "ARCHIVED";
  actionCodes: string[];
};

export type ActionCatalogItem = {
  actionCode: string;
  description: string;
  moduleName: string;
  sensitive: boolean;
  lockedReason: string | null;
};

export type StepInItem = {
  platformStepInId: string;
  actorUserId: string;
  actionCode: string;
  reason: string;
  startedAt: string;
  expiresAt: string;
};

export type CreateRolePayload = {
  roleName: string;
  basePlatformRole: PlatformRole;
  sourceTemplateId: string | null;
  actions: RoleActionItem[];
  updateTemplate: boolean;
};

export type SaveTemplatePayload = {
  templateName: string;
  description: string | null;
  basePlatformRole: PlatformRole;
  actionCodes: string[];
};

export const platformRoleService = {
  getActionCatalog: () => api.get<ActionCatalogItem[]>("/platform/role-actions"),
  getRoles: () => api.get<PlatformRoleItem[]>("/platform/roles"),
  createRole: (data: CreateRolePayload) => api.post<PlatformRoleItem>("/platform/roles", data),
  updateRole: (id: string, data: { roleName: string; actions: RoleActionItem[] }) =>
    api.put<PlatformRoleItem>(`/platform/roles/${id}`, data),
  activateRole: (id: string) => api.post<PlatformRoleItem>(`/platform/roles/${id}/activation`),
  deactivateRole: (id: string) => api.post<PlatformRoleItem>(`/platform/roles/${id}/deactivation`),
  archiveRole: (id: string) => api.post<PlatformRoleItem>(`/platform/roles/${id}/archive`),
  takeBack: (id: string, actionCode: string) =>
    api.post<PlatformRoleItem>(`/platform/roles/${id}/actions/${actionCode}/take-back`),
  getTemplates: () => api.get<RoleTemplateItem[]>("/platform/role-templates"),
  createTemplate: (data: SaveTemplatePayload) => api.post<RoleTemplateItem>("/platform/role-templates", data),
  updateTemplate: (id: string, data: SaveTemplatePayload) =>
    api.put<RoleTemplateItem>(`/platform/role-templates/${id}`, data),
  resetTemplate: (id: string) => api.post<RoleTemplateItem>(`/platform/role-templates/${id}/reset`),
  archiveTemplate: (id: string) => api.post<RoleTemplateItem>(`/platform/role-templates/${id}/archive`),
  getStepIns: () => api.get<StepInItem[]>("/platform/step-ins"),
  stepIn: (actionCode: string, reason: string) =>
    api.post<StepInItem>("/platform/step-ins", { actionCode, reason }),
};

// Mirrors PlatformActionPolicy.baseLimit, so a forbidden choice is locked before it's submitted.
export function baseLimitReason(actionCode: string, base: PlatformRole): string | null {
  if (actionCode === "DISCOUNT_APPLY" && base === "BILLING_MANAGER") {
    return "A Billing Manager can't give discounts.";
  }
  if (actionCode === "SUPPORT_ACCESS_USE" && base !== "SUPPORT_MANAGER") {
    return "Only support roles can use tenant-granted access.";
  }
  return null;
}

export const CUSTOMISABLE_ROLES: PlatformRole[] = [
  "PLATFORM_ADMIN",
  "BILLING_MANAGER",
  "SUPPORT_MANAGER",
  "RELEASE_MANAGER",
];

export const ROLE_STATUS_LABELS: Record<CustomRoleStatus, string> = {
  DRAFT: "Draft",
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  ARCHIVED: "Archived",
};
