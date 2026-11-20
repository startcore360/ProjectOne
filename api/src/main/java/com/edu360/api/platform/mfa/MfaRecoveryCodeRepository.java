package com.edu360.api.platform.mfa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    List<MfaRecoveryCode> findByRealmAndKeycloakUserIdAndUsedAtIsNull(String realm, String keycloakUserId);

    long countByRealmAndKeycloakUserIdAndUsedAtIsNull(String realm, String keycloakUserId);

    @Modifying
    @Query("DELETE FROM MfaRecoveryCode c WHERE c.realm = :realm AND c.keycloakUserId = :keycloakUserId")
    int deleteAllFor(@Param("realm") String realm, @Param("keycloakUserId") String keycloakUserId);
}
