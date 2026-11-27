import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import axios from "axios";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";
import { useSupportSession } from "../../support/useSupportSession";
import { PersonPicker } from "../../../components/common/PersonPicker";
import { SectionPicker } from "../../../components/common/SectionPicker";
import { staffService, type TeacherLookupItem } from "../api/staffService";
import { useFormErrors } from "../../../hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Badge } from "../../../components/ui";
import {
  Button,
  TextField,
  SelectField,
  TextAreaField,
  DateField,
  DateTimeField,
  Panel,
  Modal,
  EmptyState,
  Notice,
  SkeletonPage,
  SkeletonTable,
  Tabs,
  Toast,
  toast,
} from "../../../design-system/components";
import { ErrorState } from "../../../components/feedback/ErrorState";
import {
  AddAcademicSessionModal,
  AddMasterEventModal,
  AddDepartmentModal,
  AddProgramModal,
  UnlockTermModal,
  SetProgramRulesModal,
  SetGradingSchemeModal,
  AllocateSubjectModal,
  PromotionRunModal,
} from "../components";
import {
  institutionService,
  type DepartmentItem,
  type ProgramItem,
  type AcademicSessionItem,
  type MasterCalendarEventItem,
  type InstitutionItem,
  type SchoolClassItem,
  type SectionItem,
  type InstitutionHierarchyResponse,
  type ProgramYearItem,
  type SemesterItem,
  type CurriculumVersionItem,
} from "../api/institutionService";
import { JsonDocumentBuilder, type DocumentValue } from "../../../design-system/components/JsonDocumentBuilder";
import { termLockService, type TermLockItem } from "../api/termLockService";
import {
  approvalDelegationService,
  type DelegationItem,
  type ApprovalStepItem,
  type EligibleDelegate,
} from "../api/approvalDelegationService";
import {
  academicService,
  type ProgramRuleSetItem,
  type GradingSchemeItem,
  type SubjectAllocationItem,
  type PromotionRunItem,
} from "../api/academicService";

// These six screens are the first real (non-prototype) institution-setup actions: they call the
// genuine /api/tenants/{tenantId}/... endpoints added in DepartmentController, SectionController,
// SchoolClassController, CalendarEventController and WeeklyOffRuleController, instead of the
// sample-data-only screens in InstitutionAdminPages.tsx. Kept in a separate file so this round's
// additions don't collide with edits other agents are making to InstitutionAdminPages.tsx.
//
// Scoped department/section list endpoints and the teaching-staff lookup now power real
// PersonPicker and SectionPicker controls on HOD, mentor and class-teacher assignment pages.
// Other setup forms that do not yet have a supported browse endpoint keep explicit UUID fields;
// do not replace those with synthetic dropdowns.

type Status = "idle" | "busy" | "success" | "error" | "denied";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isValidUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}

/** Shows the no-tenant-identity denial state shared by every screen in this file. */
function NoTenantNotice() {
  return (
    <ErrorState
      title="Permission denied"
      message="This action is tenant-scoped and requires a signed-in institution identity. Your current session has no tenant to act within."
    />
  );
}

function ResultBanner({ status, message }: { status: Status; message: string }) {
  if (status === "idle" || status === "busy" || !message) return null;
  if (status === "success") {
    return (
      <div className="app-inline-notice" role="status">
        {message}
      </div>
    );
  }
  if (status === "denied") {
    return <ErrorState title="Permission denied" message={message} />;
  }
  return <ErrorState title="Could not complete this action" message={message} />;
}

/** Centralises the submit → request → status/message plumbing shared by all six actions. */
function useAction<TResponse>() {
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState("");

  const run = async (
    validationError: string | null,
    request: () => Promise<{ data: TResponse }>,
    onSuccess: (data: TResponse) => string,
  ) => {
    if (validationError) {
      setStatus("error");
      setMessage(validationError);
      return;
    }
    setStatus("busy");
    setMessage("");
    try {
      const res = await request();
      setStatus("success");
      setMessage(onSuccess(res.data));
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 403) {
        setStatus("denied");
        setMessage(getApiErrorMessage(err));
      } else {
        setStatus("error");
        setMessage(getApiErrorMessage(err));
      }
    }
  };

  return { status, message, run };
}

// --- Institution hierarchy (T2-S1) ------------------------------------------------------------
export function InstitutionHierarchyPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [hierarchy, setHierarchy] = useState<InstitutionHierarchyResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<InstitutionHierarchyResponse["institutions"][number] | null>(null);
  const [editDraft, setEditDraft] = useState({
    institutionName: "", addressLine: "", city: "", stateName: "", postalCode: "",
    principalInviteEmail: "", externalUniversityMode: false,
  });
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState("");
  const [hodDepartment, setHodDepartment] = useState<DepartmentItem | null>(null);
  const [hodTeachers, setHodTeachers] = useState<TeacherLookupItem[]>([]);
  const [hodStaffAssignmentId, setHodStaffAssignmentId] = useState("");
  const [hodLoading, setHodLoading] = useState(false);
  const [hodError, setHodError] = useState("");



  const beginEdit = (node: InstitutionHierarchyResponse["institutions"][number]) => {
    setEditing(node);
    setEditDraft({
      institutionName: node.institution.institutionName,
      addressLine: node.institution.addressLine ?? "",
      city: node.institution.city ?? "",
      stateName: node.institution.stateName ?? "",
      postalCode: node.institution.postalCode ?? "",
      principalInviteEmail: node.institution.principalInviteEmail ?? "",
      externalUniversityMode: node.institution.externalUniversityMode,
    });
    setEditError("");
  };

  const saveEdit = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !editing) return;
    if (editDraft.institutionName.trim().length < 2) {
      setEditError("Institution name must contain at least 2 characters.");
      return;
    }
    setSavingEdit(true);
    setEditError("");
    try {
      await institutionService.updateInstitution(
        tenantId,
        editing.institution.institutionId,
        editing.institution.rowVersion,
        {
          institutionName: editDraft.institutionName.trim(),
          addressLine: editDraft.addressLine || null,
          city: editDraft.city || null,
          stateName: editDraft.stateName || null,
          postalCode: editDraft.postalCode || null,
          principalInviteEmail: editDraft.principalInviteEmail || null,
          externalUniversityMode: editDraft.externalUniversityMode,
        }
      );
      setEditing(null);
      await load();
    } catch (cause) {
      setEditError(getApiErrorMessage(cause));
    } finally {
      setSavingEdit(false);
    }
  };

  const beginHodAssignment = async (institutionId: string, department: DepartmentItem) => {
    if (!tenantId) return;
    setHodDepartment(department);
    setHodStaffAssignmentId("");
    setHodTeachers([]);
    setHodError("");
    setHodLoading(true);
    try {
      const teachers = await staffService.teacherLookup(tenantId, institutionId, "HOD_ASSIGN", department.departmentId);
      setHodTeachers(teachers);
    } catch (cause) {
      setHodError(getApiErrorMessage(cause));
    } finally {
      setHodLoading(false);
    }
  };

  const saveHodAssignment = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !hodDepartment || !hodStaffAssignmentId) return;
    if (!hodDepartment.rowVersion) {
      setHodError("The department version is unavailable. Reload the hierarchy before assigning an HOD.");
      return;
    }
    setHodLoading(true);
    setHodError("");
    try {
      await api.put(
        `/tenants/${tenantId}/departments/${hodDepartment.departmentId}/hod`,
        { staffAssignmentId: hodStaffAssignmentId },
        { headers: { "If-Match": `"${hodDepartment.rowVersion}"` } }
      );
      setHodDepartment(null);
      await load();
    } catch (cause) {
      setHodError(getApiErrorMessage(cause));
    } finally {
      setHodLoading(false);
    }
  };

  const load = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    setError("");
    try {
      setHierarchy(await institutionService.getHierarchy(tenantId));
    } catch (cause) {
      setHierarchy(null);
      setError(getApiErrorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => { void load(); }, [load]);

  return (
    <div className="app-page">
      <PageHeader
        title="Institution hierarchy"
        description="Tenant / university-group root, constituent colleges and schools, and each college's academic departments."
        actions={<Link className="app-link" to="/university/institutions">Institution register</Link>}
      />
      {!tenantId ? <NoTenantNotice /> : (
        <>
          {error && <ErrorState title="Unable to load institution hierarchy" message={error} action={<Button variant="secondary" onClick={() => void load()}>Retry</Button>} />}
          {loading && !hierarchy && <SkeletonPage />}
          {hierarchy && (
            <Panel title={hierarchy.tenantName} description={`University-group / tenant root · ${hierarchy.tenantType}`}>
              <div className="app-hierarchy-tree" role="tree" aria-label="Institution hierarchy">
                <div className="app-hierarchy-root" role="treeitem" aria-level={1}>
                  <strong>{hierarchy.tenantName}</strong>
                  <span>University group / tenant root</span>
                </div>
                {hierarchy.institutions.length === 0 ? (
                  <EmptyState title="No colleges or schools registered" description="Register a college or school to build the institutional hierarchy." />
                ) : (
                  <ul className="app-hierarchy-institutions">
                    {hierarchy.institutions.map(({ institution, departments }) => (
                      <li key={institution.institutionId} role="treeitem" aria-level={2}>
                        <div className="app-hierarchy-institution">
                          <div>
                            <strong>{institution.institutionName}</strong>
                            <span>{institution.institutionType} · {institution.institutionCode} · {institution.status}</span>
                          </div>
                          <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center" }}>
                            <Link to={`/university/institutions/${institution.institutionId}`}>View details</Link>
                            <Button size="sm" variant="secondary" onClick={() => beginEdit({ institution, departments })}>Edit profile</Button>
                          </div>
                        </div>
                        {institution.institutionType === "COLLEGE" && (
                          <ul className="app-hierarchy-departments" role="group">
                            {departments.length === 0 ? (
                              <li className="app-hierarchy-empty">No departments configured</li>
                            ) : departments.map((department) => (
                              <li key={department.departmentId} role="treeitem" aria-level={3}>
                                <div className="app-hierarchy-department">
                                  <span><strong>{department.departmentName}</strong> <code>{department.departmentCode}</code></span>
                                  <span>{department.hodStaffAssignmentId ? "HOD assigned" : "HOD not assigned"}</span>
                                  <span>{department.facultyCount ?? 0} faculty · {department.studentCount ?? 0} students</span>
                                  <Link to={`/university/setup/structure/${institution.institutionId}/departments/${department.departmentId}`}>Open department</Link>
                                  <Button size="sm" variant="secondary" onClick={() => void beginHodAssignment(institution.institutionId, department)}>
                                    Assign HOD
                                  </Button>
                                </div>
                              </li>
                            ))}
                          </ul>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </Panel>
          )}
          <Modal
            isOpen={Boolean(hodDepartment)}
            onClose={() => setHodDepartment(null)}
            title={hodDepartment ? `Assign HOD — ${hodDepartment.departmentName}` : "Assign HOD"}
            description="Choose an active faculty assignment in this department. The server validates institution and department scope."
            size="md"
          >
            <form className="app-form" onSubmit={submitHodAssignment}>
              <PersonPicker
                label="Faculty member"
                value={hodStaffAssignmentId}
                options={hodTeachers.map((teacher) => ({
                  id: teacher.staffAssignmentId,
                  name: teacher.fullName,
                  designation: teacher.designation,
                  department: teacher.departmentName ?? undefined,
                }))}
                onChange={setHodStaffAssignmentId}
              />
              {hodError && <Notice tone="danger">{hodError}</Notice>}
              <footer className="app-modal__footer">
                <Button type="button" variant="secondary" disabled={hodBusy} onClick={() => setHodDepartment(null)}>Cancel</Button>
                <Button type="submit" busy={hodBusy} disabled={!hodStaffAssignmentId}>Save HOD assignment</Button>
              </footer>
            </form>
          </Modal>

          {/* Programs Panel */}
          <Panel
            title="Degree Programs"
            description="Degree and certification programs under departments (e.g. B.Tech, M.Sc)."
          >
            <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
              {!isSupportSession && (
                <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "1rem" }}>
                  <Button
                    type="button"
                    disabled={departments.length === 0}
                    onClick={() => setShowAddProg(true)}
                    title={departments.length === 0 ? "Create a department first" : ""}
                  >
                    Add Program
                  </Button>
                </div>
              )}

              {programs.length === 0 ? (
                <p className="app-sample-note">No degree programs found for this institution.</p>
              ) : (
                <div className="app-table-wrap">
                  <table className="app-table">
                    <thead>
                      <tr>
                        <th>Program Code</th>
                        <th>Program Name</th>
                        <th>Duration</th>
                        <th>Semesters / Year</th>
                        <th>Status</th>
                        {!isSupportSession && <th>Actions</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {programs.map((p) => (
                        <tr key={p.programId}>
                          <td><strong><code>{p.programCode}</code></strong></td>
                          <td><Link to={`/university/setup/structure/${institutionId}/programs/${p.programId}`}>{p.programName}</Link></td>
                          <td>{p.durationYears} {p.durationYears === 1 ? "Year" : "Years"}</td>
                          <td>{p.semestersPerYear}</td>
                          <td><Badge tone={p.status === "ACTIVE" ? "success" : "neutral"}>{p.status}</Badge></td>
                          {!isSupportSession && (
                            <td><div className="app-inline-actions">
                              <Button size="sm" variant="secondary" onClick={() => { setEditError(""); setEditingRecord({ kind: "program", item: p }); }}>Edit</Button>
                              <Button size="sm" variant="danger" disabled={!p.rowVersion} onClick={() => { setArchiveError(""); setArchiveTarget({ kind: "program", id: p.programId, name: p.programName, rowVersion: p.rowVersion }); }}>Archive</Button>
                            </div></td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </Panel>

          <Panel
            title="Curriculum version history"
            description="Curriculum snapshots are append-only. Each change creates a new numbered version with an effective date range."
          >
            {curriculumError && <Notice tone="danger">{curriculumError}</Notice>}
            <div className="app-form">
              <SelectField id="curriculum-program" label="Program" value={selectedProgramId}
                onChange={(event) => { setSelectedProgramId(event.target.value); setCurriculumVersions([]); setCurriculumError(""); }}>
                <option value="">Choose a program</option>
                {programs.map((program) => <option key={program.programId} value={program.programId}>{program.programName}</option>)}
              </SelectField>
              <div className="app-inline-actions">
                <Button type="button" variant="secondary" busy={curriculumBusy} disabled={!selectedProgramId}
                  onClick={() => void loadCurriculumVersions()}>Load curriculum history</Button>
              </div>
              {!isSupportSession && (
                <form onSubmit={createCurriculumVersion}>
                  <JsonDocumentBuilder
                    title="Curriculum details"
                    description="Build the curriculum snapshot using courses, groups and other structured fields. The application saves the document in the required format."
                    value={curriculumDocument}
                    onChange={setCurriculumDocument}
                  />
                  <div className="app-form-grid">
                    <TextField id="curriculum-effective-from" label="Effective from" type="date" required
                      value={curriculumEffectiveFrom} onChange={(event) => setCurriculumEffectiveFrom(event.target.value)} />
                    <TextField id="curriculum-effective-to" label="Effective to" type="date"
                      value={curriculumEffectiveTo} onChange={(event) => setCurriculumEffectiveTo(event.target.value)} />
                  </div>
                  <div className="app-inline-actions">
                    <Button type="submit" busy={curriculumBusy} disabled={!selectedProgramId}>Create immutable version</Button>
                  </div>
                </form>
              )}
              {curriculumVersions.length === 0 ? (
                <EmptyState title="No curriculum versions" description="Load the program history or create the first curriculum snapshot." />
              ) : (
                <div className="app-stack">
                  {curriculumVersions.map((version) => (
                    <div key={version.programCurriculumVersionId} className="app-list-row">
                      <div>
                        <strong>Version {version.versionNumber}</strong>
                        <div>{version.effectiveFrom} – {version.effectiveTo ?? "Open-ended"}</div>
                      </div>
                      <details>
                        <summary>View curriculum details</summary>
                        <div className="app-stack" style={{ marginTop: "var(--space-2)" }}>
                          {describeDocument(version.curriculumJson).map((entry) => (
                            <div key={entry.path} className="app-list-row">
                              <span>{entry.path}</span>
                              <strong>{entry.value}</strong>
                            </div>
                          ))}
                        </div>
                      </details>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </Panel>

          <Panel
            title="Program years and semester calendar"
            description="Configure program years and dated semesters. Term dates are checked for institution-wide overlap by the API and database constraint."
          >
            {catalogError && <Toast variant="error" description={catalogError} />}
            <div className="app-form">
              <div className="app-form-grid">
                <SelectField
                  id="catalog-program"
                  label="Program"
                  value={selectedProgramId}
                  onChange={(event) => {
                    setSelectedProgramId(event.target.value);
                    setProgramYears([]);
                    setSelectedProgramYearId("");
                    setSemesters([]);
                    setCurriculumVersions([]);
                    setCurriculumError("");
                  }}
                >
                  <option value="">Choose a program</option>
                  {programs.map((program) => (
                    <option key={program.programId} value={program.programId}>{program.programName}</option>
                  ))}
                </SelectField>
                <div className="app-inline-actions">
                  <Button type="button" variant="secondary" busy={catalogBusy} disabled={!selectedProgramId} onClick={() => void loadProgramYears()}>
                    Load program years
                  </Button>
                </div>
              </div>
              {!isSupportSession && (
                <form onSubmit={createProgramYear}>
                  <div className="app-form-grid">
                    <TextField
                      id="program-year-number"
                      label="Year number"
                      type="number"
                      min={1}
                      max={10}
                      step={1}
                      required
                      value={programYearDraft.yearNumber}
                      onChange={(event) => setProgramYearDraft({ ...programYearDraft, yearNumber: event.target.value })}
                    />
                    <TextField
                      id="program-year-label"
                      label="Year label"
                      required
                      maxLength={30}
                      value={programYearDraft.label}
                      onChange={(event) => setProgramYearDraft({ ...programYearDraft, label: event.target.value })}
                    />
                  </div>
                  <div className="app-inline-actions">
                    <Button type="submit" busy={catalogBusy} disabled={!selectedProgramId}>Create program year</Button>
                  </div>
                </form>
              )}
              {programYears.length > 0 && (
                <>
                  <SelectField
                    id="program-year-select"
                    label="Program year"
                    value={selectedProgramYearId}
                    onChange={(event) => void selectProgramYear(event.target.value)}
                  >
                    {programYears.map((year) => (
                      <option key={year.programYearId} value={year.programYearId}>{year.label}</option>
                    ))}
                  </SelectField>
                  {!isSupportSession && (
                    <form onSubmit={createSemester}>
                      <div className="app-form-grid">
                        <TextField
                          id="semester-number"
                          label="Semester number"
                          type="number"
                          min={1}
                          max={60}
                          step={1}
                          required
                          value={semesterDraft.semesterNumber}
                          onChange={(event) => setSemesterDraft({ ...semesterDraft, semesterNumber: event.target.value })}
                        />
                        <TextField
                          id="semester-label"
                          label="Semester label"
                          required
                          maxLength={30}
                          value={semesterDraft.label}
                          onChange={(event) => setSemesterDraft({ ...semesterDraft, label: event.target.value })}
                        />
                        <TextField
                          id="semester-start-date"
                          label="Start date"
                          type="date"
                          required
                          value={semesterDraft.startDate}
                          onChange={(event) => setSemesterDraft({ ...semesterDraft, startDate: event.target.value })}
                        />
                        <TextField
                          id="semester-end-date"
                          label="End date"
                          type="date"
                          required
                          value={semesterDraft.endDate}
                          onChange={(event) => setSemesterDraft({ ...semesterDraft, endDate: event.target.value })}
                        />
                      </div>
                      <div className="app-inline-actions">
                        <Button type="submit" busy={catalogBusy} disabled={!selectedProgramYearId}>Create dated semester</Button>
                      </div>
                    </form>
                  )}
                  {semesters.length > 0 ? (
                    <div className="app-table-wrap">
                      <table className="app-table">
                        <thead><tr><th>Term</th><th>Start</th><th>End</th></tr></thead>
                        <tbody>
                          {semesters.map((semester) => (
                            <tr key={semester.semesterId}>
                              <td>{semester.label}</td>
                              <td>{semester.startDate ?? "Not set"}</td>
                              <td>{semester.endDate ?? "Not set"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : <EmptyState title="No semesters configured" description="Create the first dated semester for this program year." />}
                </>
              )}
            </div>
          </Panel>
        </>
      )}

      <EditStructureRecordModal
        key={editingRecord ? `${editingRecord.kind}-${editingRecord.kind === "department" ? editingRecord.item.departmentId : editingRecord.kind === "program" ? editingRecord.item.programId : editingRecord.kind === "class" ? editingRecord.item.classId : editingRecord.item.sectionId}` : "none"}
        record={editingRecord}
        departments={departments}
        classes={schoolClasses}
        sessions={schoolSessions}
        submitting={editSubmitting}
        error={editError}
        onClose={() => { if (!editSubmitting) { setEditingRecord(null); setEditError(""); } }}
        onSave={(record, draft) => void saveStructureRecord(record, draft)}
      />
      {archiveTarget && (
        <Modal isOpen onClose={() => { if (!archiveBusy) { setArchiveTarget(null); setArchiveError(""); } }}
          title={`Archive ${archiveTarget.kind}`} titleId="archive-structure-record-title"
          description={`Archive “${archiveTarget.name}”? The record is retained for audit and cannot be used for new assignments.`} size="sm">
          {archiveError && <Notice tone="danger">{archiveError}</Notice>}
          <div className="app-inline-actions">
            <Button type="button" variant="secondary" disabled={archiveBusy} onClick={() => setArchiveTarget(null)}>Cancel</Button>
            <Button type="button" variant="danger" busy={archiveBusy} onClick={() => void archiveStructureRecord()}>Archive record</Button>
          </div>
        </Modal>
      )}

      {/* Add Department Modal */}
      <AddDepartmentModal
        isOpen={!isSchool && showAddDept}
        onClose={() => {
          setShowAddDept(false);
          setDeptError(null);
        }}
        onSubmit={async (deptCode, deptName) => {
          setDeptSubmitting(true);
          setDeptError(null);
          try {
            await institutionService.createDepartment(tenantId, institutionId, {
              departmentCode: deptCode.trim().toUpperCase(),
              departmentName: deptName.trim(),
            });
            setShowAddDept(false);
            void loadStructure();
          } catch (err) {
            setDeptError(getApiErrorMessage(err));
          } finally {
            setDeptSubmitting(false);
          }
        }}
        submitting={deptSubmitting}
        error={deptError}
      />

      {/* Add Program Modal */}
      <AddProgramModal
        isOpen={!isSchool && showAddProg}
        onClose={() => {
          setShowAddProg(false);
          setProgError(null);
        }}
        departments={departments}
        onSubmit={async (data) => {
          setProgSubmitting(true);
          setProgError(null);
          try {
            await institutionService.createProgram(tenantId, institutionId, {
              departmentId: data.departmentId,
              programCode: data.programCode.trim().toUpperCase(),
              programName: data.programName.trim(),
              durationYears: Number(data.durationYears),
              semestersPerYear: Number(data.semestersPerYear),
            });
            setShowAddProg(false);
            void loadStructure();
          } catch (err) {
            setProgError(getApiErrorMessage(err));
          } finally {
            setProgSubmitting(false);
          }
        }}
        submitting={progSubmitting}
        error={progError}
      />
    </div>
  );
}

// ============================================================================
// STORY #43 & #44: TERM_LOCK & TERM_UNLOCK (Academic Term Locking)
// ============================================================================
export function TermLockPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId) ?? "";
  const activeInstitutionId = useAppSelector((state) => state.tenantPermissions.current?.institutionId ?? "");
  const { can } = useTenantAccess();
  const [institutionId, setInstitutionId] = useState(activeInstitutionId);
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [academicSessions, setAcademicSessions] = useState<AcademicSessionItem[]>([]);
  const [sessionsLoadError, setSessionsLoadError] = useState("");
  const [sessionId, setSessionId] = useState("");
  const [lockNotes, setLockNotes] = useState("");
  const [locks, setLocks] = useState<TermLockItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [locking, setLocking] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);
  const [lockLoadError, setLockLoadError] = useState("");
  const [institutionLoadError, setInstitutionLoadError] = useState("");
  const { fieldErrors, formError: lockFormError, setFieldError: setLockFieldError, handleApiError: handleLockApiError, clearFieldError: clearLockFieldError, clearAll: clearLockAll } = useFormErrors();

  const fetchLocks = useCallback((instId: string) => {
    if (!tenantId || !isValidUuid(instId)) return;
    setLoading(true);
    setLockLoadError("");
    termLockService
      .listTermLocks(tenantId, instId.trim())
      .then((data) => setLocks(data || []))
      .catch((err) => setLockLoadError(getApiErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [tenantId]);

  useEffect(() => {
    if (!tenantId) return;
    institutionService
      .listInstitutions(tenantId)
      .then((res) => {
        if (res?.items?.length) {
          setInstitutions(res.items);
          if (!institutionId && res.items[0]?.institutionId) {
            setInstitutionId(res.items[0].institutionId);
            fetchLocks(res.items[0].institutionId);
          }
        }
      })
      .catch((err) => setInstitutionLoadError(getApiErrorMessage(err)));
  }, [tenantId, institutionId, fetchLocks]);

  useEffect(() => {
    if (!tenantId || !institutionId || !isValidUuid(institutionId)) {
      setAcademicSessions([]);
      setSessionId("");
      return;
    }
    let cancelled = false;
    setSessionsLoadError("");
    setSessionId("");
    institutionService.listAcademicSessions(tenantId, institutionId)
      .then((items) => { if (!cancelled) setAcademicSessions(items); })
      .catch((err) => { if (!cancelled) { setAcademicSessions([]); setSessionsLoadError(getApiErrorMessage(err)); } });
    return () => { cancelled = true; };
  }, [tenantId, institutionId]);

  useEffect(() => {
    if (activeInstitutionId && isValidUuid(activeInstitutionId)) {
      if (!institutionId) setInstitutionId(activeInstitutionId);
      fetchLocks(activeInstitutionId);
    }
  }, [activeInstitutionId, fetchLocks]);

  // Unlock modal state
  const [unlockModalItem, setUnlockModalItem] = useState<TermLockItem | null>(null);
  const [unlocking, setUnlocking] = useState(false);

  const handleLockSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!tenantId) return;
    clearLockAll();
    let invalid = false;
    if (!isValidUuid(institutionId)) {
      setLockFieldError("institutionId", "Choose a registered institution.");
      invalid = true;
    }
    if (!academicSessions.some((session) => session.academicSessionId === sessionId)) {
      setLockFieldError("academicSessionId", "Choose an academic session for the selected institution.");
      invalid = true;
    }
    if (invalid) return;
    setLocking(true);
    clearLockAll();
    try {
      const res = await termLockService.lockTerm(tenantId, institutionId.trim(), {
        academicSessionId: sessionId.trim(),
        notes: lockNotes.trim() || undefined,
      });
      const msg = "Term locked successfully. Attendance and marks can no longer be changed for this term.";
      setFeedback({ tone: "success", message: msg });
      
      setLocks((prev) => [res, ...prev.filter((l) => l.termLockId !== res.termLockId)]);
      setLockNotes("");
    } catch (err: unknown) {
      handleLockApiError(err);
      const msg = getApiErrorMessage(err);
      setFeedback({ tone: "danger", message: msg });
      
    } finally {
      setLocking(false);
    }
  };

  if (!tenantId) return <NoTenantNotice />;

  return (
    <div className="app-page">
      <PageHeader
        title="Term lock management"
        description="Lock academic terms to protect marks and attendance records. Temporary unlocks require justification and expire automatically."
      />

      {institutionLoadError && <Notice tone="danger">{institutionLoadError}</Notice>}
      {sessionsLoadError && <Notice tone="danger">{sessionsLoadError}</Notice>}
      {lockFormError && <Notice tone="danger">{lockFormError}</Notice>}
      {feedback && (
        <Notice tone={feedback.tone} className="mb-4">
          <div className="app-feedback-row">
            <span>{feedback.message}</span>
            <Button variant="tertiary" size="sm" onClick={() => setFeedback(null)}>Dismiss</Button>
          </div>
        </Notice>
      )}

      <Panel title="Lock academic term" description="Authorized principals can lock a term after final marks and attendance have been submitted.">
        <form onSubmit={handleLockSubmit} className="app-form" noValidate>
          <div style={{ maxWidth: "480px", marginBottom: "var(--space-3)" }}>
            <SelectField
              id="quick-lock-inst-select"
              label="Choose registered institution *"
              required
              value={institutionId}
              onChange={(e) => {
                const val = e.target.value;
                setInstitutionId(val);
                setSessionId("");
                if (isValidUuid(val)) fetchLocks(val);
                else { setLocks([]); setLockLoadError(""); }
              }}
            >
              <option value="">-- Select an institution --</option>
              {institutions.map((inst) => (
                <option key={inst.institutionId} value={inst.institutionId}>
                  {inst.institutionName} ({inst.institutionType === "COLLEGE" ? "College" : "School"}) [{inst.institutionCode}]
                </option>
              ))}
            </SelectField>
          </div>
          <div className="app-form-grid" style={{ marginBottom: "var(--space-3)" }}>
            <SelectField
              id="term-lock-session-select"
              label="Academic Session *"
              required
              value={sessionId}
              disabled={!institutionId || academicSessions.length === 0}
              onChange={(event) => { setSessionId(event.target.value); clearLockFieldError("academicSessionId"); }}
            >
              <option value="">{sessionsLoadError ? "Sessions unavailable" : academicSessions.length ? "Choose an academic session" : "No sessions available"}</option>
              {academicSessions.map((session) => <option key={session.academicSessionId} value={session.academicSessionId}>{session.sessionLabel} ({session.startDate} – {session.endDate})</option>)}
            </SelectField>
            <TextField
              id="term-lock-notes"
              label="Lock Notes / Justification"
              value={lockNotes}
              onChange={(e) => { setLockNotes(e.target.value); clearLockFieldError("notes"); }}
              placeholder="e.g. End of term marks finalised"
              hint="Statutory justification for freezing records"
              error={fieldErrors.notes}
            />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "var(--space-4)" }}>
            <Button type="submit" disabled={locking || !can("TERM_LOCK")} title={!can("TERM_LOCK") ? "Requires TERM_LOCK permission" : undefined}>
              {locking ? "Locking term…" : "Lock term"}
            </Button>
          </div>
        </form>
      </Panel>

      <Panel title="Active term locks" description="Review locked terms. Principals can temporarily unlock for grievances or corrections.">
        {loading && locks.length === 0 && <SkeletonTable rows={4} columns={5} />}
        {lockLoadError ? (
          <Notice tone="danger">{lockLoadError} <Button variant="secondary" size="sm" onClick={() => fetchLocks(institutionId)}>Retry</Button></Notice>
        ) : !isValidUuid(institutionId) ? (
          <EmptyState title="Choose an institution" description="Select a registered institution to load its term locks." />
        ) : locks.length === 0 && !loading ? (
          <EmptyState
            title="No active term locks"
            description="No active term locks recorded for this institution."
            icon="lock"
          />
        ) : loading ? null : (
          <div className="app-table-wrap">
            <table className="app-table">
              <thead>
                <tr>
                  <th>Academic session</th>
                  <th>Semester</th>
                  <th>Status</th>
                  <th>Unlock Until</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {locks.map((lock) => (
                  <tr key={lock.termLockId}>
                    <td>
                      {academicSessions.find((session) => session.academicSessionId === lock.academicSessionId)?.sessionLabel ?? "Academic session"}
                    </td>
                    <td>{lock.semesterId ? "Semester-level" : "Whole session"}</td>
                    <td>
                      <Badge tone={lock.lockState === "LOCKED" ? "danger" : "warning"}>
                        {lock.lockState === "LOCKED" ? "Locked" : "Temporarily unlocked"}
                      </Badge>
                      {lock.lockState === "LOCKED" && (
                        <small className="block mt-1" role="status">
                          Term Locked: Records are immutable
                        </small>
                      )}
                    </td>
                    <td>{lock.unlockUntil ? new Date(lock.unlockUntil).toLocaleString() : "—"}</td>
                    <td>
                      {lock.lockState === "LOCKED" ? (
                        can("TERM_UNLOCK") ? (
                          <Button
                            variant="secondary"
                            style={{ padding: "2px 8px", fontSize: "0.8rem" }}
                            onClick={() => setUnlockModalItem(lock)}
                          >
                            Unlock Term
                          </Button>
                        ) : (
                          <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>Locked</span>
                        )
                      ) : (
                        <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>Unlocked</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {/* Unlock Term Modal (Story #44: TERM_UNLOCK / Rule AT-09) */}
      <UnlockTermModal
        isOpen={!!unlockModalItem}
        onClose={() => setUnlockModalItem(null)}
        onSubmit={async (reason, hours) => {
          if (!tenantId || !unlockModalItem) return;
          setUnlocking(true);
          try {
            const targetInstId = unlockModalItem.institutionId || institutionId;
            const targetLockId = unlockModalItem.termLockId || (unlockModalItem as { id?: string }).id || "";
            const res = await termLockService.unlockTerm(tenantId, targetInstId, targetLockId, {
              reason: reason.trim(),
              unlockDurationHours: Number(hours) || 72,
            });
            setFeedback({
              tone: "success",
              message: `Term temporarily unlocked until ${res.unlockUntil || "in three days"}. It will automatically relock.`,
            });
            setLocks((prev) => prev.map((l) => (l.termLockId === res.termLockId ? res : l)));
            setUnlockModalItem(null);
          } catch (err: unknown) {
            setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
          } finally {
            setUnlocking(false);
          }
        }}
        unlocking={unlocking}
      />
    </div>
  );
}

// ============================================================================
// STORIES #20 & #21: APPROVAL_DELEGATE & APPROVAL_BULK
// ============================================================================
export function DelegationsApprovalsPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId) ?? "";
  const [delegations, setDelegations] = useState<DelegationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);
  const { fieldErrors: delegFieldErrors, formError: delegationFormError, setFieldError: setDelegFieldError, handleApiError: handleDelegApiError, clearFieldError: clearDelegFieldError, clearAll: clearDelegAll } = useFormErrors();
  const { fieldErrors: bulkFieldErrors, formError: bulkFormError, setFieldError: setBulkFieldError, handleApiError: handleBulkApiError, clearFieldError: clearBulkFieldError, clearAll: clearBulkAll } = useFormErrors();

  // New delegation state
  const [delegateUserId, setDelegateUserId] = useState("");
  const [eligibleDelegates, setEligibleDelegates] = useState<EligibleDelegate[]>([]);
  const [eligibleDelegatesError, setEligibleDelegatesError] = useState("");
  const [eligibleDelegatesLoading, setEligibleDelegatesLoading] = useState(false);
  const [delegationBasis, setDelegationBasis] = useState<"SAME_OR_HIGHER" | "DESIGNATED_DEPUTY">("SAME_OR_HIGHER");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [creating, setCreating] = useState(false);

  // Bulk approvals state
  const [selectedApprovalIds, setSelectedApprovalIds] = useState<string[]>([]);
  const [bulkDecision, setBulkDecision] = useState<"APPROVED" | "REJECTED">("APPROVED");
  const [bulkComment, setBulkComment] = useState("");
  const [bulkSubmitting, setBulkSubmitting] = useState(false);

  // Approval Inbox state
  const [inboxItems, setInboxItems] = useState<ApprovalStepItem[]>([]);
  const [inboxLoading, setInboxLoading] = useState(true);
  const [delegationsError, setDelegationsError] = useState("");
  const [inboxError, setInboxError] = useState("");
  const [decidingId, setDecidingId] = useState<string | null>(null);

  const fetchDelegations = useCallback(() => {
    if (!tenantId) return;
    setLoading(true);
    setDelegationsError("");
    approvalDelegationService
      .listDelegations(tenantId)
      .then((data) => setDelegations(data || []))
      .catch((err) => setDelegationsError(getApiErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [tenantId]);

  const fetchInbox = useCallback(() => {
    if (!tenantId) return;
    setInboxLoading(true);
    setInboxError("");
    approvalDelegationService
      .listInbox(tenantId)
      .then((items) => setInboxItems(items || []))
      .catch((err) => setInboxError(getApiErrorMessage(err)))
      .finally(() => setInboxLoading(false));
  }, [tenantId]);

  useEffect(() => {
    if (!tenantId) return;
    let cancelled = false;
    setEligibleDelegatesLoading(true);
    setEligibleDelegatesError("");
    approvalDelegationService.listEligibleDelegates(tenantId)
      .then((items) => { if (!cancelled) setEligibleDelegates(items); })
      .catch((error: unknown) => { if (!cancelled) { setEligibleDelegates([]); setEligibleDelegatesError(getApiErrorMessage(error)); } })
      .finally(() => { if (!cancelled) setEligibleDelegatesLoading(false); });
    return () => { cancelled = true; };
  }, [tenantId]);

  useEffect(() => {
    if (!tenantId) return;
    fetchDelegations();
    fetchInbox();
  }, [fetchDelegations, fetchInbox, tenantId]);

  const handleDecideStep = async (step: ApprovalStepItem, decision: "APPROVED" | "REJECTED") => {
    const requestId = step.approvalRequestId || "";
    if (!requestId) return;
    let remark: string | undefined;
    if (decision === "REJECTED") {
      const promptRemark = window.prompt("Please provide a rejection reason:");
      if (!promptRemark || !promptRemark.trim()) {
        setFeedback({ tone: "danger", message: "A reason is required when rejecting an approval." });
        return;
      }
      remark = promptRemark.trim();
    }
    setDecidingId(requestId);
    try {
      await approvalDelegationService.decideApproval(tenantId, requestId, decision, remark, step.stepNo);
      setFeedback({ tone: "success", message: `Approval step marked as ${decision}.` });
      fetchInbox();
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setDecidingId(null);
    }
  };

  const handleCreateDelegation = async (e: FormEvent) => {
    e.preventDefault();
    clearDelegAll();
    let invalid = false;
    if (!eligibleDelegates.some((user) => user.userAccountId === delegateUserId)) {
      setDelegFieldError("delegateUserId", "Choose an eligible colleague from the list.");
      invalid = true;
    }
    if (!startsAt) {
      setDelegFieldError("startsAt", "Choose when the delegation begins.");
      invalid = true;
    }
    if (!endsAt) {
      setDelegFieldError("endsAt", "Choose when the delegation ends.");
      invalid = true;
    }
    if (startsAt && endsAt && (!Number.isFinite(new Date(startsAt).getTime()) || !Number.isFinite(new Date(endsAt).getTime()) || new Date(endsAt).getTime() <= new Date(startsAt).getTime())) {
      setDelegFieldError("endsAt", "The end time must be later than the start time.");
      invalid = true;
    }
    if (invalid) return;
    setCreating(true);
    try {
      const res = await approvalDelegationService.createDelegation(tenantId, {
        delegateUserId,
        delegationBasis,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
      });
      const msg = "Approval authority delegated successfully. Self-approval remains prohibited.";
      setFeedback({
        tone: "success",
        message: msg,
      });
      toast.success("Approval authority delegated", "Self-approval remains prohibited.");
      setDelegations((prev) => [res, ...prev]);
      setDelegateUserId("");
    } catch (err: unknown) {
      handleDelegApiError(err);
      const msg = getApiErrorMessage(err);
      setFeedback({ tone: "danger", message: msg });
      toast.error("Failed to delegate approval", msg);
    } finally {
      setCreating(false);
    }
  };

  const handleCancelDelegation = async (delegationId: string) => {
    try {
      const res = await approvalDelegationService.cancelDelegation(tenantId, delegationId);
      const msg = `Delegation cancelled successfully.`;
      setFeedback({ tone: "success", message: msg });
      toast.success("Delegation cancelled");
      setDelegations((prev) => prev.map((d) => (d.delegationId === res.delegationId ? res : d)));
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err);
      setFeedback({ tone: "danger", message: msg });
      toast.error("Failed to cancel delegation", msg);
    }
  };

  const handleBulkDecision = async (e: FormEvent) => {
    e.preventDefault();
    clearBulkAll();
    const availableIds = new Set(inboxItems.map((item) => item.approvalRequestId).filter((id): id is string => Boolean(id)));
    const ids = [...new Set(selectedApprovalIds)].filter((id) => availableIds.has(id));
    if (ids.length === 0) {
      setBulkFieldError("approvalRequestIds", "Select at least one request from your pending approval inbox.");
      return;
    }

    setBulkSubmitting(true);
    try {
      const res = await approvalDelegationService.bulkDecide(tenantId, {
        approvalRequestIds: ids,
        decision: bulkDecision,
        comment: bulkComment.trim() || undefined,
      });
      const msg = `Bulk decision applied: ${res.succeeded} items processed successfully.`;
      setFeedback({
        tone: "success",
        message: msg,
      });
      toast.success("Bulk decision applied", `${res.succeeded} items processed successfully.`);
      setSelectedApprovalIds([]);
      setBulkComment("");
    } catch (err: unknown) {
      handleBulkApiError(err);
      const msg = getApiErrorMessage(err);
      setFeedback({ tone: "danger", message: msg });
      toast.error("Failed to apply bulk decision", msg);
    } finally {
      setBulkSubmitting(false);
    }
  };

  if (!tenantId) return <NoTenantNotice />;

  return (
    <div className="app-page">
      <PageHeader
        title="Approvals & delegations"
        description="Delegate approval authority for a defined period or review several requests together."
      />

      {feedback && (
        <Notice tone={feedback.tone} className="mb-4">
          <div className="app-feedback-row">
            <span>{feedback.message}</span>
            <Button variant="tertiary" size="sm" onClick={() => setFeedback(null)}>Dismiss</Button>
          </div>
        </Notice>
      )}

      {/* Delegation Form */}
      <Panel
        title="Delegate approval authority"
        description="Hand over your approval responsibilities to an eligible colleague for a defined period."
      >
        <form onSubmit={handleCreateDelegation} className="app-form" noValidate>
          <SelectField
            id="delegate-user"
            label="Choose eligible colleague *"
            required
            value={delegateUserId}
            disabled={eligibleDelegatesLoading || eligibleDelegates.length === 0}
            onChange={(e) => { setDelegateUserId(e.target.value); clearDelegFieldError("delegateUserId"); }}
            error={delegFieldErrors.delegateUserId}
          >
            <option value="">{eligibleDelegatesLoading ? "Loading eligible colleagues…" : "Choose a colleague"}</option>
            {eligibleDelegates.map((user) => <option key={user.userAccountId} value={user.userAccountId}>{user.displayName} · {user.email}</option>)}
          </SelectField>
          {eligibleDelegatesError && <Notice tone="danger">{eligibleDelegatesError}</Notice>}
          {!eligibleDelegatesLoading && !eligibleDelegatesError && eligibleDelegates.length === 0 && <Notice tone="warning">No active colleagues are eligible for delegation within your current scope.</Notice>}

          <SelectField
            id="delegation-basis"
            label="Delegation Basis"
            value={delegationBasis}
            onChange={(e) => setDelegationBasis(e.target.value as "SAME_OR_HIGHER" | "DESIGNATED_DEPUTY")}
            options={[
              { value: "SAME_OR_HIGHER", label: "Same or higher role" },
              { value: "DESIGNATED_DEPUTY", label: "Designated deputy (for example, Principal to Vice-Principal)" },
            ]}
          />

          <div className="app-form-grid">
            <DateTimeField
              id="delegation-start"
              label="Effective From *"
              required
              value={startsAt}
              onChange={(e) => { setStartsAt(e.target.value); clearDelegFieldError("startsAt"); clearDelegFieldError("endsAt"); }}
              error={delegFieldErrors.startsAt}
            />
            <DateTimeField
              id="delegation-end"
              label="Effective Until *"
              required
              value={endsAt}
              onChange={(e) => { setEndsAt(e.target.value); clearDelegFieldError("endsAt"); }}
              error={delegFieldErrors.endsAt}
            />
          </div>

          {delegationFormError && (
            <div style={{ marginTop: "var(--space-3)" }}>
              <Notice tone="danger">{delegationFormError}</Notice>
            </div>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "var(--space-4)" }}>
            <Button type="submit" disabled={creating || eligibleDelegatesLoading || !delegateUserId}>
              {creating ? "Delegating…" : "Create delegation"}
            </Button>
          </div>
        </form>
      </Panel>

      {/* Bulk Approvals Form */}
      <Panel
        title="Bulk approval decision"
        description="Review and decide several eligible requests together, with an optional comment."
      >
        <form onSubmit={handleBulkDecision} className="app-form" noValidate>
          <fieldset className="app-form-fieldset">
            <legend>Select pending requests from your inbox *</legend>
            {inboxItems.filter((item) => item.approvalRequestId).length === 0 ? (
              <p className="app-empty-inline">There are no pending requests available for bulk selection. Review your inbox below.</p>
            ) : inboxItems.filter((item) => item.approvalRequestId).map((item, index) => {
              const requestId = item.approvalRequestId as string;
              const checked = selectedApprovalIds.includes(requestId);
              return (
                <label key={`${requestId}-${item.stepNo}-${index}`} className="app-checkbox-row">
                  <input type="checkbox" checked={checked} onChange={(event) => {
                    setSelectedApprovalIds((current) => event.target.checked
                      ? [...new Set([...current, requestId])]
                      : current.filter((id) => id !== requestId));
                    clearBulkFieldError("approvalRequestIds");
                  }} />
                  <span>Request awaiting {formatStatusLabel(item.approverRoleCode)} · Step {item.stepNo}</span>
                </label>
              );
            })}
            {bulkFieldErrors.approvalRequestIds && <p className="app-field-error">{bulkFieldErrors.approvalRequestIds}</p>}
          </fieldset>

          <div className="app-form-grid">
            <SelectField
              id="bulk-decision-type"
              label="Decision"
              value={bulkDecision}
              onChange={(e) => setBulkDecision(e.target.value as "APPROVED" | "REJECTED")}
              options={[
                { value: "APPROVED", label: "Approve all selected requests" },
                { value: "REJECTED", label: "Reject all selected requests" },
              ]}
            />

            <TextField
              id="bulk-comment"
              label="Batch Comment (Optional)"
              placeholder="e.g. Moderated marks verified and approved by Dean"
              value={bulkComment}
              onChange={(e) => { setBulkComment(e.target.value); clearBulkFieldError("comment"); }}
              error={bulkFieldErrors.comment}
            />
          </div>

          {bulkFormError && (
            <div style={{ marginTop: "var(--space-3)" }}>
              <Notice tone="danger">{bulkFormError}</Notice>
            </div>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "var(--space-4)" }}>
            <Button type="submit" disabled={bulkSubmitting || selectedApprovalIds.length === 0}>
              {bulkSubmitting ? "Processing…" : "Apply bulk decision"}
            </Button>
          </div>
        </form>
      </Panel>

      <div style={{ marginTop: "var(--space-4)" }}>
        <Panel
          title="Pending approval requests (Inbox)"
          description="Pending approval steps awaiting your review or authorized delegation."
        >
          {inboxLoading && inboxItems.length === 0 && <SkeletonTable rows={4} columns={4} />}
          {inboxError && <Notice tone="danger">{inboxError} <Button variant="secondary" size="sm" onClick={fetchInbox}>Retry</Button></Notice>}
          {!inboxLoading && !inboxError && inboxItems.length === 0 ? (
            <EmptyState
              title="No pending approvals"
              description="No pending approval requests in your inbox."
              icon="check-circle"
            />
          ) : inboxLoading || inboxError ? null : (
            <div className="app-table-wrap">
              <table className="app-table">
                <thead>
                  <tr>
                    <th>Step #</th>
                    <th>Approver Role</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {inboxItems.map((item, idx) => (
                    <tr key={idx}>
                      <td>{item.stepNo}</td>
                      <td>{formatStatusLabel(item.approverRoleCode)}</td>
                      <td><Badge tone="warning">{formatStatusLabel(item.decision)}</Badge></td>
                      <td>
                        <div style={{ display: "flex", gap: "var(--space-2)" }}>
                          <Button
                            size="sm"
                            disabled={decidingId !== null}
                            onClick={() => handleDecideStep(item, "APPROVED")}
                          >
                            Approve
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            disabled={decidingId !== null}
                            onClick={() => handleDecideStep(item, "REJECTED")}
                          >
                            Reject
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      </div>

      <div style={{ marginTop: "var(--space-4)" }}>
        <Panel title="My active delegations" description="Review approval responsibilities you have delegated. You cannot approve your own request.">
          {loading && delegations.length === 0 && <SkeletonTable rows={4} columns={6} />}
          {delegationsError && <Notice tone="danger">{delegationsError} <Button variant="secondary" size="sm" onClick={fetchDelegations}>Retry</Button></Notice>}
          {!loading && !delegationsError && delegations.length === 0 ? (
            <EmptyState
              title="No active delegations"
              description="No active delegations created."
              icon="user"
            />
          ) : loading || delegationsError ? null : (
            <div className="app-table-wrap">
              <table className="app-table">
                <thead>
                  <tr>
                    <th>Delegate</th>
                    <th>Basis</th>
                    <th>Starts At</th>
                    <th>Ends At</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {delegations.map((d) => (
                    <tr key={d.delegationId}>
                      <td>{eligibleDelegates.find((user) => user.userAccountId === d.delegateUserId)?.displayName ?? "Account not available"}<br /><small>{eligibleDelegates.find((user) => user.userAccountId === d.delegateUserId)?.email ?? ""}</small></td>
                      <td>{d.delegationBasis}</td>
                      <td>{d.startsAt ? new Date(d.startsAt).toLocaleString() : "—"}</td>
                      <td>{d.endsAt ? new Date(d.endsAt).toLocaleString() : "—"}</td>
                      <td>
                        <Badge tone={d.status === "ACTIVE" ? "success" : "neutral"}>
                          {d.status}
                        </Badge>
                      </td>
                      <td>
                        {d.status === "ACTIVE" ? (
                          <Button
                            variant="secondary"
                            style={{ padding: "2px 8px", fontSize: "0.8rem" }}
                            onClick={() => handleCancelDelegation(d.delegationId)}
                          >
                            Cancel Delegation
                          </Button>
                        ) : (
                          <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>Cancelled</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}

export function AcademicGovernancePage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId) ?? "";
  const { can: canTenantAction } = useTenantAccess();
  const [activeTab, setActiveTab] = useState<"rules" | "grading" | "allocation" | "promotion">("rules");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);

  // Program rules state (Story #30: PROGRAM_RULES_SET)
  const [rules, setRules] = useState<ProgramRuleSetItem[]>([]);
  const [ruleModalOpen, setRuleModalOpen] = useState(false);

  // Grading scheme state (Story #34: GRADING_SCHEME_SET)
  const [schemes, setSchemes] = useState<GradingSchemeItem[]>([]);
  const [schemeModalOpen, setSchemeModalOpen] = useState(false);

  // Subject allocation state (Story #36: SUBJECT_ALLOCATE)
  const [allocations, setAllocations] = useState<SubjectAllocationItem[]>([]);
  const [allocationModalOpen, setAllocationModalOpen] = useState(false);

  // Promotion run state (Stories #39 & #40: PROMOTION_RUN / PROMOTION_APPROVE)
  const [promotionRuns, setPromotionRuns] = useState<PromotionRunItem[]>([]);
  const [promotionModalOpen, setPromotionModalOpen] = useState(false);

  const [saving, setSaving] = useState(false);

  const fetchCurrentTab = useCallback(() => {
    if (!tenantId) return;
    setLoading(true);
    setLoadError("");
    if (activeTab === "rules") {
      academicService.listProgramRules(tenantId)
        .then(setRules)
        .catch((err) => setLoadError(getApiErrorMessage(err)))
        .finally(() => setLoading(false));
    } else if (activeTab === "grading") {
      academicService.listGradingSchemes(tenantId)
        .then(setSchemes)
        .catch((err) => setLoadError(getApiErrorMessage(err)))
        .finally(() => setLoading(false));
    } else if (activeTab === "allocation") {
      academicService.listSubjectAllocations(tenantId)
        .then(setAllocations)
        .catch((err) => setLoadError(getApiErrorMessage(err)))
        .finally(() => setLoading(false));
    } else if (activeTab === "promotion") {
      academicService.listPromotionRuns(tenantId)
        .then(setPromotionRuns)
        .catch((err) => setLoadError(getApiErrorMessage(err)))
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, [activeTab, tenantId]);

  useEffect(() => {
    if (tenantId) fetchCurrentTab();
  }, [fetchCurrentTab, tenantId]);

  const handleSubmitPromotionRun = async (id: string) => {
    if (!tenantId) return;
    try {
      const res = await academicService.submitPromotionRun(tenantId, id);
      setPromotionRuns((prev) => prev.map((run) => run.promotionRunId === id ? res : run));
      setFeedback({ tone: "success", message: "Promotion run submitted for Principal approval." });
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    }
  };

  const handleApprovePromotion = async (id: string) => {
    if (!tenantId) return;
    try {
      const res = await academicService.decidePromotionRun(tenantId, id, { decision: "APPROVED", comment: "Verified and approved" });
      setPromotionRuns((prev) => prev.map((p) => p.promotionRunId === id ? res : p));
      setFeedback({ tone: "success", message: "Promotion run approved by Principal." });
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    }
  };

  if (!tenantId) return <NoTenantNotice />;

  return (
    <div className="app-page">
      <PageHeader
        title="Academic setup & governance"
        description="Configure curriculum requirements, grading schemes, subject allocations and year-end student promotions."
        actions={
          activeTab === "rules" ? (
            <Button onClick={() => setRuleModalOpen(true)}>+ Set Program Rules</Button>
          ) : activeTab === "grading" ? (
            <Button onClick={() => setSchemeModalOpen(true)}>+ Set Grading Scheme</Button>
          ) : activeTab === "allocation" ? (
            <Button onClick={() => setAllocationModalOpen(true)}>+ Allocate Subject</Button>
          ) : (
            <Button onClick={() => setPromotionModalOpen(true)}>+ New Promotion Run</Button>
          )
        }
      />

      <Tabs
        ariaLabel="Academic governance sections"
        activeId={activeTab}
        onChange={(id) => setActiveTab(id as "rules" | "grading" | "allocation" | "promotion")}
        items={[
          { id: "rules", label: "Curriculum and programme rules" },
          { id: "grading", label: "Grading schemes" },
          { id: "allocation", label: "Subject allocations" },
          { id: "promotion", label: "Year-end promotions" },
        ]}
      />

      {feedback && (
        <Toast
          variant={feedback.tone === "danger" ? "error" : "success"}
          description={feedback.message}
          onDismiss={() => setFeedback(null)}
          className="mb-4"
        />
      )}

      {loading && <SkeletonPage title="Loading academic configuration" showTable tableRows={5} tableColumns={5} />}
      {loadError && !loading && <Notice tone="danger">{loadError} <Button variant="secondary" size="sm" onClick={fetchCurrentTab}>Retry</Button></Notice>}

      <div id="panel-rules" role="tabpanel" aria-labelledby="tab-rules" tabIndex={0} hidden={activeTab !== "rules"}>
      {!loading && !loadError && activeTab === "rules" && (
        <Panel title="Curriculum and programme rules" description="Versioned curriculum requirements set by the Registrar.">
          <div className="app-table-wrap">
            <table className="app-table">
              <thead>
                <tr>
                  <th>Version</th>
                  <th>Effective from</th>
                  <th>Status</th>
                  <th>Programme requirements</th>
                </tr>
              </thead>
              <tbody>
                {rules.length === 0 ? (
                  <tr><td colSpan={4} style={{ textAlign: "center", padding: "var(--space-4)" }}>No program rule sets defined.</td></tr>
                ) : (
                  rules.map((r) => (
                    <tr key={r.programRuleSetId}>
                      <td>v{r.versionNo}</td>
                      <td>{r.effectiveFrom}</td>
                      <td><Badge tone={r.status === "ACTIVE" ? "success" : "neutral"}>{formatStatusLabel(r.status)}</Badge></td>
                      <td>{describeDocument(parseDocument(r.rules)).map((entry) => `${entry.path}: ${entry.value}`).join(" · ") || "No requirements recorded"}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
      </div>

      <div id="panel-grading" role="tabpanel" aria-labelledby="tab-grading" tabIndex={0} hidden={activeTab !== "grading"}>
      {!loading && !loadError && activeTab === "grading" && (
        <Panel title="Grading schemes" description="University-wide or standalone institution grade points and bands.">
          <div className="app-table-wrap">
            <table className="app-table">
              <thead>
                <tr>
                  <th>Scheme Name</th>
                  <th>Version</th>
                  <th>Effective From</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {schemes.length === 0 ? (
                  <tr><td colSpan={4} style={{ textAlign: "center", padding: "var(--space-4)" }}>No grading schemes configured.</td></tr>
                ) : (
                  schemes.map((s) => (
                    <tr key={s.gradingSchemeVersionId}>
                      <td><strong>{s.schemeName}</strong></td>
                      <td>v{s.versionNo}</td>
                      <td>{s.effectiveFrom}</td>
                      <td><Badge tone={s.status === "ACTIVE" ? "success" : "neutral"}>{formatStatusLabel(s.status)}</Badge></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
      </div>

      <div id="panel-allocation" role="tabpanel" aria-labelledby="tab-allocation" tabIndex={0} hidden={activeTab !== "allocation"}>
      {!loading && !loadError && activeTab === "allocation" && (
        <Panel title="Subject allocations" description="Allocate courses to teachers for specific class sections or elective subject groups.">
          <div className="app-table-wrap">
            <table className="app-table">
              <thead>
                <tr>
                  <th>Course offering</th>
                  <th>Section / Group</th>
                  <th>Teacher</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {allocations.length === 0 ? (
                  <tr><td colSpan={4} style={{ textAlign: "center", padding: "var(--space-4)" }}>No subjects allocated yet.</td></tr>
                ) : (
                  allocations.map((a) => (
                    <tr key={a.subjectAllocationId}>
                      <td>Course offering selected</td>
                      <td>{a.sectionId ? "Section selected" : a.subjectGroupId ? "Subject group selected" : "—"}</td>
                      <td>Teacher assigned</td>
                      <td><Badge tone={a.status === "ACTIVE" ? "success" : "neutral"}>{formatStatusLabel(a.status)}</Badge></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
      </div>

      <div id="panel-promotion" role="tabpanel" aria-labelledby="tab-promotion" tabIndex={0} hidden={activeTab !== "promotion"}>
      {!loading && !loadError && activeTab === "promotion" && (
        <Panel title="Year-end promotions" description="Promote students across academic sessions; Principal approves before execution.">
          <div className="app-table-wrap">
            <table className="app-table">
              <thead>
                <tr>
                  <th>From Session</th>
                  <th>To Session</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {promotionRuns.length === 0 ? (
                  <tr><td colSpan={4} style={{ textAlign: "center", padding: "var(--space-4)" }}>No promotion runs initiated.</td></tr>
                ) : (
                  promotionRuns.map((p) => (
                    <tr key={p.promotionRunId}>
                      <td>Previous academic session</td>
                      <td>Next academic session</td>
                      <td><Badge tone={p.status === "APPROVED" ? "success" : "neutral"}>{formatStatusLabel(p.status)}</Badge></td>
                      <td>
                        {p.status === "DRAFT" && canTenantAction("PROMOTION_RUN") && (
                          <Button variant="secondary" onClick={() => void handleSubmitPromotionRun(p.promotionRunId)}>
                            Submit for approval
                          </Button>
                        )}
                        {p.status === "PENDING_APPROVAL" && canTenantAction("PROMOTION_APPROVE") && (
                          <Button variant="secondary" onClick={() => void handleApprovePromotion(p.promotionRunId)}>
                            Approve
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
      </div>

      {/* Program Rule Set Modal */}
      <SetProgramRulesModal
        isOpen={ruleModalOpen}
        onClose={() => setRuleModalOpen(false)}
        onSubmit={async (rules, effectiveFrom) => {
          setSaving(true);
          try {
            await academicService.createProgramRules(tenantId, {
              rules,
              effectiveFrom,
            });
            setFeedback({ tone: "success", message: "Program rule set created in draft." });
            setRuleModalOpen(false);
            fetchCurrentTab();
          } catch (err: unknown) {
            setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
            throw err;
          } finally {
            setSaving(false);
          }
        }}
        saving={saving}
      />

      {/* Grading Scheme Modal */}
      <SetGradingSchemeModal
        isOpen={schemeModalOpen}
        onClose={() => setSchemeModalOpen(false)}
        onSubmit={async (name, effectiveFrom) => {
          setSaving(true);
          try {
            await academicService.createGradingScheme(tenantId, {
              schemeName: name,
              effectiveFrom,
            });
            setFeedback({ tone: "success", message: `Grading scheme "${name}" created in draft.` });
            setSchemeModalOpen(false);
            fetchCurrentTab();
          } catch (err: unknown) {
            setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
            throw err;
          } finally {
            setSaving(false);
          }
        }}
        saving={saving}
      />

      {/* Subject Allocation Modal */}
      <AllocateSubjectModal
        tenantId={tenantId}
        isOpen={allocationModalOpen}
        onClose={() => setAllocationModalOpen(false)}
        onSubmit={async (data) => {
          setSaving(true);
          try {
            await academicService.allocateSubject(tenantId, data);
            setFeedback({ tone: "success", message: "Subject allocated to teacher." });
            setAllocationModalOpen(false);
            fetchCurrentTab();
          } catch (err: unknown) {
            setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
            throw err;
          } finally {
            setSaving(false);
          }
        }}
        saving={saving}
      />

      {/* Promotion Run Modal */}
      <PromotionRunModal
        tenantId={tenantId}
        isOpen={promotionModalOpen}
        onClose={() => setPromotionModalOpen(false)}
        onSubmit={async (data) => {
          setSaving(true);
          try {
            const res = await academicService.createPromotionRun(tenantId, data);
            setFeedback({ tone: "success", message: "Year-end promotion run created in draft." });
            setPromotionRuns((prev) => [res, ...prev]);
            setPromotionModalOpen(false);
          } catch (err: unknown) {
            setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
            throw err;
          } finally {
            setSaving(false);
          }
        }}
        saving={saving}
      />
    </div>
  );
}