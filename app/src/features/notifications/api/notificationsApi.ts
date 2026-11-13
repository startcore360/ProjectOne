import api from "../../../services/axios";

export interface BellNotification {
  notificationId: string;
  triggerCode: string;
  title: string;
  body: string | null;
  priorityClass: string;
  createdAt: string;
  entityType?: string | null;
  entityId?: string | null;
  readAt?: string | null;
}

export interface NotificationSummary {
  unreadCount: number;
}

export const notificationsApi = {
  list: async (tenantId: string): Promise<BellNotification[]> => {
    const res = await api.get<BellNotification[]>(`/tenants/${tenantId}/me/notifications?limit=50`);
    return Array.isArray(res.data) ? res.data : [];
  },
  summary: async (tenantId: string): Promise<NotificationSummary> => {
    const res = await api.get<NotificationSummary>(`/tenants/${tenantId}/me/notifications/summary`);
    return res.data;
  },
  pendingApprovalCount: async (tenantId: string): Promise<number> => {
    const res = await api.get<{ items?: unknown[] }>(`/api/tenants/${tenantId}/approvals/inbox`);
    return Array.isArray(res.data?.items) ? res.data.items.length : 0;
  },
  markRead: async (tenantId: string, notificationId: string): Promise<void> => {
    await api.post(`/tenants/${tenantId}/me/notifications/${notificationId}/read`);
  },
  markAllRead: async (tenantId: string): Promise<void> => {
    await api.post(`/tenants/${tenantId}/me/notifications/read-all`);
  },
};
