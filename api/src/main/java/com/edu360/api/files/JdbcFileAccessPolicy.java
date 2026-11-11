package com.edu360.api.files;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.edu360.api.config.TenantPrincipal;

/**
 * Resolves the owning domain row before FileService performs the matching action/scope check.
 * Every query is tenant-qualified; personal submission access includes only the student or an
 * ACTIVE linked guardian, while the assignment author may review submitted work.
 */
@Component
public class JdbcFileAccessPolicy implements FileAccessPolicy {
    private static final String STUDY_MATERIAL = """
            SELECT EXISTS (
              SELECT 1
              FROM app.study_material m
              WHERE m.tenant_id = ? AND m.file_id = ? AND m.status = 'ACTIVE'
                AND (
                  m.uploaded_by = ?
                  OR EXISTS (
                    SELECT 1
                    FROM app.subject_allocation sa
                    JOIN app.staff_assignment st
                      ON st.tenant_id = sa.tenant_id
                     AND st.staff_assignment_id = sa.staff_assignment_id
                    JOIN app.staff_profile sp
                      ON sp.tenant_id = st.tenant_id
                     AND sp.staff_profile_id = st.staff_profile_id
                    WHERE sa.tenant_id = m.tenant_id
                      AND sa.institution_id = m.institution_id
                      AND sa.course_offering_id = m.course_offering_id
                      AND sa.status = 'ACTIVE'
                      AND sp.user_account_id = ?
                      AND ((m.section_id IS NOT NULL AND sa.section_id = m.section_id)
                        OR (m.subject_group_id IS NOT NULL AND sa.subject_group_id = m.subject_group_id))
                  )
                )
            )
            """;
    private static final String ASSIGNMENT_SUBMISSION = """
            SELECT EXISTS (
              SELECT 1
              FROM app.assignment_submission_file sf
              JOIN app.assignment_submission sub
                ON sub.tenant_id = sf.tenant_id AND sub.submission_id = sf.submission_id
              JOIN app.assignment a
                ON a.tenant_id = sub.tenant_id AND a.assignment_id = sub.assignment_id
              JOIN app.student_enrollment en
                ON en.tenant_id = sub.tenant_id AND en.enrollment_id = sub.enrollment_id
              JOIN app.student_profile student
                ON student.tenant_id = en.tenant_id AND student.student_profile_id = en.student_profile_id
              WHERE sf.tenant_id = ? AND sf.file_id = ?
                AND sub.status IN ('SUBMITTED', 'EVALUATED')
                AND (
                  student.user_account_id = ?
                  OR a.created_by_user_id = ?
                  OR EXISTS (
                    SELECT 1
                    FROM app.student_guardian_link gl
                    JOIN app.guardian_profile guardian
                      ON guardian.tenant_id = gl.tenant_id
                     AND guardian.guardian_profile_id = gl.guardian_profile_id
                    WHERE gl.tenant_id = student.tenant_id
                      AND gl.student_profile_id = student.student_profile_id
                      AND gl.status = 'ACTIVE'
                      AND guardian.user_account_id = ?
                  )
                )
            )
            """;
    private static final String SYLLABUS = """
            SELECT EXISTS (
              SELECT 1 FROM app.syllabus s
              WHERE s.tenant_id = ? AND s.file_id = ? AND s.status = 'PUBLISHED'
            )
            """;
    private static final String STAFF_DOCUMENT = """
            SELECT EXISTS (
              SELECT 1
              FROM app.staff_document d
              JOIN app.staff_profile p
                ON p.tenant_id = d.tenant_id AND p.staff_profile_id = d.staff_profile_id
              WHERE d.tenant_id = ? AND d.file_id = ?
                AND (d.created_by = ? OR p.user_account_id = ?)
            )
            """;
    private static final String ATTENDANCE_UPLOAD = """
            SELECT EXISTS (
              SELECT 1 FROM app.import_job j
              WHERE j.tenant_id = ? AND j.source_file_id = ?
                AND j.import_kind = 'ATTENDANCE' AND j.uploaded_by = ?
            )
            """;

    private final JdbcTemplate jdbc;

    public JdbcFileAccessPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean canAccess(FileObject file, TenantPrincipal principal) {
        UUID tenantId = principal.tenantId();
        UUID userId = principal.userAccountId();
        UUID fileId = file.getId().getFileId();
        return switch (FilePurpose.valueOf(file.getPurpose())) {
            case STUDY_MATERIAL -> exists(STUDY_MATERIAL, tenantId, fileId, userId, userId);
            case ASSIGNMENT_SUBMISSION -> exists(ASSIGNMENT_SUBMISSION, tenantId, fileId, userId, userId, userId);
            case SYLLABUS -> exists(SYLLABUS, tenantId, fileId);
            case STAFF_DOCUMENT -> exists(STAFF_DOCUMENT, tenantId, fileId, userId, userId);
            case ATTENDANCE_UPLOAD -> exists(ATTENDANCE_UPLOAD, tenantId, fileId, userId);
        };
    }

    @Override
    public boolean canAttach(FileObject file, TenantPrincipal principal, String recordType, UUID recordId) {
        // The record-attachment endpoint is not part of this chunk; no implicit attachment is allowed.
        return false;
    }

    private boolean exists(String sql, Object... parameters) {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parameters));
        } catch (org.springframework.dao.DataAccessException ex) {
            // Missing domain rows, RLS denials and database errors all fail closed.
            return false;
        }
    }
}
