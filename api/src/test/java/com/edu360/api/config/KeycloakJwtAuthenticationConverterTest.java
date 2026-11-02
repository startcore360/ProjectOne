package com.edu360.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class KeycloakJwtAuthenticationConverterTest {

    @Mock
    private UserRepository userRepository;

    private Jwt jwtFor(String subject, List<String> realmRoles) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", realmRoles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void convert_resolvesLocalUserAndMapsOnlyKnownRealmRoles() {
        User user = User.builder().id(1L).name("Jane").email("jane@edu360.local")
                .keycloakId("kc-sub-1").role(Role.TEACHER).build();
        when(userRepository.findByKeycloakId("kc-sub-1")).thenReturn(Optional.of(user));

        var converter = new KeycloakJwtAuthenticationConverter(userRepository);
        AbstractAuthenticationToken authentication = converter.convert(
                jwtFor("kc-sub-1", List.of("TEACHER", "offline_access")));

        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_TEACHER");
    }

    @Test
    void convert_noLocalAccountForSubject_throwsInvalidBearerToken() {
        when(userRepository.findByKeycloakId("kc-sub-unknown")).thenReturn(Optional.empty());

        var converter = new KeycloakJwtAuthenticationConverter(userRepository);

        assertThrows(InvalidBearerTokenException.class,
                () -> converter.convert(jwtFor("kc-sub-unknown", List.of("TEACHER"))));
    }
}
