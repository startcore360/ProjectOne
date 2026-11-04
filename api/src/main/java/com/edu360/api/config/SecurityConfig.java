package com.edu360.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

/**
 * The master security rulebook for the backend: which web addresses need
 * to be logged in, which roles can do what, and which frontend origin is
 * allowed to call this API (CORS). Authentication itself is delegated to
 * Keycloak: this app is an OAuth2 resource server only — it validates the
 * bearer token Keycloak issued and never sees a password.
 *
 * WHY authenticationManagerResolver(...) instead of a single static jwt(...) decoder (the
 * pre-migration design): this app now has multiple realms (one per tenant, plus the platform
 * realm) with no fixed issuer. TenantIssuerAuthenticationManagerResolver picks the right
 * validator per request based on the token's own issuer - see that class's javadoc for exactly
 * how an unverified issuer claim is used only to pick a validator, never to establish trust by
 * itself. JwtIssuerAuthenticationManagerResolver here is Spring's own wrapper that adapts our
 * AuthenticationManagerResolver<String> (keyed by issuer) into the
 * AuthenticationManagerResolver<HttpServletRequest> the resource server DSL expects.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final TenantIssuerAuthenticationManagerResolver tenantIssuerAuthenticationManagerResolver;
    private final MustChangePasswordFilter mustChangePasswordFilter;

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    // Dev-only: lets several local tenant hostnames (abc.edu360.local, etc.) share one CORS
    // rule instead of needing one exact origin each. Blank in prod (see application.properties),
    // so production can only ever use the single exact origin above - never a pattern.
    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Bearer tokens travel in an explicit Authorization header the browser never
                // attaches automatically, unlike a cookie - so there is no cross-site submission
                // for CSRF to exploit here.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // login/refresh/change-password/me/logout are all handled inside
                        // AuthController itself - there is no self-service sign-up, and the
                        // realm/tenant for each is resolved from the Host header or the already-
                        // verified principal (see LoginTenantResolver), never a request field.
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(fo -> fo.disable()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(
                                new JwtIssuerAuthenticationManagerResolver(tenantIssuerAuthenticationManagerResolver)))
                .addFilterAfter(mustChangePasswordFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns (e.g. http://*.edu360.local:5173) only ever come from the dev profile's own
        // property - if that property is blank (every other profile, including prod), this
        // falls through to the single exact origin exactly as before. A pattern can never reach
        // production by accident; it has to be deliberately configured in that profile's own
        // properties file.
        if (!allowedOriginPatterns.isBlank()) {
            config.setAllowedOriginPatterns(List.of(allowedOriginPatterns.split(",")));
        } else {
            config.setAllowedOrigins(List.of(allowedOrigin));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
