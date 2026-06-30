package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppTemplateResponse;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsTemplateService;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppTemplateService;
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
public class MessagingTemplateMcpTools implements McpToolGroup {

    private final SmsTemplateService smsTemplateService;
    private final WhatsAppTemplateService whatsAppTemplateService;
    private final Validator validator;

    @Tool(name = "search_sms_templates",
            description = "Search an event's SMS templates by name or template id. Read-only; returns matching "
                    + "templates with their details and ids. Leave the query empty to list every template of the event "
                    + "(there are only a few). Resolve the event from its name with search_events first; never ask the "
                    + "user for a numeric event id. Use this to turn a template name into the template id a campaign needs.")
    public List<SmsTemplateResponse> searchSmsTemplates(
            @ToolParam(description = "The id of the event whose templates to search. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(required = false, description = "Optional text to match against the template name or id; omit to list all") String query) {

        User currentUser = McpToolSupport.requireCurrentUser();
        String search = McpToolSupport.normalizeSearch(query);
        log.info("MCP search_sms_templates - event {}, query '{}', by {}", eventId, search, currentUser.getUsername());
        return smsTemplateService.getSmsTemplatesByEvent(eventId, search, currentUser);
    }

    @Tool(name = "create_sms_template",
            description = "Create a reusable SMS template for an event. This writes data. Runs as the signed-in "
                    + "user and is limited to events that user "
                    + "may manage. Resolve the event from its name with search_events; never ask the user for a numeric "
                    + "id. Returns the created template.")
    public SmsTemplateResponse createSmsTemplate(
            @ToolParam(description = "The id of the event the template belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The SMS template details") CreateSmsTemplateRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_sms_template - name '{}', event {}, by {}",
                request.getName(), eventId, currentUser.getUsername());
        return smsTemplateService.createSmsTemplate(eventId, request, currentUser);
    }

    @Tool(name = "search_whatsapp_templates",
            description = "Search an event's WhatsApp templates by name or Content SID. Read-only; returns matching "
                    + "templates with their details and ids. Leave the query empty to list every template of the event "
                    + "(at most twenty). Resolve the event from its name with search_events first; never ask the user "
                    + "for a numeric event id. Use this to turn a template name into the template id a campaign needs.")
    public List<WhatsAppTemplateResponse> searchWhatsAppTemplates(
            @ToolParam(description = "The id of the event whose templates to search. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(required = false, description = "Optional text to match against the template name or Content SID; omit to list all") String query) {

        User currentUser = McpToolSupport.requireCurrentUser();
        String search = McpToolSupport.normalizeSearch(query);
        log.info("MCP search_whatsapp_templates - event {}, query '{}', by {}", eventId, search, currentUser.getUsername());
        return whatsAppTemplateService.getTemplatesByEvent(eventId, search, currentUser);
    }

    @Tool(name = "create_whatsapp_template",
            description = "Register an approved Twilio WhatsApp Content Template for an event. This writes data. "
                    + "The Content SID must already be approved "
                    + "on Twilio. Runs as the signed-in user and is limited to events that user may manage. Resolve the "
                    + "event from its name with search_events; never ask the user for a numeric id. Returns the created template.")
    public WhatsAppTemplateResponse createWhatsAppTemplate(
            @ToolParam(description = "The id of the event the template belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The WhatsApp template details") CreateWhatsAppTemplateRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_whatsapp_template - name '{}', event {}, by {}",
                request.getName(), eventId, currentUser.getUsername());
        return whatsAppTemplateService.createTemplate(eventId, request, currentUser);
    }
}
