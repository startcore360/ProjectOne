import { useCallback, useEffect, useMemo, useState } from "react";
import { notificationsApi, type BellNotification } from "../api/notificationsApi";

/** D1 NFR: in-app notifications arrive within 1 minute, so the bell refreshes at least that often. */
export const NOTIFICATION_REFRESH_MS = 60_000;

export type NotificationsState = {
  items: BellNotification[];
  loading: boolean;
  error: string | null;
  /** True once the server sends a read state; false means "no unread count yet". */
  hasReadState: boolean;
  unreadCount: number;
  refresh: () => Promise<void>;
  markRead: (notificationId: string) => Promise<void>;
  markAllRead: () => Promise<void>;
};

export function useNotifications(tenantId: string | null | undefined): NotificationsState {
  const [items, setItems] = useState<BellNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    try {
      setItems(await notificationsApi.list(tenantId));
      setError(null);
    } catch {
      setError("Could not load notifications.");
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (!tenantId) return undefined;
    // Initial load plus a 1-minute refresh and a refresh when the tab regains focus.
    void refresh();
    const timer = window.setInterval(() => { void refresh(); }, NOTIFICATION_REFRESH_MS);
    const onFocus = () => { void refresh(); };
    window.addEventListener("focus", onFocus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", onFocus);
    };
  }, [tenantId, refresh]);

  const hasReadState = useMemo(() => items.some((n) => n.readAt !== undefined), [items]);
  const unreadCount = useMemo(
    () => (hasReadState ? items.filter((n) => n.readAt === null || n.readAt === undefined).length : 0),
    [items, hasReadState],
  );

  const markRead = useCallback(async (notificationId: string) => {
    if (!tenantId) return;
    const now = new Date().toISOString();
    // Optimistic update, rolled back if the server refuses.
    setItems((prev) => prev.map((n) => (n.notificationId === notificationId ? { ...n, readAt: n.readAt ?? now } : n)));
    try {
      await notificationsApi.markRead(tenantId, notificationId);
    } catch {
      setError("Could not mark the notification as read.");
      await refresh();
    }
  }, [tenantId, refresh]);

  const markAllRead = useCallback(async () => {
    if (!tenantId) return;
    const now = new Date().toISOString();
    setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? now })));
    try {
      await notificationsApi.markAllRead(tenantId);
    } catch {
      setError("Could not mark all notifications as read.");
      await refresh();
    }
  }, [tenantId, refresh]);

  return { items, loading, error, hasReadState, unreadCount, refresh, markRead, markAllRead };
}
