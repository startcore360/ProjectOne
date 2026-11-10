package com.edu360.api.platformops.role;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRoleTemplateRepository extends JpaRepository<PlatformRoleTemplate, UUID> {

    boolean existsByTemplateNameIgnoreCase(String templateName);

    boolean existsByTemplateNameIgnoreCaseAndPlatformRoleTemplateIdNot(String templateName, UUID id);

    List<PlatformRoleTemplate> findAllByOrderByTemplateNameAsc();
}
