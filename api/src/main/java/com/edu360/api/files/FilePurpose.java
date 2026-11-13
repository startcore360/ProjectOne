package com.edu360.api.files;

import java.util.Arrays;

public enum FilePurpose {
    STUDY_MATERIAL("STUDY_MATERIAL_UPLOAD"),
    ASSIGNMENT_SUBMISSION("ASSIGNMENT_SUBMIT"),
    SYLLABUS("SYLLABUS_MANAGE"),
    STAFF_DOCUMENT("STAFF_RECORD_MANAGE"),
    ATTENDANCE_UPLOAD("ATTENDANCE_BULK_UPLOAD");

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
