import { Icon } from "../../../components/ui/Icon";
import { PageHeader } from "../../../design-system/components";

export interface GenericWorkspaceProps {
  name: string;
  userType: string;
}

export function GenericWorkspace({ name, userType }: GenericWorkspaceProps) {
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
        "Application status, required documents and next steps will appear here when admissions services are connected.",
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
    <div className="workspace-dashboard">
      <PageHeader title={`Welcome, ${name}.`} description={content.title} />
      <section className="student-panel workspace-state">
        <span className="student-panel__chevron">
          <Icon name="home" size={20} />
        </span>
        <h2>Your workspace is ready</h2>
        <p>
          {content.description} Available actions remain controlled by your assigned role and
          permissions.
        </p>
      </section>
    </div>
  );
}
