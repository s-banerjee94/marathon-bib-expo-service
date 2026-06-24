package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
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

    private static final int SEARCH_LIMIT = 10;

    private final EventService eventService;
    private final Validator validator;

    @Tool(name = "search_events",
            description = "Search events by name (also matches description and venue) and/or by status. Read-only; "
                    + "returns up to a few matching events with brief details and their ids. Give a name, a status, or "
                    + "both — for example list every DRAFT event to find the only one in draft, without needing its "
                    + "name. Use this to turn an event the user mentioned into the event id that other tools need — "
                    + "never ask the user for a numeric event id. Scoped to events the signed-in user may see.")
    public List<EventResponse> searchEvents(
            @ToolParam(required = false, description = "Text to match against the event name, description or venue; omit it to search by status alone") String query,
            @ToolParam(required = false, description = "Optional status filter: DRAFT, PUBLISHED, CANCELLED or COMPLETED") EventStatus status) {

        User currentUser = McpToolSupport.requireCurrentUser();

        String search = (query == null || query.isBlank()) ? null : query.trim();
        if (search == null && status == null) {
            throw new IllegalArgumentException("Please provide an event name or a status to search for.");
        }

        Pageable pageable = PageRequest.of(0, SEARCH_LIMIT);
        log.info("MCP search_events - query '{}', status {}, by {}", search, status, currentUser.getUsername());

        Page<EventResponse> page = switch (currentUser.getRole()) {
            case ROOT, ADMIN -> eventService.getAllEvents(null, status, search, pageable, currentUser);
            case ORGANIZER_ADMIN, ORGANIZER_USER -> eventService.getOrganizationEvents(status, search, pageable, currentUser);
            default -> Page.<EventResponse>empty();
        };

        return McpToolSupport.capOrNarrow(page, "events");
    }

    @Tool(name = "create_event",
            description = "Create a new event under an organization. This writes data, so only call it after the "
                    + "user has confirmed the details. Runs as the signed-in user: ROOT and ADMIN can create events "
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
}
