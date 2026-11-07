package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveApproverSettingRepository extends JpaRepository<LeaveApproverSetting, LeaveApproverSettingId> {

    boolean existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
            UUID tenantId, String subjectRole, UUID institutionId, String status);

    boolean existsByIdTenantIdAndSubjectRoleAndStatus(
            UUID tenantId, String subjectRole, String status);

    Optional<LeaveApproverSetting> findByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
            UUID tenantId, String subjectRole, UUID institutionId, String status);

    Optional<LeaveApproverSetting> findByIdTenantIdAndSubjectRoleAndStatus(
            UUID tenantId, String subjectRole, String status);

    List<LeaveApproverSetting> findByIdTenantIdAndStatus(
            UUID tenantId, String status);

    List<LeaveApproverSetting> findByIdTenantIdAndSubjectRoleAndInstitutionId(
            UUID tenantId, String subjectRole, UUID institutionId);

    List<LeaveApproverSetting> findByIdTenantIdAndSubjectRole(
            UUID tenantId, String subjectRole);
}
