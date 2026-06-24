package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.messaging.provider.model.dto.request.ProviderTestSendRequest;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.MessagingProviderResponse;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderAdminService;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.entity.User;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignProviderMcpTools implements McpToolGroup {

    private final MessagingProviderAdminService messagingProviderAdminService;
    private final Validator validator;

    @Tool(name = "list_campaign_providers",
            description = "List the configured SMS and WhatsApp campaign senders for a scope. Read-only; secrets are "
                    + "masked and never shown in full. Omit the organization to see the platform-default senders (root "
                    + "only); give an organization to see that organization's own senders. Resolve the organization "
                    + "from its name with search_organizations; never ask the user for a numeric id.")
    public List<MessagingProviderResponse> listCampaignProviders(
            @ToolParam(required = false, description = "The organization whose senders to list; omit for the platform defaults. Resolve it from the organization name with search_organizations") Long organizationId) {

        User currentUser = McpToolSupport.requireCurrentUser();
        log.info("MCP list_campaign_providers - org {}, by {}", organizationId, currentUser.getUsername());
        return messagingProviderAdminService.listCampaignProviders(organizationId, currentUser);
    }

    @Tool(name = "get_campaign_provider",
            description = "Show one channel's campaign sender — whether SMS or WhatsApp is set up and enabled for a "
                    + "scope, with its settings. Read-only; secrets are masked. Omit the organization for the platform "
                    + "default (root only); give an organization for its own sender. Resolve the organization from its "
                    + "name with search_organizations; never ask the user for a numeric id.")
    public MessagingProviderResponse getCampaignProvider(
            @ToolParam(description = "The channel to inspect: SMS or WHATSAPP") MessageChannel channel,
            @ToolParam(required = false, description = "The organization whose sender to show; omit for the platform default. Resolve it from the organization name with search_organizations") Long organizationId) {

        User currentUser = McpToolSupport.requireCurrentUser();
        log.info("MCP get_campaign_provider - channel {}, org {}, by {}", channel, organizationId, currentUser.getUsername());
        return messagingProviderAdminService.getCampaignProvider(channel, organizationId, currentUser);
    }

    @Tool(name = "test_campaign_provider",
            description = "Send one real test message through a configured campaign sender to verify it works. This "
                    + "sends an actual SMS or WhatsApp message and may incur a charge, so only call it after the user "
                    + "has confirmed the channel, scope and recipient phone number. It uses the stored credentials — "
                    + "never ask the user for an API key or password. Omit the organization for the platform default "
                    + "(root only); give an organization for its own sender. Resolve the organization from its name "
                    + "with search_organizations.")
    public MessagingProviderResponse testCampaignProvider(
            @ToolParam(description = "The channel to test: SMS or WHATSAPP") MessageChannel channel,
            @ToolParam(required = false, description = "The organization whose sender to test; omit for the platform default. Resolve it from the organization name with search_organizations") Long organizationId,
            @ToolParam(description = "The test message details, including the recipient phone number") ProviderTestSendRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP test_campaign_provider - channel {}, org {}, to '{}', by {}",
                channel, organizationId, request.getRecipientPhone(), currentUser.getUsername());
        return messagingProviderAdminService.testSendCampaignProvider(channel, organizationId, request, currentUser);
    }
}
