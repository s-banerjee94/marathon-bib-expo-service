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
                        .url("/")
                        .description("Current Host"))
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
                .pathsToExclude("/api/organizations/{organizationId}/billing/**")
                .build();
    }

    @Bean
    public GroupedOpenApi eventManagementGroup() {
        return GroupedOpenApi.builder()
                .group("3-event-management")
                .displayName("Event, Race & Category Management")
                .pathsToMatch("/api/events/**", "/api/races/**", "/api/categories/**")
                .pathsToExclude(
                        "/api/events/{eventId}/distribution/**",
                        "/api/events/{eventId}/participants/**",
                        "/api/events/{eventId}/sms-templates/**",
                        "/api/events/{eventId}/sms-campaigns/**",
                        "/api/events/{eventId}/billing/**"
                )
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
    public GroupedOpenApi billingGroup() {
        return GroupedOpenApi.builder()
                .group("6-billing")
                .displayName("Event Billing")
                .pathsToMatch(
                        "/api/events/{eventId}/billing/**",
                        "/api/organizations/{organizationId}/billing/**",
                        "/api/billing/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi smsCampaignsGroup() {
        return GroupedOpenApi.builder()
                .group("6-sms")
                .displayName("SMS Campaigns & Templates")
                .pathsToMatch("/api/events/{eventId}/sms-campaigns/**", "/api/events/{eventId}/sms-templates/**")
                .build();
    }

    @Bean
    public GroupedOpenApi statisticsGroup() {
        return GroupedOpenApi.builder()
                .group("7-statistics")
                .displayName("App Statistics")
                .pathsToMatch("/api/statistics/**")
                .build();
    }

    @Bean
    public GroupedOpenApi dashboardGroup() {
        return GroupedOpenApi.builder()
                .group("8-dashboard")
                .displayName("Dashboard")
                .pathsToMatch("/api/dashboard/**")
                .build();
    }

    @Bean
    public GroupedOpenApi auditLogGroup() {
        return GroupedOpenApi.builder()
                .group("8-audit-logs")
                .displayName("Audit Logs")
                .pathsToMatch("/api/audit-logs/**")
                .build();
    }

    @Bean
    public GroupedOpenApi notificationsGroup() {
        return GroupedOpenApi.builder()
                .group("8-notifications")
                .displayName("Notifications")
                .pathsToMatch("/api/notifications/**")
                .build();
    }

    @Bean
    public GroupedOpenApi devOpsGroup() {
        return GroupedOpenApi.builder()
                .group("9-dev-operations")
                .displayName("Dev Operations (Dev Profile Only)")
                .pathsToMatch("/api/dev/**")
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
