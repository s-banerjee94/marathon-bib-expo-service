package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.service.OrganizationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationMcpTools implements McpToolGroup {

    private static final int SEARCH_LIMIT = 20;

    private final OrganizationService organizationService;
    private final Validator validator;

    @Tool(name = "search_organizations",
            description = "List or search organizations by name (also matches email and phone). Read-only; returns "
                    + "organizations with brief details and their ids. Omit the query to list all. Use this to turn an "
                    + "organization name the user mentioned into the organization id that other tools need — never ask "
                    + "the user for a numeric organization id. Only ROOT and ADMIN may use this; organization users "
                    + "should read their own organization from get_my_profile instead.")
    public List<OrganizationResponse> searchOrganizations(
            @ToolParam(required = false, description = "Optional text to match against the organization name, email or phone; omit to list all") String query) {

        User currentUser = McpToolSupport.requireCurrentUser();
        if (currentUser.getRole() != UserRole.ROOT && currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedAccessException("You are not allowed to view organizations.");
        }

        String search = McpToolSupport.normalizeSearch(query);
        log.info("MCP search_organizations - query '{}', by {}", search, currentUser.getUsername());

        return organizationService
                .getAllOrganizations(null, search, PageRequest.of(0, SEARCH_LIMIT), currentUser)
                .getContent();
    }

    @Tool(name = "create_organization",
            description = "Create a new organization. This writes data, so only call it after the user has confirmed "
                    + "the details. Only ROOT and ADMIN may create organizations. Returns the created organization.")
    public OrganizationResponse createOrganization(
            @ToolParam(description = "The organization details") CreateOrganizationRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        if (currentUser.getRole() != UserRole.ROOT && currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedAccessException("You are not allowed to create organizations.");
        }

        McpToolSupport.validate(validator, request);

        log.info("MCP create_organization - name '{}', by {}",
                request.getOrganizerName(), currentUser.getUsername());
        return organizationService.createOrganization(request);
    }
}
