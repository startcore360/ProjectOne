package com.edu360.api.institution;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a mapping of app.section - a section of a semester (college, semesterId set) or of a
 * class (school, classId set); ck_section_r1 guarantees exactly one of the two is set. Used by
 * MENTOR_ASSIGN (college sections) and CLASS_TEACHER_ASSIGN (school sections, per the table
 * comment's OD-24 note - see ClassTeacherAssignmentService javadoc for that open decision).
 */
@Entity
@Table(schema = "app", name = "section")
@Getter
@Setter
@NoArgsConstructor
public class Section {

    @Id
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "academic_session_id", nullable = false)
    private UUID academicSessionId;

    /** College only. */
    @Column(name = "semester_id")
    private UUID semesterId;

    /** School only. */
    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "section_name", nullable = false)
    private String sectionName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
