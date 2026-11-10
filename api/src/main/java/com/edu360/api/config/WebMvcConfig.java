package com.edu360.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.edu360.api.selfservice.SessionActivityInterceptor;

import lombok.RequiredArgsConstructor;

/** Registers MVC interceptors. Today only SES-01 session activity ("Active now" in My sessions). */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessionActivityInterceptor sessionActivityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Every API route; sign-in and other unauthenticated calls carry no JWT and are skipped.
        registry.addInterceptor(sessionActivityInterceptor).addPathPatterns("/api/**", "/v1/**");
    }
}
