package com.edu360.api.platformops.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /** Every realm, current policy. */
    public List<RealmSyncResult> applyToAllRealms() {
        return applyToAllRealms(securityPolicyService.current());
    }

    public List<RealmSyncResult> applyToAllRealms(SecurityPolicy policy) {
        RealmSecuritySettings settings = settingsOf(policy);
        List<RealmSyncResult> results = new ArrayList<>();
        for (String realm : allRealms()) {
            try {
                realmSecurityService.apply(realm, settings);
                results.add(new RealmSyncResult(realm, true, null));
            } catch (RuntimeException ex) {
                log.error("Could not apply the security policy to Keycloak realm '{}': {}", realm, ex.getMessage());
                results.add(new RealmSyncResult(realm, false, ex.getMessage()));
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
