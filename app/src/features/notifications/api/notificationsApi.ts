import api from "../../../services/axios";

/**
 * One notification as the bell shows it.
 *
 * BACKEND CONTRACT (the only file to change when the backend lands):
 *  - GET  /tenants/{tenantId}/me/notifications            -> NotificationItem[]  (exists today)
 *  - POST /tenants/{tenantId}/me/notifications/{id}/read  -> 204               (to be provided)
 *  - POST /tenants/{tenantId}/me/notifications/read-all   -> 204               (to be provided)
 *
 * `readAt` is the read state (OD-32 is still an open decision). The backend does not send it
 * yet. While it is absent the bell shows the list but no unread count and no "mark read"
 * controls, so it never shows a number the server cannot back up.
 */
export interface BellNotification {
  notificationId: string;
  triggerCode: string;
  title: string;
  body: string | null;
  priorityClass: string;
  createdAt: string;
  entityType?: string | null;
  entityId?: string | null;
  /** null = unread, a timestamp = read, undefined = the server has no read state yet. */
  readAt?: string | null;
}

export const notificationsApi = {
  list: async (tenantId: string): Promise<BellNotification[]> => {
    const res = await api.get<BellNotification[]>(`/tenants/${tenantId}/me/notifications`);
    return Array.isArray(res.data) ? res.data : [];
  },
  markRead: async (tenantId: string, notificationId: string): Promise<void> => {
    await api.post(`/tenants/${tenantId}/me/notifications/${notificationId}/read`);
  },
  markAllRead: async (tenantId: string): Promise<void> => {
    await api.post(`/tenants/${tenantId}/me/notifications/read-all`);
  },
};
