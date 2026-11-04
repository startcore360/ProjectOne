package com.edu360.api.platformops;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBackupStatusRepository extends JpaRepository<TenantBackupStatus, UUID> {

    List<TenantBackupStatus> findAllByOrderByTenantId();
}
