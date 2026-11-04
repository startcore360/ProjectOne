import { useState, type FormEvent, type ReactNode } from "react";
import axios from "axios";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Badge } from "../../../components/ui";
import { Button, TextField } from "../../../design-system/components";

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

function Panel({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <section className="platform-panel">
      <header className="platform-panel__header">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
      </header>
      {children}
    </section>
  );
}

function IdField({
  id,
  label,
  value,
  onChange,
  hint,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  hint?: string;
}) {
  return (
    <div className="eds-field">
      <label htmlFor={id}>
        {label} <span>Required</span>
      </label>
      <input
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Paste the ID (UUID)"
        aria-describedby={`${id}-hint`}
      />
      <p id={`${id}-hint`} className="platform-sample-note" style={{ marginTop: "var(--space-1)" }}>
        <span aria-hidden="true">ⓘ</span> {hint ?? "There is no list or picker for this yet — paste the ID directly."}
      </p>
    </div>
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
        description="HOD_ASSIGN — make a faculty member the Head of Department for a department."
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
        description="MENTOR_ASSIGN — assign a mentor / class coordinator to a college section."
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
        description="CLASS_TEACHER_ASSIGN — assign a Class Teacher to a school SECTION."
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
        description="STUDENT_LOGIN_SWITCH — turn view-only student login on or off for a school class."
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
        description="CALENDAR_LOCAL_SET — add local holidays and events for an institution. Semester dates and university-wide holidays belong to a separate, not-yet-built screen."
      />
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <>
          <Panel title="Choose institution" description="There is no institution picker yet; paste the institution ID.">
            <IdField id="cal-institution-id" label="Institution ID" value={institutionId} onChange={setInstitutionId} />
            <Button type="button" variant="secondary" onClick={() => void loadEvents()} busy={listStatus === "busy"}>
              {listStatus === "busy" ? "Loading…" : "Load events"}
            </Button>
            <ResultBanner status={listStatus} message={listMessage} />
          </Panel>

          <Panel title="Local events" description="Only LOCAL_HOLIDAY and LOCAL_EVENT rows for this institution.">
            {events === null ? (
              <div className="platform-admin-empty">
                <span aria-hidden="true">♙</span>
                <strong>No events loaded yet.</strong>
                <p>Enter an institution ID above and choose "Load events".</p>
              </div>
            ) : events.length === 0 ? (
              <div className="platform-admin-empty">
                <span aria-hidden="true">♙</span>
                <strong>No local events are recorded for this institution.</strong>
              </div>
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
                          <Badge tone={event.eventType === "LOCAL_HOLIDAY" ? "info" : "neutral"}>{event.eventType}</Badge>
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
              <label className="platform-form-field">
                Event type
                <select value={eventType} onChange={(e) => setEventType(e.target.value)}>
                  <option value="LOCAL_HOLIDAY">Local holiday</option>
                  <option value="LOCAL_EVENT">Local event</option>
                </select>
              </label>
              <TextField
                id="cal-title"
                label="Title"
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Founder's Day"
              />
              <div className="platform-form-grid">
                <label className="platform-form-field">
                  Start date
                  <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
                </label>
                <label className="platform-form-field">
                  End date
                  <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} required />
                </label>
              </div>
              <ResultBanner status={status} message={message} />
              <Button type="submit" busy={status === "busy"}>
                {status === "busy" ? "Adding…" : "Add event"}
              </Button>
            </form>
          </Panel>
        </>
      )}
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

export function WeeklyOffRulesPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [dayOfWeek, setDayOfWeek] = useState(7);
  const [occurrence, setOccurrence] = useState("ALL");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [effectiveTo, setEffectiveTo] = useState("");

  const [rules, setRules] = useState<WeeklyOffRule[] | null>(null);
  const [listStatus, setListStatus] = useState<Status>("idle");
  const [listMessage, setListMessage] = useState("");
  const { status, message, run } = useAction<WeeklyOffRule>();

  const loadRules = async () => {
    if (!isValidUuid(institutionId)) {
      setListStatus("error");
      setListMessage("Institution ID must be a valid ID before rules can be listed.");
      return;
    }
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
    const invalid =
      !isValidUuid(institutionId) || !effectiveFrom
        ? "Institution ID and effective-from date are required."
        : effectiveTo && effectiveTo < effectiveFrom
          ? "Effective-to must not be before effective-from."
          : null;
    void run(
      invalid,
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
        description="WEEKLY_OFF_SET — set the weekly-off pattern for an institution (e.g. Sunday every week, alternate Saturdays)."
      />
      {!tenantId ? (
        <NoTenantNotice />
      ) : (
        <>
          <Panel title="Choose institution" description="There is no institution picker yet; paste the institution ID.">
            <IdField id="wo-institution-id" label="Institution ID" value={institutionId} onChange={setInstitutionId} />
            <Button type="button" variant="secondary" onClick={() => void loadRules()} busy={listStatus === "busy"}>
              {listStatus === "busy" ? "Loading…" : "Load rules"}
            </Button>
            <ResultBanner status={listStatus} message={listMessage} />
          </Panel>

          <Panel title="Current weekly-off rules">
            {rules === null ? (
              <div className="platform-admin-empty">
                <span aria-hidden="true">♙</span>
                <strong>No rules loaded yet.</strong>
                <p>Enter an institution ID above and choose "Load rules".</p>
              </div>
            ) : rules.length === 0 ? (
              <div className="platform-admin-empty">
                <span aria-hidden="true">♙</span>
                <strong>No weekly-off rules are recorded for this institution.</strong>
              </div>
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
                <label className="platform-form-field">
                  Effective from
                  <input
                    type="date"
                    value={effectiveFrom}
                    onChange={(e) => setEffectiveFrom(e.target.value)}
                    required
                  />
                </label>
                <label className="platform-form-field">
                  Effective to (optional)
                  <input type="date" value={effectiveTo} onChange={(e) => setEffectiveTo(e.target.value)} />
                </label>
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
