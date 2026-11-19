package com.edu360.api.platformops.delegation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads all active platform delegations and detects delegates who cannot delegate onward. */
public interface PlatformDelegationRepository extends JpaRepository<PlatformDelegation, UUID> {

    @Query("""
            select delegation
            from PlatformDelegation delegation
            where delegation.delegateUserId = :delegateId
              and delegation.status = 'ACTIVE'
              and :today between delegation.fromDate and delegation.toDate
            order by delegation.createdAt desc
            """)
    List<PlatformDelegation> findActiveDelegations(
            @Param("delegateId") UUID delegateId,
            @Param("today") LocalDate today);

    @Query("""
            select case when count(delegation) > 0 then true else false end
            from PlatformDelegation delegation
            where delegation.delegateUserId = :delegateId
              and delegation.status = 'ACTIVE'
              and :today between delegation.fromDate and delegation.toDate
            """)
    boolean existsActiveDelegationForDelegate(
            @Param("delegateId") UUID delegateId,
            @Param("today") LocalDate today);
}
