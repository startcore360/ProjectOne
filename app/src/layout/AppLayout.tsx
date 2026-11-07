import {
  PLATFORM_PAGE_ACTIONS,
  canOpenPlatformConsole,
  loadPlatformPermissions,
  usePlatformAccess,
} from "../features/platform/platformAccess";
import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { Button } from "../design-system/components";
import { logout } from "../services/authManager";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { Icon } from "../components/ui/Icon";
import { ThemeToggle } from "../components/ui";
import { RoleSwitcher } from "../components/layout/RoleSwitcher";
import { startInactivityTracker } from "../services/inactivityTracker";
import { useProfilePhoto } from "../features/profile/hooks/useProfilePhoto";

const universityNavigation = [
  { label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Institutions", to: "/university/institutions", icon: "book" as const, important: true },
  { label: "Academic structure", to: "/university/setup/structure", icon: "book" as const, important: true },
  { label: "Term lock", to: "/university/setup/term-lock", icon: "lock" as const, important: false },
  { label: "Delegations & approvals", to: "/university/setup/delegations", icon: "check-circle" as const, important: false },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Missing roles", to: "/university/missing-roles", icon: "check-circle" as const, important: true },
  { label: "Roles & permissions", to: "/university/roles", icon: "shield" as const, important: false },
  { label: "Academic governance", to: "/university/setup/academic-governance", icon: "book" as const, important: false },
];
const platformNavigation = [
  { label: "Overview", to: "/platform", icon: "home" as const, important: true, anyOf: null },
  { label: "Tenants", to: "/platform/tenants", icon: "book" as const, important: true, anyOf: PLATFORM_PAGE_ACTIONS.tenants },
  { label: "Audit log", to: "/platform/audit", icon: "clock" as const, important: true, anyOf: PLATFORM_PAGE_ACTIONS.audit },
  { label: "Integrations", to: "/platform/integration-types", icon: "refresh" as const, important: true, anyOf: PLATFORM_PAGE_ACTIONS.integrations },
  { label: "Usage & metrics", to: "/platform/usage-counts", icon: "chart" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.usage },
  { label: "Backup status", to: "/platform/backup-status", icon: "download" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.backup },
  { label: "Plans & billing", to: "/platform/plans", icon: "wallet" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.plans },
  { label: "Invoices", to: "/platform/invoices", icon: "wallet" as const, important: true, anyOf: PLATFORM_PAGE_ACTIONS.invoices },
  { label: "Releases", to: "/platform/releases", icon: "refresh" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.releases },
  { label: "Platform reports", to: "/platform/reports", icon: "chart" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.reports },
  { label: "Security policy", to: "/platform/security-policy", icon: "lock" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.security },
  { label: "Support tickets", to: "/platform/tickets", icon: "help" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.tickets },
  { label: "Support access", to: "/platform/support", icon: "shield" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.support },
  { label: "Platform users", to: "/platform/users", icon: "users" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.staff },
  { label: "Platform roles", to: "/platform/roles", icon: "shield" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.staff },
  { label: "Role templates", to: "/platform/role-templates", icon: "book" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.staff },
];
// "My profile" is deliberately not a nav item - the sidebar's own user/avatar block (bottom of
// .app-sidebar) is already a link to /profile, so a separate nav entry for the same destination
// would just be a redundant second way to reach it.
const workspaceNavigation = [
  { label: "Overview", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Institutions & setup", to: "/university/institutions", icon: "book" as const, important: true },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: true },
  { label: "My sessions", to: "/sessions", icon: "lock" as const, important: false },
];
const studentNavigation = [
  { label: "Overview", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: true },
  { label: "My sessions", to: "/sessions", icon: "lock" as const, important: true },
];

export default function AppLayout() {
  const user = useAppSelector((s) => s.auth.user);
  const navigate = useNavigate();
  const location = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);
  const name = user?.displayName?.trim() || "Account";
  const dispatch = useAppDispatch();
  const { canAny, customRoleName } = usePlatformAccess();
  const role = customRoleName ?? ((user?.platformRole ?? user?.userType)?.replaceAll("_", " ") || "Member");
  const isPlatformConsole = canOpenPlatformConsole(user?.platformRole);
  const isUniversityAdmin = location.pathname.startsWith("/university");
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0]?.toUpperCase()).join("") || "A";
  const photoObjectUrl = useProfilePhoto(user?.photoUrl);
  useEffect(() => startInactivityTracker(() => { void logout(); }), []);
  const signOut = async () => {
    try { await logout(); } catch { /* Always clear the local signed-in view. */ }
    finally { navigate("/login", { replace: true }); }
  };
  const userId = user?.id;
  useEffect(() => {
    if (isPlatformConsole && userId) void loadPlatformPermissions(dispatch, userId);
  }, [isPlatformConsole, userId, dispatch]);
  // D9 P2: build the menu from what the server allows, never from the role name.
  const isStudent = user?.userType === "STUDENT";
  const navigation = isPlatformConsole
    ? platformNavigation.filter((item) => !item.anyOf || canAny(item.anyOf))
    : isStudent
      ? studentNavigation
      : isUniversityAdmin
        ? universityNavigation
        : workspaceNavigation;
  // D10's real mobile bottom bar (r.mobile + a "More" item - see bottomNav()/ACT.openMore in the
  // prototype) shows only a curated handful of important items directly, with everything else
  // reachable through a "More" drawer that lists the full grouped nav plus Sign out. The desktop
  // sidebar keeps the full list (D10's .side-card never truncates) - this curation is mobile-only.
  const importantNav = navigation.filter((item) => item.important);
  const moreNav = navigation.filter((item) => !item.important);

  const scopeLabel = isPlatformConsole ? "PLATFORM" : isUniversityAdmin ? "UNIVERSITY" : user?.userType === "STUDENT" ? "STUDENT SPACE" : "WORKSPACE";
  const contextLabel = isPlatformConsole ? "Platform console" : isUniversityAdmin ? "University admin" : user?.userType === "STUDENT" ? "Student space" : "Workspace";
  const navLink = (item: (typeof navigation)[number], size = 20) =>
    <NavLink key={item.to} to={item.to} end title={item.label} onClick={() => setMoreOpen(false)} className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}><Icon name={item.icon} size={size}/><span>{item.label}</span></NavLink>;

  return <div className="app-shell">
    {/* Matches D10's .side-card: a grouped nav list with the signed-in identity and sign-out
        at the bottom of the sidebar itself, not in a separate top bar. Full list, never
        truncated - D10's sidebar never shows a "More" affordance, only its mobile bottom bar does. */}
    <aside className="app-sidebar">
      <NavLink className="app-brand" to={isPlatformConsole ? "/platform" : "/dashboard"}><span className="app-brand__mark">e</span><span>EDU <strong>360</strong></span></NavLink>
      <p className="app-sidebar__label">{scopeLabel}</p>
      <nav className="app-nav" aria-label="Main navigation">
        {navigation.map((item) => navLink(item))}
        {import.meta.env.DEV && <NavLink to="/role-previews" end title="Role dashboard previews" className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}><Icon name="help" size={20}/><span>Role previews</span></NavLink>}
      </nav>
      <div className="app-sidebar__bottom">
        <NavLink to="/profile" className="app-user" title="View profile">
          {photoObjectUrl
            ? <img className="app-user__avatar app-user__avatar--photo" src={photoObjectUrl} alt="" />
            : <span className="app-user__avatar">{initials}</span>}
          <span className="app-user__details"><strong>{name}</strong><small>{role}</small></span>
        </NavLink>
        <Button variant="secondary" className="app-logout" onClick={signOut}>Sign out</Button>
      </div>
    </aside>

    {/* D10's .bottom-nav: curated important items + a "More" button. Only rendered/visible on
        mobile (CSS hides it above the 640px breakpoint; .app-sidebar is hidden below it). */}
    <nav className="app-mobile-nav" aria-label="Mobile navigation">
      {importantNav.map((item) => navLink(item, 22))}
      <button type="button" className="app-nav__link" aria-haspopup="dialog" aria-expanded={moreOpen} onClick={() => setMoreOpen(true)}><Icon name="more" size={22}/><span>More</span></button>
    </nav>
    {moreOpen && <div className="platform-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setMoreOpen(false); }}>
      <section className="app-more-drawer" role="dialog" aria-modal="true" aria-label="Menu">
        <header><h2>Menu</h2><button className="platform-icon-button" onClick={() => setMoreOpen(false)} aria-label="Close"><Icon name="x-circle" size={18}/></button></header>
        <p className="app-sidebar__label">{scopeLabel}</p>
        <div className="app-nav">{moreNav.map((item) => navLink(item))}</div>
        <hr/>
        <Button variant="secondary" onClick={signOut}>Sign out</Button>
      </section>
    </div>}

    <div className="app-main">
      {/* Breadcrumb context only - pages must not also render their own breadcrumb here. */}
      <div className="app-topbar">
        <div className="app-topbar__context"><span>EDU 360</span><span aria-hidden="true">/</span><strong>{contextLabel}</strong></div>
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <RoleSwitcher />
          <ThemeToggle variant="inline" />
        </div>
      </div>
      <main className="app-content"><Outlet /></main>
      <footer className="app-footer"><span>EDU 360 · Stillwater design system v2.1</span><span>Role and scope-aware workspace</span></footer>
    </div>
  </div>;
}
