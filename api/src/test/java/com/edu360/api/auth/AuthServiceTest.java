package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthTokenResponse;
import com.edu360.api.auth.dto.RegisterRequest;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private KeycloakAuthClient keycloakAuthClient;
    @Mock
    private AuditLogService auditLogService;

    private AuthService authService() {
        return new AuthService(userRepository, keycloakAdminClient, keycloakAuthClient, auditLogService);
    }

    private RegisterRequest request(Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Jane Teacher");
        request.setEmail("jane@edu360.local");
        request.setPassword("Temp#Pass1");
        request.setRole(role);
        return request;
    }

    @Test
    void register_rejectsPlatformAdminRole() {
        assertThatThrownBy(() -> authService().register(request(Role.PLATFORM_ADMIN), 1L, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Platform admin");
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("jane@edu360.local")).thenReturn(true);

        assertThatThrownBy(() -> authService().register(request(Role.TEACHER), 1L, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void register_provisionsKeycloakAccountAndSavesLinkedLocalUser() {
        when(userRepository.existsByEmail("jane@edu360.local")).thenReturn(false);
        when(keycloakAdminClient.provisionUser("Jane Teacher", "jane@edu360.local", "Temp#Pass1", "TEACHER"))
                .thenReturn("kc-user-123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        User created = authService().register(request(Role.TEACHER), 1L, "127.0.0.1");

        assertThat(created.getKeycloakId()).isEqualTo("kc-user-123");
        assertThat(created.getRole()).isEqualTo(Role.TEACHER);
        verify(auditLogService).record(AuditEventType.ACCOUNT_CREATED, 42L, "jane@edu360.local",
                "127.0.0.1", "Created by user 1");
    }

    @Test
    void login_wrongPassword_recordsFailureAndRethrows() {
        when(keycloakAuthClient.login("jane@edu360.local", "wrong"))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        assertThatThrownBy(() -> authService().login("jane@edu360.local", "wrong", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditLogService).record(AuditEventType.LOGIN_FAILURE, null, "jane@edu360.local",
                "127.0.0.1", null);
    }

    @Test
    void login_success_returnsTokensAndLinkedUser() {
        User user = User.builder().id(7L).name("Jane Teacher").email("jane@edu360.local")
                .keycloakId("kc-user-123").role(Role.TEACHER).build();
        when(keycloakAuthClient.login("jane@edu360.local", "Temp#Pass1"))
                .thenReturn(new KeycloakAuthClient.TokenResult("access-token", "refresh-token", 300, "kc-user-123"));
        when(userRepository.findByKeycloakId("kc-user-123")).thenReturn(java.util.Optional.of(user));

        AuthTokenResponse response = authService().login("jane@edu360.local", "Temp#Pass1", "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().email()).isEqualTo("jane@edu360.local");
        verify(auditLogService).record(AuditEventType.LOGIN_SUCCESS, 7L, "jane@edu360.local",
                "127.0.0.1", null);
    }

    @Test
    void login_noLocalAccountForKeycloakUser_throwsInvalidCredentials() {
        when(keycloakAuthClient.login("ghost@edu360.local", "Temp#Pass1"))
                .thenReturn(new KeycloakAuthClient.TokenResult("access-token", "refresh-token", 300, "kc-ghost"));
        when(userRepository.findByKeycloakId("kc-ghost")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService().login("ghost@edu360.local", "Temp#Pass1", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
