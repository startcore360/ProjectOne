import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { BellNotification } from "../../features/notifications/api/notificationsApi";

export interface NotificationsState {
  items: BellNotification[];
  loading: boolean;
  error: string | null;
  hasReadState: boolean;
  unreadCount: number;
  pendingApprovalCount: number;
}

export const initialNotificationsState: NotificationsState = {
  items: [],
  loading: false,
  error: null,
  hasReadState: false,
  unreadCount: 0,
  pendingApprovalCount: 0,
};

const slice = createSlice({
  name: "notifications",
  initialState: initialNotificationsState,
  reducers: {
    setNotificationsLoading(state, action: PayloadAction<boolean>) {
      state.loading = action.payload;
    },
    setNotificationsError(state, action: PayloadAction<string | null>) {
      state.error = action.payload;
    },
    setNotificationItems(state, action: PayloadAction<BellNotification[]>) {
      state.items = action.payload;
      state.error = null;
    },
    setUnreadCount(state, action: PayloadAction<number>) {
      state.unreadCount = action.payload;
      state.hasReadState = true;
    },
    setPendingApprovalCount(state, action: PayloadAction<number>) {
      state.pendingApprovalCount = action.payload;
    },
    markNotificationReadLocally(
      state,
      action: PayloadAction<{ notificationId: string; readAt: string }>,
    ) {
      const { notificationId, readAt } = action.payload;
      const target = state.items.find((n) => n.notificationId === notificationId);
      if (target && (target.readAt === null || target.readAt === undefined)) {
        state.unreadCount = Math.max(0, state.unreadCount - 1);
      }
      state.items = state.items.map((n) =>
        n.notificationId === notificationId ? { ...n, readAt: n.readAt ?? readAt } : n,
      );
    },
    markAllNotificationsReadLocally(state, action: PayloadAction<{ readAt: string }>) {
      const { readAt } = action.payload;
      state.items = state.items.map((n) => ({ ...n, readAt: n.readAt ?? readAt }));
      state.unreadCount = 0;
      state.hasReadState = true;
    },
    clearNotifications() {
      return initialNotificationsState;
    },
  },
});

export const {
  setNotificationsLoading,
  setNotificationsError,
  setNotificationItems,
  setUnreadCount,
  setPendingApprovalCount,
  markNotificationReadLocally,
  markAllNotificationsReadLocally,
  clearNotifications,
} = slice.actions;

export default slice.reducer;
