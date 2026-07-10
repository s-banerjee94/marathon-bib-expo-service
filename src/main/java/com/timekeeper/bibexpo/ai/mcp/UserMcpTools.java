package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.invitation.model.dto.request.CreateInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationLinkResponse;
import com.timekeeper.bibexpo.invitation.service.InvitationService;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.security.CurrentActor;
import com.timekeeper.bibexpo.service.UserService;
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
public class UserMcpTools implements McpToolGroup {

    private static final int SEARCH_LIMIT = 20;

    private final UserService userService;
    private final InvitationService invitationService;
    private final Validator validator;

    @Tool(name = "search_users",
            description = "List or search users by a text query matched against username, email and full name. "
                    + "Read-only; returns users with brief details only. Omit the query to list all users in scope. "
                    + "Use get_user for one user's full details. ROOT and ADMIN see all users; organizers see only "
                    + "their own organization's users.")
    public List<UserResponse> searchUsers(
            @ToolParam(required = false, description = "Optional text to match against username, email or full name; omit to list all") String query,
            @ToolParam(required = false, description = "Optional role filter: ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER or DISTRIBUTOR") UserRole role,
            @ToolParam(required = false, description = "Optional organization id to scope the search (honoured for ROOT and ADMIN)") Long organizationId,
            @ToolParam(required = false, description = "Optional event id; matches distributors assigned to that event") Long eventId) {

        User currentUser = McpToolSupport.requireCurrentUser();

        String search = McpToolSupport.normalizeSearch(query);
        Pageable pageable = PageRequest.of(0, SEARCH_LIMIT);
        log.info("MCP search_users - query '{}', role {}, by {}", search, role, currentUser.getUsername());

        return userService.getUsers(role, organizationId, eventId, null, search, pageable, currentUser.getUsername())
                .getContent();
    }

    @Tool(name = "get_user",
            description = "Get the full details of a single user, looked up by username (preferred) or numeric id. "
                    + "Provide at least one; username is the better way to identify a user. Read-only. Returns the "
                    + "user only if the signed-in user is allowed to see them.")
    public UserResponse getUser(
            @ToolParam(required = false, description = "The username; the preferred way to identify a user") String username,
            @ToolParam(required = false, description = "The numeric user id; an alternative when the username is not known") Long userId) {

        User currentUser = McpToolSupport.requireCurrentUser();

        if (username != null && !username.isBlank()) {
            log.info("MCP get_user - username '{}', by {}", username, currentUser.getUsername());
            return userService.getUserByUsername(username.trim(), currentUser.getUsername());
        }
        if (userId != null) {
            log.info("MCP get_user - id {}, by {}", userId, currentUser.getUsername());
            return userService.getUserById(userId, currentUser.getUsername());
        }
        throw new InvalidUserDataException("Please provide a username or a user id to look up.");
    }

    @Tool(name = "get_my_profile",
            description = "Get the signed-in user's own profile: username, full name, email, role and organization. "
                    + "Read-only.")
    public UserResponse getMyProfile() {
        User currentUser = McpToolSupport.requireCurrentUser();
        log.info("MCP get_my_profile - by {}", currentUser.getUsername());
        return userService.getCurrentUser(currentUser.getUsername());
    }

    @Tool(name = "create_user",
            description = "Create a new user account (ADMIN, ORGANIZER_ADMIN, ORGANIZER_USER or DISTRIBUTOR). "
                    + "This writes data. Runs as the signed-in user and is limited to the roles and organizations "
                    + "that user is allowed to create; "
                    + "limits and uniqueness are enforced. Resolve any organization or event from its name with "
                    + "search_organizations / search_events; never ask the user for a numeric id. Returns the created user.")
    public UserResponse createUser(
            @ToolParam(description = "The new user's details") CreateUserRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP create_user - username '{}', role {}, by {}",
                request.getUsername(), request.getRole(), currentUser.getUsername());
        return userService.createUser(request, currentUser.getUsername());
    }

    @Tool(name = "invite_user",
            description = "Invite a new user by issuing a one-time invite link, optionally delivered by WhatsApp or "
                    + "SMS. Preferred over create_user because the invitee sets their own password. This writes data. "
                    + "Runs as the signed-in user and is "
                    + "limited to the roles and organizations that user is allowed to create. Resolve any organization "
                    + "or event from its name with search_organizations / search_events; never ask the user for a "
                    + "numeric id. Returns the invite link.")
    public InvitationLinkResponse inviteUser(
            @ToolParam(description = "The invitation details: role, organization/event and optional delivery channels") CreateInvitationRequest request) {

        User currentUser = McpToolSupport.requireCurrentUser();
        McpToolSupport.validate(validator, request);

        log.info("MCP invite_user - role {}, channels {}, by {}",
                request.getRole(), request.getDeliveryChannels(), currentUser.getUsername());
        return invitationService.createInvitation(request, CurrentActor.from(currentUser));
    }

    @Tool(name = "reassign_distributor_event",
            description = "Reassign a distributor to a different event within their own organization. This writes "
                    + "data. Identify the distributor by username "
                    + "(preferred) or numeric id. The event must belong to the distributor's organization and must "
                    + "not have ended. Returns the updated user.")
    public UserResponse reassignDistributorEvent(
            @ToolParam(required = false, description = "The distributor's username; the preferred way to identify them") String username,
            @ToolParam(required = false, description = "The distributor's numeric user id; an alternative to username") Long userId,
            @ToolParam(description = "The id of the event to assign the distributor to. Resolve it from the event name with search_events; do not ask the user for a numeric id") Long eventId) {

        User currentUser = McpToolSupport.requireCurrentUser();
        Long targetId = resolveUserId(username, userId, currentUser);

        log.info("MCP reassign_distributor_event - user {}, event {}, by {}", targetId, eventId, currentUser.getUsername());
        return userService.reassignDistributorEvent(targetId, eventId, currentUser.getUsername());
    }

    private Long resolveUserId(String username, Long userId, User currentUser) {
        if (username != null && !username.isBlank()) {
            return userService.getUserByUsername(username.trim(), currentUser.getUsername()).getId();
        }
        if (userId != null) {
            return userId;
        }
        throw new InvalidUserDataException("Please provide a username or a user id to identify the distributor.");
    }
}
