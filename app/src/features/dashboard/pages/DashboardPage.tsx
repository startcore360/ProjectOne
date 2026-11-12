import { useState } from "react";
import { useAppSelector } from "../../../store/hooks";
import { roleDashboardByCode } from "../../role-previews/roleDashboardCatalog";
import {
  StudentDashboard,
  GenericWorkspace,
  RoleWorkspace,
} from "../components";
import { GuardianChildSwitcher } from "../../user-management/components/common/GuardianChildSwitcher";
import { PageHeader } from "../../../components/layout/PageHeader";

export { StudentDashboard };

export default function DashboardPage() {
  const user = useAppSelector((s) => s.auth.user);
  const name = user?.displayName?.trim() || "there";
  const [selectedChildId, setSelectedChildId] = useState("");

  if (user?.userType === "STUDENT") {
    return <StudentDashboard name={name} />;
  }

  if (user?.userType === "GUARDIAN") {
    return <div className="platform-page">
      <PageHeader title="Parent dashboard" description="Choose a child linked to your account. Child details are read-only." />
      <GuardianChildSwitcher value={selectedChildId} onChange={setSelectedChildId} />
    </div>;
  }

  if (user?.roleCode && roleDashboardByCode(user.roleCode)) {
    return <RoleWorkspace name={name} roleCode={user.roleCode} />;
  }

  return <GenericWorkspace name={name} userType={user?.userType ?? "SYSTEM"} />;
}
