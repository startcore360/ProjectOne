import { useCallback, useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
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
  EmptyState,
  Notice,
  Tabs,
  Toast,
  toast,
} from "../../../design-system/components";
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
} from "../api/institutionService";
import { termLockService, type TermLockItem } from "../api/termLockService";
import {
  approvalDelegationService,
  type DelegationItem,
  type ApprovalStepItem,
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
// IMPORTANT LIMITATION (read before changing the ID inputs below): there is no GET list endpoint
// yet for departments, sections or classes - only the PUT assignment endpoints exist. Until a
// "structure management" screen exists to browse them, every target is a plain pasted-ID text
// field, not a picker. That's deliberate, not an oversight: a fake dropdown would imply a browse
// experience the backend doesn't support.

type Status = "idle" | "busy" | "success" | "error" | "denied";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isValidUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}

function IdField({
  id,
  label,
  value,
  onChange,
  hint,
  error,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  hint?: string;
  error?: string;
}) {
  return (
    <TextField
      id={id}
      label={label}
      required
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder="Paste the ID (UUID)"
      hint={hint ?? "There is no list or picker for this yet — paste the ID directly."}
      error={error}
    />
  );
}

/** Shows the no-tenant-identity denial state shared by every screen in this file. */
function NoTenantNotice() {
  return (
    <div className="eds-error-state" role="alert">
      <h3 className="eds-error-state__title">Permission denied</h3>
      <p className="eds-error-state__message">
        This action is tenant-scoped and requires a signed-in institution identity. Your current
        session has no tenant to act within.
      </p>
    </div>
  );
}

function ResultBanner({ status, message }: { status: Status; message: string }) {
  if (status === "idle" || status === "busy" || !message) return null;
  if (status === "success") {
    return (
      <div className="platform-inline-notice" role="status">
        {message}
      </div>
    );
  }
  if (status === "denied") {
    return (
      <div className="eds-error-state" role="alert">
        <h3 className="eds-error-state__title">Permission denied</h3>
        <p className="eds-error-state__message">{message}</p>
      </div>
    );
  }
  return (
    <div className="eds-error-state" role="alert">
      <h3 className="eds-error-state__title">Could not complete this action</h3>
      <p className="eds-error-state__message">{message}</p>
    </div>
  );
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

// --- 1. HOD assignment (HOD_ASSIGN) -----------------------------------------------------------

export function HodAssignmentPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [departmentId, setDepartmentId] = useState("");
  const [staffAssignmentId, setStaffAssignmentId] = useState("");
  const { status, message, run } = useAction<{ departmentId: string; hodStaffAssignmentId: string }>();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const invalid =
      !isValidUuid(departmentId) || !isValidUuid(staffAssignmentId)
        ? "Department ID and staff assignment ID must both be valid IDs."
        : null;
    void run(
      invalid,
      () =>
        api.put(`/tenants/${tenantId}/departments/${departmentId.trim()}/hod`, {
          staffAssignmentId: staffAssignmentId.trim(),
        }),
      (data) => `HOD assignment recorded for department ${data.departmentId} (assignment ${data.hodStaffAssignmentId}).`,
    );
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Assign Head of Department"
        description="Assign a faculty member as the Head of Department for a department."
      />
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <Panel title="Department HOD" description="Both IDs are required; there is no department or staff picker yet.">
          <form onSubmit={submit} noValidate>
            <IdField id="hod-department-id" label="Department ID" value={departmentId} onChange={setDepartmentId} />
            <IdField
              id="hod-staff-assignment-id"
              label="Staff assignment ID (new HOD)"
              value={staffAssignmentId}
              onChange={setStaffAssignmentId}
              hint="This is the staff ASSIGNMENT id, not the person's user id — there is no lookup for it yet."
            />
            <ResultBanner status={status} message={message} />
            <Button type="submit" busy={status === "busy"}>
              {status === "busy" ? "Assigning…" : "Assign HOD"}
            </Button>
          </form>
        </Panel>
        </div>
      )}
    </div>
  );
}

// --- 2. Mentor assignment (MENTOR_ASSIGN) -----------------------------------------------------

export function MentorAssignmentPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [sectionId, setSectionId] = useState("");
  const [staffAssignmentId, setStaffAssignmentId] = useState("");
  const { status, message, run } = useAction<{ sectionId: string; mentorAssignmentId: string; staffAssignmentId: string }>();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const invalid =
      !isValidUuid(sectionId) || !isValidUuid(staffAssignmentId)
        ? "Section ID and staff assignment ID must both be valid IDs."
        : null;
    void run(
      invalid,
      () =>
        api.put(`/tenants/${tenantId}/sections/${sectionId.trim()}/mentor`, {
          staffAssignmentId: staffAssignmentId.trim(),
        }),
      (data) => `Mentor assigned to section ${data.sectionId} (mentor assignment ${data.mentorAssignmentId}).`,
    );
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Assign mentor"
        description="Assign a mentor or class coordinator to a college section."
      />
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <Panel title="Section mentor" description="Both IDs are required; there is no section or staff picker yet.">
          <form onSubmit={submit} noValidate>
            <IdField id="mentor-section-id" label="Section ID" value={sectionId} onChange={setSectionId} />
            <IdField
              id="mentor-staff-assignment-id"
              label="Staff assignment ID (new mentor)"
              value={staffAssignmentId}
              onChange={setStaffAssignmentId}
              hint="This is the staff ASSIGNMENT id, not the person's user id — there is no lookup for it yet."
            />
            <ResultBanner status={status} message={message} />
            <Button type="submit" busy={status === "busy"}>
              {status === "busy" ? "Assigning…" : "Assign mentor"}
            </Button>
          </form>
        </Panel>
        </div>
      )}
    </div>
  );
}

// --- 3. Class-teacher assignment (CLASS_TEACHER_ASSIGN, section-based) ------------------------

export function ClassTeacherAssignmentPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [sectionId, setSectionId] = useState("");
  const [staffAssignmentId, setStaffAssignmentId] = useState("");
  const { status, message, run } = useAction<{ sectionId: string; classTeacherAssignmentId: string; staffAssignmentId: string }>();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const invalid =
      !isValidUuid(sectionId) || !isValidUuid(staffAssignmentId)
        ? "Section ID and staff assignment ID must both be valid IDs."
        : null;
    void run(
      invalid,
      () =>
        api.put(`/tenants/${tenantId}/sections/${sectionId.trim()}/class-teacher`, {
          staffAssignmentId: staffAssignmentId.trim(),
        }),
      (data) =>
        `Class Teacher assigned to section ${data.sectionId} (assignment ${data.classTeacherAssignmentId}).`,
    );
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Assign Class Teacher"
        description="Assign a class teacher to a school section."
      />
      <div className="platform-policy-callout">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <strong>This assigns a section, not a class</strong>
          <p>
            The backend currently builds CLASS_TEACHER_ASSIGN against sections, not classes — an
            open product decision (OD-24). This screen is deliberately labelled "section" to
            match what actually happens; it does not assign a Class Teacher to a whole class.
          </p>
        </div>
      </div>
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <Panel title="Section Class Teacher" description="Both IDs are required; there is no section or staff picker yet.">
          <form onSubmit={submit} noValidate>
            <IdField id="ct-section-id" label="Section ID" value={sectionId} onChange={setSectionId} />
            <IdField
              id="ct-staff-assignment-id"
              label="Staff assignment ID (new Class Teacher)"
              value={staffAssignmentId}
              onChange={setStaffAssignmentId}
              hint="This is the staff ASSIGNMENT id, not the person's user id — there is no lookup for it yet."
            />
            <ResultBanner status={status} message={message} />
            <Button type="submit" busy={status === "busy"}>
              {status === "busy" ? "Assigning…" : "Assign Class Teacher"}
            </Button>
          </form>
        </Panel>
        </div>
      )}
    </div>
  );
}

// --- 4. Student login switch (STUDENT_LOGIN_SWITCH) -------------------------------------------

export function StudentLoginSwitchPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [classId, setClassId] = useState("");
  const [enabled, setEnabled] = useState(false);
  const { status, message, run } = useAction<{ classId: string; studentLoginEnabled: boolean }>();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const invalid = !isValidUuid(classId) ? "Class ID must be a valid ID." : null;
    void run(
      invalid,
      () => api.put(`/tenants/${tenantId}/classes/${classId.trim()}/student-login`, { enabled }),
      (data) => `Student view-only login for class ${data.classId} is now ${data.studentLoginEnabled ? "ON" : "OFF"}.`,
    );
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Student login switch"
        description="Enable or disable view-only student access for a school class."
      />
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <Panel title="Class student-login" description="There is no class picker yet; paste the class ID.">
          <form onSubmit={submit} noValidate>
            <IdField id="class-id" label="Class ID" value={classId} onChange={setClassId} />
            <label className="platform-form-field" style={{ marginTop: "var(--space-3)" }}>
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                style={{ marginRight: "var(--space-2)" }}
              />
              Enable view-only student login for this class
            </label>
            <ResultBanner status={status} message={message} />
            <Button type="submit" busy={status === "busy"}>
              {status === "busy" ? "Saving…" : "Save"}
            </Button>
          </form>
        </Panel>
        </div>
      )}
    </div>
  );
}

// --- 5. Calendar events (CALENDAR_LOCAL_SET) --------------------------------------------------

type CalendarEvent = {
  academicCalendarEventId: string;
  institutionId: string;
  academicSessionId: string;
  sourceLevel: string;
  eventType: string;
  title: string;
  startDate: string;
  endDate: string;
};

export function CalendarEventsPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [academicSessionId, setAcademicSessionId] = useState("");
  const [eventType, setEventType] = useState("LOCAL_HOLIDAY");
  const [title, setTitle] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const [events, setEvents] = useState<CalendarEvent[] | null>(null);
  const [listStatus, setListStatus] = useState<Status>("idle");
  const [listMessage, setListMessage] = useState("");
  const { status, message, run } = useAction<CalendarEvent>();

  // Master Academic Sessions (CALENDAR_MASTER_SET)
  const [sessions, setSessions] = useState<AcademicSessionItem[] | null>(null);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [sessionsError, setSessionsError] = useState("");
  const [showAddSessionModal, setShowAddSessionModal] = useState(false);
  const [sessionSubmitting, setSessionSubmitting] = useState(false);
  const [sessionModalError, setSessionModalError] = useState("");

  // Master Calendar Events (CALENDAR_MASTER_SET)
  const [masterEvents, setMasterEvents] = useState<MasterCalendarEventItem[] | null>(null);
  const [masterEventsLoading, setMasterEventsLoading] = useState(false);
  const [masterEventsError, setMasterEventsError] = useState("");
  const [showAddMasterEventModal, setShowAddMasterEventModal] = useState(false);
  const [targetSessionId, setTargetSessionId] = useState("");
  const [masterEventSubmitting, setMasterEventSubmitting] = useState(false);
  const [masterEventModalError, setMasterEventModalError] = useState("");

  const loadSessions = async () => {
    if (!tenantId) return;
    setSessionsLoading(true);
    setSessionsError("");
    try {
      const data = await institutionService.listAcademicSessions(tenantId);
      setSessions(data);
      if (data.length > 0 && !targetSessionId) {
        setTargetSessionId(data[0].academicSessionId);
      }
    } catch (err) {
      setSessionsError(getApiErrorMessage(err));
    } finally {
      setSessionsLoading(false);
    }
  };

  const loadMasterEvents = async () => {
    if (!tenantId) return;
    setMasterEventsLoading(true);
    setMasterEventsError("");
    try {
      const data = await institutionService.listMasterCalendarEvents(tenantId);
      setMasterEvents(data);
    } catch (err) {
      setMasterEventsError(getApiErrorMessage(err));
    } finally {
      setMasterEventsLoading(false);
    }
  };



  const loadEvents = async () => {
    if (!isValidUuid(institutionId)) {
      setListStatus("error");
      setListMessage("Institution ID must be a valid ID before events can be listed.");
      return;
    }
    setListStatus("busy");
    setListMessage("");
    try {
      const res = await api.get<CalendarEvent[]>(`/tenants/${tenantId}/calendar-events`, {
        params: { institutionId: institutionId.trim() },
      });
      setEvents(res.data);
      setListStatus("idle");
    } catch (err) {
      setEvents(null);
      if (axios.isAxiosError(err) && err.response?.status === 403) {
        setListStatus("denied");
      } else {
        setListStatus("error");
      }
      setListMessage(getApiErrorMessage(err));
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const invalid =
      !isValidUuid(institutionId) || !isValidUuid(academicSessionId) || !title.trim() || !startDate || !endDate
        ? "Institution ID, academic session ID, title, start date and end date are all required."
        : endDate < startDate
          ? "End date must not be before start date."
          : null;
    void run(
      invalid,
      () =>
        api.post<CalendarEvent>(`/tenants/${tenantId}/calendar-events`, {
          institutionId: institutionId.trim(),
          academicSessionId: academicSessionId.trim(),
          eventType,
          title: title.trim(),
          startDate,
          endDate,
        }),
      (data) => {
        setEvents((prev) => (prev ? [...prev, data] : [data]));
        setTitle("");
        setStartDate("");
        setEndDate("");
        return `"${data.title}" was added (${data.startDate} to ${data.endDate}).`;
      },
    );
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Local calendar events"
        description="Manage academic sessions, university holidays, and institution-specific calendar events."
      />
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <>
          {/* Master Academic Sessions */}
          <Panel
            title="Master Academic Sessions"
            description="Academic sessions shared across the tenant."
          >
            <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
              <Button type="button" variant="secondary" onClick={() => void loadSessions()} busy={sessionsLoading}>
                {sessionsLoading ? "Loading…" : "Load Sessions"}
              </Button>
              <Button type="button" variant="primary" onClick={() => setShowAddSessionModal(true)}>
                + New Academic Session
              </Button>
            </div>
            {sessionsError && <div className="platform-inline-error">{sessionsError}</div>}
            {sessions !== null && sessions.length === 0 && (
              <p className="platform-sample-note">No academic sessions found.</p>
            )}
            {sessions !== null && sessions.length > 0 && (
              <div className="platform-table-wrap">
                <table className="platform-table">
                  <thead>
                    <tr>
                      <th>Session Label</th>
                      <th>Academic Year</th>
                      <th>Start Date</th>
                      <th>End Date</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sessions.map((s) => (
                      <tr key={s.academicSessionId}>
                        <td><strong><code>{s.sessionLabel}</code></strong></td>
                        <td>{s.academicYear}</td>
                        <td>{s.startDate}</td>
                        <td>{s.endDate}</td>
                        <td><Badge tone="success">{s.status}</Badge></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>

          {/* Master Calendar Events */}
          <Panel
            title="University Master Calendar Events"
            description="Semester dates and university holidays [CL-02: mid-year changes notify institutions]."
          >
            <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
              <Button type="button" variant="secondary" onClick={() => void loadMasterEvents()} busy={masterEventsLoading}>
                {masterEventsLoading ? "Loading…" : "Load Master Calendar"}
              </Button>
              <Button type="button" variant="primary" onClick={() => setShowAddMasterEventModal(true)}>
                + Add Master Event
              </Button>
            </div>
            {masterEventsError && <div className="platform-inline-error">{masterEventsError}</div>}
            {masterEvents !== null && masterEvents.length === 0 && (
              <p className="platform-sample-note">No master calendar events found.</p>
            )}
            {masterEvents !== null && masterEvents.length > 0 && (
              <div className="platform-table-wrap">
                <table className="platform-table">
                  <thead>
                    <tr>
                      <th>Title</th>
                      <th>Event Type</th>
                      <th>Start Date</th>
                      <th>End Date</th>
                      <th>Source Level</th>
                    </tr>
                  </thead>
                  <tbody>
                    {masterEvents.map((e) => (
                      <tr key={e.academicCalendarEventId}>
                        <td><strong>{e.title}</strong></td>
                        <td><Badge tone={e.eventType === "EXAM_DAY" ? "warning" : e.eventType === "SEMESTER_DATES" ? "info" : "neutral"}>{e.eventType}</Badge></td>
                        <td>{e.startDate}</td>
                        <td>{e.endDate}</td>
                        <td>{e.sourceLevel}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>

          <Panel title="Choose institution" description="There is no institution picker yet; paste the institution ID.">
            <div className="eds-field" style={{ marginTop: 0 }}>
              <label htmlFor="cal-institution-id">
                Institution ID <span>(Required)</span>
              </label>
              <div style={{ display: "flex", gap: "var(--space-3)", alignItems: "center" }}>
                <input
                  id="cal-institution-id"
                  value={institutionId}
                  onChange={(e) => setInstitutionId(e.target.value)}
                  placeholder="Paste the ID (UUID)"
                  style={{ flex: 1 }}
                />
                <Button type="button" variant="secondary" onClick={() => void loadEvents()} busy={listStatus === "busy"}>
                  {listStatus === "busy" ? "Loading…" : "Load events"}
                </Button>
              </div>
              {fieldErrors.institutionId && <p id="wo-institution-id-error" className="eds-field__error">{fieldErrors.institutionId}</p>}
            </div>
            <ResultBanner status={listStatus} message={listMessage} />
          </Panel>

          <Panel title="Local events" description="Local holidays, local events and exam days for this institution.">
            {events === null ? (
              <EmptyState
                title="No events loaded yet"
                description="Enter an institution ID above and choose Load events."
                icon="calendar"
              />
            ) : events.length === 0 ? (
              <EmptyState
                title="No local events recorded"
                description="No local events are recorded for this institution."
                icon="calendar"
              />
            ) : (
              <div className="platform-table-wrap">
                <table className="platform-table">
                  <thead>
                    <tr>
                      <th>Title</th>
                      <th>Type</th>
                      <th>Start</th>
                      <th>End</th>
                      <th>Source</th>
                    </tr>
                  </thead>
                  <tbody>
                    {events.map((event) => (
                      <tr key={event.academicCalendarEventId}>
                        <td>
                          <strong>{event.title}</strong>
                        </td>
                        <td>
                          <Badge tone={event.eventType === "EXAM_DAY" ? "warning" : event.eventType === "LOCAL_HOLIDAY" ? "info" : "neutral"}>{event.eventType}</Badge>
                        </td>
                        <td>{event.startDate}</td>
                        <td>{event.endDate}</td>
                        <td>{event.sourceLevel}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>

          <Panel title="Add a local event" description="Academic session ID has no picker yet; paste it directly.">
            <form onSubmit={submit} noValidate>
              <IdField
                id="cal-session-id"
                label="Academic session ID"
                value={academicSessionId}
                onChange={setAcademicSessionId}
              />
              <SelectField
                id="cal-event-type"
                label="Event type"
                value={eventType}
                onChange={(e) => setEventType(e.target.value)}
                options={[
                  { value: "LOCAL_HOLIDAY", label: "Local holiday" },
                  { value: "LOCAL_EVENT", label: "Local event" },
                  { value: "EXAM_DAY", label: "Exam day" },
                ]}
              />
              <TextField
                id="cal-title"
                label="Title"
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Founder's Day"
              />
              <div className="platform-form-grid">
                <DateField
                  id="cal-start-date"
                  label="Start date"
                  required
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                />
                <DateField
                  id="cal-end-date"
                  label="End date"
                  required
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                />
              </div>
              <ResultBanner status={status} message={message} />
              <Button type="submit" busy={status === "busy"}>
                {status === "busy" ? "Adding…" : "Add event"}
              </Button>
            </form>
          </Panel>
        </>
      )}

      {/* Add Academic Session Modal */}
      <AddAcademicSessionModal
        isOpen={showAddSessionModal}
        onClose={() => {
          setShowAddSessionModal(false);
          setSessionModalError("");
        }}
        onSubmit={async (data) => {
          if (!tenantId) return;
          if (!data.sessionLabel || !data.startDate || !data.endDate) {
            setSessionModalError("Session label, start date, and end date are required.");
            return;
          }
          if (data.endDate <= data.startDate) {
            setSessionModalError("End date must be after start date.");
            return;
          }
          setSessionSubmitting(true);
          setSessionModalError("");
          try {
            const created = await institutionService.createAcademicSession(tenantId, data);
            setSessions((prev) => (prev ? [created, ...prev] : [created]));
            setShowAddSessionModal(false);
          } catch (err) {
            setSessionModalError(getApiErrorMessage(err));
          } finally {
            setSessionSubmitting(false);
          }
        }}
        submitting={sessionSubmitting}
        error={sessionModalError}
      />

      {/* Add Master Calendar Event Modal */}
      <AddMasterEventModal
        isOpen={showAddMasterEventModal}
        onClose={() => {
          setShowAddMasterEventModal(false);
          setMasterEventModalError("");
        }}
        sessions={sessions ?? []}
        onSubmit={async (data) => {
          if (!tenantId) return;
          if (!data.academicSessionId || !data.title || !data.startDate || !data.endDate) {
            setMasterEventModalError("Academic session, title, start date, and end date are required.");
            return;
          }
          if (data.endDate < data.startDate) {
            setMasterEventModalError("End date must not be before start date.");
            return;
          }
          setMasterEventSubmitting(true);
          setMasterEventModalError("");
          try {
            const created = await institutionService.createMasterCalendarEvent(tenantId, data);
            setMasterEvents((prev) => (prev ? [...prev, created] : [created]));
            setShowAddMasterEventModal(false);
          } catch (err) {
            setMasterEventModalError(getApiErrorMessage(err));
          } finally {
            setMasterEventSubmitting(false);
          }
        }}
        submitting={masterEventSubmitting}
        error={masterEventModalError}
      />
    </div>
  );
}

// --- 6. Weekly-off rules (WEEKLY_OFF_SET) ------------------------------------------------------

type WeeklyOffRule = {
  weeklyOffRuleId: string;
  institutionId: string;
  dayOfWeek: number;
  occurrence: string;
  effectiveFrom: string;
  effectiveTo: string | null;
};

const DAY_OPTIONS = [
  { value: 1, label: "Monday" },
  { value: 2, label: "Tuesday" },
  { value: 3, label: "Wednesday" },
  { value: 4, label: "Thursday" },
  { value: 5, label: "Friday" },
  { value: 6, label: "Saturday" },
  { value: 7, label: "Sunday" },
];

function dayLabel(dayOfWeek: number): string {
  return DAY_OPTIONS.find((d) => d.value === dayOfWeek)?.label ?? String(dayOfWeek);
}

function formatStatusLabel(value: string): string {
  const known: Record<string, string> = {
    ACTIVE: "Active", DRAFT: "Draft", APPROVED: "Approved", PENDING_APPROVAL: "Pending approval",
    REJECTED: "Rejected", CANCELLED: "Cancelled", EXAM_DAY: "Exam day", SEMESTER_DATES: "Semester dates",
    LOCAL_HOLIDAY: "Local holiday", LOCAL_EVENT: "Local event", UNIVERSITY: "University", COLLEGE: "College", SCHOOL: "School",
  };
  return known[value] ?? value.replaceAll("_", " ").toLowerCase().replace(/\\b\\w/g, (letter) => letter.toUpperCase());
}

export function WeeklyOffRulesPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [dayOfWeek, setDayOfWeek] = useState(7);
  const [occurrence, setOccurrence] = useState("ALL");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [effectiveTo, setEffectiveTo] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ institutionId?: string; effectiveFrom?: string; effectiveTo?: string }>({});

  const [rules, setRules] = useState<WeeklyOffRule[] | null>(null);
  const [listStatus, setListStatus] = useState<Status>("idle");
  const [listMessage, setListMessage] = useState("");
  const { status, message, run } = useAction<WeeklyOffRule>();

  const loadRules = async () => {
    if (!isValidUuid(institutionId)) {
      setFieldErrors((current) => ({ ...current, institutionId: "Enter a valid institution ID." }));
      return;
    }
    setFieldErrors((current) => ({ ...current, institutionId: undefined }));
    setListStatus("busy");
    setListMessage("");
    try {
      const res = await api.get<WeeklyOffRule[]>(`/tenants/${tenantId}/weekly-off-rules`, {
        params: { institutionId: institutionId.trim() },
      });
      setRules(res.data);
      setListStatus("idle");
    } catch (err) {
      setRules(null);
      if (axios.isAxiosError(err) && err.response?.status === 403) {
        setListStatus("denied");
      } else {
        setListStatus("error");
      }
      setListMessage(getApiErrorMessage(err));
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const nextErrors: typeof fieldErrors = {};
    if (!isValidUuid(institutionId)) nextErrors.institutionId = "Enter a valid institution ID.";
    if (!effectiveFrom) nextErrors.effectiveFrom = "Choose an effective-from date.";
    if (effectiveTo && effectiveTo < effectiveFrom) nextErrors.effectiveTo = "The end date cannot be earlier than the start date.";
    setFieldErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    void run(
      null,
      () =>
        api.post<WeeklyOffRule>(`/tenants/${tenantId}/weekly-off-rules`, {
          institutionId: institutionId.trim(),
          dayOfWeek,
          occurrence,
          effectiveFrom,
          effectiveTo: effectiveTo || null,
        }),
      (data) => {
        setRules((prev) => (prev ? [...prev, data] : [data]));
        return `Weekly-off rule added: ${dayLabel(data.dayOfWeek)} (${data.occurrence}) from ${data.effectiveFrom}.`;
      },
    );
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Weekly-off rules"
        description="Set the institution’s weekly day off, including alternate-week patterns."
      />
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <>
          <Panel title="Choose institution" description="There is no institution picker yet; paste the institution ID.">
            <div className="eds-field" style={{ marginTop: 0 }}>
              <label htmlFor="wo-institution-id">
                Institution ID <span>(Required)</span>
              </label>
              <div style={{ display: "flex", gap: "var(--space-3)", alignItems: "center" }}>
                <input
                  id="wo-institution-id"
                  value={institutionId}
                  aria-invalid={Boolean(fieldErrors.institutionId)}
                  aria-describedby={fieldErrors.institutionId ? "wo-institution-id-error" : undefined}
                  onChange={(e) => {
                    setInstitutionId(e.target.value);
                    setFieldErrors((current) => ({ ...current, institutionId: undefined }));
                  }}
                  placeholder="Paste the ID (UUID)"
                  style={{ flex: 1 }}
                />
                <Button type="button" variant="secondary" onClick={() => void loadRules()} busy={listStatus === "busy"}>
                  {listStatus === "busy" ? "Loading…" : "Load rules"}
                </Button>
              </div>
            </div>
            <ResultBanner status={listStatus} message={listMessage} />
          </Panel>

          <Panel title="Current weekly-off rules">
            {rules === null ? (
              <EmptyState
                title="No rules loaded yet"
                description="Enter an institution ID above and choose Load rules."
                icon="clock"
              />
            ) : rules.length === 0 ? (
              <EmptyState
                title="No weekly-off rules recorded"
                description="No weekly-off rules are recorded for this institution."
                icon="clock"
              />
            ) : (
              <div className="platform-table-wrap">
                <table className="platform-table">
                  <thead>
                    <tr>
                      <th>Day</th>
                      <th>Occurrence</th>
                      <th>Effective from</th>
                      <th>Effective to</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rules.map((rule) => (
                      <tr key={rule.weeklyOffRuleId}>
                        <td>
                          <strong>{dayLabel(rule.dayOfWeek)}</strong>
                        </td>
                        <td>
                          <Badge tone="neutral">{rule.occurrence}</Badge>
                        </td>
                        <td>{rule.effectiveFrom}</td>
                        <td>{rule.effectiveTo ?? "Open-ended"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>

          <Panel title="Add a weekly-off rule">
            <form onSubmit={submit} noValidate>
              <label className="platform-form-field">
                Day of week
                <select value={dayOfWeek} onChange={(e) => setDayOfWeek(Number(e.target.value))}>
                  {DAY_OPTIONS.map((d) => (
                    <option key={d.value} value={d.value}>
                      {d.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="platform-form-field">
                Occurrence
                <select value={occurrence} onChange={(e) => setOccurrence(e.target.value)}>
                  <option value="ALL">Every week</option>
                  <option value="ODD">Odd weeks only</option>
                  <option value="EVEN">Even weeks only</option>
                </select>
              </label>
              <div className="platform-form-grid">
                <DateField
                  id="weekly-off-effective-from"
                  label="Effective from"
                  value={effectiveFrom}
                  onChange={(e) => {
                    setEffectiveFrom(e.target.value);
                    setFieldErrors((current) => ({ ...current, effectiveFrom: undefined, effectiveTo: undefined }));
                  }}
                  required
                  error={fieldErrors.effectiveFrom}
                />
                <DateField
                  id="weekly-off-effective-to"
                  label="Effective to (optional)"
                  value={effectiveTo}
                  onChange={(e) => {
                    setEffectiveTo(e.target.value);
                    setFieldErrors((current) => ({ ...current, effectiveTo: undefined }));
                  }}
                  error={fieldErrors.effectiveTo}
                />
              </div>
              <ResultBanner status={status} message={message} />
              <Button type="submit" busy={status === "busy"}>
                {status === "busy" ? "Saving…" : "Add rule"}
              </Button>
            </form>
          </Panel>
        </>
      )}
    </div>
  );
}

// --- 7. Academic structure management (STRUCTURE_MANAGE) -------------------------------------

export function AcademicStructurePage() {
  const user = useAppSelector((state) => state.auth.user);
  const tenantId = user?.tenantId;
  const [institutionId, setInstitutionId] = useState("");
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [departments, setDepartments] = useState<DepartmentItem[]>([]);
  const [programs, setPrograms] = useState<ProgramItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Modals
  const [showAddDept, setShowAddDept] = useState(false);
  const [deptSubmitting, setDeptSubmitting] = useState(false);
  const [deptError, setDeptError] = useState<string | null>(null);

  const [showAddProg, setShowAddProg] = useState(false);
  const [progSubmitting, setProgSubmitting] = useState(false);
  const [progError, setProgError] = useState<string | null>(null);

  useEffect(() => {
    if (!tenantId || import.meta.env.MODE === "test") return;
    institutionService
      .listInstitutions(tenantId)
      .then((res) => {
        if (res?.items?.length) {
          setInstitutions(res.items);
        }
      })
      .catch((err) => setError(getApiErrorMessage(err)));
  }, [tenantId]);

  if (!tenantId) return <NoTenantNotice />;

  const loadStructureFor = async (id: string) => {
    if (!isValidUuid(id)) {
      setError("Please enter a valid institution UUID.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [deptRes, progRes] = await Promise.all([
        institutionService.listDepartments(tenantId, id),
        institutionService.listPrograms(tenantId, id),
      ]);
      setDepartments(deptRes);
      setPrograms(progRes);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const loadStructure = async () => {
    await loadStructureFor(institutionId);
  };

  const selectedInst = institutions.find((i) => i.institutionId === institutionId);
  const isSchool = selectedInst?.institutionType === "SCHOOL" || user?.roleCode === "SCHOOL_ADMIN";

  return (
    <div className="platform-page">
      <PageHeader
        title="Academic structure"
        description="Create and manage academic departments and degree programmes."
      />

      <Panel title="Select institution" description="Select a registered institution or enter its ID to view and manage its academic hierarchy.">
        <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
          {institutions.length > 0 && (
            <div style={{ maxWidth: "480px", marginBottom: "var(--space-3)" }}>
              <SelectField
                id="quick-inst-select"
                label="Choose from registered institutions"
                value={institutionId}
                onChange={(e) => {
                  const val = e.target.value;
                  setInstitutionId(val);
                  if (val && isValidUuid(val)) {
                    void loadStructureFor(val);
                  }
                }}
              >
                <option value="">-- Select an institution --</option>
                {institutions.map((inst, idx) => (
                  <option key={inst.institutionId || inst.institutionCode || idx} value={inst.institutionId}>
                    {inst.institutionName} ({inst.institutionType === "COLLEGE" ? "College" : "School"}) [{inst.institutionCode}]
                  </option>
                ))}
              </SelectField>
            </div>
          )}
          <div style={{ marginTop: institutions.length > 0 ? "var(--space-3)" : 0 }}>
            <div style={{ display: "flex", gap: "var(--space-3)", alignItems: "flex-start" }}>
              <div style={{ flex: 1 }}>
                <TextField
                  id="structure-institution-id"
                  label="Institution ID *"
                  value={institutionId}
                  onChange={(e) => setInstitutionId(e.target.value)}
                  placeholder="Paste the ID (UUID)"
                  hint="UUID of the college or school"
                />
              </div>
              <div style={{ paddingTop: "1.75rem" }}>
                <Button type="button" onClick={() => void loadStructure()} busy={loading}>
                  {loading ? "Loading…" : "Load structure"}
                </Button>
              </div>
            </div>
          </div>
          {error && (
            <div style={{ marginTop: "var(--space-4)" }}>
              <Toast variant="error" description={error} />
            </div>
          )}
        </div>
      </Panel>

      {isSchool && institutionId && (
        <div style={{ marginTop: "var(--space-4)" }}>
          <Toast
            variant="info"
            title="School Structure & Curriculum (Rule HI-12)"
            content={
              <div>
                <p style={{ margin: "0 0 var(--space-3) 0" }}>
                  Schools organise learners into Classes and Sections rather than academic departments.
                  Use the dedicated school configuration tools below to assign teachers and calendar rules:
                </p>
                <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap" }}>
                  <a
                    href="/university/setup/class-teacher"
                    style={{
                      display: "inline-block",
                      padding: "6px 14px",
                      borderRadius: "var(--radius-md)",
                      backgroundColor: "var(--color-brand-primary, #2563eb)",
                      color: "#ffffff",
                      fontWeight: 500,
                      textDecoration: "none",
                      fontSize: "0.8125rem",
                    }}
                  >
                    Assign Class Teachers →
                  </a>
                  <a
                    href="/university/setup/weekly-off"
                    style={{
                      display: "inline-block",
                      padding: "6px 14px",
                      borderRadius: "var(--radius-md)",
                      backgroundColor: "var(--color-bg-surface, #ffffff)",
                      color: "var(--color-brand-primary, #1e40af)",
                      border: "1px solid var(--color-border-divider, #bfdbfe)",
                      fontWeight: 500,
                      textDecoration: "none",
                      fontSize: "0.8125rem",
                    }}
                  >
                    Configure Weekly Off &amp; Calendar →
                  </a>
                </div>
              </div>
            }
          />
        </div>
      )}

      {institutionId && (
        <>
          {/* Departments Panel */}
          <Panel
            title="Academic Departments"
            description="College-level academic departments [Rule HI-12: Colleges only; schools do not have departments in v1]."
          >
            <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
              <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "1rem" }}>
                <Button type="button" onClick={() => setShowAddDept(true)}>
                  Add Department
                </Button>
              </div>

              {departments.length === 0 ? (
                <p className="platform-sample-note">No departments found for this institution.</p>
              ) : (
                <div className="platform-table-wrap">
                  <table className="platform-table">
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Department Name</th>
                        <th>Status</th>
                        <th>HOD Staff Assignment</th>
                      </tr>
                    </thead>
                    <tbody>
                      {departments.map((d) => (
                        <tr key={d.departmentId}>
                          <td><strong><code>{d.departmentCode}</code></strong></td>
                          <td>{d.departmentName}</td>
                          <td><Badge tone="success">{d.status}</Badge></td>
                          <td style={{ fontSize: "0.8125rem", color: "var(--color-text-secondary, #64748b)" }}>
                            {d.hodStaffAssignmentId ?? "Unassigned"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </Panel>

          {/* Programs Panel */}
          <Panel
            title="Degree Programs"
            description="Degree and certification programs under departments (e.g. B.Tech, M.Sc)."
          >
            <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
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

              {programs.length === 0 ? (
                <p className="platform-sample-note">No degree programs found for this institution.</p>
              ) : (
                <div className="platform-table-wrap">
                  <table className="platform-table">
                    <thead>
                      <tr>
                        <th>Program Code</th>
                        <th>Program Name</th>
                        <th>Duration</th>
                        <th>Semesters / Year</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {programs.map((p) => (
                        <tr key={p.programId}>
                          <td><strong><code>{p.programCode}</code></strong></td>
                          <td>{p.programName}</td>
                          <td>{p.durationYears} {p.durationYears === 1 ? "Year" : "Years"}</td>
                          <td>{p.semestersPerYear}</td>
                          <td><Badge tone="success">{p.status}</Badge></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </Panel>
        </>
      )}

      {/* Add Department Modal */}
      <AddDepartmentModal
        isOpen={showAddDept}
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
        isOpen={showAddProg}
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
  const [institutionId, setInstitutionId] = useState("");
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [sessionId, setSessionId] = useState("");
  const [semesterId, setSemesterId] = useState("");
  const [lockNotes, setLockNotes] = useState("");
  const [locks, setLocks] = useState<TermLockItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [locking, setLocking] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);
  const [lockLoadError, setLockLoadError] = useState("");
  const [institutionLoadError, setInstitutionLoadError] = useState("");
  const { fieldErrors, handleApiError: handleLockApiError, clearFieldError: clearLockFieldError, clearAll: clearLockAll } = useFormErrors();

  useEffect(() => {
    if (!tenantId || import.meta.env.MODE === "test") return;
    institutionService
      .listInstitutions(tenantId)
      .then((res) => {
        if (res?.items?.length) {
          setInstitutions(res.items);
        }
      })
      .catch((err) => setInstitutionLoadError(getApiErrorMessage(err)));
  }, [tenantId]);

  // Unlock modal state
  const [unlockModalItem, setUnlockModalItem] = useState<TermLockItem | null>(null);
  const [unlocking, setUnlocking] = useState(false);

  const fetchLocks = (instId: string) => {
    if (!tenantId || !isValidUuid(instId)) return;
    setLoading(true);
    setLockLoadError("");
    termLockService
      .listTermLocks(tenantId, instId.trim())
      .then((data) => setLocks(data || []))
      .catch((err) => setLockLoadError(getApiErrorMessage(err)))
      .finally(() => setLoading(false));
  };

  const handleLockSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!tenantId) return;
    // Client-side UUID check — show inline errors directly on the fields
    if (!isValidUuid(institutionId)) {
      clearLockFieldError("institutionId"); // reset first to force re-render
      setTimeout(() => clearLockFieldError("institutionId"), 0);
    }
    if (!isValidUuid(institutionId) || !isValidUuid(sessionId)) {
      const msg = "Valid Institution ID and Academic Session ID (UUID) are required.";
      setFeedback({ tone: "danger", message: msg });
      toast.error("Validation error", msg);
      return;
    }
    setLocking(true);
    clearLockAll();
    try {
      const res = await termLockService.lockTerm(tenantId, institutionId.trim(), {
        academicSessionId: sessionId.trim(),
        semesterId: semesterId.trim() ? semesterId.trim() : undefined,
        notes: lockNotes.trim() || undefined,
      });
      const msg = `Term locked successfully [Rule R2-08]. Attendance and marks cannot change.`;
      setFeedback({ tone: "success", message: msg });
      toast.success("Term locked successfully [Rule R2-08]", "Attendance and marks cannot change.");
      setLocks((prev) => [res, ...prev.filter((l) => l.termLockId !== res.termLockId)]);
      setLockNotes("");
    } catch (err: unknown) {
      handleLockApiError(err);
      const msg = getApiErrorMessage(err);
      setFeedback({ tone: "danger", message: msg });
      toast.error("Failed to lock term", msg);
    } finally {
      setLocking(false);
    }
  };

  if (!tenantId) return <NoTenantNotice />;

  return (
    <div className="platform-page">
      <PageHeader
        title="Term lock management"
        description="Lock academic terms to protect marks and attendance records. Temporary unlocks require justification and expire automatically."
      />

      {institutionLoadError && <Notice tone="danger">{institutionLoadError}</Notice>}
      {feedback && (
        <Toast
          variant={feedback.tone === "danger" ? "error" : "success"}
          description={feedback.message}
          onDismiss={() => setFeedback(null)}
          className="mb-4"
        />
      )}

      <Panel title="Lock academic term" description="Principal authority to lock a term upon final marks/attendance submission [Rule R2-08].">
        <form onSubmit={handleLockSubmit} className="platform-form">
          {institutions.length > 0 && (
            <div style={{ maxWidth: "480px", marginBottom: "var(--space-3)" }}>
              <SelectField
                id="quick-lock-inst-select"
                label="Choose registered institution"
                value={institutionId}
                onChange={(e) => {
                  const val = e.target.value;
                  setInstitutionId(val);
                  if (isValidUuid(val)) fetchLocks(val);
                  else { setLocks([]); setLockLoadError(""); }
                }}
              >
                <option value="">-- Select an institution --</option>
                {institutions.map((inst, idx) => (
                  <option key={inst.institutionId || inst.institutionCode || idx} value={inst.institutionId}>
                    {inst.institutionName} ({inst.institutionType === "COLLEGE" ? "College" : "School"}) [{inst.institutionCode}]
                  </option>
                ))}
              </SelectField>
            </div>
          )}
          <div className="platform-form-grid" style={{ marginBottom: "var(--space-3)" }}>
            <IdField
              id="term-lock-inst-id"
              label="Institution ID"
              value={institutionId}
              onChange={(val) => {
                setInstitutionId(val);
                clearLockFieldError("institutionId");
                if (isValidUuid(val)) fetchLocks(val);
                else { setLocks([]); setLockLoadError(""); }
              }}
              hint="Paste college or school UUID"
              error={fieldErrors.institutionId}
            />
            <IdField
              id="term-lock-session-id"
              label="Academic Session ID"
              value={sessionId}
              onChange={(val) => { setSessionId(val); clearLockFieldError("academicSessionId"); }}
              hint="Paste academic session UUID"
              error={fieldErrors.academicSessionId}
            />
          </div>
          <div className="platform-form-grid" style={{ marginBottom: "var(--space-3)" }}>
            <TextField
              id="term-lock-sem-id"
              label="Semester ID (Optional)"
              value={semesterId}
              onChange={(e) => { setSemesterId(e.target.value); clearLockFieldError("semesterId"); }}
              placeholder="Optional semester UUID for colleges"
              hint="Optional semester UUID for colleges"
              error={fieldErrors.semesterId}
            />
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
            <Button type="submit" disabled={locking || !isValidUuid(institutionId) || !isValidUuid(sessionId)}>
              {locking ? "Locking term..." : "Lock Term [Rule R2-08]"}
            </Button>
          </div>
        </form>
      </Panel>

      <Panel title="Active term locks" description="Review locked terms. Principals can temporarily unlock for grievances or corrections.">
        {loading && <p className="platform-empty-note">Loading locks...</p>}
        {lockLoadError ? (
          <Notice tone="danger">{lockLoadError} <Button variant="secondary" size="sm" onClick={() => fetchLocks(institutionId)}>Retry</Button></Notice>
        ) : !isValidUuid(institutionId) ? (
          <EmptyState title="Choose an institution" description="Select an institution or enter its UUID to load term locks." />
        ) : locks.length === 0 && !loading ? (
          <EmptyState
            title="No active term locks"
            description="No active term locks recorded for this institution."
            icon="lock"
          />
        ) : loading ? null : (
          <div className="platform-table-wrap">
            <table className="platform-table">
              <thead>
                <tr>
                  <th>Session ID</th>
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
                      <code>{lock.academicSessionId}</code>
                    </td>
                    <td>{lock.semesterId ? <code>{lock.semesterId}</code> : "Whole Session"}</td>
                    <td>
                      <Badge tone={lock.lockState === "LOCKED" ? "danger" : "warning"}>
                        {lock.lockState}
                      </Badge>
                    </td>
                    <td>{lock.unlockUntil ? new Date(lock.unlockUntil).toLocaleString() : "—"}</td>
                    <td>
                      {lock.lockState === "LOCKED" ? (
                        <Button
                          variant="secondary"
                          style={{ padding: "2px 8px", fontSize: "0.8rem" }}
                          onClick={() => setUnlockModalItem(lock)}
                        >
                          Unlock Term
                        </Button>
                      ) : (
                        <span style={{ fontSize: "0.8rem", color: "var(--color-muted)" }}>Unlocked</span>
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
            const res = await termLockService.unlockTerm(tenantId, unlockModalItem.institutionId, unlockModalItem.termLockId, {
              reason: reason.trim(),
              unlockDurationHours: Number(hours) || 72,
            });
            setFeedback({
              tone: "success",
              message: `Term temporarily unlocked until ${res.unlockUntil || "3 days"} [Rule AT-09]. It will automatically relock.`,
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
  const { fieldErrors: delegFieldErrors, handleApiError: handleDelegApiError, clearFieldError: clearDelegFieldError, clearAll: clearDelegAll } = useFormErrors();
  const { fieldErrors: bulkFieldErrors, handleApiError: handleBulkApiError, clearFieldError: clearBulkFieldError, clearAll: clearBulkAll } = useFormErrors();

  // New delegation state
  const [delegateUserId, setDelegateUserId] = useState("");
  const [delegationBasis, setDelegationBasis] = useState<"SAME_OR_HIGHER" | "DESIGNATED_DEPUTY">("SAME_OR_HIGHER");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [creating, setCreating] = useState(false);

  // Bulk approvals state
  const [bulkIds, setBulkIds] = useState("");
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
    fetchDelegations();
    fetchInbox();
  }, [fetchDelegations, fetchInbox, tenantId]);

  const handleDecideStep = async (step: ApprovalStepItem, decision: "APPROVED" | "REJECTED") => {
    const requestId = step.approvalRequestId || "";
    if (!requestId) return;
    let remark: string | undefined;
    if (decision === "REJECTED") {
      const promptRemark = window.prompt("Please provide a rejection remark (mandatory under Rule AP-06):");
      if (!promptRemark || !promptRemark.trim()) {
        setFeedback({ tone: "danger", message: "A remark is mandatory when rejecting an approval." });
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
    if (!isValidUuid(delegateUserId)) {
      const msg = "Valid Delegate User Account ID (UUID) is required.";
      setFeedback({ tone: "danger", message: msg });
      toast.error("Validation error", msg);
      return;
    }
    if (!startsAt || !endsAt) {
      const msg = "Start and end dates are required for time-bounded delegation.";
      setFeedback({ tone: "danger", message: msg });
      toast.error("Validation error", msg);
      return;
    }
    setCreating(true);
    clearDelegAll();
    try {
      const res = await approvalDelegationService.createDelegation(tenantId, {
        delegateUserId: delegateUserId.trim(),
        delegationBasis,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
      });
      const msg = `Approval authority successfully delegated [APPROVAL_DELEGATE / Rule 11]. Self-approval prohibited.`;
      setFeedback({
        tone: "success",
        message: msg,
      });
      toast.success("Approval authority delegated [Rule 11]", "Self-approval prohibited.");
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
    const ids = bulkIds
      .split(",")
      .map((s) => s.trim())
      .filter((s) => isValidUuid(s));

    if (ids.length === 0) {
      const msg = "Please provide at least one valid approval request UUID.";
      setFeedback({ tone: "danger", message: msg });
      toast.error("Validation error", msg);
      return;
    }

    setBulkSubmitting(true);
    clearBulkAll();
    try {
      const res = await approvalDelegationService.bulkDecide(tenantId, {
        approvalRequestIds: ids,
        decision: bulkDecision,
        comment: bulkComment.trim() || undefined,
      });
      const msg = `Bulk decision applied: ${res.succeeded} items processed successfully [APPROVAL_BULK / Rule AP-25].`;
      setFeedback({
        tone: "success",
        message: msg,
      });
      toast.success("Bulk decision applied [Rule AP-25]", `${res.succeeded} items processed successfully.`);
      setBulkIds("");
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
    <div className="platform-page">
      <PageHeader
        title="Approvals & delegations"
        description="Delegate approval authority for a defined period or review several requests together."
      />

      {feedback && (
        <Toast
          variant={feedback.tone === "danger" ? "error" : "success"}
          description={feedback.message}
          onDismiss={() => setFeedback(null)}
          className="mb-4"
        />
      )}

      {/* Delegation Form */}
      <Panel
        title="Delegate approval authority"
        description="Hand over your approval responsibilities to an eligible colleague for a defined period."
      >
        <form onSubmit={handleCreateDelegation} className="platform-form">
          <TextField
            id="delegate-user-id"
            label="Delegate User Account ID (UUID) *"
            required
            placeholder="Paste delegate user account UUID"
            value={delegateUserId}
            onChange={(e) => { setDelegateUserId(e.target.value); clearDelegFieldError("delegateUserId"); }}
            style={{ fontFamily: "monospace" }}
            error={delegFieldErrors.delegateUserId}
          />

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

          <div className="platform-form-grid">
            <DateTimeField
              id="delegation-start"
              label="Effective From *"
              required
              value={startsAt}
              onChange={(e) => { setStartsAt(e.target.value); clearDelegFieldError("startsAt"); }}
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

          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "var(--space-4)" }}>
            <Button type="submit" disabled={creating || !isValidUuid(delegateUserId) || !startsAt || !endsAt}>
              {creating ? "Delegating..." : "Create Delegation [Rule 11]"}
            </Button>
          </div>
        </form>
      </Panel>

      {/* Bulk Approvals Form */}
      <Panel
        title="Bulk approval decision"
        description="Review and decide several eligible requests together, with an optional comment."
      >
        <form onSubmit={handleBulkDecision} className="platform-form">
          <TextAreaField
            id="bulk-request-ids"
            label="Approval Request IDs (comma-separated UUIDs) *"
            rows={3}
            required
            placeholder="UUID1, UUID2, UUID3..."
            value={bulkIds}
            onChange={(e) => { setBulkIds(e.target.value); clearBulkFieldError("approvalRequestIds"); }}
            style={{ fontFamily: "monospace", fontSize: "0.875rem" }}
            error={bulkFieldErrors.approvalRequestIds}
          />

          <div className="platform-form-grid">
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

          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "var(--space-4)" }}>
            <Button type="submit" disabled={bulkSubmitting || !bulkIds.trim()}>
              {bulkSubmitting ? "Processing..." : `Bulk ${bulkDecision === "APPROVED" ? "Approve" : "Reject"} [Rule AP-25]`}
            </Button>
          </div>
        </form>
      </Panel>

      <div style={{ marginTop: "var(--space-4)" }}>
        <Panel
          title="Pending approval requests (Inbox)"
          description="Pending steps awaiting your review or acting delegation [Addendum Rule 11 / Self-approval prohibited]."
        >
          {inboxLoading && <p className="platform-empty-note">Loading pending approvals...</p>}
          {inboxError && <Notice tone="danger">{inboxError} <Button variant="secondary" size="sm" onClick={fetchInbox}>Retry</Button></Notice>}
          {!inboxLoading && !inboxError && inboxItems.length === 0 ? (
            <EmptyState
              title="No pending approvals"
              description="No pending approval requests in your inbox."
              icon="check-circle"
            />
          ) : inboxLoading || inboxError ? null : (
            <div className="platform-table-wrap">
              <table className="platform-table">
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
                      <td><code>{item.approverRoleCode}</code></td>
                      <td><Badge tone="warning">{item.decision}</Badge></td>
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
        <Panel title="My active delegations" description="Review delegations you have granted. Self-approval is strictly prohibited under Rule 11.">
          {loading && <p className="platform-empty-note">Loading delegations...</p>}
          {delegationsError && <Notice tone="danger">{delegationsError} <Button variant="secondary" size="sm" onClick={fetchDelegations}>Retry</Button></Notice>}
          {!loading && !delegationsError && delegations.length === 0 ? (
            <EmptyState
              title="No active delegations"
              description="No active delegations created."
              icon="user"
            />
          ) : loading || delegationsError ? null : (
            <div className="platform-table-wrap">
              <table className="platform-table">
                <thead>
                  <tr>
                    <th>Delegate User ID</th>
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
                      <td>
                        <code>{d.delegateUserId}</code>
                      </td>
                      <td>{d.delegationBasis}</td>
                      <td>{new Date(d.startsAt).toLocaleString()}</td>
                      <td>{new Date(d.endsAt).toLocaleString()}</td>
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
                          <span style={{ fontSize: "0.8rem", color: "var(--color-muted)" }}>Cancelled</span>
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
    } else {
      setLoading(false);
    }
  }, [activeTab, tenantId]);

  useEffect(() => {
    if (tenantId) fetchCurrentTab();
  }, [fetchCurrentTab, tenantId]);

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
    <div className="platform-page">
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

      {loading && <p className="platform-empty-note">Loading academic configuration...</p>}
      {loadError && !loading && <Notice tone="danger">{loadError} <Button variant="secondary" size="sm" onClick={fetchCurrentTab}>Retry</Button></Notice>}

      {!loading && !loadError && activeTab === "rules" && (
        <div id="panel-rules" role="tabpanel" aria-labelledby="tab-rules" tabIndex={0}>
        <Panel title="Curriculum and programme rules" description="Versioned curriculum requirements set by the Registrar.">
          <div className="platform-table-wrap">
            <table className="platform-table">
              <thead>
                <tr>
                  <th>Rule Set ID</th>
                  <th>Version</th>
                  <th>Effective From</th>
                  <th>Status</th>
                  <th>Rules (JSON)</th>
                </tr>
              </thead>
              <tbody>
                {rules.length === 0 ? (
                  <tr><td colSpan={5} style={{ textAlign: "center", padding: "var(--space-4)" }}>No program rule sets defined.</td></tr>
                ) : (
                  rules.map((r) => (
                    <tr key={r.programRuleSetId}>
                      <td><code>{r.programRuleSetId}</code></td>
                      <td>v{r.versionNo}</td>
                      <td>{r.effectiveFrom}</td>
                      <td><Badge tone={r.status === "ACTIVE" ? "success" : "neutral"}>{formatStatusLabel(r.status)}</Badge></td>
                      <td><code style={{ fontSize: "0.8rem" }}>{r.rules}</code></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Panel>
      )}

      {!loading && !loadError && activeTab === "grading" && (
        <div id="panel-grading" role="tabpanel" aria-labelledby="tab-grading" tabIndex={0}>
        <Panel title="Grading schemes" description="University-wide or standalone institution grade points and bands.">
          <div className="platform-table-wrap">
            <table className="platform-table">
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

      {!loading && !loadError && activeTab === "allocation" && (
        <div id="panel-allocation" role="tabpanel" aria-labelledby="tab-allocation" tabIndex={0}>
        <Panel title="Subject allocations" description="Allocate courses to teachers for specific class sections or elective subject groups.">
          <div className="platform-table-wrap">
            <table className="platform-table">
              <thead>
                <tr>
                  <th>Offering ID</th>
                  <th>Section / Group</th>
                  <th>Teacher Assignment ID</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {allocations.length === 0 ? (
                  <tr><td colSpan={4} style={{ textAlign: "center", padding: "var(--space-4)" }}>No subjects allocated yet.</td></tr>
                ) : (
                  allocations.map((a) => (
                    <tr key={a.subjectAllocationId}>
                      <td><code>{a.courseOfferingId}</code></td>
                      <td><code>{a.sectionId || a.subjectGroupId}</code></td>
                      <td><code>{a.staffAssignmentId}</code></td>
                      <td><Badge tone={a.status === "ACTIVE" ? "success" : "neutral"}>{formatStatusLabel(a.status)}</Badge></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Panel>
      )}

      {!loading && !loadError && activeTab === "promotion" && (
        <div id="panel-promotion" role="tabpanel" aria-labelledby="tab-promotion" tabIndex={0}>
        <Panel title="Year-end promotions" description="Promote students across academic sessions; Principal approves before execution.">
          <div className="platform-table-wrap">
            <table className="platform-table">
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
                      <td><code>{p.fromSessionId}</code></td>
                      <td><code>{p.toSessionId}</code></td>
                      <td><Badge tone={p.status === "APPROVED" ? "success" : "neutral"}>{formatStatusLabel(p.status)}</Badge></td>
                      <td>
                        {p.status === "PENDING_APPROVAL" && (
                          <Button variant="secondary" onClick={() => handleApprovePromotion(p.promotionRunId)}>
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
          } finally {
            setSaving(false);
          }
        }}
        saving={saving}
      />

      {/* Subject Allocation Modal */}
      <AllocateSubjectModal
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
          } finally {
            setSaving(false);
          }
        }}
        saving={saving}
      />

      {/* Promotion Run Modal */}
      <PromotionRunModal
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
          } finally {
            setSaving(false);
          }
        }}
        saving={saving}
      />
    </div>
  );
}