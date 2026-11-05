export type RoleLevel = "Platform" | "Tenant" | "University" | "College" | "School" | "External";
export type DashboardFamily = "platform" | "leadership" | "academic" | "teaching" | "finance" | "people" | "operations" | "governance" | "student" | "family" | "admissions";
export type RoleProfile = { code: string; name: string; level: RoleLevel; family: DashboardFamily; purpose: string };

const rows: Array<[string,string,RoleLevel,string]> = [
["PRODUCT_OWNER","Product Owner","Platform","Owns the SaaS product: plans, pricing, tenants, features, discounts, billing and releases."],
["PLATFORM_ADMIN","Platform Admin","Platform","Runs day-to-day platform operations within Product Owner controls."],
["BILLING_MANAGER","Billing Manager","Platform","Runs subscription billing, invoices and overdue account follow-up."],
["SUPPORT_MANAGER","Support Manager","Platform","Runs the support desk and escalations across tenants."],
["RELEASE_MANAGER","Release Manager","Platform","Plans, deploys and rolls back platform versions."],
["DATA_PROTECTION_OFFICER","Data Protection Officer","Platform","Approves full-tenant exports, tenant deletion and retention changes."],
["MANAGEMENT","Management","Tenant","Tenant owner/trust oversight, buyer dashboard and assigned final approvals."],
["DATA_PROTECTION_CONTACT","Data Protection Contact","Tenant","Handles personal-data requests inside the tenant."],
["GOVERNING_BODY_APPROVER","Governing Body Approver","Tenant","Approves only specified senior-role leave when no Management holder exists."],
["VICE_CHANCELLOR","Vice Chancellor","University","University head; oversees institutions and designated approvals."],
["REGISTRAR","Registrar","University","University administration, academic and examination governance."],
["FINANCE_DIRECTOR","Finance Director","University","University finance, fee policy and payroll-total approvals."],
["CONTROLLER_OF_EXAMINATIONS","Controller of Examinations","University","Owns examination schedules, marks process and result publishing."],
["UNIVERSITY_ADMIN","University Admin","University","Configures university institutions, users, calendar and policies."],
["UNIVERSITY_ACCOUNTANT","University Accountant","University","University accounts, fee policy data and reconciliation."],
["UNIVERSITY_HR_MANAGER","University HR Manager","University","University staff records and staff-leave verification."],
["RESEARCH_COORDINATOR","Research Coordinator","University","Governs research projects, grants and publications."],
["AUDIT_OFFICER","Audit Officer","University","Reviews audit logs and audit findings."],
["COMPLIANCE_OFFICER","Compliance Officer","University","Prepares compliance reports and statutory returns."],
["ACCREDITATION_COORDINATOR_UNIVERSITY","Accreditation Coordinator (University)","University","Uploads accreditation documents and maps evidence."],
["QUALITY_ASSURANCE_OFFICER","Quality Assurance Officer","University","Reviews quality data and quality assurance items."],
["LEGAL_OFFICER","Legal Officer","University","Reviews legal matters and assigned legal items."],
["PRINCIPAL_COLLEGE","Principal (College)","College","College head; final approvals for long leave, results, fees, promotion and HR items."],
["VICE_PRINCIPAL_COLLEGE","Vice Principal (College)","College","Deputises for the Principal, handles delegated approvals and setup."],
["COLLEGE_ADMIN","College Admin","College","Configures and runs the college: structure, users, students and settings."],
["COLLEGE_ACCOUNTANT","College Accountant","College","Fee structures, collections, receipts, concessions and finance reports."],
["COLLEGE_AUDIT_OFFICER","College Audit Officer","College","Reviews college-level audit activity and findings."],
["ACCREDITATION_COORDINATOR_COLLEGE","Accreditation Coordinator (College)","College","Prepares college accreditation items and evidence."],
["HEAD_OF_DEPARTMENT","Head of Department","College","Runs the department: subjects, faculty, attendance corrections and marks approval."],
["PROFESSOR","Professor","College","Teaching, attendance, marks, assignments and mentor responsibilities."],
["ASSOCIATE_PROFESSOR","Associate Professor","College","Teaching, attendance, marks, assignments and mentor responsibilities."],
["ASSISTANT_PROFESSOR","Assistant Professor","College","Teaching, attendance, marks, assignments and mentor responsibilities."],
["LAB_ASSISTANT","Lab Assistant","College","Laboratory operations and assigned practical-session support."],
["RESEARCH_ASSISTANT","Research Assistant","College","Research project support."],
["DEPARTMENT_COORDINATOR","Department Coordinator","College","Coordinates department timetables, notices and syllabus."],
["OFFICE_STAFF","Office Staff","College","Front-office record lookups and parent-message handling."],
["ADMISSION_OFFICER","Admission Officer","College","Runs admissions and creates student records (Phase 2 module)."],
["EXAMINATION_CLERK","Examination Clerk","College","Exam logistics: schedules, hall tickets and marks data."],
["RECORDS_OFFICER","Records Officer","College","Maintains student records and certificates."],
["LIBRARIAN","Librarian","College","Runs the library (Phase 2 module)."],
["LIBRARY_ASSISTANT","Library Assistant","College","Supports library operations (Phase 2 module)."],
["IT_ADMINISTRATOR","IT Administrator","College","Users, security, backups and technical support; no academic or finance data."],
["SYSTEM_OPERATOR","System Operator","College","Users, security, backups and technical support; no academic or finance data."],
["HR_MANAGER","HR Manager","College","Staff records, staff leave and future staff-attendance workflows."],
["HR_EXECUTIVE","HR Executive","College","Staff records, staff leave and future staff-attendance workflows."],
["PAYROLL_OFFICER","Payroll Officer","College","Salary structures and payroll generation (Phase 2 module)."],
["PLACEMENT_OFFICER","Placement Officer","College","Runs campus placements (Phase 2 module)."],
["ALUMNI_COORDINATOR","Alumni Coordinator","College","Runs alumni engagement and alumni records (Phase 3 module)."],
["HOSTEL_WARDEN","Hostel Warden","College","Hostel rooms, allocation and hostel attendance (Phase 3 module)."],
["TRANSPORT_MANAGER","Transport Manager","College","Routes, vehicles and student route mapping (Phase 3 module)."],
["DRIVER","Driver","College","Assigned transport routes and vehicle operations."],
["SECURITY_SUPERVISOR","Security Supervisor","College","Operational staff role with minimal record access and self-service."],
["HOUSEKEEPING_SUPERVISOR","Housekeeping Supervisor","College","Operational staff role with minimal record access and self-service."],
["STUDENT_COLLEGE","Student (College)","College","Learns, tracks progress, pays fees, applies for leave and explores placements."],
["PARENT_COLLEGE","Parent (College)","College","Monitors linked student progress, pays fees and applies for leave."],
["SCHOOL_PRINCIPAL","School Principal","School","School head; approves leave, results, notices and attendance corrections."],
["VICE_PRINCIPAL_SCHOOL","Vice Principal (School)","School","Deputises for the School Principal and handles delegated approvals."],
["SCHOOL_ADMIN","School Admin","School","Configures and runs the school; publishes approved results."],
["SCHOOL_ACCOUNTANT","School Accountant","School","School fees, receipts, finance reports and Phase 2 payroll."],
["CLASS_TEACHER","Class Teacher","School","Owns a class: attendance, marks review, student leave and parent contact."],
["SUBJECT_TEACHER","Subject Teacher","School","Teaches a subject: attendance, marks and homework."],
["STUDENT_SCHOOL","Student (School)","School","Optional student login for selected classes; view-oriented experience."],
["SCHOOL_OFFICE_STAFF","School Office Staff","School","School office operations and assigned support duties."],
["SCHOOL_LIBRARIAN","School Librarian","School","School library and book-circulation operations."],
["SCHOOL_LAB_ASSISTANT","School Lab Assistant","School","School laboratory support and practical-session readiness."],
["SCHOOL_IT_SUPPORT","School IT Support","School","School user support, security and technical operations."],
["TRANSPORT_STAFF","Transport Staff","School","Assigned school transport operations."],
["HOSTEL_STAFF","Hostel Staff","School","Assigned hostel operations and attendance."],
["SECURITY_STAFF","Security Staff","School","School security operations with minimal record access."],
["PARENT_SCHOOL","Parent (School)","School","Digital representative for a linked school student."],
["APPLICANT","Applicant","External","Applies for admission using a one-time-code login (Phase 2)."]
];

function familyFor(code: string, level: RoleLevel): DashboardFamily {
  if (level === "Platform") return "platform";
  if (code.includes("STUDENT")) return "student";
  if (code.includes("PARENT")) return "family";
  if (code === "APPLICANT" || code.includes("ADMISSION")) return "admissions";
  if (code.includes("FINANCE") || code.includes("ACCOUNTANT") || code.includes("BILLING") || code.includes("PAYROLL")) return "finance";
  if (code.includes("HR_")) return "people";
  if (code.includes("TEACHER") || code.includes("PROFESSOR") || code.includes("LAB_ASSISTANT") || code.includes("RESEARCH_ASSISTANT")) return "teaching";
  if (code.includes("ACADEMIC") || code.includes("EXAM") || code.includes("DEPARTMENT") || code.includes("REGISTRAR") || code.includes("QUALITY") || code.includes("RESEARCH")) return "academic";
  if (code.includes("AUDIT") || code.includes("COMPLIANCE") || code.includes("LEGAL") || code.includes("DATA_PROTECTION") || code.includes("GOVERNING") || code.includes("ACCREDITATION")) return "governance";
  if (code.includes("PRINCIPAL") || code.includes("MANAGEMENT") || code.includes("CHANCELLOR") || code.includes("VICE_PRINCIPAL") || code.includes("COLLEGE_ADMIN") || code.includes("SCHOOL_ADMIN") || code.includes("UNIVERSITY_ADMIN")) return "leadership";
  if (code.includes("LIBRARY") || code.includes("LIBRARIAN") || code.includes("TRANSPORT") || code.includes("DRIVER") || code.includes("HOSTEL") || code.includes("SECURITY") || code.includes("HOUSEKEEPING") || code.includes("IT_") || code.includes("SYSTEM_OPERATOR") || code.includes("OFFICE_STAFF") || code.includes("RECORDS")) return "operations";
  return "operations";
}

export const roleDashboardCatalog: RoleProfile[] = rows.map(([code,name,level,purpose]) => ({ code,name,level,family:familyFor(code,level),purpose }));
export const roleDashboardByCode = (code?: string) => roleDashboardCatalog.find(role => role.code === code);
