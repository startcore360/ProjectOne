package com.edu360.api.platformops.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.dto.StepInRequest;
import com.edu360.api.platformops.role.dto.StepInResponse;

class PlatformStepInServiceTest {

    private static final PlatformPrincipal OWNER =
            new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "owner@edu360.test", false);

    private PlatformStepInRepository repository;
    private PlatformAccessLookup lookup;
    private PlatformAuditWriter audit;
    private PlatformStepInService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformStepInRepository.class);
        lookup = mock(PlatformAccessLookup.class);
        audit = mock(PlatformAuditWriter.class);
        when(lookup.customRoleActions(any())).thenReturn(java.util.Optional.empty());
        service = new PlatformStepInService(repository, lookup, new PlatformPermissionService(lookup), audit);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void stepInLastsThirtyMinutesAndIsRecorded() {
        when(lookup.isHandedOver("RELEASE_MANAGE")).thenReturn(true);

        StepInResponse res = service.stepIn(new StepInRequest("RELEASE_MANAGE", "Release Manager is on leave"), OWNER);

        assertThat(Duration.between(res.startedAt(), res.expiresAt())).isEqualTo(Duration.ofMinutes(30));
        assertThat(res.reason()).isEqualTo("Release Manager is on leave");
        verify(audit).record(OWNER, "UPDATE", "RELEASE_MANAGE", "platform_step_in", res.platformStepInId());
    }

    @Test
    void onlyTheProductOwnerCanStepIn() {
        PlatformPrincipal admin = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN, "admin@edu360.test", false);

        assertThatThrownBy(() -> service.stepIn(new StepInRequest("RELEASE_MANAGE", "Covering this today"), admin))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void dataProtectionApprovalsCanNotBeSteppedInto() {
        when(lookup.isHandedOver("TENANT_DELETION_APPROVE")).thenReturn(true);

        assertThatThrownBy(() -> service.stepIn(new StepInRequest("TENANT_DELETION_APPROVE", "Trying to approve this"), OWNER))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void anActionNotHandedOverNeedsNoStepIn() {
        when(lookup.isHandedOver("RELEASE_MANAGE")).thenReturn(false);

        assertThatThrownBy(() -> service.stepIn(new StepInRequest("RELEASE_MANAGE", "Covering this today"), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(repository, never()).save(any());
    }
}
