package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, InstitutionId> {

    List<Institution> findByIdTenantId(UUID tenantId);

    List<Institution> findByIdTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

    Optional<Institution> findByIdTenantIdAndIdInstitutionId(UUID tenantId, UUID institutionId);

    boolean existsByIdTenantIdAndInstitutionCode(UUID tenantId, String institutionCode);

    boolean existsByIdTenantIdAndInstitutionName(UUID tenantId, String institutionName);

    boolean existsByIdTenantIdAndInstitutionNameAndIdInstitutionIdNot(UUID tenantId, String institutionName, UUID institutionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.institution
               SET institution_name = :institutionName,
                   address_line = :addressLine,
                   city = :city,
                   state_name = :stateName,
                   postal_code = :postalCode,
                   principal_invite_email = :principalInviteEmail,
                   external_university_mode = :externalUniversityMode,
                   updated_by = :updatedBy
             WHERE tenant_id = :tenantId
               AND institution_id = :institutionId
               AND row_version = :expectedVersion
            """, nativeQuery = true)
    int updateEditableFields(
            @Param("tenantId") UUID tenantId,
            @Param("institutionId") UUID institutionId,
            @Param("expectedVersion") int expectedVersion,
            @Param("institutionName") String institutionName,
            @Param("addressLine") String addressLine,
            @Param("city") String city,
            @Param("stateName") String stateName,
            @Param("postalCode") String postalCode,
            @Param("principalInviteEmail") String principalInviteEmail,
            @Param("externalUniversityMode") boolean externalUniversityMode,
            @Param("updatedBy") UUID updatedBy);

    long countByIdTenantIdAndStatusNot(UUID tenantId, String status);
}
