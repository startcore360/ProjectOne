package com.edu360.api.selfservice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, NoticeId> {
    List<Notice> findByIdTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
}
