package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.response.ParticipantListResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.SearchType;
import com.timekeeper.bibexpo.service.ParticipantService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParticipantMcpTools implements McpToolGroup {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final ParticipantService participantService;
    private final Validator validator;

    @Tool(name = "search_participants",
            description = "Search participants of a marathon event. Match by NAME, EMAIL, PHONE, BIB, "
                    + "RACE or CATEGORY using a prefix of the value. Read-only; returns matching participants. "
                    + "Only events the signed-in user may access are searchable.")
    public ParticipantListResponse searchParticipants(
            @ToolParam(description = "The numeric event ID to search within") Long eventId,
            @ToolParam(description = "Which field to match on: NAME, EMAIL, PHONE, BIB, RACE or CATEGORY") SearchType searchType,
            @ToolParam(description = "The value to search for; matched as a prefix") String searchValue,
            @ToolParam(required = false, description = "Maximum results to return (default 50, max 100)") Integer limit) {

        User currentUser = McpToolSupport.requireCurrentUser();
        int effectiveLimit = clampLimit(limit);

        log.info("MCP search_participants - event {}, type {}, value '{}', limit {}, user {}",
                eventId, searchType, searchValue, effectiveLimit, currentUser.getUsername());

        return participantService.lookupParticipants(
                eventId, searchType, searchValue, effectiveLimit, null, currentUser);
    }

    @Tool(name = "create_participant",
            description = "Manually add one participant to an event. This writes data, so only call it after the "
                    + "user has confirmed the details. Runs as the signed-in user and is limited to events that user "
                    + "may manage; the bib number must be unique within the event. Resolve the event from its name with "
                    + "search_events, the race with list_event_races and the category with list_race_categories; never "
                    + "ask the user for a numeric id. A participant needs at least a phone or an email, and either a "
                    + "date of birth or an age. Returns the created participant.")
    public ParticipantResponse createParticipant(
            @ToolParam(description = "The id of the event the participant belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The participant details, including the race and category ids resolved with list_event_races and list_race_categories") CreateParticipantRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_participant - bib '{}', event {}, by {}",
                request.getBibNumber(), eventId, currentUser.getUsername());
        return participantService.createParticipant(eventId, request, currentUser);
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
