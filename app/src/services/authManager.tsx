import { store } from "../store";
import { clearUser, setUser } from "../store/slices/authSlice";
import { authService } from "./modules/authService";
export const login = async (email:string,password:string) => {
  const {data}=await authService.login(email,password);
  const user={id:data.id,name:data.name,email:data.email,role:data.role};
  store.dispatch(setUser(user)); return user;
};
export const logout = async () => {
  try { await authService.logout(); }
  finally { store.dispatch(clearUser()); window.location.replace("/login"); }
};
export const hydrateAuth = async () => {
  try {
    const {data}=await authService.me();
    const user={id:data.id,name:data.name,email:data.email,role:data.role};
    store.dispatch(setUser(user)); return user;
  } catch { store.dispatch(clearUser()); return null; }
};
