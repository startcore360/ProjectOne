package com.edu360.api.platformops.role;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRoleTemplateActionRepository extends JpaRepository<PlatformRoleTemplateAction, UUID> {

    List<PlatformRoleTemplateAction> findByPlatformRoleTemplateId(UUID templateId);

    List<PlatformRoleTemplateAction> findByPlatformRoleTemplateIdIn(Collection<UUID> templateIds);

    void deleteByPlatformRoleTemplateId(UUID templateId);
}
