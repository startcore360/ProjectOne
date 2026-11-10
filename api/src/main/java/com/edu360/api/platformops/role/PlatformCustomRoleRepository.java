package com.edu360.api.platformops.role;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformCustomRoleRepository extends JpaRepository<PlatformCustomRole, UUID> {

    boolean existsByRoleNameIgnoreCase(String roleName);

    boolean existsByRoleNameIgnoreCaseAndPlatformRoleIdNot(String roleName, UUID platformRoleId);

    List<PlatformCustomRole> findAllByOrderByRoleNameAsc();
}
