package com.edu360.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class RegM01Test {

    @Test
    @DisplayName("REG-M01-1")
    void REG_M01_1() {
        assertThatThrownBy(() -> {
            new SpringApplicationBuilder(ApiApplication.class)
                    .web(WebApplicationType.NONE)
                    .run("--spring.profiles.active=");
        })
        .satisfies(throwable -> {
            assertThat(throwable.getMessage()).contains("Set SPRING_PROFILES_ACTIVE");
        });
    }
}
