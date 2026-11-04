import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Button } from "../design-system/components";
import { logout } from "../services/authManager";
import { useAppSelector } from "../store/hooks";

const universityNavigation = [\n  { label: "Institutions", to: "/university/institutions", icon: "▤" },\n  { label: "Users & roles", to: "/university/users", icon: "♙" },\n  { label: "Roles & permissions", to: "/university/roles", icon: "⛨" },\n  { label: "Missing roles", to: "/university/missing-roles", icon: "⇣" },\n];\n\nconst platformNavigation = [
  { label: "Overview", to: "/platform", icon: "▦" },
  { label: "Tenants", to: "/platform/tenants", icon: "▤" },
  { label: "Plans & billing", to: "/platform/plans", icon: "◈" },
  { label: "Platform users", to: "/platform/users", icon: "♙" },
  { label: "Support access", to: "/platform/support", icon: "⛨" },
  { label: "Audit log", to: "/platform/audit", icon: "◷" },
];

export default function AppLayout() {
  const user = useAppSelector((s) => s.auth.user);
  const navigate = useNavigate();
  const name = user?.name?.trim() || "Account";
  const role = user?.role?.replaceAll("_", " ") || "Member";
  const normalizedRole = role.toUpperCase().replaceAll(" ", "_");
  const isPlatformAdmin = normalizedRole === "PLATFORM_ADMIN" || normalizedRole === "PLATFORM_ADMINISTRATOR";\n  const isUniversityAdmin = normalizedRole === "UNIVERSITY_ADMIN";
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0]?.toUpperCase()).join("") || "A";

  const signOut = async () => {
    try {
      await logout();
    } catch {
      // A failed network sign-out must not leave the user looking authenticated.
    } finally {
      navigate("/login", { replace: true });
    }
  };

  const navigation = isPlatformAdmin ? platformNavigation : isUniversityAdmin ? universityNavigation : [{ label: "Overview", to: "/dashboard", icon: "▦" }];

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <NavLink className="app-brand" to={isPlatformAdmin ? "/platform" : "/dashboard"}>
          <span className="app-brand__mark">e</span>
          <span>EDU <strong>360</strong></span>
        </NavLink>
        <p className="app-sidebar__label">{isPlatformAdmin ? "PLATFORM" : isUniversityAdmin ? "UNIVERSITY" : "WORKSPACE"}</p>
        <nav className="app-nav" aria-label="Main navigation">
          {navigation.map((item) => (
            <NavLink key={item.to} to={item.to} end title={item.label} aria-label={item.label} className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}>
              <span aria-hidden="true">{item.icon}</span>{item.label}
            </NavLink>
          ))}
        </nav>
        <div className="app-sidebar__bottom">
          <span className="app-sidebar__dot" />
          {isPlatformAdmin ? "Platform scope" : isUniversityAdmin ? "University scope" : "Institution workspace"}
        </div>
      </aside>
      <div className="app-main">
        <header className="app-topbar">
          <div className="app-topbar__context"><span>EDU 360</span><span>/</span><strong>{isPlatformAdmin ? "Platform console" : isUniversityAdmin ? "University admin" : "Workspace"}</strong></div>
          <div className="app-user">
            <span className="app-user__avatar">{initials}</span>
            <span className="app-user__details"><strong>{name}</strong><small>{role}</small></span>
            <Button variant="secondary" className="app-logout" onClick={signOut}>Sign out</Button>
          </div>
        </header>
        <main className="app-content"><Outlet /></main>
        <footer className="app-footer"><span>EDU 360 · Stillwater design system v2.1</span><span>Platform foundation · Sprint 1</span></footer>
      </div>
    </div>
  );
}
