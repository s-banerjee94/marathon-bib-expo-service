package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.entity.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared helpers for AI tools. Tools run on the authenticated request thread, so the signed-in
 * user is resolved from the security context and every downstream call enforces that user's
 * role-based access automatically.
 */
public final class McpToolSupport {

    private McpToolSupport() {
    }

    /**
     * Run bean validation on a request a tool assembled, mirroring the controller's {@code @Valid}
     * so a tool call is held to the same field rules as the REST API.
     *
     * @param validator the bean validator
     * @param request   the request object to validate
     * @throws IllegalArgumentException if any constraint is violated, with the messages joined
     */
    public static <T> void validate(Validator validator, T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(" "));
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Cap a page of search results for an AI tool. When the query matched more rows than the page
     * can hold, returning a silently truncated list would let the assistant pick the wrong record,
     * so this refuses and asks the user to narrow the search instead; otherwise it returns the rows.
     *
     * @param page the page of results, sized to the tool's display cap
     * @param what plural label for the records, used in the narrow-down message (e.g. "events")
     * @return the page content when it is the complete result set
     * @throws IllegalArgumentException if more rows matched than the page can hold
     */
    public static <T> List<T> capOrNarrow(Page<T> page, String what) {
        if (page.getTotalElements() > page.getSize()) {
            throw new IllegalArgumentException(
                    "There are too many matching " + what + " to list (" + page.getTotalElements()
                            + " found). Please narrow the search with a more specific name or extra detail.");
        }
        return page.getContent();
    }

    /**
     * Resolve the signed-in user from the security context.
     *
     * @return the authenticated {@link User}
     * @throws IllegalStateException if the request is not authenticated
     */
    public static User requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new IllegalStateException("You must be signed in to use this tool.");
        }
        return user;
    }
}
