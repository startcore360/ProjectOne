package com.edu360.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserType;

/**
 * R2-35 / story 2 (AUTH_FIRST_LOGIN_RESET) / I-05: a student's printed first-login password is
 * refused 7 days after it was issued (app.user_account.credential_expires_at, checked at sign-in
 * by AuthService before any token is issued, and cleared when the student sets their own).
 * Applies to students only; staff temporary passwords are governed by the forced first change.
 */
@Component
public class PrintedPasswordPolicy {

    public static final Duration PRINTED_PASSWORD_VALIDITY = Duration.ofDays(7);

    private final UserAccountRepository userAccountRepository;

    public PrintedPasswordPolicy(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /** Expiry for a printed password issued now to a user of this type; null when none applies. */
    public static Instant expiryFor(UserType userType, Instant issuedAt) {
        return userType == UserType.STUDENT ? issuedAt.plus(PRINTED_PASSWORD_VALIDITY) : null;
    }

    /** After an admin reset: (re)start the 7-day window for a student, clear it for anyone else. */
    public void markIssued(UUID tenantId, UUID userAccountId) {
        userAccountRepository.findById(new UserAccountId(tenantId, userAccountId)).ifPresent((UserAccount account) -> {
            account.setCredentialExpiresAt(expiryFor(account.getUserType(), Instant.now()));
            userAccountRepository.save(account);
        });
    }
}
