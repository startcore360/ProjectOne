import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./slices/authSlice";
import platformPermissionsReducer from "./slices/platformPermissionsSlice";

// The single app-wide Redux store: login/account info (slices/authSlice.ts) and what a
// platform user can do (slices/platformPermissionsSlice.ts).
export const store = configureStore({
  reducer: {
    auth: authReducer,
    platformPermissions: platformPermissionsReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
