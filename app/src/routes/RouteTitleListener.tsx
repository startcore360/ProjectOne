import { useEffect } from "react";
import { useLocation } from "react-router-dom";

const APP_NAME = "EDU 360";

const ROUTE_TITLES: Array<{ pattern: RegExp; title: string }> = [
  // Auth
  { pattern: /^\/login\/?$/, title: "Sign In" },
  { pattern: /^\/change-password\/?$/, title: "Change Password" },

  // Platform console
  { pattern: /^\/platform\/?$/, title: "Platform Overview" },
  { pattern: /^\/platform\/tenants\/?$/, title: "Tenants" },
  { pattern: /^\/platform\/plans\/?$/, title: "Plans & Packages" },
  { pattern: /^\/platform\/invoices\/?$/, title: "Invoices & Billing" },
  { pattern: /^\/platform\/releases\/?$/, title: "Releases & Deployments" },
  { pattern: /^\/platform\/reports\/?$/, title: "Reports" },
  { pattern: /^\/platform\/security-policy\/?$/, title: "Security Policy" },
  { pattern: /^\/platform\/tickets\/?$/, title: "Support Tickets" },
  { pattern: /^\/platform\/users\/?$/, title: "Platform Users" },
  { pattern: /^\/platform\/roles\/?$/, title: "Platform Roles" },
  { pattern: /^\/platform\/role-templates\/?$/, title: "Role Templates" },
  { pattern: /^\/platform\/support\/?$/, title: "Support Access" },
  { pattern: /^\/platform\/audit\/?$/, title: "Audit Log" },
  { pattern: /^\/platform\/usage-counts\/?$/, title: "Usage Counts" },
  { pattern: /^\/platform\/backup-status\/?$/, title: "Backup Status" },
  { pattern: /^\/platform\/integration-types\/?$/, title: "Integration Types" },
  { pattern: /^\/platform\/data-protection\/?$/, title: "Platform Data Protection" },
  { pattern: /^\/platform\/landing\/?$/, title: "Landing & Enquiries" },

  // University Admin
  { pattern: /^\/university\/institutions\/[^/]+\/appraisals\/?$/, title: "Staff Appraisals" },
  { pattern: /^\/university\/institutions\/[^/]+\/?$/, title: "Institution Details" },
  { pattern: /^\/university\/institutions\/?$/, title: "Institutions" },
  { pattern: /^\/university\/setup\/hierarchy\/?$/, title: "Institution Hierarchy" },
  { pattern: /^\/university\/setup\/structure\/[^/]+\/departments\/[^/]+\/?$/, title: "Department Details" },
  { pattern: /^\/university\/setup\/structure\/[^/]+\/programs\/[^/]+\/?$/, title: "Program Details" },
  { pattern: /^\/university\/setup\/structure\/[^/]+\/sections\/[^/]+\/?$/, title: "Section Details" },
  { pattern: /^\/university\/setup\/structure\/[^/]+\/academic-sessions\/[^/]+\/?$/, title: "Academic Session Details" },
  { pattern: /^\/university\/setup\/structure\/?$/, title: "Academic Structure" },
  { pattern: /^\/university\/staff\/[^/]+\/?$/, title: "Staff Profile" },
  { pattern: /^\/university\/staff\/?$/, title: "Staff Directory" },
  { pattern: /^\/university\/users\/?$/, title: "User Management" },
  { pattern: /^\/university\/roles\/?$/, title: "Roles & Permissions" },
  { pattern: /^\/university\/missing-roles\/?$/, title: "Missing Roles" },
  { pattern: /^\/university\/setup\/mentor\/?$/, title: "Mentor Assignment" },
  { pattern: /^\/university\/setup\/class-teacher\/?$/, title: "Class Teacher Assignment" },
  { pattern: /^\/university\/setup\/student-login\/?$/, title: "Student Login Switch" },
  { pattern: /^\/university\/setup\/calendar-events\/?$/, title: "Academic Calendar Events" },
  { pattern: /^\/university\/setup\/weekly-off\/?$/, title: "Weekly Off Rules" },
  { pattern: /^\/university\/setup\/term-lock\/?$/, title: "Term Lock" },
  { pattern: /^\/university\/setup\/delegations\/?$/, title: "Delegations & Approvals" },
  { pattern: /^\/university\/setup\/academic-governance\/?$/, title: "Academic Governance" },

  // Tenants & Students
  { pattern: /^\/tenants\/users\/[^/]+\/?$/, title: "Tenant User Details" },
  { pattern: /^\/tenants\/users\/?$/, title: "Tenant Users" },
  { pattern: /^\/tenants\/students\/[^/]+\/guardians\/[^/]+\/?$/, title: "Student Guardian Details" },
  { pattern: /^\/tenants\/students\/[^/]+\/notes\/[^/]+\/?$/, title: "Student Note Details" },
  { pattern: /^\/tenants\/students\/[^/]+\/legal-holds\/[^/]+\/?$/, title: "Student Legal Hold Details" },
  { pattern: /^\/tenants\/students\/[^/]+\/?$/, title: "Student Profile" },
  { pattern: /^\/tenants\/students\/?$/, title: "Student Directory" },
  { pattern: /^\/tenant\/data-protection\/?$/, title: "Data Protection" },

  // Common Tenant Views
  { pattern: /^\/dashboard\/?$/, title: "Dashboard" },
  { pattern: /^\/admissions\/apply\/?$/, title: "Admissions Application" },
  { pattern: /^\/admissions\/cycles\/?$/, title: "Admission Cycles" },
  { pattern: /^\/admissions\/scrutiny\/?$/, title: "Application Scrutiny" },
  { pattern: /^\/admissions\/merit\/?$/, title: "Merit Lists" },
  { pattern: /^\/support\/?$/, title: "Support" },
  { pattern: /^\/role-previews?\/?/, title: "Role Preview" },
  { pattern: /^\/profile\/?$/, title: "Profile" },
  { pattern: /^\/self-service\/?$/, title: "Self-Service" },
  { pattern: /^\/sessions\/?$/, title: "Sessions" },
];

// eslint-disable-next-line react-refresh/only-export-components
export function getTitleForPath(pathname: string): string {
  for (const route of ROUTE_TITLES) {
    if (route.pattern.test(pathname)) {
      return `${route.title} — ${APP_NAME}`;
    }
  }
  return APP_NAME;
}

/**
 * RouteTitleListener automatically updates document.title based on the current location.
 */
export function RouteTitleListener(): null {
  const { pathname } = useLocation();

  useEffect(() => {
    const previousTitle = document.title;
    document.title = getTitleForPath(pathname);

    return () => {
      document.title = previousTitle;
    };
  }, [pathname]);

  return null;
}
