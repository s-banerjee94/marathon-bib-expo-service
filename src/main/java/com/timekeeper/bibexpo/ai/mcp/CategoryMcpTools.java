package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.entity.Gender;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.CategoryService;
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
public class CategoryMcpTools implements McpToolGroup {

    private final CategoryService categoryService;
    private final Validator validator;

    @Tool(name = "list_race_categories",
            description = "List the categories of one race. Read-only; returns each category with brief details and "
                    + "its id. Resolve the event and race from their names with search_events and list_event_races "
                    + "first; never ask the user for numeric ids. Scoped to events the signed-in user may see.")
    public List<CategoryResponse> listRaceCategories(
            @ToolParam(description = "The id of the event the race belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The id of the race whose categories to list. Resolve it from the race name with list_event_races") Long raceId,
            @ToolParam(required = false, description = "Optional gender filter: MALE, FEMALE, OTHER or OPEN") Gender gender) {

        User currentUser = McpToolSupport.requireCurrentUser();
        log.info("MCP list_race_categories - event {}, race {}, gender {}, by {}",
                eventId, raceId, gender, currentUser.getUsername());
        return categoryService.getCategoriesByRaceId(eventId, raceId, gender, currentUser);
    }

    @Tool(name = "create_category",
            description = "Create a new category under a race. This writes data. Runs as the signed-in user and "
                    + "is limited to events that user may manage. "
                    + "Resolve the event and race from their names with search_events and list_event_races; never ask "
                    + "the user for a numeric id. Returns the created category.")
    public CategoryResponse createCategory(
            @ToolParam(description = "The id of the event the race belongs to. Resolve it from the event name with search_events") Long eventId,
            @ToolParam(description = "The id of the race the category belongs to. Resolve it from the race name with list_event_races") Long raceId,
            @ToolParam(description = "The category details") CreateCategoryRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_category - name '{}', event {}, race {}, by {}",
                request.getCategoryName(), eventId, raceId, currentUser.getUsername());
        return categoryService.createCategory(eventId, raceId, request, currentUser);
    }
}
