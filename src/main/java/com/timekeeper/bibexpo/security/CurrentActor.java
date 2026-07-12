package com.timekeeper.bibexpo.security;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;

/**
 * Immutable identity of the authenticated caller, resolved once at the web/tool boundary and
 * passed through service layers instead of a raw username string or the JPA {@link User} entity.
 * Services read id/role/organization directly from this record rather than re-fetching the user.
 *
 * @param id             the caller's user id
 * @param username       the caller's unique username
 * @param role           the caller's role
 * @param organizationId the caller's organization id, or {@code null} for ROOT/ADMIN users
 */
public record CurrentActor(Long id, String username, UserRole role, Long organizationId) {

    /**
     * Builds an actor from the authenticated principal.
     *
     * @param user the authenticated user, never {@code null}
     * @return the actor view of the user
     */
    public static CurrentActor from(User user) {
        return new CurrentActor(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getOrganization() != null ? user.getOrganization().getId() : null);
    }
}
