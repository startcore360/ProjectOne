import { Outlet } from "react-router-dom";
// The page frame used for pages a signed-out user can see, like the login
// page. Much simpler than AppLayout — no sidebar or user menu, since there's
// no signed-in user to show one for.
export default function AuthLayout() {
  return (
    <div className="auth-layout">
      <Outlet />
    </div>
  );
}
