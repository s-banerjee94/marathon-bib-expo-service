package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateRaceRequest;
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

    @Tool(name = "update_race",
            description = "Update an existing race under an event. This writes data. Runs as the signed-in user and "
                    + "is limited to events that user may manage. Only the fields you provide are changed; omit the "
                    + "rest. Resolve the event and race from their names with search_events and list_event_races; never "
                    + "ask the user for a numeric id. Returns the updated race.")
    public RaceResponse updateRace(
            @ToolParam(description = "The id of the event the race belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The id of the race to update. Resolve it from the race name with list_event_races") Long raceId,
            @ToolParam(description = "The fields to change; unspecified fields are left as they are") UpdateRaceRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP update_race - event {}, race {}, by {}", eventId, raceId, currentUser.getUsername());
        return raceService.updateRace(eventId, raceId, request, currentUser);
    }
}
