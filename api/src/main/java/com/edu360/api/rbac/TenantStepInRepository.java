package com.edu360.api.rbac;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface TenantStepInRepository extends Repository<TenantStepIn, TenantStepInId> {

    TenantStepIn save(TenantStepIn stepIn);

    List<TenantStepIn> findByIdTenantIdAndActorUserIdAndActionCodeAndExpiresAtAfter(
            UUID tenantId, UUID actorUserId, String actionCode, Instant now);

    List<TenantStepIn> findByIdTenantIdAndActorUserId(UUID tenantId, UUID actorUserId);
}
