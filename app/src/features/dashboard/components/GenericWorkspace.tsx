import { useNavigate } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import { PageHeader } from "../../../design-system/components";
import { Button } from "../../../design-system/components";

export interface GenericWorkspaceProps {
  name: string;
  userType: string;
}

export function GenericWorkspace({ name, userType }: GenericWorkspaceProps) {
  const navigate = useNavigate();
  const labels: Record<string, { title: string; description: string; scope: string }> = {
    STAFF: {
      title: "Your work, in one place.",
      description:
        "Your classes, responsibilities and institution updates will appear here as connected services become available.",
      scope: "Staff workspace",
    },
    GUARDIAN: {
      title: "A clearer view of learning.",
      description:
        "Linked student progress, attendance, notices and fee information will appear here when available to your account.",
      scope: "Family workspace",
    },
    APPLICANT: {
      title: "Your application journey.",
      description:
        "Start or continue your admission application, upload supporting documents and track scrutiny and fee status.",
      scope: "Applicant workspace",
    },
    GOVERNANCE: {
      title: "Governance at a glance.",
      description:
        "Items assigned to your governance role will appear here, within your authorised scope.",
      scope: "Governance workspace",
    },
    SYSTEM: {
      title: "Your workspace.",
      description:
        "Your assigned services and permitted actions will appear here when connected.",
      scope: "System workspace",
    },
  };
  const content = labels[userType] ?? {
    title: "Your workspace.",
    description:
      "Your assigned services and permitted actions will appear here as they become available.",
    scope: "Institution workspace",
  };
  return (
    <div className="app-dashboard">
      <PageHeader title={`Welcome, ${name}.`} description={content.title} />
      <section className="app-dash-card app-welcome-state">
        <span className="app-dash-card__chevron">
          <Icon name="home" size={20} />
        </span>
        <h2>Your workspace is ready</h2>
        <p>
          {content.description} Available actions remain controlled by your assigned role and
          permissions.
        </p>
        {userType === "APPLICANT" && (
          <Button onClick={() => navigate("/admissions/apply")}>Open applicant portal</Button>
        )}
      </section>
    </div>
  );
}
