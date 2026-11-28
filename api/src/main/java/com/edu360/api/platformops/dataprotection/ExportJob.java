package com.edu360.api.platformops.dataprotection;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.edu360.api.files.FileStorage;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

/**
 * E5-S3: generates approved tenant exports (CL-09) and expires old ones.
 *
 * WHAT: every minute, claims APPROVED export requests (→ GENERATING), builds each tenant's ZIP, stores it
 * through FileStorage under tenants/{tenantId}/{fileRef} and marks it READY; any failure marks it FAILED.
 * Every 15 minutes, READY exports past their 24-hour window become EXPIRED and their file is deleted.
 * HOW: the tenant comes from the claimed request, never from a job parameter. TenantContext is set for that
 * one tenant with institution scope ALL (a tenant-wide, DPO-approved export) and cleared in finally, so the
 * tenant-routing DataSource applies that tenant's RLS settings to every query the builder runs.
 * WHY: platform requests normally carry no TenantContext and RLS then hides every app.* row; this job is the
 * single, narrow place that opens one tenant's data, without any new database role or grant.
 */
@Component
public class ExportJob {

    private static final Logger log = LoggerFactory.getLogger(ExportJob.class);

    private final ExportRequestLifecycleService lifecycle;
    private final ExportArchiveBuilder builder;
    private final TenantRepository tenants;
    private final FileStorage storage;

    public ExportJob(ExportRequestLifecycleService lifecycle, ExportArchiveBuilder builder,
            TenantRepository tenants, FileStorage storage) {
        this.lifecycle = lifecycle;
        this.builder = builder;
        this.tenants = tenants;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${app.export.poll-ms:60000}", initialDelayString = "${app.export.initial-delay-ms:60000}")
    public void run() {
        for (ExportRequestLifecycleService.ExportClaim claim : lifecycle.claimApproved()) {
            process(claim);
        }
    }

    @Scheduled(fixedDelayString = "${app.export.expiry-poll-ms:900000}", initialDelayString = "${app.export.initial-delay-ms:60000}")
    public void expire() {
        int expired = lifecycle.expireReadyExports();
        if (expired > 0) log.info("Expired {} tenant export(s)", expired);
    }

    void process(ExportRequestLifecycleService.ExportClaim claim) {
        Path archive = null;
        try {
            Tenant tenant = tenants.findById(claim.tenantId())
                    .orElseThrow(() -> new IllegalStateException("Tenant " + claim.tenantId() + " not found."));
            TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                    "ALL", Set.of(), null, null);
            try {
                archive = builder.build(tenant);
            } finally {
                TenantContext.clear();
            }
            UUID fileRef = UUID.randomUUID();
            try (InputStream input = Files.newInputStream(archive)) {
                storage.store(claim.tenantId(), fileRef, input);
            }
            lifecycle.markReady(claim.exportRequestId(), fileRef);
        } catch (Exception ex) {
            log.error("Tenant export {} failed", claim.exportRequestId(), ex);
            lifecycle.markFailed(claim.exportRequestId(), ex);
        } finally {
            ExportArchiveBuilder.deleteFile(archive);
        }
    }
}
