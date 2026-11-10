package com.edu360.api.platformops.security;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WHAT: the one place that answers "what is the platform security policy right now?" for every
 * consumer - the policy screen (SECURITY_POLICY_SET), the password checks in AuthService, the
 * Keycloak realm sync (I-06) and the idle-timeout setting sent to the browser.
 *
 * Source: the ACTIVE platform.setting_value row with key "security.policy" (written by
 * PlatformSecurityPolicyController). When none has been saved yet, the D1 values apply
 * (D1 Part II §8.1 / §11.0 "Security", NF-07, NF-08, R2-12): passwords at least 8 characters,
 * lockout for 15 minutes after 5 wrong passwords, forced change every 120 days, two-step login
 * for the listed roles. The 30-minute idle timeout and 90-day backup retention are the values
 * this app already used before the policy could be saved.
 */
@Service
public class SecurityPolicyService {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyService.class);

    public static final String SETTING_KEY_SECURITY_POLICY = "security.policy";

    /** D1 values used until the Product Owner saves a policy. */
    public static final SecurityPolicy D1_DEFAULT = new SecurityPolicy(8, false, false, 5, 15, 30, true, 90, 120);

    /** The policy as stored. passwordRotationDays: forced change after that many days (R2-12). */
    public record SecurityPolicy(
            int minPasswordLength,
            boolean requireSpecialChar,
            boolean requireDigit,
            int maxFailedLogins,
            int lockoutMinutes,
            int idleSessionTimeoutMinutes,
            boolean mfaRequiredForPrivileged,
            int defaultBackupRetentionDays,
            Integer passwordRotationDays) {

        /** Rotation days, D1's 120 for a policy saved before the field existed. */
        public int rotationDays() {
            return passwordRotationDays == null || passwordRotationDays <= 0
                    ? D1_DEFAULT.passwordRotationDays() : passwordRotationDays;
        }
    }

    private final PlatformSettingValueRepository settingValueRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SecurityPolicyService(@Autowired(required = false) PlatformSettingValueRepository settingValueRepository) {
        this.settingValueRepository = settingValueRepository;
    }

    /** The current policy; D1 defaults when none has been saved (or the row cannot be read). */
    public SecurityPolicy current() {
        if (settingValueRepository == null) {
            return D1_DEFAULT;
        }
        try {
            Optional<PlatformSettingValue> row = settingValueRepository
                    .findBySettingKeyAndStatus(SETTING_KEY_SECURITY_POLICY, "ACTIVE");
            if (row.isPresent()) {
                return objectMapper.readValue(row.get().getValue(), SecurityPolicy.class);
            }
        } catch (Exception ex) {
            log.warn("Could not read the saved security policy, using the D1 defaults: {}", ex.getMessage());
        }
        return D1_DEFAULT;
    }

    public String toJson(SecurityPolicy policy) {
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise the security policy", ex);
        }
    }
}
