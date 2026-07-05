package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.EventService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventMcpTools implements McpToolGroup {

    private static final int SEARCH_LIMIT = 20;

    private final EventService eventService;
    private final Validator validator;

    @Tool(name = "search_events",
            description = "List or search events the signed-in user may see. Read-only; returns events with brief "
                    + "details and their ids. Optionally filter by name (also matches description and venue) and/or by "
                    + "status — for example list every DRAFT event — or omit both to list all events in scope. Use this "
                    + "to turn an event the user mentioned into the event id that other tools need; never ask the user "
                    + "for a numeric event id.")
    public List<EventResponse> searchEvents(
            @ToolParam(required = false, description = "Optional text to match against the event name, description or venue; omit to list all") String query,
            @ToolParam(required = false, description = "Optional status filter: DRAFT, PUBLISHED, CANCELLED or COMPLETED") EventStatus status) {

        User currentUser = McpToolSupport.requireCurrentUser();

        String search = McpToolSupport.normalizeSearch(query);
        Pageable pageable = PageRequest.of(0, SEARCH_LIMIT);
        log.info("MCP search_events - query '{}', status {}, by {}", search, status, currentUser.getUsername());

        Page<EventResponse> page = switch (currentUser.getRole()) {
            case ROOT, ADMIN -> eventService.getAllEvents(null, status, search, pageable, currentUser);
            case ORGANIZER_ADMIN, ORGANIZER_USER -> eventService.getOrganizationEvents(status, search, pageable, currentUser);
            default -> Page.<EventResponse>empty();
        };

        return page.getContent();
    }

    @Tool(name = "create_event",
            description = "Create a new event under an organization. This writes data. Runs as the signed-in "
                    + "user: ROOT and ADMIN can create events "
                    + "for any organization, organizer admins and users only for their own. The event starts in DRAFT. "
                    + "Resolve the organization from its name with search_organizations; never ask the user for a "
                    + "numeric id. Dates are yyyy-MM-dd and times are 24-hour HH:mm in the event timezone. "
                    + "Returns the created event.")
    public EventResponse createEvent(
            @ToolParam(description = "The event details, including the owning organization id") CreateEventRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_event - name '{}', org {}, by {}",
                request.getEventName(), request.getOrganizationId(), currentUser.getUsername());
        return eventService.createEvent(request, currentUser);
    }

    @Tool(name = "update_event",
            description = "Update an existing event. This writes data. Runs as the signed-in user and is limited to "
                    + "events that user may manage. Only the fields you provide are changed; omit the rest. Resolve the "
                    + "event from its name with search_events; never ask the user for a numeric id. Dates are yyyy-MM-dd "
                    + "and times are 24-hour HH:mm in the event timezone. Returns the updated event.")
    public EventResponse updateEvent(
            @ToolParam(description = "The id of the event to update. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The fields to change; unspecified fields are left as they are") UpdateEventRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP update_event - event {}, by {}", eventId, currentUser.getUsername());
        return eventService.updateEvent(eventId, request, currentUser);
    }
}
