package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.files.FileStorage;
import com.edu360.api.platformops.role.PlatformAuditWriter;

class ExportRequestLifecycleServiceTest {

    private ExportRequestRepository repository;
    private FileStorage storage;
    private PlatformAuditWriter audit;
    private ExportRequestLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExportRequestRepository.class);
        storage = mock(FileStorage.class);
        audit = mock(PlatformAuditWriter.class);
        service = new ExportRequestLifecycleService(repository, storage, audit);
    }

    private static ExportRequest request(String status, UUID tenantId, UUID fileRef, Instant expiresAt) {
        return ExportRequest.builder().exportRequestId(UUID.randomUUID()).tenantId(tenantId)
                .requestedByUserId(UUID.randomUUID()).requestedByKind("PRODUCT_OWNER").status(status)
                .fileRef(fileRef).downloadExpiresAt(expiresAt).rowVersion(1).build();
    }

    @Test
    void claimMovesApprovedToGeneratingAndAuditsAsSystem() {
        ExportRequest approved = request("APPROVED", UUID.randomUUID(), null, null);
        when(repository.findForUpdateByStatus(eq("APPROVED"), any())).thenReturn(List.of(approved));

        List<ExportRequestLifecycleService.ExportClaim> claims = service.claimApproved();

        assertThat(claims).containsExactly(new ExportRequestLifecycleService.ExportClaim(
                approved.getExportRequestId(), approved.getTenantId()));
        assertThat(approved.getStatus()).isEqualTo("GENERATING");
        verify(audit).recordSystem(eq(approved.getTenantId()), eq("TENANT_EXPORT_GENERATING"), eq("EXPORT"),
                any(), eq("EXPORT_REQUEST"), eq(approved.getExportRequestId()), eq(PlatformAuditWriter.SUCCESS), any());
    }

    @Test
    void readySetsFileRefAndA24HourWindow() {
        ExportRequest generating = request("GENERATING", UUID.randomUUID(), null, null);
        when(repository.findByIdForUpdate(generating.getExportRequestId())).thenReturn(Optional.of(generating));
        UUID fileRef = UUID.randomUUID();

        service.markReady(generating.getExportRequestId(), fileRef);

        assertThat(generating.getStatus()).isEqualTo("READY");
        assertThat(generating.getFileRef()).isEqualTo(fileRef);
        assertThat(generating.getDownloadExpiresAt())
                .isBetween(Instant.now().plusSeconds(23 * 3600), Instant.now().plusSeconds(24 * 3600 + 5));
    }

    @Test
    void failureMarksFailedAndAuditsWithoutRowContents() {
        ExportRequest generating = request("GENERATING", UUID.randomUUID(), null, null);
        when(repository.findByIdForUpdate(generating.getExportRequestId())).thenReturn(Optional.of(generating));

        service.markFailed(generating.getExportRequestId(), new IllegalStateException("secret row data"));

        assertThat(generating.getStatus()).isEqualTo("FAILED");
        verify(audit).recordSystem(any(), eq("TENANT_EXPORT_FAILED"), eq("EXPORT"), any(), any(),
                eq(generating.getExportRequestId()), eq(PlatformAuditWriter.FAILED), any());
    }

    @Test
    void expiredDownloadReturns410WithExportExpiredCode() {
        ExportRequest expired = request("EXPIRED", UUID.randomUUID(), UUID.randomUUID(), Instant.now().minusSeconds(1));
        when(repository.findById(expired.getExportRequestId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.openDownload(expired.getExportRequestId(), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(ex.getErrorCode()).isEqualTo("EXPORT_EXPIRED");
                });
        verify(storage, never()).openRead(any());
    }

    @Test
    void readyButPastWindowIsAlsoExpired() {
        ExportRequest stale = request("READY", UUID.randomUUID(), UUID.randomUUID(), Instant.now().minusSeconds(1));
        when(repository.findById(stale.getExportRequestId())).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> service.openDownload(stale.getExportRequestId(), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void notYetProducedIsRefused() {
        ExportRequest approved = request("APPROVED", UUID.randomUUID(), null, null);
        when(repository.findById(approved.getExportRequestId())).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.openDownload(approved.getExportRequestId(), null))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("not ready");
    }

    @Test
    void readyDownloadStreamsTheDerivedKeyAndIsAudited() {
        UUID tenantId = UUID.randomUUID();
        UUID fileRef = UUID.randomUUID();
        ExportRequest ready = request("READY", tenantId, fileRef, Instant.now().plusSeconds(3600));
        when(repository.findById(ready.getExportRequestId())).thenReturn(Optional.of(ready));
        when(storage.openRead("tenants/" + tenantId + "/" + fileRef)).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        PlatformPrincipal principal = mock(PlatformPrincipal.class);

        assertThat(service.openDownload(ready.getExportRequestId(), principal)).isNotNull();
        verify(audit).record(principal, tenantId, "TENANT_EXPORT_DOWNLOADED", "DOWNLOAD", "TENANT_EXPORT_REQUEST",
                "EXPORT_REQUEST", ready.getExportRequestId(), PlatformAuditWriter.SUCCESS);
    }

    @Test
    void expiryDeletesDerivedTenantFileAndKeepsFileRef() {
        UUID tenantId = UUID.randomUUID();
        UUID fileRef = UUID.randomUUID();
        ExportRequest ready = request("READY", tenantId, fileRef, Instant.now().minusSeconds(1));
        when(repository.findReadyToExpireForUpdate(any())).thenReturn(List.of(ready));

        assertThat(service.expireReadyExports()).isEqualTo(1);
        assertThat(ready.getStatus()).isEqualTo("EXPIRED");
        assertThat(ready.getFileRef()).isEqualTo(fileRef);
        verify(storage).delete("tenants/" + tenantId + "/" + fileRef);
        verify(repository).save(ready);
    }

    @Test
    void aFailedDeleteNeverBlocksExpiryAndIsRetriedLater() {
        UUID tenantId = UUID.randomUUID();
        UUID fileRef = UUID.randomUUID();
        ExportRequest ready = request("READY", tenantId, fileRef, Instant.now().minusSeconds(1));
        when(repository.findReadyToExpireForUpdate(any())).thenReturn(List.of(ready));
        doThrow(new RuntimeException("disk busy")).when(storage).delete(any());

        assertThat(service.expireReadyExports()).isEqualTo(1);
        assertThat(ready.getStatus()).isEqualTo("EXPIRED");

        // A later run re-visits recently expired exports and deletes again (delete is idempotent).
        org.mockito.Mockito.reset(storage);
        when(repository.findReadyToExpireForUpdate(any())).thenReturn(List.of());
        when(repository.findByStatusAndUpdatedAtAfter(eq("EXPIRED"), any())).thenReturn(List.of(ready));
        service.expireReadyExports();
        verify(storage).delete("tenants/" + tenantId + "/" + fileRef);
    }
}
