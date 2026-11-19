package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantIssuerAuthenticationManagerResolver;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformConcurrency;
import com.edu360.api.platformops.PlatformNotificationService;
import com.edu360.api.platformops.dataprotection.DeletionRequestRepository;
import com.edu360.api.platformops.dataprotection.ExportRequest;
import com.edu360.api.platformops.dataprotection.ExportRequestRepository;
import com.edu360.api.platformops.dataprotection.PlatformDataProtectionService;
import com.edu360.api.platformops.dataprotection.RetentionChangeRequestRepository;
import com.edu360.api.platformops.dataprotection.TenantRetentionPolicyRepository;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.TenantRepository;

/** Verifies that delegates cannot decide requests owned by any active delegator. */
class PlatformDelegatedApprovalGuardTest {

    @Test
    void delegateCannotDecideExportRaisedByDelegator() {
        UUID delegateId = UUID.randomUUID();
        UUID delegatorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        ExportRequestRepository exports = mock(ExportRequestRepository.class);
        PlatformAccessLookup access = mock(PlatformAccessLookup.class);
        ExportRequest request = exportRequest(requestId, delegatorId);

        when(exports.findById(requestId)).thenReturn(Optional.of(request));
        when(access.activeDelegationDelegators(delegateId)).thenReturn(List.of(delegatorId));

        assertThatThrownBy(() -> service(exports, access).decideExportRequest(
                requestId, "APPROVED", null, "\"1\"", principal(delegateId)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("delegating DPO");
    }

    @Test
    void delegateCannotDecideRequestRaisedByEitherOfTwoActiveDelegators() {
        UUID delegateId = UUID.randomUUID();
        UUID firstDelegatorId = UUID.randomUUID();
        UUID secondDelegatorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        ExportRequestRepository exports = mock(ExportRequestRepository.class);
        PlatformAccessLookup access = mock(PlatformAccessLookup.class);
        ExportRequest request = exportRequest(requestId, secondDelegatorId);

        when(exports.findById(requestId)).thenReturn(Optional.of(request));
        when(access.activeDelegationDelegators(delegateId))
                .thenReturn(List.of(firstDelegatorId, secondDelegatorId));

        assertThatThrownBy(() -> service(exports, access).decideExportRequest(
                requestId, "APPROVED", null, "\"1\"", principal(delegateId)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("delegating DPO");
    }

    private PlatformDataProtectionService service(ExportRequestRepository exports, PlatformAccessLookup access) {
        return new PlatformDataProtectionService(
                exports,
                mock(DeletionRequestRepository.class),
                mock(TenantRetentionPolicyRepository.class),
                mock(RetentionChangeRequestRepository.class),
                mock(TenantRepository.class),
                mock(PlatformAuditWriter.class),
                mock(PlatformNotificationService.class),
                new PlatformConcurrency(),
                mock(TenantIssuerAuthenticationManagerResolver.class),
                access);
    }

    private ExportRequest exportRequest(UUID requestId, UUID requesterId) {
        return ExportRequest.builder()
                .exportRequestId(requestId)
                .tenantId(UUID.randomUUID())
                .requestedByUserId(requesterId)
                .requestedByKind("DATA_PROTECTION_OFFICER")
                .status("REQUESTED")
                .rowVersion(1)
                .build();
    }

    private PlatformPrincipal principal(UUID userId) {
        return new PlatformPrincipal(userId, PlatformRole.DATA_PROTECTION_OFFICER, "delegate@example.test", false);
    }
}
