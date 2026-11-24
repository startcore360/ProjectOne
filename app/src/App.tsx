import { Suspense } from "react";
import AppRoutes from "./routes/Routes";
import ToastHost from "./design-system/components/ToastHost";
import { LoadingScreen } from "./components/ui/LoadingScreen";

// The top of the whole app. Each layout (AuthLayout for signed-out pages,
// AppLayout for the signed-in shell) mounts its own ThemeToggle, since the
// right placement differs between them - no toggle lives here directly.
export default function App() {
  return (
    <>
      <Suspense fallback={<LoadingScreen />}>
        <AppRoutes />
      </Suspense>
      <ToastHost position="top" popup />
    </>
  );
}
