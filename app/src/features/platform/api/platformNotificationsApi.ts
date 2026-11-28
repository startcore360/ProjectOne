import api from "../../../services/axios";

export type PlatformBellNotification = {
  notificationId: string;
  triggerCode: string;
  title: string;
  body: string | null;
  entityType?: string | null;
  entityId?: string | null;
  priorityClass: string;
  readAt: string | null;
  createdAt: string;
};

export const platformNotificationsApi = {
  async list(): Promise<PlatformBellNotification[]> {
    const response = await api.get<PlatformBellNotification[]>("/platform/me/notifications");
    return Array.isArray(response.data) ? response.data : [];
  },
  async markRead(notificationId: string): Promise<void> {
    await api.post(`/platform/me/notifications/${notificationId}/read`);
  },
  async markAllRead(): Promise<void> {
    await api.post("/platform/me/notifications/read-all");
  },
};
