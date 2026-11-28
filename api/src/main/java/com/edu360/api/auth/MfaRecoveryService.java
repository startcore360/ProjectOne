package com.edu360.api.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.AuthChallengeException;
import com.edu360.api.platform.mfa.MfaRecoveryAttempt;
import com.edu360.api.platform.mfa.MfaRecoveryAttemptRepository;
import com.edu360.api.platform.mfa.MfaRecoveryCode;
import com.edu360.api.platform.mfa.MfaRecoveryCodeRepository;

/**
 * WHAT: one-time backup codes for two-step login (decision MFA-01).
 *
 * HOW: ten codes like "K7QD4-9MXRT" are issued when an authenticator is set up (or on request,
 * after the password is proven again). Only a BCrypt hash of each is stored. A code works once:
 * it is marked used, the user's old authenticator is removed by the caller, and the sign-in screen
 * then sets up a new one, which issues a fresh set and discards the rest.
 *
 * WHY a limit here: Keycloak counts wrong passwords and wrong authenticator codes, but backup codes
 * are checked by this API. So wrong guesses are stored (platform.mfa_recovery_attempt) and more
 * than {@value #MAX_FAILED_PER_HOUR} in an hour block further tries for that account - stored in
 * the database, so every API node and a restart agree.
 */
@Service
public class MfaRecoveryService {

    static final int CODE_COUNT = 10;
    static final int MAX_FAILED_PER_HOUR = 5;
    /** No 0/O, 1/I/L: codes are read off paper and typed by hand. */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MfaRecoveryCodeRepository codes;
    private final MfaRecoveryAttemptRepository attempts;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public MfaRecoveryService(MfaRecoveryCodeRepository codes, MfaRecoveryAttemptRepository attempts) {
        this.codes = codes;
        this.attempts = attempts;
    }

    /** Replaces any earlier set with ten new codes and returns them in plain text, once. */
    @Transactional(transactionManager = "platformTransactionManager")
    public List<String> issue(String realm, String keycloakUserId) {
        codes.deleteAllFor(realm, keycloakUserId);
        Instant now = Instant.now();
        List<String> plain = new ArrayList<>(CODE_COUNT);
        List<MfaRecoveryCode> rows = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = newCode();
            plain.add(code);
            rows.add(MfaRecoveryCode.builder()
                    .mfaRecoveryCodeId(UUID.randomUUID())
                    .realm(realm)
                    .keycloakUserId(keycloakUserId)
                    .codeHash(encoder.encode(normalise(code)))
                    .createdAt(now)
                    .build());
        }
        codes.saveAll(rows);
        return plain;
    }

    /**
     * True when the code matches one of the account's unused codes, which is then marked used.
     * Every attempt is recorded. Throws 429 RECOVERY_CODE_LIMIT once too many wrong codes were tried.
     */
    @Transactional(transactionManager = "platformTransactionManager")
    public boolean consume(String realm, String keycloakUserId, String typedCode) {
        Instant now = Instant.now();
        long recentFailures = attempts.countByRealmAndKeycloakUserIdAndSucceededFalseAndAttemptedAtAfter(
                realm, keycloakUserId, now.minus(Duration.ofHours(1)));
        if (recentFailures >= MAX_FAILED_PER_HOUR) {
            throw new AuthChallengeException(429, "RECOVERY_CODE_LIMIT",
                    "Too many wrong backup codes. Wait an hour, or ask your administrator to reset your two-step login.");
        }
        String normalised = normalise(typedCode);
        MfaRecoveryCode match = normalised.isEmpty() ? null
                : codes.findByRealmAndKeycloakUserIdAndUsedAtIsNull(realm, keycloakUserId).stream()
                        .filter(c -> encoder.matches(normalised, c.getCodeHash()))
                        .findFirst()
                        .orElse(null);
        if (match != null) {
            match.setUsedAt(now);
            codes.save(match);
        }
        attempts.save(MfaRecoveryAttempt.builder()
                .mfaRecoveryAttemptId(UUID.randomUUID())
                .realm(realm)
                .keycloakUserId(keycloakUserId)
                .succeeded(match != null)
                .attemptedAt(now)
                .build());
        return match != null;
    }

    /** Removes every code of the account (after an admin reset, or before a new set is issued). */
    @Transactional(transactionManager = "platformTransactionManager")
    public void clear(String realm, String keycloakUserId) {
        codes.deleteAllFor(realm, keycloakUserId);
    }

    public long remaining(String realm, String keycloakUserId) {
        return codes.countByRealmAndKeycloakUserIdAndUsedAtIsNull(realm, keycloakUserId);
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Case, spaces and the dash do not matter when typing a code. */
    static String normalise(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
