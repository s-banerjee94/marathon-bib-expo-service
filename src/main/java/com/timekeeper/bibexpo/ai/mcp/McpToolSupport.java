package com.timekeeper.bibexpo.ai.mcp;

import com.timekeeper.bibexpo.model.entity.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    /**
     * Normalize a free-text search argument from a tool call. A null or blank value means "no
     * filter", so it is returned as null to let the search list everything in the user's scope;
     * otherwise the trimmed text is returned.
     *
     * @param query the raw query argument, possibly null or blank
     * @return the trimmed query, or null when nothing was provided
     */
    public static String normalizeSearch(String query) {
        return (query == null || query.isBlank()) ? null : query.trim();
    }
}
