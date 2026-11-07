package com.edu360.api.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, ApprovalRequestId> {

    Optional<ApprovalRequest> findByIdTenantIdAndIdApprovalRequestId(UUID tenantId, UUID approvalRequestId);

    List<ApprovalRequest> findByIdTenantIdAndRequestedByUserId(UUID tenantId, UUID requestedByUserId);

    List<ApprovalRequest> findByIdTenantIdAndStatus(UUID tenantId, String status);
}
