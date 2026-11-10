package com.edu360.api.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.UserRoleAssignmentRepository;

/**
 * WHAT: decides who must use two-step login (I-01, PD-8) and keeps Keycloak's mfa_required user
 * attribute - the switch the realm's direct-grant flow reads - in line with it.
 *
 * WHO (D1 Part II §11.0 "Two-step login" and AUTH_LOGIN in D2, incl. F-14 and C6): platform
 * roles, Admins, Principals, Accountants, HR and Payroll, plus Management, Vice-Chancellor,
 * Registrar, Finance Director, Controller of Examinations, IT Administrator, School IT Support
 * and the Governing Body Approver. A user needs it when ANY of their current role assignments is
 * in the list, not just the first one. Every code below exists in ref.standard_role
 * (sql/05_ref_data.sql) - MfaRequirementServiceTest checks that against the file (REG-I01-7).
 *
 * WHEN the attribute is written:
 * - after a role is assigned or ended (RoleAssignmentService calls syncTenantUser);
 * - at sign-in, after the password is proven (AuthService) - this also covers accounts whose
 *   roles were given before this existed, so nobody holding a listed role can sign in without
 *   the second step.
 * When it is set and the user has no authenticator yet, Keycloak's CONFIGURE_TOTP required
 * action is added so Keycloak's own set-up page appears at the user's next Keycloak sign-in.
 */
@Service
public class MfaRequirementService {

    private static final Logger log = LoggerFactory.getLogger(MfaRequirementService.class);

    public static final Set<String> MFA_REQUIRED_ROLES = Set.of(
            "PRODUCT_OWNER", "PLATFORM_ADMIN", "BILLING_MANAGER", "SUPPORT_MANAGER", "RELEASE_MANAGER", "DATA_PROTECTION_OFFICER",
            "MANAGEMENT", "GOVERNING_BODY_APPROVER", "UNIVERSITY_ADMIN", "REGISTRAR", "VICE_CHANCELLOR",
            "FINANCE_DIRECTOR", "CONTROLLER_OF_EXAMINATIONS",
            "COLLEGE_ADMIN", "PRINCIPAL_COLLEGE", "VICE_PRINCIPAL_COLLEGE",
            "SCHOOL_ADMIN", "SCHOOL_PRINCIPAL", "VICE_PRINCIPAL_SCHOOL",
            "UNIVERSITY_ACCOUNTANT", "COLLEGE_ACCOUNTANT", "SCHOOL_ACCOUNTANT",
            "UNIVERSITY_HR_MANAGER", "HR_MANAGER", "HR_EXECUTIVE", "PAYROLL_OFFICER", "IT_ADMINISTRATOR", "SCHOOL_IT_SUPPORT"
    );

    private final KeycloakAdminClient keycloakAdminClient;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;

    @Autowired
    public MfaRequirementService(KeycloakAdminClient keycloakAdminClient,
                                 UserIdentityLinkRepository userIdentityLinkRepository,
                                 UserRoleAssignmentRepository userRoleAssignmentRepository,
                                 RoleDefinitionRepository roleDefinitionRepository) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
    }

    public static boolean requiresMfa(Collection<String> roleCodes) {
        return roleCodes != null && roleCodes.stream().anyMatch(MFA_REQUIRED_ROLES::contains);
    }

    /** Role codes of every ACTIVE assignment currently inside its effective window. */
    public List<String> currentTenantRoleCodes(UUID tenantId, UUID userAccountId) {
        Instant now = Instant.now();
        return userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, userAccountId, AssignmentStatus.ACTIVE)
                .stream()
                .filter(a -> (a.getEffectiveFrom() == null || !a.getEffectiveFrom().isAfter(now))
                        && (a.getEffectiveTo() == null || a.getEffectiveTo().isAfter(now)))
                .map(a -> roleDefinitionRepository.findById(new RoleDefinitionId(tenantId, a.getRoleDefinitionId()))
                        .map(RoleDefinition::getRoleCode).orElse(null))
                .filter(code -> code != null)
                .toList();
    }

    public boolean tenantUserRequiresMfa(UUID tenantId, UUID userAccountId) {
        return requiresMfa(currentTenantRoleCodes(tenantId, userAccountId));
    }

    /**
     * REG-I01-6: after a role change, set mfa_required=true when a listed role is held, remove it
     * when none is. Runs inside the caller's tenant context (RLS).
     */
    public void syncTenantUser(UUID tenantId, UUID userAccountId) {
        boolean required = tenantUserRequiresMfa(tenantId, userAccountId);
        userIdentityLinkRepository
                .findFirstByIdTenantIdAndUserAccountIdAndStatus(tenantId, userAccountId, IdentityLinkStatus.ACTIVE)
                .ifPresent(link -> applyToKeycloak(link.getIdentityRealm(), link.getIdentitySubject(), required));
    }

    /**
     * Writes the attribute (and the set-up required action when needed) for one Keycloak user.
     * Returns true when the user already has an authenticator.
     */
    public boolean applyToKeycloak(String realm, String keycloakUserId, boolean required) {
        boolean hasOtp = keycloakAdminClient.listCredentials(realm, keycloakUserId).stream()
                .anyMatch(c -> "otp".equalsIgnoreCase(c.type()));
        if (required) {
            keycloakAdminClient.setUserAttribute(realm, keycloakUserId, KeycloakAdminClient.MFA_REQUIRED_ATTRIBUTE, "true");
            if (!hasOtp) {
                keycloakAdminClient.setRequiredAction(realm, keycloakUserId, KeycloakAdminClient.CONFIGURE_TOTP_ACTION, true);
            }
        } else {
            keycloakAdminClient.setUserAttribute(realm, keycloakUserId, KeycloakAdminClient.MFA_REQUIRED_ATTRIBUTE, null);
            keycloakAdminClient.setRequiredAction(realm, keycloakUserId, KeycloakAdminClient.CONFIGURE_TOTP_ACTION, false);
        }
        log.info("Two-step login {} for Keycloak user {} in realm {}", required ? "required" : "not required",
                keycloakUserId, realm);
        return hasOtp;
    }
}
