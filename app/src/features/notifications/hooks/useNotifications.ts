import { useCallback, useEffect, useSyncExternalStore } from "react";
import { notificationsApi, type BellNotification } from "../api/notificationsApi";
import { LIVE_EVENT_NAME, type LiveEnvelope } from "../../live/liveSocket";
import { store, type RootState } from "../../../store";
import {
  clearNotifications,
  initialNotificationsState,
  markAllNotificationsReadLocally,
  markNotificationReadLocally,
  setNotificationItems,
  setNotificationsError,
  setNotificationsLoading,
  setPendingApprovalCount,
  setUnreadCount,
} from "../../../store/slices/notificationsSlice";

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

// Selectors for narrow consumption
export const selectNotifications = (state: RootState) =>
  state.notifications ?? initialNotificationsState;
export const selectPendingApprovalCount = (state: RootState) =>
  state.notifications?.pendingApprovalCount ?? 0;
export const selectUnreadCount = (state: RootState) =>
  state.notifications?.unreadCount ?? 0;
export const selectNotificationItems = (state: RootState) =>
  state.notifications?.items ?? [];

// Shared controller ensuring exactly ONE timer, ONE focus listener, ONE live listener, and deduplicated in-flight fetches
let activeTenantId: string | null = null;
let timerId: ReturnType<typeof setInterval> | null = null;
let subscriberCount = 0;
let refreshInFlight: Promise<void> | null = null;
let globalFocusListener: (() => void) | null = null;
let globalLiveListener: ((raw: Event) => void) | null = null;

export async function refreshNotifications(tenantId: string | null | undefined): Promise<void> {
  if (!tenantId) return;
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    store.dispatch(setNotificationsLoading(true));
    try {
      const [listResult, summaryResult] = await Promise.allSettled([
        notificationsApi.list(tenantId),
        notificationsApi.summary(tenantId),
      ]);
      if (listResult.status === "fulfilled") {
        store.dispatch(setNotificationItems(listResult.value));
      } else {
        store.dispatch(setNotificationsError("Could not load notifications."));
      }
      if (summaryResult.status === "fulfilled") {
        store.dispatch(setUnreadCount(summaryResult.value.unreadCount));
      }
      // Approval inbox is an independent source: its failure must not suppress notifications.
      try {
        const approvalCount = await notificationsApi.pendingApprovalCount(tenantId);
        if (Number.isFinite(approvalCount) && approvalCount >= 0) {
          store.dispatch(setPendingApprovalCount(approvalCount));
        }
      } catch {
        // Keep the last known approval count; the next poll/focus refresh retries it.
      }
    } catch {
      store.dispatch(setNotificationsError("Could not load notifications."));
    } finally {
      store.dispatch(setNotificationsLoading(false));
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

function setupSharedController(tenantId: string) {
  if (timerId !== null) {
    clearInterval(timerId);
    timerId = null;
  }
  if (globalFocusListener) {
    window.removeEventListener("focus", globalFocusListener);
    globalFocusListener = null;
  }
  if (globalLiveListener) {
    window.removeEventListener(LIVE_EVENT_NAME, globalLiveListener);
    globalLiveListener = null;
  }

  activeTenantId = tenantId;
  void refreshNotifications(tenantId);

  timerId = setInterval(() => {
    void refreshNotifications(tenantId);
  }, NOTIFICATION_REFRESH_MS);

  globalFocusListener = () => {
    void refreshNotifications(tenantId);
  };
  window.addEventListener("focus", globalFocusListener);

  globalLiveListener = (raw: Event) => {
    const event = (raw as CustomEvent<LiveEnvelope>).detail;
    if (!event) return;
    if (event.type === "NOTIFICATION_CREATED") {
      void refreshNotifications(tenantId);
    } else if (event.type === "APPROVAL_COUNT") {
      const value = event.payload?.pendingCount;
      if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
        store.dispatch(setPendingApprovalCount(value));
      }
    } else if (event.type === "UNREAD_COUNT") {
      const value = event.payload?.unreadCount;
      if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
        store.dispatch(setUnreadCount(value));
      }
    }
  };
  window.addEventListener(LIVE_EVENT_NAME, globalLiveListener);
}

function cleanupSharedController() {
  if (timerId !== null) {
    clearInterval(timerId);
    timerId = null;
  }
  if (globalFocusListener) {
    window.removeEventListener("focus", globalFocusListener);
    globalFocusListener = null;
  }
  if (globalLiveListener) {
    window.removeEventListener(LIVE_EVENT_NAME, globalLiveListener);
    globalLiveListener = null;
  }
  activeTenantId = null;
  refreshInFlight = null;
  store.dispatch(clearNotifications());
}

export function useNotifications(tenantId: string | null | undefined): NotificationsState {
  const state = useSyncExternalStore(
    store.subscribe,
    () => store.getState().notifications ?? initialNotificationsState,
    () => initialNotificationsState,
  );

  useEffect(() => {
    if (!tenantId) return undefined;

    subscriberCount++;
    if (activeTenantId !== tenantId) {
      setupSharedController(tenantId);
    }

    return () => {
      subscriberCount--;
      if (subscriberCount <= 0) {
        subscriberCount = 0;
        cleanupSharedController();
      }
    };
  }, [tenantId]);

  const refresh = useCallback(async () => {
    if (!tenantId) return;
    await refreshNotifications(tenantId);
  }, [tenantId]);

  const markRead = useCallback(
    async (notificationId: string) => {
      if (!tenantId) return;
      const now = new Date().toISOString();
      store.dispatch(markNotificationReadLocally({ notificationId, readAt: now }));
      try {
        await notificationsApi.markRead(tenantId, notificationId);
      } catch {
        store.dispatch(setNotificationsError("Could not mark the notification as read."));
        await refreshNotifications(tenantId);
      }
    },
    [tenantId],
  );

  const markAllRead = useCallback(async () => {
    if (!tenantId) return;
    const now = new Date().toISOString();
    store.dispatch(markAllNotificationsReadLocally({ readAt: now }));
    try {
      await notificationsApi.markAllRead(tenantId);
    } catch {
      store.dispatch(setNotificationsError("Could not mark all notifications as read."));
      await refreshNotifications(tenantId);
    }
  }, [tenantId]);

  return {
    items: state.items,
    loading: state.loading,
    error: state.error,
    hasReadState: state.hasReadState,
    unreadCount: state.unreadCount,
    pendingApprovalCount: state.pendingApprovalCount,
    refresh,
    markRead,
    markAllRead,
  };
}
