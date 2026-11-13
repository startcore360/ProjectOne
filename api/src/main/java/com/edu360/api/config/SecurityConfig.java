package com.edu360.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
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
    private final TrialReadOnlyFilter trialReadOnlyFilter;
    private final Environment environment;

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    // Dev-only: lets several local tenant hostnames (abc.edu360.local, etc.) share one CORS
    // rule instead of needing one exact origin each. Blank in prod (see application.properties),
    // so production can only ever use the single exact origin above - never a pattern.
    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    @Bean
    @Order(1)
    public SecurityFilterChain identityEventsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/internal/identity-events/**",
                        "/v1/internal/identity-events/**",
                        "/api/auth/internal/identity-events/**",
                        "/v1/auth/internal/identity-events/**",
                        "/api/v1/auth/internal/identity-events/**"
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isDevOrTest = environment != null && (environment.matchesProfiles("dev") || environment.matchesProfiles("test"));

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Bearer tokens travel in an explicit Authorization header the browser never
                // attaches automatically, unlike a cookie - so there is no cross-site submission
                // for CSRF to exploit here.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // BS-27: Both /api/auth/** and /v1/auth/** permitted consistently, plus identity-events webhooks (M-03)
                    auth.requestMatchers("/api/auth/**", "/v1/auth/**", "/internal/identity-events", "/v1/internal/identity-events", "/v1/ws", "/api/ws").permitAll();
                    // BS-25: Swagger and H2 console only open in dev or test profiles
                    if (isDevOrTest) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .headers(headers -> {
                    if (isDevOrTest) {
                        headers.frameOptions(fo -> fo.sameOrigin());
                    } else {
                        headers.frameOptions(fo -> fo.deny());
                    }
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(
                                new JwtIssuerAuthenticationManagerResolver(tenantIssuerAuthenticationManagerResolver)))
                .addFilterAfter(mustChangePasswordFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(trialReadOnlyFilter, MustChangePasswordFilter.class);
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
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Requested-With",
                "X-Correlation-ID", "x-correlation-id", "If-Match", "If-None-Match", "serviceAuth",
                // SES-01: random per-browser id sent on sign-in (see AuthService.BROWSER_ID_HEADER).
                "X-Browser-Id"
        ));
        config.setExposedHeaders(List.of("X-Correlation-ID", "ETag", "Location"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
