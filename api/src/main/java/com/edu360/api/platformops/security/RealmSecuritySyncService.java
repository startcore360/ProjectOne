package com.edu360.api.platformops.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService.RealmSecuritySettings;
import com.edu360.api.platformops.security.SecurityPolicyService.SecurityPolicy;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * I-06: pushes the saved security policy (and the rest of the B3 sign-in baseline - lockout,
 * two-step flow, password-check client) into Keycloak:
 * - to every realm when the Product Owner saves the policy (PlatformSecurityPolicyController);
 * - to a new tenant's realm when it is created (TenantManagementService);
 * - to every realm once at start-up (app.keycloak.realm-security-sync-on-startup, on by default,
 *   off in the test profile), so realms created before this existed - or changed by hand - are
 *   brought back to the policy without anyone having to remember to.
 *
 * "Every realm" = the platform realm plus each platform.tenant row's keycloak_realm. A failure
 * on one realm is logged and reported in the result; it does not stop the others.
 */
@Service
public class RealmSecuritySyncService {

    private static final Logger log = LoggerFactory.getLogger(RealmSecuritySyncService.class);

    public record RealmSyncResult(String realm, boolean applied, String error) {
    }

    public record RealmSyncPreview(UUID previewId, List<String> realms, RealmSecuritySettings settings) {
    }

    public record RealmSyncRunResult(UUID runId, List<RealmSyncResult> results) {
    }

    private volatile UUID latestPreviewId;
    private volatile Set<String> latestPreviewRealms = Set.of();
    private volatile RealmSecuritySettings latestPreviewSettings;
    private volatile UUID latestRunId;
    private volatile Set<String> latestFailedRealms = Set.of();
    private volatile RealmSecuritySettings latestRunSettings;

    private final SecurityPolicyService securityPolicyService;
    private final KeycloakRealmSecurityService realmSecurityService;
    private final KeycloakProperties keycloakProperties;
    private final TenantRepository tenantRepository;
    private final boolean syncOnStartup;

    public RealmSecuritySyncService(SecurityPolicyService securityPolicyService,
                                    KeycloakRealmSecurityService realmSecurityService,
                                    KeycloakProperties keycloakProperties,
                                    TenantRepository tenantRepository,
                                    @Value("${app.keycloak.realm-security-sync-on-startup:true}") boolean syncOnStartup) {
        this.securityPolicyService = securityPolicyService;
        this.realmSecurityService = realmSecurityService;
        this.keycloakProperties = keycloakProperties;
        this.tenantRepository = tenantRepository;
        this.syncOnStartup = syncOnStartup;
    }

    public static RealmSecuritySettings settingsOf(SecurityPolicy policy) {
        return new RealmSecuritySettings(policy.minPasswordLength(), policy.requireDigit(), policy.requireSpecialChar(),
                policy.maxFailedLogins(), policy.lockoutMinutes(), policy.idleSessionTimeoutMinutes());
    }

    /** One realm, current policy. Throws on failure (tenant creation must not half-succeed). */
    public void applyToRealm(String realm) {
        realmSecurityService.apply(realm, settingsOf(securityPolicyService.current()));
    }

    /** Read-only preview; it does not contact or mutate Keycloak. */
    public synchronized RealmSyncPreview previewAllRealms() {
        latestPreviewId = UUID.randomUUID();
        latestPreviewRealms = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(allRealms()));
        latestPreviewSettings = settingsOf(securityPolicyService.current());
        return new RealmSyncPreview(latestPreviewId, List.copyOf(latestPreviewRealms), latestPreviewSettings);
    }

    /** Existing callers still apply the current policy to every realm. */
    public List<RealmSyncResult> applyToAllRealms() {
        return applyToAllRealms(securityPolicyService.current());
    }

    public List<RealmSyncResult> applyToAllRealms(SecurityPolicy policy) {
        return applyToRealms(allRealms(), settingsOf(policy));
    }

    /** Applies exactly the realm set and policy captured by the latest preview. */
    public synchronized RealmSyncRunResult applyPreviewedRealms(UUID previewId) {
        if (previewId == null || !previewId.equals(latestPreviewId) || latestPreviewSettings == null) {
            throw new IllegalArgumentException("Realm sync preview is no longer available.");
        }
        latestPreviewId = null;
        return rememberRun(applyToRealms(latestPreviewRealms, latestPreviewSettings), latestPreviewSettings);
    }

    /** Retries only the failed subset from the immediately preceding run, with that run's policy. */
    public synchronized RealmSyncRunResult retryFailedRealms(UUID runId) {
        if (runId == null || !runId.equals(latestRunId)) {
            throw new IllegalArgumentException("Realm sync run is no longer available.");
        }
        if (latestFailedRealms.isEmpty()) {
            throw new IllegalArgumentException("There are no failed realms to retry.");
        }
        return rememberRun(applyToRealms(latestFailedRealms, latestRunSettings), latestRunSettings);
    }

    private RealmSyncRunResult rememberRun(List<RealmSyncResult> results, RealmSecuritySettings settings) {
        UUID runId = UUID.randomUUID();
        latestRunId = runId;
        latestRunSettings = settings;
        latestFailedRealms = results.stream().filter(result -> !result.applied())
                .map(RealmSyncResult::realm).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RealmSyncRunResult(runId, List.copyOf(results));
    }

    private List<RealmSyncResult> applyToRealms(Set<String> realms, RealmSecuritySettings settings) {
        List<RealmSyncResult> results = new ArrayList<>();
        for (String realm : realms) {
            try {
                realmSecurityService.apply(realm, settings);
                results.add(new RealmSyncResult(realm, true, null));
            } catch (RuntimeException ex) {
                // Never return or log provider messages: they may contain credentials or response bodies.
                log.error("Could not apply the security policy to Keycloak realm '{}' ({}).",
                        realm, ex.getClass().getSimpleName());
                results.add(new RealmSyncResult(realm, false, "REALM_SYNC_FAILED"));
            }
        }
        return results;
    }

    Set<String> allRealms() {
        Set<String> realms = new LinkedHashSet<>();
        if (keycloakProperties.getPlatformRealm() != null && !keycloakProperties.getPlatformRealm().isBlank()) {
            realms.add(keycloakProperties.getPlatformRealm());
        }
        for (Tenant tenant : tenantRepository.findAll()) {
            if (tenant.getKeycloakRealm() != null && !tenant.getKeycloakRealm().isBlank()) {
                realms.add(tenant.getKeycloakRealm());
            }
        }
        return realms;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncAtStartup() {
        if (!syncOnStartup) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                List<RealmSyncResult> results = applyToAllRealms();
                long failed = results.stream().filter(r -> !r.applied()).count();
                log.info("Start-up realm security sync: {} realm(s) applied, {} failed", results.size() - failed, failed);
            } catch (RuntimeException ex) {
                log.error("Start-up realm security sync failed: {}", ex.getMessage(), ex);
            }
        }, "realm-security-sync");
        worker.setDaemon(true);
        worker.start();
    }
}
