import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./slices/authSlice";

// The single app-wide Redux store. Right now it only holds login/account info
// (see slices/authSlice.ts); more slices would get added to this reducer object
// as the app grows.
export const store = configureStore({
  reducer: {
    auth: authReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
