import { useEffect } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { Button } from "../design-system/components";
import { logout } from "../services/authManager";
import { useAppSelector } from "../store/hooks";
import { Icon } from "../components/ui/Icon";
import { startInactivityTracker } from "../services/inactivityTracker";

const universityNavigation = [
  { label: "Institutions", to: "/university/institutions", icon: "book" as const },
  { label: "Users & roles", to: "/university/users", icon: "users" as const },
  { label: "Roles & permissions", to: "/university/roles", icon: "shield" as const },
  { label: "Missing roles", to: "/university/missing-roles", icon: "check-circle" as const },
];
const platformNavigation = [
  { label: "Overview", to: "/platform", icon: "home" as const },
  { label: "Tenants", to: "/platform/tenants", icon: "book" as const },
  { label: "Plans & billing", to: "/platform/plans", icon: "wallet" as const },
  { label: "Platform users", to: "/platform/users", icon: "users" as const },
  { label: "Support access", to: "/platform/support", icon: "shield" as const },
  { label: "Audit log", to: "/platform/audit", icon: "clock" as const },
];

export default function AppLayout() {
  const user = useAppSelector((s) => s.auth.user);
  const navigate = useNavigate();
  const location = useLocation();
  const name = user?.displayName?.trim() || "Account";
  const role = (user?.platformRole ?? user?.userType)?.replaceAll("_", " ") || "Member";
  const isPlatformAdmin = user?.platformRole === "PLATFORM_ADMIN";
  const isUniversityAdmin = location.pathname.startsWith("/university");
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0]?.toUpperCase()).join("") || "A";
  useEffect(() => startInactivityTracker(() => { void logout(); }), []);
  const signOut = async () => {
    try { await logout(); } catch { /* Always clear the local signed-in view. */ }
    finally { navigate("/login", { replace: true }); }
  };
  const navigation = isPlatformAdmin ? platformNavigation : isUniversityAdmin ? universityNavigation : [{ label: "Overview", to: "/dashboard", icon: "home" as const }];

  return <div className="app-shell">
    <aside className="app-sidebar">
      <NavLink className="app-brand" to={isPlatformAdmin ? "/platform" : "/dashboard"}><span className="app-brand__mark">e</span><span>EDU <strong>360</strong></span></NavLink>
      <p className="app-sidebar__label">{isPlatformAdmin ? "PLATFORM" : isUniversityAdmin ? "UNIVERSITY" : user?.userType === "STUDENT" ? "STUDENT SPACE" : "WORKSPACE"}</p>
      <nav className="app-nav" aria-label="Main navigation">
        {navigation.map((item) => <NavLink key={item.to} to={item.to} end title={item.label} className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}><Icon name={item.icon} size={24}/><span>{item.label}</span></NavLink>)}
        {import.meta.env.DEV && <NavLink to="/role-previews" end title="Role dashboard previews" className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}><Icon name="help" size={24}/><span>Role previews</span></NavLink>}
      </nav>
      <div className="app-sidebar__bottom"><span className="app-sidebar__dot" />{isPlatformAdmin ? "Platform scope" : isUniversityAdmin ? "University scope" : "Institution workspace"}</div>
    </aside>
    <div className="app-main">
      <header className="app-topbar">
        <div className="app-topbar__context"><span>EDU 360</span><span aria-hidden="true">/</span><strong>{isPlatformAdmin ? "Platform console" : isUniversityAdmin ? "University admin" : user?.userType === "STUDENT" ? "Student space" : "Workspace"}</strong></div>
        <div className="app-user"><span className="app-user__avatar">{initials}</span><span className="app-user__details"><strong>{name}</strong><small>{role}</small></span><Button variant="secondary" className="app-logout" onClick={signOut}>Sign out</Button></div>
      </header>
      <main className="app-content"><Outlet /></main>
      <footer className="app-footer"><span>EDU 360 · Stillwater design system v2.1</span><span>Role and scope-aware workspace</span></footer>
    </div>
  </div>;
}
