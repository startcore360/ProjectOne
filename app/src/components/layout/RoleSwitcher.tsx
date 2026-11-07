import { useEffect, useState } from "react";
import { Badge } from "../ui";
import { meService, type UserRoleOption } from "../../features/profile/api/meService";
import { useAppDispatch, useAppSelector } from "../../store/hooks";
import { switchActiveRole } from "../../store/slices/authSlice";

export function RoleSwitcher() {
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const [roles, setRoles] = useState<UserRoleOption[]>([]);
  const [currentId, setCurrentId] = useState<string>("");
  const [switching, setSwitching] = useState(false);

  useEffect(() => {
    if (!user?.tenantId) return;
    let mounted = true;
    meService
      .getMe()
      .then(({ data }) => {
        if (!mounted) return;
        setRoles(data.roles || []);
        if (data.currentAssignmentId) {
          setCurrentId(data.currentAssignmentId);
        } else if (data.roles && data.roles.length > 0) {
          setCurrentId(data.roles[0].userRoleAssignmentId);
        }
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
    } catch {
      // Retain prior on error
    } finally {
      setSwitching(false);
    }
  };

  if (!user || user.platformRole) {
    return null;
  }

  if (roles.length > 1) {
    return (
      <div
        className="role-switcher"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "8px",
          fontSize: "0.8125rem",
        }}
      >
        <span style={{ color: "var(--color-muted)", fontWeight: 500 }}>Active Role:</span>
        <select
          aria-label="Switch active role"
          value={currentId}
          disabled={switching}
          onChange={(e) => void handleChange(e.target.value)}
          style={{
            padding: "3px 8px",
            fontSize: "0.8125rem",
            borderRadius: "var(--radius-md, 4px)",
            border: "1px solid var(--color-border, #cbd5e1)",
            backgroundColor: "var(--color-surface, #ffffff)",
            color: "var(--color-text, #1e293b)",
            cursor: "pointer",
          }}
        >
          {roles.map((r) => (
            <option key={r.userRoleAssignmentId} value={r.userRoleAssignmentId}>
              {r.roleName} ({r.scopeType})
            </option>
          ))}
        </select>
      </div>
    );
  }

  const roleLabel = user.roleName || user.roleCode || user.userType || "Member";
  return <Badge tone="neutral">{roleLabel}</Badge>;
}
