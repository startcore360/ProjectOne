package com.edu360.api.platformops.support;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<SupportTicket> findByTenantIdAndRaisedByUserIdOrderByCreatedAtDesc(UUID tenantId, UUID raisedByUserId);
    Optional<SupportTicket> findByTicketNumber(String ticketNumber);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    @Query("select t from SupportTicket t order by case t.priority when 'CRITICAL' then 0 when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end, t.createdAt asc")
    List<SupportTicket> findAllPrioritizedByPriorityThenAge();
}
