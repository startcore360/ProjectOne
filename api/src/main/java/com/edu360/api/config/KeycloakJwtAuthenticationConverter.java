package com.edu360.api.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns a validated Keycloak access token into the Authentication the rest of the app expects:
 * principal = our local User row (OD-10: matched by the token's "sub", never by email), and
 * Spring Security authorities derived from Keycloak's realm roles (realm_access.roles) that
 * also exist in our own Role enum.
 */
@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = userRepository.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new InvalidBearerTokenException(
                        "No local account provisioned for this Keycloak user"));

        Collection<GrantedAuthority> authorities = extractRealmRoles(jwt).stream()
                .flatMap(roleName -> matchingRole(roleName).stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .map(GrantedAuthority.class::cast)
                .toList();

        return new UsernamePasswordAuthenticationToken(user, jwt, authorities);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream().map(Object::toString).toList();
    }

    private Optional<Role> matchingRole(String roleName) {
        try {
            return Optional.of(Role.valueOf(roleName));
        } catch (IllegalArgumentException notOurRole) {
            return Optional.empty();
        }
    }
}
