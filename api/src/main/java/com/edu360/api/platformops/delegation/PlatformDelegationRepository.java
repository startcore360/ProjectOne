package com.edu360.api.platformops.delegation;
import java.time.LocalDate; import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface PlatformDelegationRepository extends JpaRepository<PlatformDelegation,UUID> {
 @Query("select d from PlatformDelegation d where d.delegateUserId=:delegateId and d.status='ACTIVE' and :today between d.fromDate and d.toDate order by d.createdAt desc")
 Optional<PlatformDelegation> findActiveDelegation(@Param("delegateId") UUID id,@Param("today") LocalDate today);
 @Query("select case when count(d)>0 then true else false end from PlatformDelegation d where d.delegateUserId=:delegateId and d.status='ACTIVE' and :today between d.fromDate and d.toDate")
 boolean existsActiveDelegationForDelegate(@Param("delegateId") UUID id,@Param("today") LocalDate today);
}
