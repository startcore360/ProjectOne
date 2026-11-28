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
import { isSharedLandingHost } from "./lib/tenantHost";
import "./main.css";

initTheme();

// Prevent Vite dev server from auto-reloading when mobile browsers background the tab
// (which drops the HMR WebSocket connection while the user switches to an Authenticator app)
if (import.meta.hot) {
  import.meta.hot.on("vite:ws:disconnect", () => {
    // Halts Vite's internal reconnect polling from calling location.reload()
    throw new Error("[Vite HMR] Disconnect reload suppressed to preserve mobile form state.");
  });
}

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
  // An unauthenticated visit on the shared landing host renders LandingPage (routes/Routes.tsx's
  // RootEntry) - its own lazy chunk otherwise only starts downloading once React gets there,
  // which is the one navigation nobody else prefetches (unlike the post-login dashboard chunk,
  // started during the sign-in request itself). Start it now, in parallel with renderApp(),
  // so the RootEntry Suspense fallback rarely has anything left to wait for by the time it runs.
  if (isSharedLandingHost(window.location.hostname)) {
    void import("./features/landing/LandingPage");
  }
  renderApp();
} else {
  void bootstrapAuth().then(renderApp);
}
