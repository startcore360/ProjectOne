package com.edu360.api.tenant;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers TenantContextClearingFilter as the very first/outermost filter in the whole
 *  servlet chain (HIGHEST_PRECEDENCE), so it wraps Spring Security's own filter chain too -
 *  not just this app's own request handling. */
@Configuration
public class TenantContextFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantContextClearingFilter> tenantContextClearingFilter() {
        FilterRegistrationBean<TenantContextClearingFilter> registration = new FilterRegistrationBean<>(
                new TenantContextClearingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
