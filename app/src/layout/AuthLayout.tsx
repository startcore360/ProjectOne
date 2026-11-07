import { Outlet } from "react-router-dom";
import { ThemeToggle } from "../components/ui";
// The page frame used for pages a signed-out user can see, like the login
// page. Much simpler than AppLayout — no sidebar or user menu, since there's
// no signed-in user to show one for. Floating theme toggle is fine here:
// there's no bottom nav on these pages for it to collide with.
export default function AuthLayout() {
  return (
    <div className="auth-layout">
      <Outlet />
      <ThemeToggle />
    </div>
  );
}
