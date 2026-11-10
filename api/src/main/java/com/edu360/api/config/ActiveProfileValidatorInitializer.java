package com.edu360.api.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActiveProfileValidatorInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String[] activeProfiles = applicationContext.getEnvironment().getActiveProfiles();
        boolean hasActiveProfile = false;
        if (activeProfiles != null) {
            for (String profile : activeProfiles) {
                if (profile != null && !profile.trim().isEmpty() && !"default".equalsIgnoreCase(profile.trim())) {
                    hasActiveProfile = true;
                    break;
                }
            }
        }
        if (!hasActiveProfile) {
            throw new IllegalStateException("Set SPRING_PROFILES_ACTIVE: No Spring profile active. Please configure SPRING_PROFILES_ACTIVE (e.g. dev, test, prod).");
        }
    }
}
