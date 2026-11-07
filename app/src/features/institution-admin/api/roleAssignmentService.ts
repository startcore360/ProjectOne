import api from "../../../services/axios";

export type ScopeType = "TENANT" | "INSTITUTION" | "DEPARTMENT";
export type AssignmentStatus = "ACTIVE" | "ENDED";
export type RoleOrigin = "STANDARD" | "CUSTOM";
export type RoleLevel = "TENANT" | "COLLEGE" | "SCHOOL" | "DEPARTMENT" | "GOVERNANCE";
export type RoleDefinitionStatus = "ACTIVE" | "RETIRED";

export type RoleDefinitionItem = {
  roleDefinitionId: string;
  tenantId: string;
  roleCode: string;
  roleName: string;
  roleLevel: RoleLevel;
  roleOrigin: RoleOrigin;
  sensitiveRole: boolean;
  status: RoleDefinitionStatus;
};

export type UserRoleAssignmentItem = {
  userRoleAssignmentId: string;
  tenantId: string;
  userAccountId: string;
  roleDefinitionId: string;
  roleCode?: string;
  roleName?: string;
  scopeType: ScopeType;
  institutionId?: string;
  departmentId?: string;
  status: AssignmentStatus;
  effectiveFrom: string;
  effectiveTo?: string;
};

export type AssignRoleRequest = {
  roleDefinitionId?: string;
  roleCode?: string;
  scopeType: ScopeType;
  institutionId?: string;
  departmentId?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
};

export const roleAssignmentService = {
  async listRoles(tenantId: string): Promise<RoleDefinitionItem[]> {
    const res = await api.get<RoleDefinitionItem[]>(`/api/tenants/${tenantId}/roles`);
    return res.data;
  },

  async listUserRoleAssignments(tenantId: string, userId: string): Promise<UserRoleAssignmentItem[]> {
    const res = await api.get<UserRoleAssignmentItem[]>(
      `/api/tenants/${tenantId}/users/${userId}/role-assignments`
    );
    return res.data;
  },

  async assignRole(
    tenantId: string,
    userId: string,
    request: AssignRoleRequest
  ): Promise<UserRoleAssignmentItem> {
    const res = await api.post<UserRoleAssignmentItem>(
      `/api/tenants/${tenantId}/users/${userId}/role-assignments`,
      request
    );
    return res.data;
  },

  async endRoleAssignment(tenantId: string, assignmentId: string): Promise<UserRoleAssignmentItem> {
    const res = await api.post<UserRoleAssignmentItem>(
      `/api/tenants/${tenantId}/role-assignments/${assignmentId}/end`
    );
    return res.data;
  },

  async createCustomRole(
    tenantId: string,
    request: {
      roleCode: string;
      roleName: string;
      roleLevel: string;
      customScope: string;
      actionCodes?: string[];
    }
  ): Promise<RoleDefinitionItem> {
    const res = await api.post<RoleDefinitionItem>(`/api/tenants/${tenantId}/roles`, request);
    return res.data;
  },

  async retireCustomRole(tenantId: string, roleDefinitionId: string): Promise<RoleDefinitionItem> {
    const res = await api.post<RoleDefinitionItem>(`/api/tenants/${tenantId}/roles/${roleDefinitionId}/retirement`);
    return res.data;
  },
};
