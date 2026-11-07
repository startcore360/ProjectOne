package com.edu360.api.platformops.support;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<SupportTicket> findByTicketNumber(String ticketNumber);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
