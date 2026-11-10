package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpResponse;

import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.CreateUserResponse;
import com.edu360.api.rbac.dto.PasswordResetResponse;

/** REG-I05-1: every response that carries a temporary password is marked no-store, whichever endpoint sends it. */
class TemporaryPasswordResponseAdviceTest {

    private final TemporaryPasswordResponseAdvice advice = new TemporaryPasswordResponseAdvice();

    private HttpHeaders bodyWrittenWith(Object body) {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        when(response.getHeaders()).thenReturn(headers);
        advice.beforeBodyWrite(body, null, null, null, null, response);
        return headers;
    }

    @Test
    @DisplayName("REG-I05-1: create-user response carrying a temporary password gets Cache-Control: no-store")
    void createUserResponseIsNoStore() {
        CreateUserResponse body = new CreateUserResponse(UUID.randomUUID(), UUID.randomUUID(), UserType.STAFF, "A", "a@x.test",
                null, UserAccountStatus.ACTIVE, "Temp-Pass-123", "CLASS_TEACHER", Instant.now());

        assertThat(bodyWrittenWith(body).getCacheControl()).isEqualTo("no-store");
    }

    @Test
    @DisplayName("REG-I05-1: reset-password response carrying a temporary password gets Cache-Control: no-store")
    void resetResponseIsNoStore() {
        PasswordResetResponse body = new PasswordResetResponse("USER_PASSWORD_RESET_UNLOCK", "ALLOWED", "ok", "Temp-Pass-123");

        assertThat(bodyWrittenWith(body).getCacheControl()).isEqualTo("no-store");
    }

    @Test
    @DisplayName("REG-I05-1 guard: a response with no temporary password is left alone")
    void ordinaryResponseIsUntouched() {
        PasswordResetResponse body = new PasswordResetResponse("USER_PASSWORD_RESET_UNLOCK", "ALLOWED", "ok", null);

        assertThat(bodyWrittenWith(body).getCacheControl()).isNull();
    }
}
