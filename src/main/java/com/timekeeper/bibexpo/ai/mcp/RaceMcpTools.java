package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.RaceService;
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
public class RaceMcpTools implements McpToolGroup {

    private final RaceService raceService;
    private final Validator validator;

    @Tool(name = "list_event_races",
            description = "List the races of one event (an event usually has only a few). Read-only; returns each "
                    + "race with brief details and its id. Resolve the event from its name with search_events first; "
                    + "never ask the user for a numeric event id. Scoped to events the signed-in user may see.")
    public List<RaceResponse> listEventRaces(
            @ToolParam(description = "The id of the event whose races to list. Resolve it from the event name with search_events") Long eventId) {

        User currentUser = McpToolSupport.requireCurrentUser();
        log.info("MCP list_event_races - event {}, by {}", eventId, currentUser.getUsername());
        return raceService.getRacesByEventId(eventId, currentUser);
    }

    @Tool(name = "create_race",
            description = "Create a new race under an event. This writes data. Runs as the signed-in user and is "
                    + "limited to events that user may manage. "
                    + "Resolve the event from its name with search_events; never ask the user for a numeric id. "
                    + "Returns the created race.")
    public RaceResponse createRace(
            @ToolParam(description = "The id of the event the race belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The race details") CreateRaceRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_race - name '{}', event {}, by {}",
                request.getRaceName(), eventId, currentUser.getUsername());
        return raceService.createRace(eventId, request, currentUser);
    }
}
