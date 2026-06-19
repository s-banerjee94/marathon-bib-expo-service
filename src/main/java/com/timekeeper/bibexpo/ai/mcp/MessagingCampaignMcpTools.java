package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppCampaignResponse;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsCampaignService;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignService;
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
public class MessagingCampaignMcpTools implements McpToolGroup {

    private final SmsCampaignService smsCampaignService;
    private final WhatsAppCampaignService whatsAppCampaignService;
    private final Validator validator;

    @Tool(name = "list_sms_campaigns",
            description = "List an event's SMS campaigns. Read-only; returns each campaign with its status, trigger "
                    + "and ids. Resolve the event from its name with search_events first; never ask the user for a "
                    + "numeric event id. Scoped to events the signed-in user may see.")
    public List<SmsCampaignResponse> listSmsCampaigns(
            @ToolParam(description = "The id of the event whose campaigns to list. Resolve it from the event name with search_events") Long eventId) {

        User currentUser = McpToolSupport.requireCurrentUser();
        log.info("MCP list_sms_campaigns - event {}, by {}", eventId, currentUser.getUsername());
        return smsCampaignService.getCampaignsByEvent(eventId, currentUser);
    }

    @Tool(name = "create_sms_campaign",
            description = "Create an SMS campaign for an event. This writes data, so only call it after the user has "
                    + "confirmed the details. Without a trigger type it is saved as a DRAFT; with one it is armed and "
                    + "starts sending. Resolve the event from its name with search_events and the SMS template from its "
                    + "name with search_sms_templates; never ask the user for a numeric id. Returns the created campaign.")
    public SmsCampaignResponse createSmsCampaign(
            @ToolParam(description = "The id of the event the campaign belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The campaign details, including the SMS template id resolved with search_sms_templates") CreateSmsCampaignRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_sms_campaign - name '{}', event {}, trigger {}, by {}",
                request.getName(), eventId, request.getTriggerType(), currentUser.getUsername());
        return smsCampaignService.createCampaign(eventId, request, currentUser);
    }

    @Tool(name = "list_whatsapp_campaigns",
            description = "List an event's WhatsApp campaigns. Read-only; returns each campaign with its status, "
                    + "trigger and ids. Resolve the event from its name with search_events first; never ask the user "
                    + "for a numeric event id. Scoped to events the signed-in user may see.")
    public List<WhatsAppCampaignResponse> listWhatsAppCampaigns(
            @ToolParam(description = "The id of the event whose campaigns to list. Resolve it from the event name with search_events") Long eventId) {

        User currentUser = McpToolSupport.requireCurrentUser();
        log.info("MCP list_whatsapp_campaigns - event {}, by {}", eventId, currentUser.getUsername());
        return whatsAppCampaignService.getCampaignsByEvent(eventId, currentUser);
    }

    @Tool(name = "create_whatsapp_campaign",
            description = "Create a WhatsApp campaign for an event. This writes data, so only call it after the user "
                    + "has confirmed the details. Without a trigger type it is saved as a DRAFT; with one it is armed "
                    + "and starts sending. Resolve the event from its name with search_events and the WhatsApp template "
                    + "from its name with search_whatsapp_templates; never ask the user for a numeric id. Returns the created campaign.")
    public WhatsAppCampaignResponse createWhatsAppCampaign(
            @ToolParam(description = "The id of the event the campaign belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The campaign details, including the WhatsApp template id resolved with search_whatsapp_templates") CreateWhatsAppCampaignRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_whatsapp_campaign - name '{}', event {}, trigger {}, by {}",
                request.getName(), eventId, request.getTriggerType(), currentUser.getUsername());
        return whatsAppCampaignService.createCampaign(eventId, request, currentUser);
    }
}
