import { useCallback, useEffect, useState } from "react";
import { notificationsApi, type BellNotification } from "../api/notificationsApi";
import { LIVE_EVENT_NAME, type LiveEnvelope } from "../../live/liveSocket";

/** D1 NFR: in-app notifications arrive within 1 minute, so the bell refreshes at least that often. */
export const NOTIFICATION_REFRESH_MS = 60_000;

export type NotificationsState = {
  items: BellNotification[];
  loading: boolean;
  error: string | null;
  /** True once the server sends a read state; false means "no unread count yet". */
  hasReadState: boolean;
  unreadCount: number;
  pendingApprovalCount: number;
  refresh: () => Promise<void>;
  markRead: (notificationId: string) => Promise<void>;
  markAllRead: () => Promise<void>;
};

export function useNotifications(tenantId: string | null | undefined): NotificationsState {
  const [items, setItems] = useState<BellNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [pendingApprovalCount, setPendingApprovalCount] = useState(0);
  const [hasReadState, setHasReadState] = useState(false);

  const refresh = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    try {
      const [rows, summary] = await Promise.all([notificationsApi.list(tenantId), notificationsApi.summary(tenantId)]);
      setItems(rows);
      setUnreadCount(summary.unreadCount);
      // Approval inbox is an independent source: its failure must not suppress notifications.
      try {
        const approvalCount = await notificationsApi.pendingApprovalCount(tenantId);
        if (Number.isFinite(approvalCount) && approvalCount >= 0) setPendingApprovalCount(approvalCount);
      } catch {
        // Keep the last known approval count; the next poll/focus refresh retries it.
      }
      setHasReadState(true);
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


  useEffect(() => {
    if (!tenantId) return undefined;
    const onLiveEvent = (raw: Event) => {
      const event = (raw as CustomEvent<LiveEnvelope>).detail;
      if (!event) return;
      if (event.type === "NOTIFICATION_CREATED") {
        void refresh();
      } else if (event.type === "APPROVAL_COUNT") {
        const value = event.payload?.pendingCount;
        if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
          setPendingApprovalCount(value);
        }
      } else if (event.type === "UNREAD_COUNT") {
        const value = event.payload?.unreadCount;
        if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
          setUnreadCount(value);
          setHasReadState(true);
        }
      }
    };
    window.addEventListener(LIVE_EVENT_NAME, onLiveEvent);
    return () => window.removeEventListener(LIVE_EVENT_NAME, onLiveEvent);
  }, [tenantId, refresh]);

  const markRead = useCallback(async (notificationId: string) => {
    if (!tenantId) return;
    const now = new Date().toISOString();
    // Optimistic update, rolled back if the server refuses.
    setItems((prev) => prev.map((n) => (n.notificationId === notificationId ? { ...n, readAt: n.readAt ?? now } : n)));
    setUnreadCount((count) => Math.max(0, count - (items.some((n) => n.notificationId === notificationId && (n.readAt === null || n.readAt === undefined)) ? 1 : 0)));
    try {
      await notificationsApi.markRead(tenantId, notificationId);
    } catch {
      setError("Could not mark the notification as read.");
      await refresh();
    }
  }, [tenantId, refresh, items]);

  const markAllRead = useCallback(async () => {
    if (!tenantId) return;
    const now = new Date().toISOString();
    setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? now })));
    setUnreadCount(0);
    setHasReadState(true);
    try {
      await notificationsApi.markAllRead(tenantId);
    } catch {
      setError("Could not mark all notifications as read.");
      await refresh();
    }
  }, [tenantId, refresh]);

  return { items, loading, error, hasReadState, unreadCount, pendingApprovalCount, refresh, markRead, markAllRead };
}
