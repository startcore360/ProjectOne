package com.edu360.api.platformops.role;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformCustomRoleActionRepository extends JpaRepository<PlatformCustomRoleAction, UUID> {

    List<PlatformCustomRoleAction> findByPlatformRoleId(UUID platformRoleId);

    List<PlatformCustomRoleAction> findByPlatformRoleIdIn(Collection<UUID> platformRoleIds);

    void deleteByPlatformRoleId(UUID platformRoleId);

    /** True only while an ACTIVE person holds an ACTIVE role with this action handed over. A role
     *  with nobody in it must never block the Product Owner, or the work would stop. */
    @Query("""
            select count(a) > 0
            from PlatformCustomRoleAction a, PlatformCustomRole r, PlatformUser u
            where a.platformRoleId = r.platformRoleId
              and u.platformRoleId = r.platformRoleId
              and a.actionCode = :actionCode
              and a.accessMode = com.edu360.api.platformops.role.AccessMode.HANDED_OVER
              and r.status = com.edu360.api.platformops.role.PlatformCustomRoleStatus.ACTIVE
              and u.status = com.edu360.api.platform.PlatformUserStatus.ACTIVE
            """)
    boolean isHandedOver(@Param("actionCode") String actionCode);

    @Query("""
            select distinct a.actionCode
            from PlatformCustomRoleAction a, PlatformCustomRole r, PlatformUser u
            where a.platformRoleId = r.platformRoleId
              and u.platformRoleId = r.platformRoleId
              and a.accessMode = com.edu360.api.platformops.role.AccessMode.HANDED_OVER
              and r.status = com.edu360.api.platformops.role.PlatformCustomRoleStatus.ACTIVE
              and u.status = com.edu360.api.platform.PlatformUserStatus.ACTIVE
            """)
    List<String> findHandedOverActions();
}
