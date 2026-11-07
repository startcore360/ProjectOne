package com.edu360.api.rbac;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface TenantRoleTemplateActionRepository extends Repository<TenantRoleTemplateAction, TenantRoleTemplateActionId> {

    TenantRoleTemplateAction save(TenantRoleTemplateAction action);

    List<TenantRoleTemplateAction> findByIdTenantIdAndTenantRoleTemplateId(UUID tenantId, UUID templateId);

    void deleteByIdTenantIdAndTenantRoleTemplateId(UUID tenantId, UUID templateId);
}
