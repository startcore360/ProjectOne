package com.edu360.api.auth;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

/**
 * Exercises the real request pipeline - SecurityConfig's resource-server filter,
 * KeycloakJwtAuthenticationConverter, and AuthController together - the same way a real
 * Keycloak-issued bearer token would flow, except JwtDecoder is stubbed so no live Keycloak
 * is needed: only token *validation* is faked, everything after it (converting the token to
 * our User principal, @PreAuthorize, the controller logic, persistence) is real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private KeycloakAdminClient keycloakAdminClient;
    @MockitoBean
    private KeycloakAuthClient keycloakAuthClient;

    private User admin;
    private User teacher;

    @BeforeEach
    void seedUsers() {
        admin = userRepository.save(User.builder().name("Admin").email("admin@edu360.local")
                .keycloakId("kc-admin-1").role(Role.PLATFORM_ADMIN).mustChangePassword(false).build());
        teacher = userRepository.save(User.builder().name("Teacher").email("teacher@edu360.local")
                .keycloakId("kc-teacher-1").role(Role.TEACHER).mustChangePassword(false).build());
    }

    private Jwt jwtFor(String subject, String role) {
        return Jwt.withTokenValue(subject + "-token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void me_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returnsLinkedLocalUser() throws Exception {
        when(jwtDecoder.decode("kc-admin-1-token")).thenReturn(jwtFor("kc-admin-1", "PLATFORM_ADMIN"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer kc-admin-1-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"email\":\"admin@edu360.local\",\"role\":\"PLATFORM_ADMIN\"}"));
    }

    @Test
    void me_withTokenForUnprovisionedKeycloakUser_isUnauthorized() throws Exception {
        when(jwtDecoder.decode("kc-ghost-1-token")).thenReturn(jwtFor("kc-ghost-1", "TEACHER"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer kc-ghost-1-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_asPlatformAdmin_createsTeacherAndLinksKeycloakId() throws Exception {
        when(jwtDecoder.decode("kc-admin-1-token")).thenReturn(jwtFor("kc-admin-1", "PLATFORM_ADMIN"));
        when(keycloakAdminClient.provisionUser(eq("New Teacher"), eq("new.teacher@edu360.local"),
                eq("Temp#Pass1"), eq("TEACHER"))).thenReturn("kc-new-teacher-1");

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer kc-admin-1-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Teacher","email":"new.teacher@edu360.local",
                                 "password":"Temp#Pass1","role":"TEACHER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"email\":\"new.teacher@edu360.local\",\"role\":\"TEACHER\"}"));

        var saved = userRepository.findByEmail("new.teacher@edu360.local").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getKeycloakId()).isEqualTo("kc-new-teacher-1");
    }

    @Test
    void register_asTeacher_isForbidden() throws Exception {
        when(jwtDecoder.decode("kc-teacher-1-token")).thenReturn(jwtFor("kc-teacher-1", "TEACHER"));

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer kc-teacher-1-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Teacher","email":"someone@edu360.local",
                                 "password":"Temp#Pass1","role":"TEACHER"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_platformAdminRole_isRejected() throws Exception {
        when(jwtDecoder.decode("kc-admin-1-token")).thenReturn(jwtFor("kc-admin-1", "PLATFORM_ADMIN"));

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer kc-admin-1-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Another Admin","email":"another.admin@edu360.local",
                                 "password":"Temp#Pass1","role":"PLATFORM_ADMIN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mustChangePasswordUser_isBlockedFromOtherEndpoints() throws Exception {
        userRepository.save(User.builder().name("New Teacher").email("new.teacher@edu360.local")
                .keycloakId("kc-new-teacher-1").role(Role.TEACHER).mustChangePassword(true).build());
        when(jwtDecoder.decode("kc-new-teacher-1-token")).thenReturn(jwtFor("kc-new-teacher-1", "TEACHER"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer kc-new-teacher-1-token"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer kc-new-teacher-1-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"PASSWORD_CHANGE_REQUIRED\"}"));
    }

    @Test
    void changePassword_success_clearsFlagAndResetsKeycloakCredential() throws Exception {
        userRepository.save(User.builder().name("New Teacher").email("new.teacher@edu360.local")
                .keycloakId("kc-new-teacher-1").role(Role.TEACHER).mustChangePassword(true).build());
        when(jwtDecoder.decode("kc-new-teacher-1-token")).thenReturn(jwtFor("kc-new-teacher-1", "TEACHER"));

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer kc-new-teacher-1-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Temp#Pass1","newPassword":"NewPass#2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"mustChangePassword\":false}"));

        var saved = userRepository.findByEmail("new.teacher@edu360.local").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.isMustChangePassword()).isFalse();
        org.mockito.Mockito.verify(keycloakAdminClient).resetPassword("kc-new-teacher-1", "NewPass#2");
    }

    @Test
    void changePassword_wrongCurrentPassword_isUnauthorizedAndFlagStaysSet() throws Exception {
        userRepository.save(User.builder().name("New Teacher").email("new.teacher@edu360.local")
                .keycloakId("kc-new-teacher-1").role(Role.TEACHER).mustChangePassword(true).build());
        when(jwtDecoder.decode("kc-new-teacher-1-token")).thenReturn(jwtFor("kc-new-teacher-1", "TEACHER"));
        when(keycloakAuthClient.login("new.teacher@edu360.local", "WrongPass"))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer kc-new-teacher-1-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongPass","newPassword":"NewPass#2"}
                                """))
                .andExpect(status().isUnauthorized());

        var saved = userRepository.findByEmail("new.teacher@edu360.local").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.isMustChangePassword()).isTrue();
    }
}
