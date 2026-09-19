package com.foodrisk.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger documentation configuration.
 *
 * Configures API title, version, description, and JWT Bearer authentication scheme.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Food Risk Analysis API",
                version = "v1.0",
                description = "REST API documentation for the Food Risk Analysis Progressive Web App (PWA). Includes authentication, health, and user endpoints.",
                contact = @Contact(name = "Food Risk Analysis Team")
        ),
        security = @SecurityRequirement(name = "BearerAuth")
)
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT Bearer token. Enter the accessToken from /api/auth/login"
)
public class OpenApiConfig {
}
