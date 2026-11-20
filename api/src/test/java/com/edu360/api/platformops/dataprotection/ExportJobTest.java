package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.edu360.api.files.FileStorage;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

class ExportJobTest {

    private final ExportRequestLifecycleService lifecycle = mock(ExportRequestLifecycleService.class);
    private final ExportArchiveBuilder builder = mock(ExportArchiveBuilder.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    private Tenant tenant() {
        Tenant tenant = Tenant.builder().tenantId(tenantId).isolationMode(IsolationMode.SHARED).build();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(lifecycle.claimApproved()).thenReturn(List.of(new ExportRequestLifecycleService.ExportClaim(requestId, tenantId)));
        return tenant;
    }

    @Test
    void approvedExportIsBuiltForThatTenantStoredAndMarkedReady() throws Exception {
        Tenant tenant = tenant();
        Path archive = Files.createTempFile("export-job-test", ".zip");
        Files.write(archive, new byte[] {1, 2, 3});
        AtomicReference<String> scopeDuringBuild = new AtomicReference<>();
        AtomicReference<UUID> tenantDuringBuild = new AtomicReference<>();
        doAnswer(inv -> {
            tenantDuringBuild.set(TenantContext.currentTenantId());
            scopeDuringBuild.set(TenantContext.currentInstitutionScope());
            return archive;
        }).when(builder).build(tenant);

        new ExportJob(lifecycle, builder, tenants, storage).run();

        assertThat(tenantDuringBuild.get()).isEqualTo(tenantId);
        assertThat(scopeDuringBuild.get()).isEqualTo("ALL");
        assertThat(TenantContext.currentTenantId()).as("context cleared after the build").isNull();
        verify(storage).store(eq(tenantId), any(UUID.class), any(InputStream.class));
        verify(lifecycle).markReady(eq(requestId), any(UUID.class));
        verify(lifecycle, never()).markFailed(any(), any());
        assertThat(Files.exists(archive)).as("temporary ZIP deleted").isFalse();
    }

    @Test
    void archiveFailureMarksTheRequestFailedAndClearsTheContext() {
        Tenant tenant = tenant();
        when(builder.build(tenant)).thenThrow(new IllegalStateException("archive failure"));

        new ExportJob(lifecycle, builder, tenants, storage).run();

        verify(lifecycle).markFailed(eq(requestId), any(Exception.class));
        verify(lifecycle, never()).markReady(any(), any());
        assertThat(TenantContext.currentTenantId()).isNull();
    }
}
