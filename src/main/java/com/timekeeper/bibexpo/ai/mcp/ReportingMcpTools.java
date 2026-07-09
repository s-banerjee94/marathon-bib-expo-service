package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.EventActivityRange;
import com.timekeeper.bibexpo.service.dashboard.EventDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportingMcpTools implements McpToolGroup {

    private final EventDashboardService eventDashboardService;

    @Tool(name = "get_event_dashboard",
            description = "Get the reporting dashboard for one event: total registered participants, how many bibs "
                    + "have been collected and how many are still pending (with percentages), the gender split, and "
                    + "per-race and per-category totals, plus bib-collection activity for the chosen window. Read-only "
                    + "and scoped to events the signed-in user may access. Resolve the event from its name with "
                    + "search_events; never ask the user for a numeric id. Use this to answer questions about one "
                    + "event's numbers, such as how many bibs have been collected or how today is going.")
    public EventDashboardResponse getEventDashboard(
            @ToolParam(description = "The id of the event to report on. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(required = false, description = "Activity window: TODAY for the current expo day, or FULL_EXPO for the whole event (the default)") EventActivityRange range) {

        User currentUser = McpToolSupport.requireCurrentUser();
        EventActivityRange window = range != null ? range : EventActivityRange.FULL_EXPO;

        log.info("MCP get_event_dashboard - event {}, range {}, by {}", eventId, window, currentUser.getUsername());
        return eventDashboardService.loadDashboard(eventId, window, currentUser);
    }
}
