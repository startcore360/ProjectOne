import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  Badge, Button, DateField, EmptyState, ErrorState, Modal, Notice, PageHeader,
  Panel, SearchBox, SelectField, Spinner, TextAreaField, TextField,
} from "../../../design-system/components";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";
import {
  staffService, type StaffAssignment, type StaffDocument, type StaffProfile,
  type StaffProfilePayload, type StaffServiceRecord,
} from "../api/staffService";

type LoadState = "idle" | "loading" | "loaded" | "error";
const EVENT_OPTIONS = [
  { value: "JOINED", label: "Joined" },
  { value: "DESIGNATION_CHANGE", label: "Designation change" },
  { value: "TRANSFER", label: "Transfer" },
  { value: "CONFIRMATION", label: "Confirmation" },
  { value: "LEAVE_OF_ABSENCE", label: "Leave of absence" },
  { value: "LEFT", label: "Left service" },
  { value: "OTHER", label: "Other" },
];
function humanize(value: string) {
  return value.replaceAll("_", " ").toLowerCase().split(" ").map((word) => word ? word[0].toUpperCase() + word.slice(1) : "").join(" ");
}
const today = () => new Date().toISOString().slice(0, 10);

export default function StaffRecordsPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const userId = useAppSelector((state) => state.auth.user?.id);
  const { can } = useTenantAccess();
  const canManage = can("STAFF_RECORD_MANAGE");
  const canVerify = can("STAFF_DOCUMENT_VERIFY");
  const [query, setQuery] = useState("");
  const [state, setState] = useState<LoadState>("idle");
  const [staff, setStaff] = useState<StaffProfile[]>([]);
  const [listError, setListError] = useState("");
  const [selected, setSelected] = useState<StaffProfile | null>(null);
  const [etag, setEtag] = useState("");
  const [assignments, setAssignments] = useState<StaffAssignment[]>([]);
  const [documents, setDocuments] = useState<StaffDocument[]>([]);
  const [records, setRecords] = useState<StaffServiceRecord[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [draft, setDraft] = useState<StaffProfilePayload>({ fullName: "", employmentStatus: "ACTIVE", joinedOn: today() });
  const [profileDraft, setProfileDraft] = useState<StaffProfilePayload | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);
  const [assignmentDraft, setAssignmentDraft] = useState({ institutionId: "", departmentId: "", designation: "", effectiveFrom: today() });
  const [savingAssignment, setSavingAssignment] = useState(false);
  const [documentType, setDocumentType] = useState("DEGREE");
  const [documentFile, setDocumentFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [verifyingId, setVerifyingId] = useState<string | null>(null);
  const [verifyRemark, setVerifyRemark] = useState("");
  const [serviceDraft, setServiceDraft] = useState({
    eventType: "OTHER" as "JOINED" | "DESIGNATION_CHANGE" | "TRANSFER" | "CONFIRMATION" | "LEAVE_OF_ABSENCE" | "LEFT" | "OTHER",
    effectiveOn: today(), details: "",
  });
  const [savingService, setSavingService] = useState(false);

  const loadDirectory = useCallback(async (search: string) => {
    if (!tenantId) return;
    setState("loading");
    setListError("");
    try {
      const result = await staffService.list(tenantId, search.trim());
      setStaff(result.items ?? []);
      setState("loaded");
    } catch (error) {
      setStaff([]);
      setListError(getApiErrorMessage(error));
      setState("error");
    }
  }, [tenantId]);

  const loadProfile = useCallback(async (id: string) => {
    if (!tenantId) return;
    setDetailLoading(true);
    setDetailError("");
    try {
      const [profile, assignmentRows, documentRows, serviceRows] = await Promise.all([
        staffService.get(tenantId, id),
        staffService.listAssignments(tenantId, id),
        staffService.listDocuments(tenantId, id),
        staffService.listServiceRecords(tenantId, id),
      ]);
      setSelected(profile.data);
      setEtag(profile.etag || `"${profile.data.version}"`);
      setProfileDraft({
        staffCode: profile.data.staffCode, fullName: profile.data.fullName,
        dateOfBirth: profile.data.dateOfBirth ?? "", personalEmail: profile.data.personalEmail ?? "",
        personalMobile: profile.data.personalMobile ?? "", nationalIdLast4: profile.data.nationalIdLast4 ?? "",
        employmentStatus: profile.data.employmentStatus, joinedOn: profile.data.joinedOn ?? "",
        leftOn: profile.data.leftOn ?? "",
      });
      setAssignments(assignmentRows);
      setDocuments(documentRows);
      setRecords(serviceRows);
    } catch (error) {
      setDetailError(getApiErrorMessage(error));
    } finally {
      setDetailLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) void loadDirectory("");
  }, [tenantId, loadDirectory]);

  const createStaff = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId) return;
    if (!draft.fullName?.trim()) { setCreateError("Enter the staff member’s full name."); return; }
    setCreating(true);
    setCreateError("");
    try {
      const created = await staffService.create(tenantId, {
        ...draft, fullName: draft.fullName.trim(),
        staffCode: draft.staffCode?.trim() || undefined,
        personalEmail: draft.personalEmail?.trim() || undefined,
        personalMobile: draft.personalMobile?.trim() || undefined,
        nationalIdLast4: draft.nationalIdLast4?.trim() || undefined,
        dateOfBirth: draft.dateOfBirth || undefined, joinedOn: draft.joinedOn || undefined,
      });
      setCreateOpen(false);
      setDraft({ fullName: "", employmentStatus: "ACTIVE", joinedOn: today() });
      await loadDirectory("");
      setSelected(created);
      await loadProfile(created.id);
      setFeedback({ tone: "success", message: "Staff profile created. Add an institution or department assignment to make it appear in that scoped directory." });
    } catch (error) {
      setCreateError(getApiErrorMessage(error));
    } finally { setCreating(false); }
  };

  const saveProfile = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selected || !profileDraft || !etag) return;
    setSavingProfile(true);
    setFeedback(null);
    try {
      const result = await staffService.update(tenantId, selected.id, etag, {
        ...profileDraft, fullName: profileDraft.fullName?.trim(),
        staffCode: profileDraft.staffCode?.trim() || undefined,
        personalEmail: profileDraft.personalEmail?.trim() || "",
        personalMobile: profileDraft.personalMobile?.trim() || "",
        nationalIdLast4: profileDraft.nationalIdLast4?.trim() || "",
        dateOfBirth: profileDraft.dateOfBirth || undefined,
        joinedOn: profileDraft.joinedOn || undefined, leftOn: profileDraft.leftOn || undefined,
      });
      setSelected(result.data);
      setEtag(result.etag || `"${result.data.version}"`);
      setFeedback({ tone: "success", message: "Staff profile updated." });
      await loadDirectory(query);
    } catch (error) {
      const statusCode = (error as { response?: { status?: number } })?.response?.status;
      setFeedback({ tone: "danger", message: statusCode === 409 || statusCode === 412
        ? "This staff profile changed after you opened it. Reload the record before saving."
        : getApiErrorMessage(error) });
    } finally { setSavingProfile(false); }
  };

  const addAssignment = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selected) return;
    if (!assignmentDraft.designation.trim()) { setFeedback({ tone: "danger", message: "Enter a designation." }); return; }
    setSavingAssignment(true);
    setFeedback(null);
    try {
      await staffService.addAssignment(tenantId, selected.id, {
        institutionId: assignmentDraft.institutionId.trim() || undefined,
        departmentId: assignmentDraft.departmentId.trim() || undefined,
        designation: assignmentDraft.designation.trim(), effectiveFrom: assignmentDraft.effectiveFrom,
      });
      setAssignmentDraft({ institutionId: "", departmentId: "", designation: "", effectiveFrom: today() });
      setAssignments(await staffService.listAssignments(tenantId, selected.id));
      await loadDirectory(query);
      setFeedback({ tone: "success", message: "Staff assignment added." });
    } catch (error) { setFeedback({ tone: "danger", message: getApiErrorMessage(error) }); }
    finally { setSavingAssignment(false); }
  };

  const uploadDocument = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selected || !documentFile) { setFeedback({ tone: "danger", message: "Choose a document file first." }); return; }
    setUploading(true);
    setFeedback(null);
    try {
      const institutionId = assignments.find((item) => item.status === "ACTIVE")?.institutionId ?? undefined;
      const file = await staffService.uploadDocument(tenantId, documentFile, institutionId);
      if (file.scanStatus !== "CLEAN" || file.status !== "ACTIVE") {
        throw new Error("The file is still being scanned or was rejected. Refresh the directory after scanning finishes.");
      }
      await staffService.attachDocument(tenantId, selected.id, { documentType, fileId: file.fileId });
      setDocuments(await staffService.listDocuments(tenantId, selected.id));
      setDocumentFile(null);
      setFeedback({ tone: "success", message: "Document scanned and attached. Verification must be completed by another authorized person." });
    } catch (error) { setFeedback({ tone: "danger", message: getApiErrorMessage(error) }); }
    finally { setUploading(false); }
  };

  const verifyDocument = async (document: StaffDocument, decision: "VERIFIED" | "REJECTED") => {
    if (!tenantId) return;
    setVerifyingId(document.id);
    setFeedback(null);
    try {
      await staffService.verifyDocument(tenantId, document.id, { verificationStatus: decision, remark: verifyRemark.trim() || undefined });
      setDocuments(await staffService.listDocuments(tenantId, document.staffProfileId));
      setVerifyRemark("");
      setFeedback({ tone: "success", message: `Document marked ${humanize(decision).toLowerCase()}.` });
    } catch (error) { setFeedback({ tone: "danger", message: getApiErrorMessage(error) }); }
    finally { setVerifyingId(null); }
  };

  const addServiceRecord = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selected) return;
    if (!serviceDraft.details.trim()) { setFeedback({ tone: "danger", message: "Enter the service record details." }); return; }
    setSavingService(true);
    setFeedback(null);
    try {
      await staffService.addServiceRecord(tenantId, selected.id, { ...serviceDraft, details: serviceDraft.details.trim() });
      setRecords(await staffService.listServiceRecords(tenantId, selected.id));
      setServiceDraft({ eventType: "OTHER", effectiveOn: today(), details: "" });
      setFeedback({ tone: "success", message: "Service history entry added." });
    } catch (error) { setFeedback({ tone: "danger", message: getApiErrorMessage(error) }); }
    finally { setSavingService(false); }
  };

  if (!tenantId) return <div className="platform-page"><PageHeader title="Staff records" description="Staff profiles and institution assignments." /><ErrorState title="Tenant context unavailable" message="Sign in with an institution account to access staff records." /></div>;

  return (
    <div className="platform-page">
      <PageHeader title="Staff records" description="Manage staff profiles, institution assignments, service history and verified documents."
        actions={canManage ? <Button onClick={() => setCreateOpen(true)}>Add staff member</Button> : null} />
      {feedback && <Notice tone={feedback.tone}>{feedback.message}</Notice>}
      <div className="platform-two-col staff-records-layout">
        <Panel title="Staff directory" description="Results are limited to the active role scope.">
          <div className="platform-toolbar">
            <SearchBox value={query} onChange={setQuery} placeholder="Search staff name or code" />
            <Button variant="secondary" size="sm" onClick={() => void loadDirectory(query)} busy={state === "loading"}>Search</Button>
          </div>
          {state === "loading" && <Spinner label="Loading staff directory" />}
          {state === "error" && <ErrorState title="Could not load staff" message={listError} action={<Button variant="secondary" onClick={() => void loadDirectory(query)}>Retry</Button>} />}
          {state === "loaded" && staff.length === 0 && <EmptyState title="No staff records found" description={query ? "Try a different search term." : "No profiles are available in your active role scope."} />}
          {state === "loaded" && staff.length > 0 && (
            <div className="staff-record-list" role="list" aria-label="Staff directory">
              {staff.map((person) => (
                <button key={person.id} type="button" role="listitem"
                  className={`staff-record-list__item ${selected?.id === person.id ? "is-selected" : ""}`}
                  onClick={() => { setSelected(person); void loadProfile(person.id); }}>
                  <span className="staff-record-list__copy"><strong>{person.fullName}</strong><small>{person.staffCode} · {humanize(person.employmentStatus)}</small></span>
                  <Badge tone={person.employmentStatus === "ACTIVE" ? "success" : "neutral"}>{humanize(person.employmentStatus)}</Badge>
                </button>
              ))}
            </div>
          )}
          <div className="platform-inline-actions"><Button size="sm" variant="tertiary" disabled={state === "loading"} onClick={() => void loadDirectory(query)}>Refresh directory</Button></div>
        </Panel>

        <div className="staff-record-detail">
          {!selected && !detailLoading && <EmptyState title="Select a staff member" description="Choose a record to view profile details, assignments, documents and service history." icon="users" />}
          {detailLoading && <Spinner label="Loading staff profile" />}
          {detailError && <ErrorState title="Could not load staff details" message={detailError} action={selected ? <Button variant="secondary" onClick={() => void loadProfile(selected.id)}>Retry</Button> : undefined} />}
          {selected && !detailLoading && !detailError && (
            <>
              <Panel title={selected.fullName} description={`${selected.staffCode} · ${humanize(selected.employmentStatus)}`}>
                {profileDraft && <form onSubmit={saveProfile} className="platform-form">
                  <div className="platform-form-grid">
                    <TextField id="staff-edit-code" label="Staff code" value={profileDraft.staffCode ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, staffCode: e.target.value })} disabled={!canManage} />
                    <TextField id="staff-edit-name" label="Full name" required value={profileDraft.fullName ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, fullName: e.target.value })} disabled={!canManage} />
                    <TextField id="staff-edit-email" label="Personal email" type="email" value={profileDraft.personalEmail ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, personalEmail: e.target.value })} disabled={!canManage} />
                    <TextField id="staff-edit-mobile" label="Personal mobile (E.164)" value={profileDraft.personalMobile ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, personalMobile: e.target.value })} disabled={!canManage} />
                    <DateField id="staff-edit-dob" label="Date of birth" value={profileDraft.dateOfBirth ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, dateOfBirth: e.target.value })} disabled={!canManage} />
                    <TextField id="staff-edit-national" label="National ID (last four only)" maxLength={4} value={profileDraft.nationalIdLast4 ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, nationalIdLast4: e.target.value.replace(/\\D/g, "") })} disabled={!canManage} />
                    <SelectField id="staff-edit-status" label="Employment status" value={profileDraft.employmentStatus ?? "ACTIVE"} onChange={(e) => setProfileDraft({ ...profileDraft, employmentStatus: e.target.value })} disabled={!canManage}
                      options={[{ value: "ACTIVE", label: "Active" }, { value: "INACTIVE", label: "Inactive" }, { value: "LEFT", label: "Left service" }]} />
                    <DateField id="staff-edit-joined" label="Joined on" value={profileDraft.joinedOn ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, joinedOn: e.target.value })} disabled={!canManage} />
                    <DateField id="staff-edit-left" label="Left on" value={profileDraft.leftOn ?? ""} onChange={(e) => setProfileDraft({ ...profileDraft, leftOn: e.target.value })} disabled={!canManage} />
                  </div>
                  {canManage && <div className="platform-inline-actions"><Button type="submit" busy={savingProfile}>Save profile</Button></div>}
                  <p className="platform-modal-note">Updates use the server version tag. Reload before saving if another administrator changed this record.</p>
                </form>}
              </Panel>

              <Panel title="Institution assignments" description="Assign the staff member to an institution or department with an effective date.">
                {assignments.length === 0 ? <EmptyState title="No assignments recorded" description="A staff profile must have an active assignment to appear in an institution or department directory." /> : (
                  <div className="platform-table-wrap"><table className="platform-table">
                    <thead><tr><th>Designation</th><th>Institution</th><th>Department</th><th>Effective from</th><th>Status</th></tr></thead>
                    <tbody>{assignments.map((item) => <tr key={item.id}><td>{item.designation}</td><td><code>{item.institutionId ?? "Tenant-wide"}</code></td><td><code>{item.departmentId ?? "—"}</code></td><td>{item.effectiveFrom}</td><td><Badge tone={item.status === "ACTIVE" ? "success" : "neutral"}>{humanize(item.status)}</Badge></td></tr>)}</tbody>
                  </table></div>
                )}
                {canManage && <form onSubmit={addAssignment} className="platform-form staff-form-divider">
                  <div className="platform-form-grid">
                    <TextField id="staff-inst-id" label="Institution ID (optional)" value={assignmentDraft.institutionId} onChange={(e) => setAssignmentDraft({ ...assignmentDraft, institutionId: e.target.value })} />
                    <TextField id="staff-dept-id" label="Department ID (optional)" value={assignmentDraft.departmentId} onChange={(e) => setAssignmentDraft({ ...assignmentDraft, departmentId: e.target.value })} />
                    <TextField id="staff-designation" label="Designation" required value={assignmentDraft.designation} onChange={(e) => setAssignmentDraft({ ...assignmentDraft, designation: e.target.value })} />
                    <DateField id="staff-assignment-start" label="Effective from" required value={assignmentDraft.effectiveFrom} onChange={(e) => setAssignmentDraft({ ...assignmentDraft, effectiveFrom: e.target.value })} />
                  </div>
                  <div className="platform-inline-actions"><Button type="submit" variant="secondary" busy={savingAssignment}>Add assignment</Button></div>
                </form>}
              </Panel>

              <Panel title="Staff documents" description="Upload supporting documents for scanning and attach them to this profile. An authorized second person must verify them.">
                {documents.length === 0 ? <EmptyState title="No documents attached" description="Add a degree certificate, identity proof or other supporting document." icon="book" /> : (
                  <div className="platform-table-wrap"><table className="platform-table">
                    <thead><tr><th>Document</th><th>File ID</th><th>Status</th><th>Review</th></tr></thead>
                    <tbody>{documents.map((doc) => <tr key={doc.id}>
                      <td>{humanize(doc.documentType)}</td><td><code>{doc.fileId}</code></td><td>{humanize(doc.verificationStatus)}</td>
                      <td>{canVerify && doc.verificationStatus === "UPLOADED" && doc.createdBy !== userId ? (
                        <span className="staff-document-actions">
                          <Button size="sm" variant="secondary" disabled={verifyingId === doc.id} onClick={() => void verifyDocument(doc, "VERIFIED")}>Verify</Button>
                          <Button size="sm" variant="tertiary" disabled={verifyingId === doc.id} onClick={() => void verifyDocument(doc, "REJECTED")}>Reject</Button>
                        </span>
                      ) : doc.createdBy === userId && doc.verificationStatus === "UPLOADED" ? "Awaiting independent review" : "—"}</td>
                    </tr>)}</tbody>
                  </table></div>
                )}
                {canManage && <form onSubmit={uploadDocument} className="platform-form staff-form-divider">
                  <div className="platform-form-grid">
                    <SelectField id="staff-doc-type" label="Document type" value={documentType} onChange={(e) => setDocumentType(e.target.value)} options={[{ value: "DEGREE", label: "Degree certificate" }, { value: "ID_PROOF", label: "Identity proof" }, { value: "OTHER", label: "Other" }]} />
                    <div className="eds-field"><label htmlFor="staff-doc-file">Choose file</label><input id="staff-doc-file" type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={(e) => setDocumentFile(e.target.files?.[0] ?? null)} /><p className="eds-field__hint">The file is scanned before it can be attached.</p></div>
                  </div>
                  <div className="platform-inline-actions"><Button type="submit" variant="secondary" busy={uploading} disabled={!documentFile}>Upload and attach</Button></div>
                </form>}
                {canVerify && documents.some((doc) => doc.verificationStatus === "UPLOADED" && doc.createdBy !== userId) && <TextField id="staff-doc-remark" label="Verification note (optional)" value={verifyRemark} onChange={(e) => setVerifyRemark(e.target.value)} />}
              </Panel>

              <Panel title="Service history" description="Record employment events and other staff service history.">
                {records.length === 0 ? <EmptyState title="No service history yet" description="Add the first service record below." icon="clock" /> : (
                  <div className="platform-table-wrap"><table className="platform-table">
                    <thead><tr><th>Event</th><th>Effective on</th><th>Details</th></tr></thead>
                    <tbody>{records.map((record) => <tr key={record.id}><td>{humanize(record.eventType)}</td><td>{record.effectiveOn}</td><td>{record.details}</td></tr>)}</tbody>
                  </table></div>
                )}
                {canManage && <form onSubmit={addServiceRecord} className="platform-form staff-form-divider">
                  <div className="platform-form-grid">
                    <SelectField id="staff-service-event" label="Event type" value={serviceDraft.eventType} onChange={(e) => setServiceDraft({ ...serviceDraft, eventType: e.target.value as typeof serviceDraft.eventType })} options={EVENT_OPTIONS} />
                    <DateField id="staff-service-date" label="Effective date" required value={serviceDraft.effectiveOn} onChange={(e) => setServiceDraft({ ...serviceDraft, effectiveOn: e.target.value })} />
                  </div>
                  <TextAreaField id="staff-service-details" label="Details" required rows={3} value={serviceDraft.details} onChange={(e) => setServiceDraft({ ...serviceDraft, details: e.target.value })} />
                  <div className="platform-inline-actions"><Button type="submit" variant="secondary" busy={savingService}>Add service record</Button></div>
                </form>}
              </Panel>
            </>
          )}
        </div>
      </div>

      <Modal isOpen={createOpen} onClose={() => setCreateOpen(false)} title="Add staff member" kicker="STAFF RECORDS" description="Create a staff profile, then add its institution or department assignment." size="lg">
        <form onSubmit={createStaff} className="platform-form">
          <div className="platform-form-grid">
            <TextField id="new-staff-full-name" label="Full name" required value={draft.fullName} onChange={(e) => setDraft({ ...draft, fullName: e.target.value })} />
            <TextField id="new-staff-code" label="Staff code (optional)" value={draft.staffCode ?? ""} onChange={(e) => setDraft({ ...draft, staffCode: e.target.value })} />
            <TextField id="new-staff-email" label="Personal email" type="email" value={draft.personalEmail ?? ""} onChange={(e) => setDraft({ ...draft, personalEmail: e.target.value })} />
            <TextField id="new-staff-mobile" label="Personal mobile (E.164)" value={draft.personalMobile ?? ""} onChange={(e) => setDraft({ ...draft, personalMobile: e.target.value })} />
            <DateField id="new-staff-dob" label="Date of birth" value={draft.dateOfBirth ?? ""} onChange={(e) => setDraft({ ...draft, dateOfBirth: e.target.value })} />
            <TextField id="new-staff-national-id" label="National ID (last four only)" maxLength={4} value={draft.nationalIdLast4 ?? ""} onChange={(e) => setDraft({ ...draft, nationalIdLast4: e.target.value.replace(/\\D/g, "") })} />
            <DateField id="new-staff-joined-on" label="Joined on" value={draft.joinedOn ?? ""} onChange={(e) => setDraft({ ...draft, joinedOn: e.target.value })} />
          </div>
          {createError && <Notice tone="danger">{createError}</Notice>}
          <footer className="platform-modal__footer">
            <Button type="button" variant="secondary" onClick={() => setCreateOpen(false)} disabled={creating}>Cancel</Button>
            <Button type="submit" busy={creating}>Create staff profile</Button>
          </footer>
        </form>
      </Modal>
    </div>
  );
}
