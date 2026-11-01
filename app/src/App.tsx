import { Suspense } from "react";
import { ThemeToggle } from "./components/ui";
import AppRoutes from "./routes/Routes";

// The top of the whole app. It shows all the pages (via AppRoutes) and always
// shows the light/dark theme button on every screen, so it never has to be
// added page by page.
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
      <ThemeToggle />
    </>
  );
}
