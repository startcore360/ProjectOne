package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
    Optional<Program> findByTenantIdAndProgramId(UUID tenantId, UUID programId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Program p where p.tenantId = :tenantId and p.programId = :programId")
    Optional<Program> findForUpdate(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("programId") UUID programId);
    List<Program> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);
    boolean existsByTenantIdAndInstitutionIdAndProgramCode(UUID tenantId, UUID institutionId, String programCode);
    boolean existsByTenantIdAndInstitutionIdAndProgramCodeAndProgramIdNot(UUID tenantId, UUID institutionId, String programCode, UUID programId);
    boolean existsByTenantIdAndDepartmentIdAndStatus(UUID tenantId, UUID departmentId, String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.program
               SET department_id=:departmentId, program_code=:programCode, program_name=:programName,
                   duration_years=:durationYears, semesters_per_year=:semestersPerYear,
                   degree_level=:degreeLevel, total_credits=:totalCredits, updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND program_id=:programId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int updateProfile(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
            @Param("expectedVersion") int expectedVersion, @Param("departmentId") UUID departmentId,
            @Param("programCode") String programCode, @Param("programName") String programName,
            @Param("durationYears") short durationYears, @Param("semestersPerYear") short semestersPerYear,
            @Param("degreeLevel") String degreeLevel, @Param("totalCredits") java.math.BigDecimal totalCredits,
            @Param("updatedBy") UUID updatedBy);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.program SET status='ARCHIVED', updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND program_id=:programId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int archive(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
            @Param("expectedVersion") int expectedVersion, @Param("updatedBy") UUID updatedBy);
}
