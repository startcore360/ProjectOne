import { Suspense } from "react";
import AppRoutes from "./routes/Routes";
import { RouteTitleListener } from "./routes/RouteTitleListener";
import ToastHost from "./design-system/components/ToastHost";
import { AppLoader } from "./design-system/components";

// The top of the whole app. Each layout (AuthLayout for signed-out pages,
// AppLayout for the signed-in shell) mounts its own ThemeToggle, since the
// right placement differs between them - no toggle lives here directly.
//
// This Suspense is the outermost one in the tree: it's the fallback shown while AppLayout
// itself (lazy-loaded, routes/Routes.tsx) is still being fetched, before any of Routes.tsx's
// own nested Suspense boundaries (LazyRouteView, RootEntry) are even reached. It must use the
// same lighter branded AppLoader as those inner ones - a stale separate "LoadingScreen"
// component used to sit here with the old card+spinner design, so a refresh would flash
// through three different loading screens in sequence instead of one consistent one.
export default function App() {
  return (
    <>
      <Suspense fallback={<AppLoader label="Loading EDU 360" />}>
        <RouteTitleListener />
        <AppRoutes />
      </Suspense>
      <ToastHost position="top" popup />
    </>
  );
}
