import { Suspense } from "react";
import AppRoutes from "./routes/Routes";

export default function App() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center" role="status">
          Loading...
        </div>
      }
    >
      <AppRoutes />
    </Suspense>
  );
}
