import { store } from "../store";
import { clearUser, setLoading, setUser } from "../store/slices/authSlice";
import { authService } from "./modules/authService";
export const bootstrapAuth = async () => {
  store.dispatch(setLoading(true));
  try {
    const {data}=await authService.me();
    const user={id:data.id,name:data.name,email:data.email,role:data.role};
    store.dispatch(setUser(user)); return user;
  } catch { store.dispatch(clearUser()); return null; }
  finally { store.dispatch(setLoading(false)); }
};
