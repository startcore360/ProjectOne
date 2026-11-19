package com.edu360.api.platformops.delegation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.role.PlatformAuditWriter;

/** Tests delegation eligibility and rejects invalid dates, self-delegation, and onward delegation. */
class PlatformDelegationServiceTest {

    private final PlatformDelegationRepository delegationRepository = mock(PlatformDelegationRepository.class);
    private final PlatformUserRepository userRepository = mock(PlatformUserRepository.class);
    private final PlatformDelegationService service = new PlatformDelegationService(
            delegationRepository,
            userRepository,
            mock(PlatformAuditWriter.class));

    private PlatformUser user(UUID userId, PlatformRole role) {
        return PlatformUser.builder()
                .platformUserId(userId)
                .platformRole(role)
                .status(PlatformUserStatus.ACTIVE)
                .build();
    }

    @Test
    void nonDpoDelegateIsRejected() {
        UUID actorId = UUID.randomUUID();
        UUID delegateId = UUID.randomUUID();
        when(userRepository.findById(delegateId))
                .thenReturn(Optional.of(user(delegateId, PlatformRole.PLATFORM_ADMIN)));

        PlatformDelegationRequest request = new PlatformDelegationRequest(
                delegateId,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 10));
        PlatformPrincipal actor = new PlatformPrincipal(
                actorId,
                PlatformRole.DATA_PROTECTION_OFFICER,
                "dpo@example.test",
                false);

        assertThatThrownBy(() -> service.create(request, actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("active Data Protection Officer");
    }

    @Test
    void activeDelegateCannotDelegateAgain() {
        UUID actorId = UUID.randomUUID();
        when(delegationRepository.existsActiveDelegationForDelegate(
                eq(actorId),
                any(LocalDate.class)))
                .thenReturn(true);

        PlatformDelegationRequest request = new PlatformDelegationRequest(
                UUID.randomUUID(),
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 10));
        PlatformPrincipal actor = new PlatformPrincipal(
                actorId,
                PlatformRole.DATA_PROTECTION_OFFICER,
                "dpo@example.test",
                false);

        assertThatThrownBy(() -> service.create(request, actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cannot delegate approvals onward");
    }

    @Test
    void selfDelegationAndInvalidDateRangeAreRejected() {
        UUID actorId = UUID.randomUUID();
        PlatformPrincipal actor = new PlatformPrincipal(
                actorId,
                PlatformRole.DATA_PROTECTION_OFFICER,
                "dpo@example.test",
                false);

        assertThatThrownBy(() -> service.create(
                new PlatformDelegationRequest(
                        actorId,
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2026, 12, 10)),
                actor))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThatThrownBy(() -> service.create(
                new PlatformDelegationRequest(
                        UUID.randomUUID(),
                        LocalDate.of(2026, 12, 10),
                        LocalDate.of(2026, 12, 1)),
                actor))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
