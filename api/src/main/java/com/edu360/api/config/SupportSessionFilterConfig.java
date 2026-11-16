package com.edu360.api.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.support.SupportAccessGrantRepository;
import com.edu360.api.platformops.support.SupportSessionAuthenticationFilter;
import com.edu360.api.platformops.support.SupportSessionRepository;
import com.edu360.api.platformops.support.SupportSessionTokenService;
import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/** Creates security-chain filters and explicitly disables their second, plain-servlet registration. */
@Configuration
@RequiredArgsConstructor
public class SupportSessionFilterConfig {
    private final SupportSessionTokenService tokenService;
    private final SupportSessionRepository sessionRepository;
    private final SupportAccessGrantRepository grantRepository;
    private final TenantRepository tenantRepository;
    private final LoginTenantResolver loginTenantResolver;
    private final PlatformAuditWriter platformAuditWriter;
    private final JdbcTemplate jdbcTemplate;

    @Bean
    public SupportSessionAuthenticationFilter supportSessionAuthenticationFilter() {
        return new SupportSessionAuthenticationFilter(tokenService, sessionRepository, grantRepository,
                tenantRepository, jdbcTemplate, platformAuditWriter);
    }

    @Bean
    public FilterRegistrationBean<SupportSessionAuthenticationFilter> supportSessionServletRegistration(
            SupportSessionAuthenticationFilter filter) {
        FilterRegistrationBean<SupportSessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public TokenHostMatchingFilter tokenHostMatchingFilter() {
        return new TokenHostMatchingFilter(loginTenantResolver);
    }

    @Bean
    public FilterRegistrationBean<TokenHostMatchingFilter> tokenHostServletRegistration(TokenHostMatchingFilter filter) {
        FilterRegistrationBean<TokenHostMatchingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
