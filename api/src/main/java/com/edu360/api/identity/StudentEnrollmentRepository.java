package com.edu360.api.identity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-qualified roster queries; historical and ended placements are never returned. */
public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, StudentEnrollmentId> {

    Optional<StudentEnrollment> findFirstByIdTenantIdAndStudentProfileIdAndOutcome(
            UUID tenantId, UUID studentProfileId, String outcome);

    @Query(value = """
            SELECT e.*
              FROM app.student_enrollment e
              JOIN app.student_profile s
                ON s.tenant_id = e.tenant_id
               AND s.student_profile_id = e.student_profile_id
             WHERE e.tenant_id = :tenantId
               AND e.section_id = :sectionId
               AND e.outcome = 'IN_PROGRESS'
               AND e.started_on <= :onDate
               AND (e.ended_on IS NULL OR e.ended_on >= :onDate)
               AND s.status = 'ACTIVE'
             ORDER BY e.roll_number NULLS LAST, e.enrollment_id
            """, nativeQuery = true)
    List<StudentEnrollment> activeEnrollmentsOfSection(
            @Param("tenantId") UUID tenantId,
            @Param("sectionId") UUID sectionId,
            @Param("onDate") LocalDate onDate);

    @Query(value = """
            SELECT e.*
              FROM app.student_enrollment e
              JOIN app.student_profile s
                ON s.tenant_id = e.tenant_id
               AND s.student_profile_id = e.student_profile_id
              JOIN app.subject_group_member gm
                ON gm.tenant_id = e.tenant_id
               AND gm.enrollment_id = e.enrollment_id
              JOIN app.subject_group g
                ON g.tenant_id = gm.tenant_id
               AND g.subject_group_id = gm.subject_group_id
             WHERE e.tenant_id = :tenantId
               AND gm.subject_group_id = :groupId
               AND g.status = 'ACTIVE'
               AND e.outcome = 'IN_PROGRESS'
               AND e.started_on <= :onDate
               AND (e.ended_on IS NULL OR e.ended_on >= :onDate)
               AND s.status = 'ACTIVE'
             ORDER BY e.roll_number NULLS LAST, e.enrollment_id
            """, nativeQuery = true)
    List<StudentEnrollment> activeEnrollmentsOfSubjectGroup(
            @Param("tenantId") UUID tenantId,
            @Param("groupId") UUID groupId,
            @Param("onDate") LocalDate onDate);

    @Query(value = """
            SELECT e.*
              FROM app.student_enrollment e
              JOIN app.student_profile s
                ON s.tenant_id = e.tenant_id
               AND s.student_profile_id = e.student_profile_id
             WHERE e.tenant_id = :tenantId
               AND s.user_account_id = :userAccountId
               AND e.section_id = :sectionId
               AND e.outcome = 'IN_PROGRESS'
               AND e.started_on <= :onDate
               AND (e.ended_on IS NULL OR e.ended_on >= :onDate)
               AND s.status = 'ACTIVE'
             ORDER BY e.started_on DESC
             LIMIT 1
            """, nativeQuery = true)
    Optional<StudentEnrollment> activeEnrollmentOfUserInSection(
            @Param("tenantId") UUID tenantId,
            @Param("userAccountId") UUID userAccountId,
            @Param("sectionId") UUID sectionId,
            @Param("onDate") LocalDate onDate);

    @Query(value = """
            SELECT e.*
              FROM app.student_enrollment e
              JOIN app.student_profile s
                ON s.tenant_id = e.tenant_id
               AND s.student_profile_id = e.student_profile_id
              JOIN app.subject_group_member gm
                ON gm.tenant_id = e.tenant_id
               AND gm.enrollment_id = e.enrollment_id
              JOIN app.subject_group g
                ON g.tenant_id = gm.tenant_id
               AND g.subject_group_id = gm.subject_group_id
             WHERE e.tenant_id = :tenantId
               AND s.user_account_id = :userAccountId
               AND gm.subject_group_id = :groupId
               AND g.status = 'ACTIVE'
               AND e.outcome = 'IN_PROGRESS'
               AND e.started_on <= :onDate
               AND (e.ended_on IS NULL OR e.ended_on >= :onDate)
               AND s.status = 'ACTIVE'
             ORDER BY e.started_on DESC
             LIMIT 1
            """, nativeQuery = true)
    Optional<StudentEnrollment> activeEnrollmentOfUserInSubjectGroup(
            @Param("tenantId") UUID tenantId,
            @Param("userAccountId") UUID userAccountId,
            @Param("groupId") UUID groupId,
            @Param("onDate") LocalDate onDate);
}
