import { Suspense } from "react";
import AppRoutes from "./routes/Routes";
import ToastHost from "./design-system/components/ToastHost";

// The top of the whole app. Each layout (AuthLayout for signed-out pages,
// AppLayout for the signed-in shell) mounts its own ThemeToggle, since the
// right placement differs between them - no toggle lives here directly.
export default function App() {
  return (
    <>
      <Suspense
        fallback={
          <div className="flex min-h-screen items-center justify-center" role="status">
            Loading...
          </div>
        }
      >
        <AppRoutes />
      </Suspense>
      <ToastHost />
    </>
  );
}
