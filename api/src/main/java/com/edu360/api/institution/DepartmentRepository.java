package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    Optional<Department> findByTenantIdAndDepartmentId(UUID tenantId, UUID departmentId);
    List<Department> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);
    boolean existsByTenantIdAndInstitutionIdAndDepartmentCode(UUID tenantId, UUID institutionId, String departmentCode);
    boolean existsByTenantIdAndInstitutionIdAndDepartmentCodeAndDepartmentIdNot(UUID tenantId, UUID institutionId, String departmentCode, UUID departmentId);

    @Query(value = "SELECT count(*) FROM app.staff_assignment WHERE tenant_id=:tenantId AND department_id=:departmentId AND status='ACTIVE'", nativeQuery = true)
    long countActiveFaculty(@Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

    @Query(value = """
            SELECT count(DISTINCT e.student_profile_id)
              FROM app.student_enrollment e
              JOIN app.section s ON s.tenant_id=e.tenant_id AND s.section_id=e.section_id
              JOIN app.semester sem ON sem.tenant_id=s.tenant_id AND sem.semester_id=s.semester_id
              JOIN app.program_year py ON py.tenant_id=sem.tenant_id AND py.program_year_id=sem.program_year_id
              JOIN app.program p ON p.tenant_id=py.tenant_id AND p.program_id=py.program_id
             WHERE e.tenant_id=:tenantId AND p.department_id=:departmentId
               AND e.outcome='IN_PROGRESS' AND s.status='ACTIVE'
            """, nativeQuery = true)
    long countCurrentStudents(@Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.department
               SET hod_staff_assignment_id=:staffAssignmentId, updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND department_id=:departmentId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int updateHodAssignment(@Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId,
            @Param("expectedVersion") int expectedVersion, @Param("staffAssignmentId") UUID staffAssignmentId,
            @Param("updatedBy") UUID updatedBy);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.department
               SET department_code=:departmentCode, department_name=:departmentName, updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND department_id=:departmentId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int updateProfile(@Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId,
            @Param("expectedVersion") int expectedVersion, @Param("departmentCode") String departmentCode,
            @Param("departmentName") String departmentName, @Param("updatedBy") UUID updatedBy);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.department SET status='ARCHIVED', updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND department_id=:departmentId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int archive(@Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId,
            @Param("expectedVersion") int expectedVersion, @Param("updatedBy") UUID updatedBy);
}
