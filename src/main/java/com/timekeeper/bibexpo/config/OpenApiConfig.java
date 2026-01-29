package com.timekeeper.bibexpo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marathon Bib Expo Service API")
                        .version("1.0.0")
                        .description("BIB Expo Management Service - Handles user authentication, authorization, and user lifecycle management for organizers, organizer users, and distributors")
                        .contact(new Contact()
                                .name("Bibliographic Expo Team")
                                .email("support@bibexpo.com")))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Local Development Server"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token (without 'Bearer' prefix)")));
    }

    @Bean
    public GroupedOpenApi authenticationGroup() {
        return GroupedOpenApi.builder()
                .group("1-authentication")
                .displayName("Authentication & User Management")
                .pathsToMatch("/api/auth/**", "/api/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi organizationGroup() {
        return GroupedOpenApi.builder()
                .group("2-organizations")
                .displayName("Organization Management")
                .pathsToMatch("/api/organizations/**")
                .build();
    }

    @Bean
    public GroupedOpenApi eventManagementGroup() {
        return GroupedOpenApi.builder()
                .group("3-event-management")
                .displayName("Event, Race & Category Management")
                .pathsToMatch("/api/events/**", "/api/races/**", "/api/categories/**")
                .pathsToExclude("/api/events/{eventId}/distribution/**", "/api/events/{eventId}/participants/**")
                .build();
    }

    @Bean
    public GroupedOpenApi participantGroup() {
        return GroupedOpenApi.builder()
                .group("4-participants")
                .displayName("Participant Management")
                .pathsToMatch("/api/events/{eventId}/participants/**")
                .build();
    }

    @Bean
    public GroupedOpenApi distributionGroup() {
        return GroupedOpenApi.builder()
                .group("5-distribution")
                .displayName("Bib & Goodies Distribution")
                .pathsToMatch("/api/events/{eventId}/distribution/**")
                .build();
    }

    @Bean
    public GroupedOpenApi communicationGroup() {
        return GroupedOpenApi.builder()
                .group("6-communication")
                .displayName("Communication & Templates")
                .pathsToMatch("/api/sms-templates/**")
                .build();
    }

    @Bean
    public GroupedOpenApi allApisGroup() {
        return GroupedOpenApi.builder()
                .group("0-all-apis")
                .displayName("All APIs")
                .pathsToMatch("/api/**")
                .build();
    }
}
