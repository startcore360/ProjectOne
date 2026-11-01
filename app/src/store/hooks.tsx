import { useDispatch, useSelector, type TypedUseSelectorHook } from "react-redux";
import type { AppDispatch, RootState } from "./index";

// These two are used instead of plain Redux useDispatch/useSelector so that
// TypeScript already knows the exact shape of our store's state and actions.
export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
