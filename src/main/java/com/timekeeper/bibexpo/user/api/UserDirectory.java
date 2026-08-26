package com.timekeeper.bibexpo.user.api;

import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Read access to users for modules that need to name one or gather an audience, but own no user
 * logic. The importer resolves who uploaded a file, notifications resolve who should receive one,
 * and password reset resolves who a link belongs to; each reached the repository to do it.
 * Anything that changes a user still goes through {@code UserService}.
 */
public interface UserDirectory {

    /**
     * Returns the user with the given id.
     *
     * @param userId the user id, may be {@code null}
     * @return the user, or empty when the id is null or unknown
     */
    Optional<User> findById(Long userId);

    /**
     * Returns the user with the given id, for callers that treat a missing one as an error.
     *
     * @param userId the user id
     * @return the user, never {@code null}
     * @throws com.timekeeper.bibexpo.user.exception.UserNotFoundException if no user has that id
     */
    User requireById(Long userId);

    /**
     * Returns the username for the given id, for callers that want a label rather than the user.
     *
     * @param userId the user id, may be {@code null}
     * @return the username, or empty when the id is null or unknown
     */
    Optional<String> findUsername(Long userId);

    /**
     * Resolves a user from whatever an account holder is likely to type — their username, their
     * email address, or their phone number, tried in that order. Surrounding whitespace is ignored.
     *
     * @param identifier the typed identifier, may be {@code null}
     * @return the first user matching it, or empty when nothing matches
     */
    Optional<User> findByLoginIdentifier(String identifier);

    /**
     * Returns every user holding the given role, across all organizations.
     *
     * @param role the role to match
     * @return the matching users, never {@code null}
     */
    List<User> findByRole(UserRole role);

    /**
     * Returns every user holding the given role within one organization.
     *
     * @param role           the role to match
     * @param organizationId the organization to scope to
     * @return the matching users, never {@code null}
     */
    List<User> findByRoleAndOrganizationId(UserRole role, Long organizationId);

    /**
     * Returns every user belonging to one organization, whatever their role.
     *
     * @param organizationId the organization to scope to
     * @return the matching users, never {@code null}
     */
    List<User> findByOrganizationId(Long organizationId);
}
