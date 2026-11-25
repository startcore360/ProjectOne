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
import ApplicantPortalPage from "../../admissions/pages/ApplicantPortalPage";
import { renderGreeting, formatGreetingName } from "../../../utils/greeting";

export { StudentDashboard };

export default function DashboardPage() {
  const user = useAppSelector((s) => s.auth.user);
  const name = formatGreetingName(user?.displayName) || "there";
  const [selectedChildId, setSelectedChildId] = useState("");

  if (user?.userType === "APPLICANT") {
    return <ApplicantPortalPage />;
  }

  if (user?.userType === "STUDENT") {
    return <StudentDashboard name={name} tenantId={user.tenantId ?? ""} />;
  }

  if (user?.userType === "GUARDIAN") {
    return <div className="app-page">
      <PageHeader title={renderGreeting(user.displayName)} description="Parent dashboard · Choose a child linked to your account. Only linked student code and status are currently available." />
      <GuardianChildSwitcher value={selectedChildId} onChange={setSelectedChildId} />
    </div>;
  }

  if (user?.roleCode && roleDashboardByCode(user.roleCode)) {
    return <RoleWorkspace name={name} roleCode={user.roleCode} />;
  }

  return <GenericWorkspace name={name} userType={user?.userType ?? "SYSTEM"} />;
}
