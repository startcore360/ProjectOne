package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.exception.AuthChallengeException;
import com.edu360.api.platform.mfa.MfaRecoveryAttempt;
import com.edu360.api.platform.mfa.MfaRecoveryAttemptRepository;
import com.edu360.api.platform.mfa.MfaRecoveryCode;
import com.edu360.api.platform.mfa.MfaRecoveryCodeRepository;

/** MFA-01 backup codes: issued once, hashed, single use, and wrong guesses are limited. */
@ExtendWith(MockitoExtension.class)
class MfaRecoveryServiceTest {

    private static final String REALM = "edu360-platform";
    private static final String USER = "kc-user-1";

    @Mock private MfaRecoveryCodeRepository codes;
    @Mock private MfaRecoveryAttemptRepository attempts;

    private MfaRecoveryService service;
    private final List<MfaRecoveryCode> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MfaRecoveryService(codes, attempts);
    }

    @SuppressWarnings("unchecked")
    private List<String> issueAndCapture() {
        ArgumentCaptor<Iterable<MfaRecoveryCode>> saved = ArgumentCaptor.forClass(Iterable.class);
        List<String> plain = service.issue(REALM, USER);
        verify(codes).saveAll(saved.capture());
        saved.getValue().forEach(stored::add);
        return plain;
    }

    @Test
    void issuesTenDistinctReadableCodesAndStoresOnlyHashes() {
        List<String> plain = issueAndCapture();

        assertThat(plain).hasSize(10);
        assertThat(new HashSet<>(plain)).hasSize(10);
        assertThat(plain).allMatch(c -> c.matches("^[A-HJ-KM-NP-Z2-9]{5}-[A-HJ-KM-NP-Z2-9]{5}$"));
        verify(codes).deleteAllFor(REALM, USER);
        assertThat(stored).hasSize(10);
        assertThat(stored).allSatisfy(row -> {
            assertThat(row.getCodeHash()).startsWith("$2");
            assertThat(plain).doesNotContain(row.getCodeHash());
            assertThat(row.getUsedAt()).isNull();
        });
    }

    @Test
    void aCodeWorksOnceRegardlessOfCaseSpacesAndDash() {
        List<String> plain = issueAndCapture();
        when(codes.findByRealmAndKeycloakUserIdAndUsedAtIsNull(REALM, USER)).thenReturn(List.copyOf(stored));

        String typed = " " + plain.get(3).toLowerCase().replace("-", " ") + " ";
        assertThat(service.consume(REALM, USER, typed)).isTrue();

        MfaRecoveryCode used = stored.stream().filter(c -> c.getUsedAt() != null).findFirst().orElseThrow();
        verify(codes).save(used);
        ArgumentCaptor<MfaRecoveryAttempt> attempt = ArgumentCaptor.forClass(MfaRecoveryAttempt.class);
        verify(attempts).save(attempt.capture());
        assertThat(attempt.getValue().isSucceeded()).isTrue();
    }

    @Test
    void anUnknownOrAlreadyUsedCodeIsRefusedAndCountedAsAFailure() {
        when(codes.findByRealmAndKeycloakUserIdAndUsedAtIsNull(REALM, USER)).thenReturn(List.of());

        assertThat(service.consume(REALM, USER, "AAAAA-BBBBB")).isFalse();

        ArgumentCaptor<MfaRecoveryAttempt> attempt = ArgumentCaptor.forClass(MfaRecoveryAttempt.class);
        verify(attempts).save(attempt.capture());
        assertThat(attempt.getValue().isSucceeded()).isFalse();
        verify(codes, never()).save(any());
    }

    @Test
    void afterFiveWrongCodesInAnHourFurtherTriesAreBlockedWithoutCheckingTheCode() {
        when(attempts.countByRealmAndKeycloakUserIdAndSucceededFalseAndAttemptedAtAfter(eq(REALM), eq(USER), any()))
                .thenReturn(5L);

        assertThatThrownBy(() -> service.consume(REALM, USER, "AAAAA-BBBBB"))
                .isInstanceOf(AuthChallengeException.class)
                .satisfies(ex -> {
                    assertThat(((AuthChallengeException) ex).getStatus()).isEqualTo(429);
                    assertThat(((AuthChallengeException) ex).getCode()).isEqualTo("RECOVERY_CODE_LIMIT");
                });
        verify(codes, never()).findByRealmAndKeycloakUserIdAndUsedAtIsNull(anyString(), anyString());
    }

    @Test
    void anEmptyCodeNeverMatches() {
        assertThat(service.consume(REALM, USER, "  -  ")).isFalse();
        verify(codes, never()).findByRealmAndKeycloakUserIdAndUsedAtIsNull(anyString(), anyString());
    }

    @Test
    void clearRemovesEveryCodeOfTheAccount() {
        service.clear(REALM, USER);
        verify(codes).deleteAllFor(REALM, USER);
    }
}
