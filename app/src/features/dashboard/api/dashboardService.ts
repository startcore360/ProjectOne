import api from "../../../services/axios";

// Mirrors com.edu360.api.selfservice.DashboardSummaryController.DashboardSummary byte for byte.
export type DashboardSummary = {
  activeUsers: number;
  activeSections: number;
};

export const dashboardService = {
  getSummary: (tenantId: string) => api.get<DashboardSummary>(`/tenants/${tenantId}/dashboard/summary`),
};
