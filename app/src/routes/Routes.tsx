import { Suspense, lazy } from "react";
import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { useAppSelector } from "../store/hooks";
import AuthLayout from "../layout/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import { SkeletonPage } from "../design-system/components";
import RouteErrorBoundary from "./RouteErrorBoundary";
import { PLATFORM_PAGE_ACTIONS, canOpenPlatformConsole, platformLandingPath } from "../features/platform/platformAccess";
import PlatformConsoleRoute, { PlatformPageGate } from "./PlatformConsoleRoute";
import PrivateRoute from "./PrivateRoute";
import PublicRoute from "./PublicRoute";
import ActionGate from "./ActionGate";
import TenantUserRoute from "./TenantUserRoute";
import { TENANT_ROUTE_ACTIONS } from "./tenantRouteActions";
import { isSharedLandingHost } from "../lib/tenantHost";
import { useSupportSession } from "../features/support/useSupportSession";

const AppLayout = lazy(() => import("../layout/AppLayout"));
const LandingPage = lazy(() => import("../features/landing/LandingPage"));

const ApplicantPortalPage = lazy(() => import("../features/admissions/pages/ApplicantPortalPage"));
const AdmissionCyclesPage = lazy(() => import("../features/admissions/pages/AdmissionCyclesPage"));
const ApplicationScrutinyPage = lazy(() => import("../features/admissions/pages/ApplicationScrutinyPage"));
const MeritListGenerationPage = lazy(() => import("../features/admissions/pages/MeritListGenerationPage"));

const StudentDirectoryPage = lazy(() => import("../features/students/pages/StudentDirectoryPage").then((m) => ({ default: m.StudentDirectoryPage })));
const StudentDetailPage = lazy(() => import("../features/students/pages/StudentDirectoryPage").then((m) => ({ default: m.StudentDetailPage })));
const StudentSubrecordDetailPage = lazy(() => import("../features/students/pages/StudentDirectoryPage").then((m) => ({ default: m.StudentSubrecordDetailPage })));

// Lazy-loaded feature pages
const ChangePasswordPage = lazy(() => import("../features/auth/pages/ChangePasswordPage"));
const DashboardPage = lazy(() => import("../features/dashboard/pages/DashboardPage"));
const RoleDashboardPage = lazy(() => import("../features/role-previews/RoleDashboardPage"));
const ProfilePage = lazy(() => import("../features/profile/pages/ProfilePage"));
const SelfServicePage = lazy(() => import("../features/self-service/pages/SelfServicePage"));
const NoticeBulletinPage = lazy(() => import("../features/t10/pages/NoticeBulletinPage"));
const MessageComposerPage = lazy(() => import("../features/t10/pages/MessageComposerPage"));
const LibraryCatalogPage = lazy(() => import("../features/t10/pages/LibraryCatalogPage"));
const TransportRoutePage = lazy(() => import("../features/t10/pages/TransportRoutePage"));
const ExecutiveDashboardPage = lazy(() => import("../features/t10/pages/ExecutiveDashboardPage"));
const SessionsPage = lazy(() => import("../features/sessions/pages/SessionsPage"));
const FeeStructureManagementPage = lazy(() => import("../features/finance/pages/FeeStructureManagementPage"));
const StudentFeeInvoicesPage = lazy(() => import("../features/finance/pages/StudentFeeInvoicesPage"));
const FeePaymentPortalPage = lazy(() => import("../features/finance/pages/FeePaymentPortalPage"));
const IdCardManagementPage = lazy(() => import("../features/documents/pages/IdCardManagementPage"));
const CertificateRequestPage = lazy(() => import("../features/documents/pages/CertificateRequestPage"));
const PublicCertificateVerificationPage = lazy(() => import("../features/documents/pages/PublicCertificateVerificationPage"));
const GrievanceCaseManagementPage = lazy(() => import("../features/governance/pages/GrievanceCaseManagementPage"));
const DisciplinaryIncidentManagementPage = lazy(() => import("../features/governance/pages/DisciplinaryIncidentManagementPage"));
const TenantSupportPage = lazy(() => import("../features/institution-admin/pages/TenantSupportPage"));
const StaffRecordsPage = lazy(() => import("../features/institution-admin/pages/StaffRecordsPage"));
const StaffAppraisalPage = lazy(() => import("../features/staff/pages/StaffAppraisalPage"));
const TenantDataProtectionPage = lazy(() => import("../features/dataprotection/TenantDataProtectionPage"));

// Lazy-loaded platform pages
const PlatformOverviewPage = lazy(() => import("../features/platform/components/overview/PlatformOverviewView").then((m) => ({ default: m.PlatformOverviewPage })));
const TenantsPage = lazy(() => import("../features/platform/components/tenants/TenantsView").then((m) => ({ default: m.TenantsPage })));
const PlansPage = lazy(() => import("../features/platform/components/plans/PlansView").then((m) => ({ default: m.PlansPage })));
const InvoicesPage = lazy(() => import("../features/platform/components/billing/InvoicesView").then((m) => ({ default: m.InvoicesPage })));
const ReleasesPage = lazy(() => import("../features/platform/components/release/ReleasesView").then((m) => ({ default: m.ReleasesPage })));
const ReportsPage = lazy(() => import("../features/platform/components/reports/ReportsView").then((m) => ({ default: m.ReportsPage })));
const SecurityPolicyPage = lazy(() => import("../features/platform/components/security/SecurityPolicyView").then((m) => ({ default: m.SecurityPolicyPage })));
const TicketsPage = lazy(() => import("../features/platform/components/support/TicketsView").then((m) => ({ default: m.TicketsPage })));
const PlatformUsersPage = lazy(() => import("../features/platform/components/users/PlatformUsersView").then((m) => ({ default: m.PlatformUsersPage })));
const PlatformRolesPage = lazy(() => import("../features/platform/components/roles/RolesView").then((m) => ({ default: m.PlatformRolesPage })));
const PlatformRoleTemplatesPage = lazy(() => import("../features/platform/components/roles/TemplatesView").then((m) => ({ default: m.PlatformRoleTemplatesPage })));
const SupportAccessPage = lazy(() => import("../features/platform/components/support/SupportAccessView").then((m) => ({ default: m.SupportAccessPage })));
const AuditLogPage = lazy(() => import("../features/platform/components/observability/AuditLogView").then((m) => ({ default: m.AuditLogPage })));
const UsageCountsPage = lazy(() => import("../features/platform/components/observability/UsageCountsView").then((m) => ({ default: m.UsageCountsPage })));
const BackupStatusPage = lazy(() => import("../features/platform/components/observability/BackupStatusView").then((m) => ({ default: m.BackupStatusPage })));
const IntegrationTypesPage = lazy(() => import("../features/platform/components/integrations/IntegrationTypesView").then((m) => ({ default: m.IntegrationTypesPage })));
const DataProtectionWorkspacePage = lazy(() => import("../features/platform/components/dataprotection/DataProtectionWorkspaceView").then((m) => ({ default: m.DataProtectionWorkspacePage })));
const PlatformLandingCmsPage = lazy(() => import("../features/platform/components/landing/PlatformLandingCmsView").then((m) => ({ default: m.PlatformLandingCmsPage })));

// Lazy-loaded institution admin pages
const InstitutionsPage = lazy(() => import("../features/institution-admin/pages/InstitutionAdminPages").then((m) => ({ default: m.InstitutionsPage })));

const InstitutionDetailPage = lazy(() => import("../features/institution-admin/pages/InstitutionDetailPage"));
const DepartmentDetailPage = lazy(() => import("../features/institution-admin/pages/DepartmentDetailPage"));
const ProgramDetailPage = lazy(() => import("../features/institution-admin/pages/ProgramDetailPage"));
const SectionDetailPage = lazy(() => import("../features/institution-admin/pages/SectionDetailPage"));
const AcademicSessionDetailPage = lazy(() => import("../features/institution-admin/pages/AcademicSessionDetailPage"));
const AssignmentManagementPage = lazy(() => import("../features/academic/pages/AssignmentManagementPage"));
const ExamSchedulePage = lazy(() => import("../features/academic/pages/ExamSchedulePage"));
const TimetableGridView = lazy(() => import("../features/academic/pages/TimetableGridView"));
const AttendanceRegisterPage = lazy(() => import("../features/academic/pages/AttendanceRegisterPage"));
const StudentMedicalExemptionPage = lazy(() => import("../features/academic/pages/StudentMedicalExemptionPage"));
const MedicalExemptionReviewPage = lazy(() => import("../features/academic/pages/MedicalExemptionReviewPage"));
const MarksEntrySheetPage = lazy(() => import("../features/academic/pages/MarksEntrySheetPage"));
const ReportCardViewerPage = lazy(() => import("../features/academic/pages/ReportCardViewerPage"));

const MissingRolesPage = lazy(() => import("../features/institution-admin/pages/InstitutionAdminPages").then((m) => ({ default: m.MissingRolesPage })));
const RolesPermissionsPage = lazy(() => import("../features/institution-admin/pages/InstitutionAdminPages").then((m) => ({ default: m.RolesPermissionsPage })));

// Lazy-loaded institution setup pages
const AcademicGovernancePage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.AcademicGovernancePage })));
const AcademicStructurePage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.AcademicStructurePage })));
const InstitutionHierarchyPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.InstitutionHierarchyPage })));
const CalendarEventsPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.CalendarEventsPage })));
const DelegationsApprovalsPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.DelegationsApprovalsPage })));
const ClassTeacherAssignmentPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.ClassTeacherAssignmentPage })));
const MentorAssignmentPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.MentorAssignmentPage })));
const StudentLoginSwitchPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.StudentLoginSwitchPage })));
const TermLockPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.TermLockPage })));
const WeeklyOffRulesPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.WeeklyOffRulesPage })));

// Lazy-loaded user management pages
const UserListPage = lazy(() => import("../features/user-management/pages/UserManagementPages").then((m) => ({ default: m.UserListPage })));
const UserDetailPage = lazy(() => import("../features/user-management/pages/UserManagementPages").then((m) => ({ default: m.UserDetailPage })));

function LazyRouteView() {
  return (
    <RouteErrorBoundary>
      <Suspense
        fallback={
          <SkeletonPage title="Loading page" showKpis showTable tableRows={5} tableColumns={5} />
        }
      >
        <Outlet />
      </Suspense>
    </RouteErrorBoundary>
  );
}

// AuthUserResponse (api/.../auth/dto/AuthUserResponse.java) only ever populates platformRole
// for a platform identity, or tenantId+userType for a tenant identity - never a generic "role"
// string. platformRole is real, so this branch can be checked for real.
function RoleHomeRedirect() {
  const platformRole = useAppSelector((state) => state.auth.user?.platformRole);
  if (canOpenPlatformConsole(platformRole)) return <Navigate to={platformLandingPath(platformRole)} replace />;
  // There is no tenant-side "university admin" role on the backend yet (Foundation item 7,
  // not yet built - no role field exists on AuthUserResponse beyond userType), so that branch
  // can't be satisfied by any real login right now. Default everyone else to the one route
  // that actually exists - do NOT point this at "/dashboard/workspace", which doesn't exist
  // and broke login entirely before this fix.
  return <Navigate to="/dashboard" replace />;
}

function RootEntry() {
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);
  const mustChangePassword = useAppSelector((state) => state.auth.user?.mustChangePassword);
  const { isSupportSession } = useSupportSession();

  if (isAuthenticated || isSupportSession) {
    if (!isSupportSession && mustChangePassword) {
      return <Navigate to="/change-password" replace />;
    }
    return <RoleHomeRedirect />;
  }

  if (typeof window !== "undefined" && isSharedLandingHost(window.location.hostname)) {
    return (
      <RouteErrorBoundary>
        <Suspense fallback={<SkeletonPage title="Loading EDU 360" />}>
          <LandingPage />
        </Suspense>
      </RouteErrorBoundary>
    );
  }

  return <Navigate to="/login" replace />;
}

// Server-backed authorization for tenant and university administration routes.
// The backend enforces each endpoint independently via PermissionEvaluatorService.
function UniversityAdminRoute() {
  const userType = useAppSelector((s) => s.auth.user?.userType);
  const location = useLocation();
  if ((userType === "STUDENT" || userType === "GUARDIAN") && !["/student/medical-exemptions", "/academic/timetable"].includes(location.pathname)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<RootEntry />} />
      <Route element={<LazyRouteView />}><Route path="/verify/certificate" element={<PublicCertificateVerificationPage />} /></Route>
      <Route element={<PublicRoute />}>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
        </Route>
      </Route>

      <Route element={<PrivateRoute />}>
        <Route element={<LazyRouteView />}>
          <Route path="/change-password" element={<ChangePasswordPage />} />
        </Route>
        <Route element={<UniversityAdminRoute />}>
          <Route element={<AppLayout />}>
            <Route element={<LazyRouteView />}>
              <Route path="/university/institutions" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.institutions}><InstitutionsPage /></ActionGate>} />
              <Route path="/university/setup/hierarchy" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.institutions}><InstitutionHierarchyPage /></ActionGate>} />

              <Route path="/university/institutions/:institutionId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.institutions}><InstitutionDetailPage /></ActionGate>} />
              <Route path="/university/setup/structure/:institutionId/departments/:departmentId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicStructure}><DepartmentDetailPage /></ActionGate>} />
              <Route path="/university/setup/structure/:institutionId/programs/:programId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicStructure}><ProgramDetailPage /></ActionGate>} />
              <Route path="/university/setup/structure/:institutionId/sections/:sectionId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicStructure}><SectionDetailPage /></ActionGate>} />
              <Route path="/university/setup/structure/:institutionId/academic-sessions/:academicSessionId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicStructure}><AcademicSessionDetailPage /></ActionGate>} />

              <Route path="/university/staff/:staffId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.staffRecords}><StaffRecordsPage /></ActionGate>} />
              <Route path="/university/staff" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.staffRecords}><StaffRecordsPage /></ActionGate>} />
              <Route path="/university/institutions/:institutionId/appraisals" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.staffRecords}><StaffAppraisalPage /></ActionGate>} />
              <Route path="/university/users" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.users}><UserListPage /></ActionGate>} />
              <Route path="/university/roles" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.roles}><RolesPermissionsPage /></ActionGate>} />
              <Route path="/university/missing-roles" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.missingRoles}><MissingRolesPage /></ActionGate>} />
              <Route path="/university/setup/mentor" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.mentorAssignment}><MentorAssignmentPage /></ActionGate>} />
              <Route path="/university/setup/class-teacher" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.classTeacherAssignment}><ClassTeacherAssignmentPage /></ActionGate>} />
              <Route path="/university/setup/student-login" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.studentLogin}><StudentLoginSwitchPage /></ActionGate>} />
              <Route path="/university/setup/calendar-events" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.calendarEvents}><CalendarEventsPage /></ActionGate>} />
              <Route path="/university/setup/weekly-off" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.weeklyOff}><WeeklyOffRulesPage /></ActionGate>} />
              <Route path="/university/setup/structure" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicStructure}><AcademicStructurePage /></ActionGate>} />
              <Route path="/university/setup/term-lock" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.termLock}><TermLockPage /></ActionGate>} />
              <Route path="/university/setup/delegations" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.delegations}><DelegationsApprovalsPage /></ActionGate>} />
              <Route path="/university/setup/academic-governance" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicGovernance}><AcademicGovernancePage /></ActionGate>} />
              <Route path="/academic/assignments" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.assignments}><AssignmentManagementPage /></ActionGate>} />
              <Route path="/academic/exams" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.exams}><ExamSchedulePage /></ActionGate>} />
              <Route path="/academic/timetable" element={<ActionGate anyOf={["TIMETABLE_VIEW", "TIMETABLE_BUILD"]}><TimetableGridView /></ActionGate>} />
              <Route path="/academic/attendance" element={<ActionGate anyOf={["ATTENDANCE_VIEW", "ATTENDANCE_MARK"]}><AttendanceRegisterPage /></ActionGate>} />
              <Route path="/student/medical-exemptions" element={<ActionGate anyOf={["STUDENT_LEAVE_APPLY"]}><StudentMedicalExemptionPage /></ActionGate>} />
              <Route path="/academic/medical-exemptions/review" element={<ActionGate anyOf={["ATTENDANCE_CORRECTION_APPROVE"]}><MedicalExemptionReviewPage /></ActionGate>} />
              <Route path="/academic/marks" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.marks}><MarksEntrySheetPage /></ActionGate>} />
              <Route path="/academic/report-cards" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.reportCards}><ReportCardViewerPage /></ActionGate>} />
              <Route path="/tenants/users" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.users}><UserListPage /></ActionGate>} />
              <Route path="/tenants/users/:userId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.users}><UserDetailPage /></ActionGate>} />
              <Route path="/tenants/students" element={<ActionGate anyOf={["STUDENT_SEARCH"]}><StudentDirectoryPage /></ActionGate>} />
              <Route path="/tenants/students/:studentId" element={<ActionGate anyOf={["STUDENT_SEARCH"]}><StudentDetailPage /></ActionGate>} />
              <Route path="/tenants/students/:studentId/guardians/:recordId" element={<ActionGate anyOf={["STUDENT_SEARCH"]}><StudentSubrecordDetailPage /></ActionGate>} />
              <Route path="/tenants/students/:studentId/notes/:recordId" element={<ActionGate anyOf={["STUDENT_SEARCH"]}><StudentSubrecordDetailPage /></ActionGate>} />
              <Route path="/tenants/students/:studentId/legal-holds/:recordId" element={<ActionGate anyOf={["STUDENT_SEARCH"]}><StudentSubrecordDetailPage /></ActionGate>} />
              <Route path="/tenant/data-protection" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.dataProtection}><TenantDataProtectionPage /></ActionGate>} />
            </Route>
          </Route>
        </Route>
        <Route element={<PlatformConsoleRoute />}>
          <Route element={<AppLayout />}>
            <Route element={<LazyRouteView />}>
              <Route path="/platform" element={<PlatformOverviewPage />} />
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.tenants} />}>
                <Route path="/platform/tenants" element={<TenantsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.plans} />}>
                <Route path="/platform/plans" element={<PlansPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.invoices} />}>
                <Route path="/platform/invoices" element={<InvoicesPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.releases} />}>
                <Route path="/platform/releases" element={<ReleasesPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.reports} />}>
                <Route path="/platform/reports" element={<ReportsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.security} />}>
                <Route path="/platform/security-policy" element={<SecurityPolicyPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.tickets} />}>
                <Route path="/platform/tickets" element={<TicketsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.staff} />}>
                <Route path="/platform/users" element={<PlatformUsersPage />} />
                <Route path="/platform/roles" element={<PlatformRolesPage />} />
                <Route path="/platform/role-templates" element={<PlatformRoleTemplatesPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.support} />}>
                <Route path="/platform/support" element={<SupportAccessPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.audit} />}>
                <Route path="/platform/audit" element={<AuditLogPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.usage} />}>
                <Route path="/platform/usage-counts" element={<UsageCountsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.backup} />}>
                <Route path="/platform/backup-status" element={<BackupStatusPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.integrations} />}>
                <Route path="/platform/integration-types" element={<IntegrationTypesPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.dataProtection} />}>
                <Route path="/platform/data-protection" element={<DataProtectionWorkspacePage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.landing} />}>
                <Route path="/platform/landing" element={<PlatformLandingCmsPage />} />
              </Route>
            </Route>
          </Route>
        </Route>
        <Route element={<AppLayout />}>
          <Route element={<LazyRouteView />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/admissions/apply" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.applicantPortal}><ApplicantPortalPage /></ActionGate>} />
            <Route path="/admissions/cycles" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.admissionCycles}><AdmissionCyclesPage /></ActionGate>} />
            <Route path="/admissions/scrutiny" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.applicationScrutiny}><ApplicationScrutinyPage /></ActionGate>} />
            <Route path="/admissions/merit" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.meritLists}><MeritListGenerationPage /></ActionGate>} />
            <Route path="/support" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.support}><TenantSupportPage /></ActionGate>} />
            {import.meta.env.DEV && <><Route path="/role-previews" element={<RoleDashboardPage />} /><Route path="/role-preview/:roleCode" element={<RoleDashboardPage />} /></>}
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/self-service" element={<TenantUserRoute><SelfServicePage /></TenantUserRoute>} />
            <Route path="/notices" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.notices}><NoticeBulletinPage /></ActionGate>} />
            <Route path="/communications" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.communications}><MessageComposerPage /></ActionGate>} />
            <Route path="/library" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.library}><LibraryCatalogPage /></ActionGate>} />
            <Route path="/transport" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.transport}><TransportRoutePage /></ActionGate>} />
            <Route path="/executive-dashboard" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.reports}><ExecutiveDashboardPage /></ActionGate>} />
            <Route path="/sessions" element={<SessionsPage />} />
            <Route path="/finance/structures" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.financeConfiguration}><FeeStructureManagementPage /></ActionGate>} />
            <Route path="/finance/invoices" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.feeInvoices}><StudentFeeInvoicesPage /></ActionGate>} />
            <Route path="/finance/payments" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.feePayments}><FeePaymentPortalPage /></ActionGate>} />
            <Route path="/documents/id-cards" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.identityCards} allowSupportSession={false}><IdCardManagementPage /></ActionGate>} />
            <Route path="/documents/certificates" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.certificates} allowSupportSession={false}><CertificateRequestPage /></ActionGate>} />
            <Route path="/governance/grievances/submit" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.grievanceSubmission} allowSupportSession={false}><GrievanceCaseManagementPage mode="submit" /></ActionGate>} />
            <Route path="/governance/grievances" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.confidentialGrievances} allowSupportSession={false}><GrievanceCaseManagementPage mode="review" /></ActionGate>} />
            <Route path="/governance/disciplinary" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.disciplinaryIncidents} allowSupportSession={false}><DisciplinaryIncidentManagementPage /></ActionGate>} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
