import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./slices/authSlice";
import platformPermissionsReducer from "./slices/platformPermissionsSlice";
import tenantPermissionsReducer from "./slices/tenantPermissionsSlice";

export const store = configureStore({
  reducer: {
    auth: authReducer,
    platformPermissions: platformPermissionsReducer,
    tenantPermissions: tenantPermissionsReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
