package com.timekeeper.bibexpo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI setup: the base document (info + JWT scheme) and the grouped views shown in the
 * Swagger UI dropdown. Each group's numeric id prefix sets its order in that dropdown.
 *
 * <p>Path patterns that one group owns but a broader group must exclude (so an endpoint appears in
 * exactly one place) are declared once as constants below and referenced from both sides.
 */
@Configuration
public class OpenApiConfig {

    // Endpoints owned by a dedicated group, excluded from the broader Event / Organization groups.
    private static final String DISTRIBUTION = "/api/events/{eventId}/distribution/**";
    private static final String PARTICIPANTS = "/api/events/{eventId}/participants/**";
    private static final String SMS_CAMPAIGNS = "/api/events/{eventId}/sms-campaigns/**";
    private static final String SMS_TEMPLATES = "/api/events/{eventId}/sms-templates/**";
    private static final String WHATSAPP_CAMPAIGNS = "/api/events/{eventId}/whatsapp-campaigns/**";
    private static final String WHATSAPP_TEMPLATES = "/api/events/{eventId}/whatsapp-templates/**";
    private static final String EVENT_BILLING = "/api/events/{eventId}/billing/**";
    private static final String ORG_BILLING = "/api/organizations/{organizationId}/billing/**";
    private static final String ORG_CAMPAIGN_PROVIDERS = "/api/organizations/{organizationId}/campaign-providers/**";
    private static final String SYSTEM_CAMPAIGN_PROVIDERS = "/api/system/campaign-providers/**";

    // ---- Base document ----

    @Bean
    public OpenAPI openAPI() {
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
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token (without 'Bearer' prefix)")));
    }

    // ---- Grouped views (ordered by the numeric id prefix) ----

    @Bean
    public GroupedOpenApi allApisDoc() {
        return group("00-all", "All APIs", "/api/**");
    }

    @Bean
    public GroupedOpenApi authenticationDoc() {
        return group("01-authentication", "Authentication & User Management",
                match("/api/auth/**", "/api/users/**"),
                exclude("/api/users/invitations/**", "/api/auth/invitations/**"));
    }

    @Bean
    public GroupedOpenApi inviteUserCreationDoc() {
        return group("02-invite-user-creation", "Invite User Creation",
                "/api/users/invitations/**", "/api/auth/invitations/**");
    }

    @Bean
    public GroupedOpenApi organizationDoc() {
        return group("03-organizations", "Organization Management",
                match("/api/organizations/**"),
                exclude(ORG_BILLING, ORG_CAMPAIGN_PROVIDERS));
    }

    @Bean
    public GroupedOpenApi eventManagementDoc() {
        return group("04-event-management", "Event, Race & Category Management",
                match("/api/events/**", "/api/races/**", "/api/categories/**"),
                exclude(DISTRIBUTION, PARTICIPANTS, SMS_TEMPLATES, SMS_CAMPAIGNS,
                        EVENT_BILLING, WHATSAPP_TEMPLATES, WHATSAPP_CAMPAIGNS));
    }

    @Bean
    public GroupedOpenApi participantsDoc() {
        return group("05-participants", "Participant Management", PARTICIPANTS);
    }

    @Bean
    public GroupedOpenApi distributionDoc() {
        return group("06-distribution", "Bib & Goodies Distribution", DISTRIBUTION);
    }

    @Bean
    public GroupedOpenApi billingDoc() {
        return group("07-billing", "Event Billing", EVENT_BILLING, ORG_BILLING, "/api/billing/**");
    }

    @Bean
    public GroupedOpenApi smsCampaignsDoc() {
        return group("08-sms", "SMS Campaigns & Templates", SMS_CAMPAIGNS, SMS_TEMPLATES);
    }

    @Bean
    public GroupedOpenApi whatsAppCampaignsDoc() {
        return group("09-whatsapp", "WhatsApp Campaigns & Templates", WHATSAPP_CAMPAIGNS, WHATSAPP_TEMPLATES);
    }

    @Bean
    public GroupedOpenApi campaignProvidersDoc() {
        return group("10-campaign-providers", "Campaign Providers (SMS/WhatsApp senders)",
                SYSTEM_CAMPAIGN_PROVIDERS, ORG_CAMPAIGN_PROVIDERS);
    }

    @Bean
    public GroupedOpenApi statisticsDoc() {
        return group("11-statistics", "App Statistics", "/api/statistics/**");
    }

    @Bean
    public GroupedOpenApi dashboardDoc() {
        return group("12-dashboard", "Dashboard", "/api/dashboard/**", "/api/events/*/dashboard");
    }

    @Bean
    public GroupedOpenApi auditLogsDoc() {
        return group("13-audit-logs", "Audit Logs", "/api/audit-logs/**");
    }

    @Bean
    public GroupedOpenApi notificationsDoc() {
        return group("14-notifications", "Notifications", "/api/notifications/**");
    }

    @Bean
    public GroupedOpenApi systemMessagingDoc() {
        return group("15-system-messaging", "System Messaging (Root)",
                "/api/system/messaging-providers/**", "/api/system/message-templates/**");
    }

    @Bean
    public GroupedOpenApi devOperationsDoc() {
        return group("16-dev-operations", "Dev Operations (Dev Profile Only)", "/api/dev/**");
    }

    @Bean
    public GroupedOpenApi aiAssistantDoc() {
        return group("17-ai-assistant", "AI Assistant", "/api/ai/**");
    }

    @Bean
    public GroupedOpenApi aiAgentDoc() {
        return group("18-ai-agent", "AI Agent (Python service)", "/api/agent/**");
    }

    // ---- helpers ----

    private static GroupedOpenApi group(String id, String displayName, String... pathsToMatch) {
        return GroupedOpenApi.builder()
                .group(id)
                .displayName(displayName)
                .pathsToMatch(pathsToMatch)
                .build();
    }

    private static GroupedOpenApi group(String id, String displayName, String[] pathsToMatch, String[] pathsToExclude) {
        return GroupedOpenApi.builder()
                .group(id)
                .displayName(displayName)
                .pathsToMatch(pathsToMatch)
                .pathsToExclude(pathsToExclude)
                .build();
    }

    /** Readability wrappers so a match+exclude group reads clearly. */
    private static String[] match(String... paths) {
        return paths;
    }

    private static String[] exclude(String... paths) {
        return paths;
    }
}
