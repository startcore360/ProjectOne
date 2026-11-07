package com.edu360.api.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, ApprovalStepId> {

    Optional<ApprovalStep> findByIdTenantIdAndIdApprovalStepId(UUID tenantId, UUID approvalStepId);

    List<ApprovalStep> findByIdTenantIdAndApprovalRequestIdOrderByStepNoAsc(UUID tenantId, UUID approvalRequestId);

    Optional<ApprovalStep> findByIdTenantIdAndApprovalRequestIdAndStepNo(UUID tenantId, UUID approvalRequestId, short stepNo);

    List<ApprovalStep> findByIdTenantIdAndApproverRoleCodeAndDecision(UUID tenantId, String approverRoleCode, String decision);

    List<ApprovalStep> findByIdTenantIdAndApproverUserIdAndDecision(UUID tenantId, UUID approverUserId, String decision);
}
