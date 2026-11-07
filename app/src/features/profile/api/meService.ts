import api from "../../../services/axios";
import type { ScopeType, UserType } from "../../user-management/api/userManagementService";

export type UserRoleOption = {
  userRoleAssignmentId: string;
  roleDefinitionId: string;
  roleCode: string;
  roleName: string;
  scopeType: ScopeType;
  institutionId?: string;
  institutionName?: string;
  departmentId?: string;
  departmentName?: string;
  currentActive: boolean;
};

export type UserMeResponse = {
  userAccountId: string;
  tenantId: string;
  email: string;
  displayName: string;
  userType: UserType;
  currentRoleCode?: string;
  currentRoleName?: string;
  currentAssignmentId?: string;
  roles: UserRoleOption[];
};

export type SwitchActiveRoleResponse = {
  assignmentId: string;
  roleCode: string;
  roleName: string;
  scopeType: ScopeType;
  institutionId?: string;
  departmentId?: string;
  message: string;
};

export const meService = {
  getMe: () => api.get<UserMeResponse>("/me"),
  switchActiveRole: (assignmentId: string) =>
    api.post<SwitchActiveRoleResponse>("/me/active-role", { assignmentId }),
};
