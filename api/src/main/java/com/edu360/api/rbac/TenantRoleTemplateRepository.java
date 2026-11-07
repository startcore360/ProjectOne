package com.edu360.api.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface TenantRoleTemplateRepository extends Repository<TenantRoleTemplate, TenantRoleTemplateId> {

    TenantRoleTemplate save(TenantRoleTemplate template);

    Optional<TenantRoleTemplate> findByIdTenantIdAndIdTenantRoleTemplateId(UUID tenantId, UUID templateId);

    List<TenantRoleTemplate> findByIdTenantIdAndStatus(UUID tenantId, String status);

    boolean existsByIdTenantIdAndTemplateName(UUID tenantId, String templateName);
}
