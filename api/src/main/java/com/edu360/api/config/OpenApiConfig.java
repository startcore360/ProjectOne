package com.edu360.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI edu360OpenAPI() {
        final String securitySchemeName = "Bearer Auth";

        return new OpenAPI()
                .info(new Info()
                        .title("Edu360 API")
                        .description("Student Management System API — manage courses, attendance, scores, and performance tracking with role-based access control (TEACHER / STUDENT).")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Edu360")
                                .email("support@edu360.com")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token obtained from /api/auth/login")));
    }
}
