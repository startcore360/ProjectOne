import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Badge } from "../ui";
import { meService, type UserRoleOption } from "../../features/profile/api/meService";
import { useAppDispatch, useAppSelector } from "../../store/hooks";
import { switchActiveRole } from "../../store/slices/authSlice";
import { ACTIVE_ROLE_REQUIRED_EVENT } from "../../services/activeRoleRequired";

type RoleSwitcherProps = {
  /**
   * "inline" (default): label and select on one row, for the desktop top bar.
   * "stacked": label above a full-width select, for the mobile Menu drawer.
   * Styling lives in design-system/components.css (.role-switcher*), token-only.
   */
  layout?: "inline" | "stacked";
};

export function RoleSwitcher({ layout = "inline" }: RoleSwitcherProps = {}) {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const user = useAppSelector((state) => state.auth.user);
  // After any successful role switch, land on the dashboard: the page the user was on may not
  // exist (or not be permitted) for the new role, and the dashboard is that role's home.
  const goToDashboard = () => navigate("/dashboard", { replace: false });
  const [roles, setRoles] = useState<UserRoleOption[]>([]);
  const [currentId, setCurrentId] = useState<string>("");
  const [switching, setSwitching] = useState(false);
  const selectRef = useRef<HTMLSelectElement | null>(null);

  // A-02: when the server answers 409 ACTIVE_ROLE_REQUIRED, bring the user to this switcher.
  useEffect(() => {
    const focusSwitcher = () => selectRef.current?.focus();
    window.addEventListener(ACTIVE_ROLE_REQUIRED_EVENT, focusSwitcher);
    return () => window.removeEventListener(ACTIVE_ROLE_REQUIRED_EVENT, focusSwitcher);
  }, []);

  useEffect(() => {
    if (!user?.tenantId) return;
    let mounted = true;
    meService
      .getMe()
      .then(({ data }) => {
        if (!mounted) return;
        setRoles(data.roles || []);
        // A-02: with several roles and no saved choice the server treats no role as active, so
        // none is pre-selected here either; the user must choose one.
        setCurrentId(data.currentAssignmentId ?? "");
      })
      .catch(() => {
        // Non-fatal if offline or demo
      });

    return () => {
      mounted = false;
    };
  }, [user?.tenantId]);

  const handleChange = async (newAssignmentId: string) => {
    if (newAssignmentId === currentId || switching) return;
    setSwitching(true);
    try {
      const { data } = await meService.switchActiveRole(newAssignmentId);
      setCurrentId(data.assignmentId);
      dispatch(
        switchActiveRole({
          roleCode: data.roleCode,
          roleName: data.roleName,
        })
      );
      goToDashboard();
    } catch {
      // Retain prior on error
    } finally {
      setSwitching(false);
    }
  };

  if (!user || user.platformRole) {
    return null;
  }

  const className = `role-switcher role-switcher--${layout}`;

  if (roles.length > 1) {
    return (
      <label className={className}>
        <span className="role-switcher__label">Active role</span>
        <select
          className="role-switcher__select"
          aria-label="Switch active role"
          ref={selectRef}
          value={currentId}
          disabled={switching}
          aria-invalid={currentId === "" ? true : undefined}
          onChange={(e) => void handleChange(e.target.value)}
        >
          {currentId === "" && (
            <option value="" disabled>
              Choose your active role
            </option>
          )}
          {roles.map((r) => (
            <option key={r.userRoleAssignmentId} value={r.userRoleAssignmentId}>
              {r.roleName} ({r.scopeType})
            </option>
          ))}
        </select>
      </label>
    );
  }

  // In DEV mode (outside automated test runs), allow switching active admin roles for convenient testing
  if (import.meta.env.DEV && !import.meta.env.VITEST) {
    const devAdminRoles = [
      { code: "UNIVERSITY_ADMIN", name: "University Admin" },
      { code: "COLLEGE_ADMIN", name: "College Admin" },
      { code: "SCHOOL_ADMIN", name: "School Admin" },
    ];
    const currentCode = user.roleCode || "UNIVERSITY_ADMIN";

    return (
      <label className={className}>
        <span className="role-switcher__label">Role</span>
        <select
          className="role-switcher__select"
          aria-label="Switch active role"
          value={currentCode}
          onChange={(e) => {
            const found = devAdminRoles.find((r) => r.code === e.target.value);
            if (found) {
              dispatch(switchActiveRole({ roleCode: found.code, roleName: found.name }));
              goToDashboard();
            }
          }}
        >
          {devAdminRoles.map((r) => (
            <option key={r.code} value={r.code}>
              {r.name}
            </option>
          ))}
        </select>
      </label>
    );
  }

  const roleLabel = user.roleName || user.roleCode || user.userType || "Member";
  if (layout === "stacked") {
    return (
      <div className={className}>
        <span className="role-switcher__label">Role</span>
        <Badge tone="neutral">{roleLabel}</Badge>
      </div>
    );
  }
  return <Badge tone="neutral">{roleLabel}</Badge>;
}
