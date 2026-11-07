package com.edu360.api.selfservice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLeaveRequestRepository extends JpaRepository<UserLeaveRequest, UserLeaveRequestId> {
    List<UserLeaveRequest> findByIdTenantIdAndRequestedByUserIdOrderByCreatedAtDesc(UUID tenantId, UUID requestedByUserId);
}
