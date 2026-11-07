import { useAppSelector } from "../../../store/hooks";
import { roleDashboardByCode } from "../../role-previews/roleDashboardCatalog";
import {
  StudentDashboard,
  GenericWorkspace,
  RoleWorkspace,
} from "../components";

export { StudentDashboard };

export default function DashboardPage() {
  const user = useAppSelector((s) => s.auth.user);
  const name = user?.displayName?.trim() || "there";

  if (user?.userType === "STUDENT") {
    return <StudentDashboard name={name} />;
  }

  if (user?.roleCode && roleDashboardByCode(user.roleCode)) {
    return <RoleWorkspace name={name} roleCode={user.roleCode} />;
  }

  return <GenericWorkspace name={name} userType={user?.userType ?? "SYSTEM"} />;
}
