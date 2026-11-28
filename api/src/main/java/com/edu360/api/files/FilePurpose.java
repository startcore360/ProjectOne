package com.edu360.api.files;

import java.util.Arrays;

public enum FilePurpose {
    STUDY_MATERIAL("STUDY_MATERIAL_UPLOAD"),
    ASSIGNMENT_SUBMISSION("ASSIGNMENT_SUBMIT"),
    SYLLABUS("SYLLABUS_MANAGE"),
    STAFF_DOCUMENT("STAFF_RECORD_MANAGE"),
    ATTENDANCE_UPLOAD("ATTENDANCE_BULK_UPLOAD"),
    ATTENDANCE_MEDICAL_EXEMPTION("STUDENT_LEAVE_APPLY"),
    ADMISSION_DOCUMENT("APPLICATION_SUBMIT"),
    GRIEVANCE_EVIDENCE("GRIEVANCE_EVIDENCE_UPLOAD"),
    DISCIPLINARY_EVIDENCE("DISCIPLINARY_INCIDENT_MANAGE"),
    /** Fresh selfie for a staff self-attendance check-in (sql/76); JPEG/PNG only, at most 5 MiB. */
    STAFF_ATTENDANCE_SELFIE("STAFF_ATTENDANCE_CHECKIN_SUBMIT");

    private final String actionCode;

    FilePurpose(String actionCode) {
        this.actionCode = actionCode;
    }

    public String actionCode() {
        return actionCode;
    }

    public static FilePurpose parse(String value) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file purpose."));
    }
}
