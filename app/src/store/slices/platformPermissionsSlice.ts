import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export type PlatformPermissions = {
  userId: string;
  actions: string[];
  customRoleName: string | null;
  stepInRequired: string[];
  steppedIn: string[];
};

// What the signed-in platform user can do, used to build menus. The stored userId keeps one
// person's permissions from being read for another after sign-out in the same tab.
const slice = createSlice({
  name: "platformPermissions",
  initialState: { current: null as PlatformPermissions | null },
  reducers: {
    setPlatformPermissions: (state, action: PayloadAction<PlatformPermissions | null>) => {
      state.current = action.payload;
    },
  },
});

export const { setPlatformPermissions } = slice.actions;
export default slice.reducer;
