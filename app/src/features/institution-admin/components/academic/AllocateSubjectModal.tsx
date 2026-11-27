import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { institutionService, type InstitutionItem, type CourseOfferingItem, type SectionItem, type AcademicSessionItem, type SubjectItem } from "../../api/institutionService";
import { staffService, type TeacherLookupItem } from "../../api/staffService";

export interface AllocateSubjectModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: {
    institutionId: string;
    courseOfferingId: string;
    sectionId?: string;
    staffAssignmentId: string;
  }) => Promise<void>;
  saving: boolean;
}

export function AllocateSubjectModal({ tenantId, isOpen, onClose, onSubmit, saving }: AllocateSubjectModalProps) {
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [institutionId, setInstitutionId] = useState("");
  const [sessions, setSessions] = useState<AcademicSessionItem[]>([]);
  const [subjects, setSubjects] = useState<SubjectItem[]>([]);
  const [offerings, setOfferings] = useState<CourseOfferingItem[]>([]);
  const [sections, setSections] = useState<SectionItem[]>([]);
  const [teachers, setTeachers] = useState<TeacherLookupItem[]>([]);
  const [courseOfferingId, setCourseOfferingId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [staffAssignmentId, setStaffAssignmentId] = useState("");
  const [loading, setLoading] = useState(false);
  const [lookupError, setLookupError] = useState("");
  const [modalError, setModalError] = useState("");

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    setLoading(true);
    setLookupError("");
    institutionService.listInstitutions(tenantId)
      .then((response) => {
        if (!cancelled) {
          setInstitutions(response.items ?? []);
          setInstitutionId((current) => current || response.items?.[0]?.institutionId || "");
        }
      })
      .catch((error: unknown) => { if (!cancelled) setLookupError(getApiErrorMessage(error)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [tenantId, isOpen]);

  useEffect(() => {
    if (!isOpen || !institutionId) {
      setSessions([]); setSubjects([]); setOfferings([]); setSections([]); setTeachers([]);
      setCourseOfferingId(""); setSectionId(""); setStaffAssignmentId("");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLookupError("");
    setCourseOfferingId(""); setSectionId(""); setStaffAssignmentId("");
    Promise.all([
      institutionService.listAcademicSessions(tenantId, institutionId),
      institutionService.listSubjects(tenantId, institutionId),
      institutionService.listCourseOfferings(tenantId, institutionId),
      institutionService.listSections(tenantId, institutionId),
      staffService.teacherLookup(tenantId, institutionId, "SUBJECT_ALLOCATE"),
    ]).then(([sessionRows, subjectRows, offeringRows, sectionRows, teacherRows]) => {
      if (cancelled) return;
      setSessions(sessionRows);
      setSubjects(subjectRows);
      setOfferings(offeringRows);
      setSections(sectionRows.filter((section) => section.status === "ACTIVE"));
      setTeachers(teacherRows);
    }).catch((error: unknown) => {
      if (!cancelled) {
        setSessions([]); setSubjects([]); setOfferings([]); setSections([]); setTeachers([]);
        setLookupError(getApiErrorMessage(error));
      }
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [tenantId, isOpen, institutionId]);

  const offeringOptions = useMemo(() => offerings.map((offering) => {
    const subject = subjects.find((item) => item.subjectId === offering.subjectId);
    const session = sessions.find((item) => item.academicSessionId === offering.academicSessionId);
    const classOrSemester = offering.classId
      ? "School class"
      : offering.semesterId ? "Semester" : "All sections";
    return {
      value: offering.courseOfferingId,
      label: [subject ? `${subject.subjectCode} · ${subject.subjectName}` : "Course offering", session?.sessionLabel, classOrSemester].filter(Boolean).join(" — "),
    };
  }), [offerings, subjects, sessions]);

  const selectedOffering = offerings.find((offering) => offering.courseOfferingId === courseOfferingId);
  const eligibleSections = sections.filter((section) => selectedOffering
    && section.academicSessionId === selectedOffering.academicSessionId
    && (!selectedOffering.classId || section.classId === selectedOffering.classId)
    && (!selectedOffering.semesterId || section.semesterId === selectedOffering.semesterId));

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setModalError("");
    if (!institutionId || !courseOfferingId || !staffAssignmentId) {
      setModalError("Choose an institution, course offering and eligible teacher.");
      return;
    }
    try {
      await onSubmit({ institutionId, courseOfferingId, sectionId: sectionId || undefined, staffAssignmentId });
      setCourseOfferingId(""); setSectionId(""); setStaffAssignmentId("");
      onClose();
    } catch (error: unknown) {
      setModalError(getApiErrorMessage(error));
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} kicker="ACADEMIC SETUP" title="Allocate Subject to Teacher" titleId="alloc-modal-title" size="sm">
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }} noValidate>
        {modalError && <div style={{ marginBottom: "var(--space-3)" }}><Notice tone="danger">{modalError}</Notice></div>}
        {lookupError && <div style={{ marginBottom: "var(--space-3)" }}><Notice tone="danger">{lookupError}</Notice></div>}
        <SelectField id="alloc-institution" label="Institution *" required value={institutionId} disabled={loading || institutions.length === 0}
          onChange={(event) => setInstitutionId(event.target.value)}>
          <option value="">{loading ? "Loading institutions…" : "Choose an institution"}</option>
          {institutions.map((item) => <option key={item.institutionId} value={item.institutionId}>{item.institutionName} · {item.institutionCode}</option>)}
        </SelectField>
        <SelectField id="alloc-course-offering" label="Course offering *" required value={courseOfferingId}
          disabled={loading || !institutionId || offeringOptions.length === 0}
          onChange={(event) => { setCourseOfferingId(event.target.value); setSectionId(""); }}>
          <option value="">{loading ? "Loading course offerings…" : "Choose a course offering"}</option>
          {offeringOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
        </SelectField>
        <SelectField id="alloc-section" label="Section (Optional)" value={sectionId} disabled={!courseOfferingId || eligibleSections.length === 0}
          onChange={(event) => setSectionId(event.target.value)}>
          <option value="">No specific section</option>
          {eligibleSections.map((item) => <option key={item.sectionId} value={item.sectionId}>{item.sectionName}</option>)}
        </SelectField>
        <SelectField id="alloc-teacher" label="Teacher *" required value={staffAssignmentId}
          disabled={loading || !institutionId || teachers.length === 0}
          onChange={(event) => setStaffAssignmentId(event.target.value)}>
          <option value="">{loading ? "Loading eligible teachers…" : "Choose a teacher"}</option>
          {teachers.map((teacher) => <option key={teacher.staffAssignmentId} value={teacher.staffAssignmentId}>{teacher.fullName} · {teacher.designation}{teacher.departmentName ? ` · ${teacher.departmentName}` : ""}</option>)}
        </SelectField>
        {!loading && !lookupError && institutionId && offeringOptions.length === 0 && <Notice tone="warning">No course offerings are available for this institution yet.</Notice>}
        {!loading && !lookupError && institutionId && teachers.length === 0 && <Notice tone="warning">No eligible teaching staff are available for subject allocation.</Notice>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", marginTop: "var(--space-4)" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" disabled={saving || loading || !institutionId || !courseOfferingId || !staffAssignmentId}>{saving ? "Allocating…" : "Allocate Subject"}</Button>
        </div>
      </form>
    </Modal>
  );
}
export default AllocateSubjectModal;
