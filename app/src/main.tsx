import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { initTheme } from "./lib/theme";
import { bootstrapAuth } from "./services/bootstrapAuth";
import { setupAxiosInterceptors } from "./services/axiosInterceptor";
import { getAccessToken } from "./services/tokenStorage";
import { clearUser } from "./store/slices/authSlice";
import { setPlatformPermissions } from "./store/slices/platformPermissionsSlice";
import { clearTenantPermissions } from "./store/slices/tenantPermissionsSlice";
import { store } from "./store";
import "./main.css";

initTheme();

// Install the response interceptor before bootstrapAuth makes its first API call.
// Without this, expired access tokens cannot use the refresh flow during the session.
setupAxiosInterceptors();

const rootElement = document.getElementById("root")!;
const renderApp = () => {
  createRoot(rootElement).render(
    <StrictMode>
      <Provider store={store}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </Provider>
    </StrictMode>,
  );
};

if (!getAccessToken()) {
  store.dispatch(clearUser());
  store.dispatch(setPlatformPermissions(null));
  store.dispatch(clearTenantPermissions());
  renderApp();
} else {
  void bootstrapAuth().then(renderApp);
}
