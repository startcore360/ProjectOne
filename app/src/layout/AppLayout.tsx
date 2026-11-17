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
import { useSupportSession } from "../features/support/useSupportSession";
import { SupportSessionBanner } from "../features/support/SupportSessionBanner";
import { useTenantBranding } from "../features/branding/useTenantBranding";
import { TrialExpiredBanner } from "../features/branding/TrialExpiredBanner";

const universityNavigation = [
  { label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Roles & permissions", to: "/university/roles", icon: "shield" as const, important: true },
  { label: "Institutions", to: "/university/institutions", icon: "building" as const, important: false },
  { label: "Academic structure", to: "/university/setup/structure", icon: "grid" as const, important: false },
  { label: "Academic governance", to: "/university/setup/academic-governance", icon: "book" as const, important: false },
  { label: "Delegations & approvals", to: "/university/setup/delegations", icon: "check-circle" as const, important: false },
  { label: "Missing roles", to: "/university/missing-roles", icon: "alert" as const, important: false },
  { label: "Term lock", to: "/university/setup/term-lock", icon: "lock" as const, important: false },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: false },
  { label: "My sessions", to: "/sessions", icon: "clock" as const, important: false },
];

const collegeNavigation = [
  { label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Academic structure", to: "/university/setup/structure", icon: "grid" as const, important: true },
  { label: "Delegations & approvals", to: "/university/setup/delegations", icon: "check-circle" as const, important: false },
  { label: "Term lock", to: "/university/setup/term-lock", icon: "lock" as const, important: false },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: false },
  { label: "My sessions", to: "/sessions", icon: "clock" as const, important: false },
];

const schoolNavigation = [
  { label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true },
  { label: "Users & roles", to: "/university/users", icon: "users" as const, important: true },
  { label: "Classes & structure", to: "/university/setup/structure", icon: "grid" as const, important: true },
  { label: "Class teachers", to: "/university/setup/class-teacher", icon: "edit" as const, important: false },
  { label: "Calendar & weekly off", to: "/university/setup/weekly-off", icon: "calendar" as const, important: false },
  { label: "Delegations & approvals", to: "/university/setup/delegations", icon: "check-circle" as const, important: false },
  { label: "Term lock", to: "/university/setup/term-lock", icon: "lock" as const, important: false },
  { label: "Self-service", to: "/self-service", icon: "user" as const, important: false },
  { label: "My sessions", to: "/sessions", icon: "clock" as const, important: false },
];

const platformNavigation = [
  { label: "Overview", to: "/platform", icon: "home" as const, important: true, anyOf: null },
  { label: "My sessions", to: "/sessions", icon: "lock" as const, important: false, anyOf: null },
  { label: "Tenants", to: "/platform/tenants", icon: "building" as const, important: true, anyOf: PLATFORM_PAGE_ACTIONS.tenants },
  { label: "Plans & billing", to: "/platform/plans", icon: "wallet" as const, important: true, anyOf: PLATFORM_PAGE_ACTIONS.plans },
  { label: "Invoices", to: "/platform/invoices", icon: "file-text" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.invoices },
  { label: "Audit log", to: "/platform/audit", icon: "clock" as const, important: true, anyOf: PLATFORM_PAGE_ACTIONS.audit },
  { label: "Platform users", to: "/platform/users", icon: "users" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.staff },
  { label: "Platform roles", to: "/platform/roles", icon: "shield" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.staff },
  { label: "Role templates", to: "/platform/role-templates", icon: "layers" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.staff },
  { label: "Platform reports", to: "/platform/reports", icon: "chart" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.reports },
  { label: "Usage & metrics", to: "/platform/usage-counts", icon: "activity" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.usage },
  { label: "Support tickets", to: "/platform/tickets", icon: "help" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.tickets },
  { label: "Support access", to: "/platform/support", icon: "eye" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.support },
  { label: "Backup status", to: "/platform/backup-status", icon: "download" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.backup },
  { label: "Security policy", to: "/platform/security-policy", icon: "key" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.security },
  { label: "Integrations", to: "/platform/integration-types", icon: "cpu" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.integrations },
  { label: "Releases", to: "/platform/releases", icon: "package" as const, important: false, anyOf: PLATFORM_PAGE_ACTIONS.releases },
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
  const { isSupportSession } = useSupportSession();
  // E1-S4: tenant users see their tenant's logo and colours; the platform console never does.
  const { logoUrl: tenantLogoUrl } = useTenantBranding(user?.platformRole ? null : user?.tenantId);
  useLiveSocket(user?.tenantId ?? null, Boolean(user?.platformRole));
  const navigate = useNavigate();
  const location = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    try { return localStorage.getItem("edu360-sidebar-collapsed") === "true"; } catch { return false; }
  });
  const [logoSpin, setLogoSpin] = useState<"cw" | "ccw" | null>(null);
  const toggleSidebar = () => {
    setSidebarCollapsed((v) => {
      const next = !v;
      try { localStorage.setItem("edu360-sidebar-collapsed", String(next)); } catch { /* noop */ }
      setLogoSpin(next ? "cw" : "ccw");
      return next;
    });
  };
  // Clear spin class after animation completes so re-renders don't replay it
  useEffect(() => {
    if (!logoSpin) return undefined;
    const t = setTimeout(() => setLogoSpin(null), 420);
    return () => clearTimeout(t);
  }, [logoSpin]);
  // Scroll the page to the top whenever the route path changes.
  useEffect(() => { window.scrollTo({ top: 0, behavior: "instant" }); }, [location.pathname]);

  // User card ⋮ menu
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!userMenuOpen) return undefined;
    const handler = (e: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) setUserMenuOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [userMenuOpen]);
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
    (user?.roleCode ? user.roleCode.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()) : null) ??
    ((user?.platformRole ?? user?.userType)?.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()) || "Member");

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

  const baseNavigation = isSupportSession
    ? [{ label: "Dashboard", to: "/dashboard", icon: "home" as const, important: true }, { label: "Support", to: "/support", icon: "help" as const, important: true }]
    : isPlatformConsole
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
    { label: "Support", to: "/support", icon: "help" as const, important: false, anyOf: ["SUPPORT_TICKET_RAISE", "SUPPORT_ACCESS_GRANT"] },
    { label: "Timetable", to: "/dashboard", icon: "calendar" as const, important: false, disabled: true, anyOf: ["TIMETABLE_BUILD", "TIMETABLE_VIEW"] },
    { label: "Attendance", to: "/dashboard", icon: "check-circle" as const, important: false, disabled: true, anyOf: ["ATTENDANCE_MARK", "ATTENDANCE_VIEW", "ATTENDANCE_VIEW_OWN", "ATTENDANCE_BULK_UPLOAD"] },
    { label: "Assignments", to: "/dashboard", icon: "book" as const, important: false, disabled: true, anyOf: ["ASSIGNMENT_CREATE", "ASSIGNMENT_SUBMIT", "ASSIGNMENT_VIEW_OWN", "ASSIGNMENT_EVALUATE"] },
    { label: "Study material", to: "/dashboard", icon: "book" as const, important: false, disabled: true, anyOf: ["STUDY_MATERIAL_UPLOAD"] },
    { label: "Syllabus", to: "/dashboard", icon: "book" as const, important: false, disabled: true, anyOf: ["SYLLABUS_VIEW", "SYLLABUS_MANAGE"] },
    { label: "Staff records", to: "/university/staff", icon: "users" as const, important: false, anyOf: ["STAFF_RECORD_VIEW"] },
  ].filter((item) => canTenantAny(item.anyOf));
  const navigation = isPlatformConsole ? baseNavigation : [...baseNavigation, ...academicNavigation];

  // Sidebar overflow hint: overlay scrollbars (macOS, touch laptops) hide until hovered, so a
  // long menu gives no sign that more items sit below. Track whether the list can scroll
  // further in either direction and let CSS fade that edge, plus show a "More" button below.
  const navRef = useRef<HTMLElement>(null);
  const drawerRef = useRef<HTMLElement>(null);
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

  // Focus-trap for the mobile "more" drawer: matches the Modal pattern exactly.
  // Declared as a constant so the dep array stays stable.
  const DRAWER_FOCUSABLE = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';
  useEffect(() => {
    if (!moreOpen) return undefined;
    drawerRef.current?.focus();
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") { setMoreOpen(false); return; }
      if (e.key !== "Tab" || !drawerRef.current) return;
      const focusable = Array.from(drawerRef.current.querySelectorAll<HTMLElement>(DRAWER_FOCUSABLE));
      if (focusable.length === 0) { e.preventDefault(); drawerRef.current.focus(); return; }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const idx = focusable.indexOf(document.activeElement as HTMLElement);
      if (e.shiftKey && idx <= 0) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && (idx === -1 || idx === focusable.length - 1)) { e.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [moreOpen, DRAWER_FOCUSABLE]);

  const scrollNavDown = () => {
    const el = navRef.current;
    if (el) el.scrollTop += Math.max(el.clientHeight * 0.75, 88);
  };
  const scrollNavUp = () => {
    const el = navRef.current;
    if (el) el.scrollTop -= Math.max(el.clientHeight * 0.75, 88);
  };

  const importantNav = navigation.filter((item) => item.important);
  const moreNav = navigation.filter((item) => !item.important);
  const navLink = (item: (typeof navigation)[number], size = 20) =>
    "disabled" in item && item.disabled
      ? <button key={item.label} type="button" disabled className="app-nav__link is-disabled" aria-label={`${item.label} — coming soon`}><Icon name={item.icon} size={size}/><span>{item.label} <small>Coming soon</small></span></button>
      : <NavLink key={item.to} to={item.to} end title={item.label} onClick={() => setMoreOpen(false)} className={({ isActive }) => "app-nav__link" + (isActive ? " is-active" : "")}><Icon name={item.icon} size={size}/><span>{item.label}</span></NavLink>;

  return <div className="app-shell">
    {/* Matches D10's .side-card: a grouped nav list with the signed-in identity and sign-out
        at the bottom of the sidebar itself, not in a separate top bar. Full list, never
        truncated - D10's sidebar never shows a "More" affordance, only its mobile bottom bar does. */}
    <aside className={`app-sidebar${sidebarCollapsed ? " app-sidebar--collapsed" : ""}`}>
      <button
        type="button"
        className="app-sidebar__handle"
        onClick={toggleSidebar}
        title={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
        aria-label={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
      >
        <Icon name={sidebarCollapsed ? "chevron-right" : "chevron-left"} size={12} />
      </button>
      <div className="app-brand-row">
        <button type="button" className="app-brand__logo-btn" onClick={toggleSidebar} title={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"} aria-label={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}>
          <Edu360Logo variant="mark" className={`app-brand__mark${logoSpin === "cw" ? " app-brand__mark--spin-cw" : logoSpin === "ccw" ? " app-brand__mark--spin-ccw" : ""}`} />
        </button>
        <NavLink className="app-brand__name" to={isPlatformConsole ? "/platform" : "/dashboard"}>EDU <strong>360</strong></NavLink>
        {tenantLogoUrl && !sidebarCollapsed && (
          <img
            src={tenantLogoUrl}
            alt="Institution logo"
            style={{ maxHeight: "var(--space-6)", maxWidth: "96px", objectFit: "contain", marginLeft: "auto" }}
          />
        )}
      </div>
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
            tabbing, which scrolls the list itself, so these buttons stay out of the tab order. */}
        {navOverflow.above && <button type="button" className="app-nav__more app-nav__more--up" tabIndex={-1} aria-hidden="true" title="Show items above" onClick={scrollNavUp}><Icon name="chevron-down" size={16} style={{transform:"rotate(180deg)"}}/><span>Up</span></button>}
        {navOverflow.below && <button type="button" className="app-nav__more" tabIndex={-1} aria-hidden="true" title="Show more menu options" onClick={scrollNavDown}><span>More</span><Icon name="chevron-down" size={16}/></button>}
      </div>
      <div className="app-sidebar__bottom">
        {sidebarCollapsed ? (
          /* Collapsed rail: avatar → profile, sign-out icon below. No dropdown needed —
             the sidebar overflow:hidden clips any popup escaping the 72px rail.          */
          <div className="app-user-card app-user-card--rail">
            <NavLink to="/profile" className="app-user" title="View profile">{avatar}</NavLink>
            <button type="button" className="app-signout-icon" onClick={() => void signOut()} title="Sign out" aria-label="Sign out">
              <Icon name="return" size={16}/>
            </button>
          </div>
        ) : (
          <div className="app-user-card" ref={userMenuRef}>
            <NavLink to="/profile" className="app-user" title="View profile" onClick={() => setUserMenuOpen(false)}>
              {avatar}
              <span className="app-user__details"><strong>{name}</strong><small>{role}</small></span>
            </NavLink>
            <button type="button" className="app-user-card__more" onClick={() => setUserMenuOpen((v) => !v)} aria-label="Account options" aria-expanded={userMenuOpen}>
              <Icon name="more" size={16}/>
            </button>
            {userMenuOpen && (
              <div className="app-user-card__dropdown" role="menu">
                <NavLink to="/profile" className="app-user-card__item" role="menuitem" onClick={() => setUserMenuOpen(false)}>
                  <Icon name="user" size={14}/><span>View profile</span>
                </NavLink>
                <button type="button" className="app-user-card__item app-user-card__item--danger" role="menuitem" onClick={() => { setUserMenuOpen(false); void signOut(); }}>
                  <Icon name="return" size={14}/><span>Sign out</span>
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </aside>

    {/* D10's .bottom-nav: curated important items + a "More" button. Only rendered/visible on
        mobile (CSS hides it above the 640px breakpoint; .app-sidebar is hidden below it). */}
    <nav className="app-mobile-nav" aria-label="Mobile navigation">
      {importantNav.map((item) => navLink(item, 22))}
      <button type="button" className="app-nav__link" aria-haspopup="dialog" aria-expanded={moreOpen} onClick={() => setMoreOpen(true)}><Icon name="more" size={22}/><span>More</span></button>
    </nav>
    {moreOpen && <div className="app-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setMoreOpen(false); }}>
      <section ref={drawerRef} tabIndex={-1} className="app-more-drawer" role="dialog" aria-modal="true" aria-label="Menu">
        <header><h2>Menu</h2><button className="app-icon-button" onClick={() => setMoreOpen(false)} aria-label="Close"><Icon name="x" size={16}/></button></header>
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

    <div className={`app-main${sidebarCollapsed ? " app-main--collapsed" : ""}`}>
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
      <main className="app-content">
        {isSupportSession && <SupportSessionBanner />}
        {!user?.platformRole && <TrialExpiredBanner />}
        <Outlet />
      </main>    </div>
  </div>;
}
