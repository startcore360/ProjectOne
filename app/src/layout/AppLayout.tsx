import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Button } from "../design-system/components";
import { logout } from "../services/authManager";
import { useAppSelector } from "../store/hooks";
// The page frame shown once a user is signed in: sidebar, top bar with the
// user's name/role and a sign-out button, and the actual page content in
// the middle (via <Outlet />, which is where React Router plugs in whatever
// page matches the current URL).
export default function AppLayout() {
  const user = useAppSelector((s) => s.auth.user);
  const navigate = useNavigate();
  const name = user?.name?.trim() || "Account";
  const role = user?.role?.replaceAll("_", " ") || "Member";
  const initials =
    name
      .split(/\\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((x) => x[0]?.toUpperCase())
      .join("") || "A";
  // Always send the user back to /login, even if the sign-out request to the
  // server fails (e.g. they're offline) — being stuck looking signed-in is
  // worse than one failed network call.
  const signOut = async () => {
    try {
      await logout();
    } catch {
      navigate("/login", { replace: true });
    }
  };
  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <a className="app-brand" href="/dashboard">
          <span className="app-brand__mark">e</span>
          <span>
            EDU <strong>360</strong>
          </span>
        </a>
        <p className="app-sidebar__label">WORKSPACE</p>
        <nav className="app-nav" aria-label="Main navigation">
          <NavLink
            to="/dashboard"
            className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}
          >
            <span aria-hidden="true">▦</span> Overview
          </NavLink>
        </nav>
        <div className="app-sidebar__bottom">
          <span className="app-sidebar__dot" />
          Institution workspace
        </div>
      </aside>
      <div className="app-main">
        <header className="app-topbar">
          <div className="app-topbar__context">
            <span>EDU 360</span>
            <span>/</span>
            <strong>Workspace</strong>
          </div>
          <div className="app-user">
            <span className="app-user__avatar">{initials}</span>
            <span className="app-user__details">
              <strong>{name}</strong>
              <small>{role}</small>
            </span>
            <Button variant="secondary" className="app-logout" onClick={signOut}>
              Sign out
            </Button>
          </div>
        </header>
        <main className="app-content">
          <Outlet />
        </main>
        <footer className="app-footer">
          <span>EDU 360</span>
          <span>Learning, made clearer.</span>
        </footer>
      </div>
    </div>
  );
}
