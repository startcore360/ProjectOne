package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    Optional<SchoolClass> findByTenantIdAndClassId(UUID tenantId, UUID classId);
    List<SchoolClass> findByTenantIdAndInstitutionIdOrderByDisplayOrderAsc(UUID tenantId, UUID institutionId);
    boolean existsByTenantIdAndInstitutionIdAndClassName(UUID tenantId, UUID institutionId, String className);
    boolean existsByTenantIdAndInstitutionIdAndClassNameAndClassIdNot(UUID tenantId, UUID institutionId, String className, UUID classId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.school_class
               SET class_name=:className, display_order=:displayOrder,
                   student_login_enabled=:studentLoginEnabled, updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND class_id=:classId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int updateProfile(@Param("tenantId") UUID tenantId, @Param("classId") UUID classId,
            @Param("expectedVersion") int expectedVersion, @Param("className") String className,
            @Param("displayOrder") short displayOrder, @Param("studentLoginEnabled") boolean studentLoginEnabled,
            @Param("updatedBy") UUID updatedBy);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.school_class SET status='ARCHIVED', updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND class_id=:classId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int archive(@Param("tenantId") UUID tenantId, @Param("classId") UUID classId,
            @Param("expectedVersion") int expectedVersion, @Param("updatedBy") UUID updatedBy);
}
