import api from "./axios";
import { store } from "../store";
import { clearUser } from "../store/slices/authSlice";
export const setupAxiosInterceptors = () => {
  api.interceptors.response.use(
    response=>response,
    error=>{
      if(error?.response?.status===401){
        store.dispatch(clearUser());
        if(window.location.pathname!=="/login") window.location.replace("/login");
      }
      return Promise.reject(error);
    },
  );
};
