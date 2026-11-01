import api from "./axios";

export const setupAxiosInterceptors = () => {
  api.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error?.response?.status === 401) {
        window.location.replace("/login");
      }

      return Promise.reject(error);
    },
  );
};
