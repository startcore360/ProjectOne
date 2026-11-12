import {
  PLATFORM_PAGE_ACTIONS,
  canOpenPlatformConsole,
  loadPlatformPermissions,
  usePlatformAccess,
} from "../features/platform/platformAccess";
import { useCallback, useEffect, useRef, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { Button } from "../design-system/components";
import { idleLogout, logout } from "../services/authManager";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { Icon } from "../components/ui/Icon";
import Edu360Logo from "../assets/Edu360Logo";
import { ThemeToggle } from "../components/ui";
import { RoleSwitcher } from "../components/layout/RoleSwitcher";
import { NotificationBell } from "../features/notifications/components/NotificationBell";
import { useLiveSocket } from "../features/live/liveSocket";
import { SESSION_IDLE_LIMIT_MS, startInactivityTracker } from "../services/inactivityTracker";
import api from "../services/axios";
import { useProfilePhoto } from "../features/profile/hooks/useProfilePhoto";
import { roleDashboardByCode } from "../features/role-previews/roleDashboardCatalog";
import { useTenantAccess } from "../features/profile/hooks/useTenantAccess";
import { tenantPermissionsService } from "../features/profile/api/tenantPermissionsService";

const universityNavigation = [
  { label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Institutions", to: "/university/institutions", icon: "book" as const, important: true },
  { label: "Academic structure", to: "/university/setup/structure", icon: "book" as const, important: true },
  { label: "Academic governance", to: "/university/setup/academic-governance", icon: "book" as const, important: false },
  { label: "Term lock", to: "/university/setup/term-lock", icon: "lock" as const, important: false },
  { label: "Delegations & approvals", to: "/university/setup/delegations", icon: "check-circle" as const, important: false },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Missing roles", to: "/university/missing-roles", icon: "check-circle" as const, important: false },
  { label: "Roles & permissions", to: "/university/roles", icon: "shield" as const, important: false },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: false },
  { label: "My sessions", to: "/sessions", icon: "lock" as const, important: false },
];

const collegeNavigation = [
  { label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Academic structure", to: "/university/setup/structure", icon: "book" as const, important: true },
  { label: "Term lock", to: "/university/setup/term-lock", icon: "lock" as const, important: false },
  { label: "Delegations & approvals", to: "/university/setup/delegations", icon: "check-circle" as const, important: false },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: true },
  { label: "My sessions", to: "/sessions", icon: "lock" as const, important: false },
];

const schoolNavigation = [
  { label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Classes & structure", to: "/university/setup/structure", icon: "book" as const, important: true },
  { label: "Class teachers", to: "/university/setup/class-teacher", icon: "users" as const, important: false },
  { label: "Calendar & weekly off", to: "/university/setup/weekly-off", icon: "book" as const, important: false },
  { label: "Term lock", to: "/university/setup/term-lock", icon: "lock" as const, important: false },
  { label: "Delegations & approvals", to: "/university/setup/delegations", icon: "check-circle" as const, important: false },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: true },
  { label: "My sessions", to: "/sessions", icon: "lock" as const, important: false },
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

const workspaceNavigation = [
  { label: "Overview", to: "/dashboard", icon: "home" as const, important: true },
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
  useLiveSocket(user?.tenantId ?? null, Boolean(user?.platformRole));
  const navigate = useNavigate();
  const location = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);
  // Close the mobile Menu drawer on every navigation (location.key changes even when the path
  // is the same, e.g. a role switch that lands on /dashboard while already there). Adjusting
  // state during render on a changed key is React's documented alternative to an effect.
  const [drawerLocationKey, setDrawerLocationKey] = useState(location.key);
  if (drawerLocationKey !== location.key) {
    setDrawerLocationKey(location.key);
    if (moreOpen) setMoreOpen(false);
  }
  const name = user?.displayName?.trim() || "Account";
  const dispatch = useAppDispatch();
  const { canAny, customRoleName } = usePlatformAccess();
  const { canAny: canTenantAny, loading: tenantPermissionsLoading, error: tenantPermissionsError, permissions: tenantPermissions } = useTenantAccess();

  // Role / Level detection
  const previewRoleCode = location.pathname.startsWith("/role-preview/")
    ? location.pathname.split("/role-preview/")[1]
    : null;
  const effectiveRoleCode = previewRoleCode || user?.roleCode;
  const roleProfile = effectiveRoleCode ? roleDashboardByCode(effectiveRoleCode) : undefined;
  const roleLevel = roleProfile?.level;

  const role =
    customRoleName ??
    user?.roleName ??
    (roleProfile ? roleProfile.name : null) ??
    (user?.roleCode ? user.roleCode.replaceAll("_", " ") : null) ??
    ((user?.platformRole ?? user?.userType)?.replaceAll("_", " ") || "Member");

  const isPlatformConsole = canOpenPlatformConsole(user?.platformRole);
  const isStudent = user?.userType === "STUDENT";
  const isUniversityLevel =
    roleLevel === "University" ||
    roleLevel === "Tenant" ||
    effectiveRoleCode === "UNIVERSITY_ADMIN" ||
    effectiveRoleCode === "VICE_CHANCELLOR";
  const isCollegeLevel =
    roleLevel === "College" ||
    effectiveRoleCode === "COLLEGE_ADMIN" ||
    effectiveRoleCode === "PRINCIPAL_COLLEGE";
  const isSchoolLevel =
    roleLevel === "School" ||
    effectiveRoleCode === "SCHOOL_ADMIN" ||
    effectiveRoleCode === "SCHOOL_PRINCIPAL";

  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0]?.toUpperCase()).join("") || "A";
  const photoObjectUrl = useProfilePhoto(user?.photoUrl, user?.photoRevision);
  const avatar = photoObjectUrl
    ? <img className="app-user__avatar app-user__avatar--photo" src={photoObjectUrl} alt="" />
    : <span className="app-user__avatar">{initials}</span>;
  // I-06: the idle limit is the platform security policy value; 30 minutes until it is read.
  const [idleLimitMs, setIdleLimitMs] = useState(SESSION_IDLE_LIMIT_MS);
  useEffect(() => {
    api.get<{ idleTimeoutMinutes?: number }>("/auth/session-settings")
      .then(({ data }) => { if (data.idleTimeoutMinutes && data.idleTimeoutMinutes > 0) setIdleLimitMs(data.idleTimeoutMinutes * 60_000); })
      .catch(() => { /* keep the default limit */ });
  }, []);
  useEffect(() => startInactivityTracker(() => { void idleLogout(); }, idleLimitMs), [idleLimitMs]);
  const signOut = async () => {
    try { await logout(); } catch { /* Always clear the local signed-in view. */ }
    finally { navigate("/login", { replace: true }); }
  };
  const userId = user?.id;
  useEffect(() => {
    if (isPlatformConsole && userId) void loadPlatformPermissions(dispatch, userId);
  }, [isPlatformConsole, userId, dispatch]);

  // Bootstrap also runs after refresh/hydrate; login and role-switch flows dispatch the same
  // permission load. Tenant/user mismatches are never allowed to reuse a previous scope.
  useEffect(() => {
    if (!user?.tenantId || user.platformRole || tenantPermissionsLoading || tenantPermissionsError) return;
    if (tenantPermissions?.tenantId !== user.tenantId || tenantPermissions?.userAccountId !== user.id) {
      void tenantPermissionsService.reload();
    }
  }, [user?.tenantId, user?.id, user?.platformRole, tenantPermissionsLoading, tenantPermissionsError, tenantPermissions?.tenantId, tenantPermissions?.userAccountId]);

  const scopeLabel = isPlatformConsole
    ? "PLATFORM"
    : isStudent
      ? "STUDENT SPACE"
      : isUniversityLevel
        ? "UNIVERSITY"
        : isCollegeLevel
          ? "COLLEGE"
          : isSchoolLevel
            ? "SCHOOL"
            : location.pathname.startsWith("/university")
              ? "UNIVERSITY"
              : "WORKSPACE";

  const contextLabel = isPlatformConsole
    ? "Platform console"
    : isStudent
      ? "Student space"
      : isUniversityLevel
        ? previewRoleCode ? "University admin (Preview)" : "University admin"
        : isCollegeLevel
          ? previewRoleCode ? "College admin (Preview)" : "College admin"
          : isSchoolLevel
            ? previewRoleCode ? "School admin (Preview)" : "School admin"
            : location.pathname.startsWith("/university")
              ? "University admin"
              : "Workspace";

  const baseNavigation = isPlatformConsole
    ? platformNavigation.filter((item) => !item.anyOf || canAny(item.anyOf))
    : isStudent || user?.userType === "GUARDIAN"
      ? studentNavigation
      : isUniversityLevel
        ? universityNavigation
        : isCollegeLevel
          ? collegeNavigation
          : isSchoolLevel
            ? schoolNavigation
            : location.pathname.startsWith("/university")
              ? universityNavigation
              : workspaceNavigation;

  // Academic destinations appear only when the active role has the matching server grant.
  // They remain non-clickable until their real feature pages ship; no placeholder routes.
  const academicNavigation = tenantPermissionsLoading ? [] : [
    { label: "Timetable", to: "/dashboard", icon: "calendar" as const, important: false, disabled: true, anyOf: ["TIMETABLE_BUILD", "TIMETABLE_VIEW"] },
    { label: "Attendance", to: "/dashboard", icon: "check-circle" as const, important: false, disabled: true, anyOf: ["ATTENDANCE_MARK", "ATTENDANCE_VIEW", "ATTENDANCE_VIEW_OWN", "ATTENDANCE_BULK_UPLOAD"] },
    { label: "Assignments", to: "/dashboard", icon: "book" as const, important: false, disabled: true, anyOf: ["ASSIGNMENT_CREATE", "ASSIGNMENT_SUBMIT", "ASSIGNMENT_VIEW_OWN", "ASSIGNMENT_EVALUATE"] },
    { label: "Study material", to: "/dashboard", icon: "book" as const, important: false, disabled: true, anyOf: ["STUDY_MATERIAL_UPLOAD"] },
    { label: "Syllabus", to: "/dashboard", icon: "book" as const, important: false, disabled: true, anyOf: ["SYLLABUS_VIEW", "SYLLABUS_MANAGE"] },
    { label: "Staff", to: "/dashboard", icon: "users" as const, important: false, disabled: true, anyOf: ["STAFF_RECORD_VIEW", "STAFF_RECORD_MANAGE"] },
  ].filter((item) => canTenantAny(item.anyOf));
  const navigation = isPlatformConsole ? baseNavigation : [...baseNavigation, ...academicNavigation];

  // Sidebar overflow hint: overlay scrollbars (macOS, touch laptops) hide until hovered, so a
  // long menu gives no sign that more items sit below. Track whether the list can scroll
  // further in either direction and let CSS fade that edge, plus show a "More" button below.
  const navRef = useRef<HTMLElement>(null);
  const [navOverflow, setNavOverflow] = useState({ above: false, below: false });
  const updateNavOverflow = useCallback(() => {
    const el = navRef.current;
    if (!el) return;
    const above = el.scrollTop > 1;
    const below = el.scrollTop + el.clientHeight < el.scrollHeight - 1;
    setNavOverflow((prev) => (prev.above === above && prev.below === below ? prev : { above, below }));
  }, []);
  useEffect(() => {
    updateNavOverflow();
    const el = navRef.current;
    if (!el) return;
    window.addEventListener("resize", updateNavOverflow);
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(updateNavOverflow) : null;
    observer?.observe(el);
    return () => {
      window.removeEventListener("resize", updateNavOverflow);
      observer?.disconnect();
    };
  }, [updateNavOverflow, navigation.length]);
  const scrollNavDown = () => {
    const el = navRef.current;
    if (el) el.scrollTop += Math.max(el.clientHeight * 0.75, 88);
  };

  const importantNav = navigation.filter((item) => item.important);
  const moreNav = navigation.filter((item) => !item.important);
  const navLink = (item: (typeof navigation)[number], size = 20) =>
    "disabled" in item && item.disabled
      ? <span key={item.label} className="app-nav__link is-disabled" aria-disabled="true" title={`${item.label} page is not part of this sprint`}><Icon name={item.icon} size={size}/><span>{item.label} <small>Coming soon</small></span></span>
      : <NavLink key={item.to} to={item.to} end title={item.label} onClick={() => setMoreOpen(false)} className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}><Icon name={item.icon} size={size}/><span>{item.label}</span></NavLink>;

  return <div className="app-shell">
    {/* Matches D10's .side-card: a grouped nav list with the signed-in identity and sign-out
        at the bottom of the sidebar itself, not in a separate top bar. Full list, never
        truncated - D10's sidebar never shows a "More" affordance, only its mobile bottom bar does. */}
    <aside className="app-sidebar">
      <NavLink className="app-brand" to={isPlatformConsole ? "/platform" : "/dashboard"}><Edu360Logo variant="mark" className="app-brand__mark" /><span>EDU <strong>360</strong></span></NavLink>
      <p className="app-sidebar__label">{scopeLabel}</p>
      <div className="app-nav-wrap">
        <nav
          ref={navRef}
          onScroll={updateNavOverflow}
          className={"app-nav" + (navOverflow.above ? " app-nav--more-above" : "") + (navOverflow.below ? " app-nav--more-below" : "")}
          aria-label="Main navigation"
        >
          {navigation.map((item) => navLink(item))}
          {import.meta.env.DEV && <NavLink to="/role-previews" end title="Role dashboard previews" className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}><Icon name="help" size={20}/><span>Role previews</span></NavLink>}
        </nav>
        {/* Mouse affordance only: keyboard and screen-reader users already reach every link by
            tabbing, which scrolls the list itself, so this button stays out of the tab order. */}
        {navOverflow.below && <button type="button" className="app-nav__more" tabIndex={-1} aria-hidden="true" title="Show more menu options" onClick={scrollNavDown}><span>More</span><Icon name="chevron-down" size={16}/></button>}
      </div>
      <div className="app-sidebar__bottom">
        <NavLink to="/profile" className="app-user" title="View profile">
          {avatar}
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
        <header><h2>Menu</h2><button className="platform-icon-button" onClick={() => setMoreOpen(false)} aria-label="Close"><Icon name="x" size={16}/></button></header>
        {/* Mobile has no sidebar, so the signed-in identity + profile link live here too. */}
        <NavLink to="/profile" className="app-user app-user--drawer" onClick={() => setMoreOpen(false)}>
          {avatar}
          <span className="app-user__details"><strong>{name}</strong><small>{role}</small></span>
          <span className="app-user__cta">View profile</span>
        </NavLink>
        {/* On mobile the top-bar role switcher is hidden (no room beside the breadcrumb), so it
            lives here instead - the one place every mobile user already goes for account actions. */}
        <div className="app-more-drawer__role"><RoleSwitcher layout="stacked" /></div>
        <p className="app-sidebar__label">{scopeLabel}</p>
        <div className="app-nav">{moreNav.map((item) => navLink(item))}</div>
        <hr/>
        <Button variant="secondary" className="app-more-drawer__signout" onClick={signOut}>Sign out</Button>
      </section>
    </div>}

    <div className="app-main">
      {/* Breadcrumb context only - pages must not also render their own breadcrumb here. */}
      <div className="app-topbar">
        <div className="app-topbar__context"><span>EDU 360</span><span aria-hidden="true">/</span><strong>{contextLabel}</strong></div>
        <div className="app-topbar__tools">
          {!isPlatformConsole && <NotificationBell />}
          <div className="app-topbar__role"><RoleSwitcher /></div>
          <ThemeToggle variant="inline" />
          {/* Mobile only (CSS): who is signed in, always visible, one tap to the profile. */}
          <NavLink to="/profile" className="app-topbar__avatar" aria-label={`${name} - view profile`} title="View profile">{avatar}</NavLink>
        </div>
      </div>
      <main className="app-content"><Outlet /></main>    </div>
  </div>;
}
