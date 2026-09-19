package com.timekeeper.bibexpo.inventory.service.validator;

import com.timekeeper.bibexpo.organization.api.OrganizationDirectory;
import com.timekeeper.bibexpo.shared.error.AccessForbiddenException;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one reachability check behind every organization-scoped inventory endpoint.
 */
@Component
@RequiredArgsConstructor
public class InventoryAccessGuard {

    private final OrganizationDirectory organizationDirectory;

    /**
     * Confirms the organization exists and this caller may reach its inventory.
     *
     * @param currentUser    the authenticated caller
     * @param organizationId the organization being read or written
     * @throws AccessForbiddenException if the caller belongs to another organization
     */
    public void requireOrgAccess(User currentUser, Long organizationId) {
        organizationDirectory.requireById(organizationId);
        UserRole role = currentUser.getRole();
        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }
        if (currentUser.getOrganization() == null
                || !currentUser.getOrganization().getId().equals(organizationId)) {
            throw new AccessForbiddenException("You do not have access to this organization's inventory.");
        }
    }
}
