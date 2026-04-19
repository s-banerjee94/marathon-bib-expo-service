package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceToggleUserEnabledTest extends UserServiceTestBase {

    @Test
    @DisplayName("ROOT toggles any user's enabled status")
    void rootTogglesAnyUserEnabled() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        target.setEnabled(true);

        mockCurrentUser(root);
        mockTargetUser(target);
        mockSave(target);

        userService.toggleUserEnabled(10L, root.getUsername());

        assertThat(target.getEnabled()).isFalse();
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("ADMIN toggles any user's enabled status")
    void adminTogglesAnyUserEnabled() {
        User admin = user(2L, UserRole.ADMIN, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        target.setEnabled(false);

        mockCurrentUser(admin);
        mockTargetUser(target);
        mockSave(target);

        userService.toggleUserEnabled(10L, admin.getUsername());

        assertThat(target.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN toggles ORGANIZER_USER in same organization")
    void orgAdminTogglesOrgUserInSameOrganization() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        User orgUser = user(10L, UserRole.ORGANIZER_USER, organization);
        orgUser.setEnabled(true);

        mockCurrentUser(orgAdmin);
        mockTargetUser(orgUser);
        mockSave(orgUser);

        userService.toggleUserEnabled(10L, orgAdmin.getUsername());

        assertThat(orgUser.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN tries to toggle user with equal or higher privileges")
    void orgAdminCannotTogglePrivilegedUser() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        User anotherOrgAdmin = user(4L, UserRole.ORGANIZER_ADMIN, organization);

        mockCurrentUser(orgAdmin);
        mockTargetUser(anotherOrgAdmin);

        assertThatThrownBy(() -> userService.toggleUserEnabled(4L, orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("equal or higher privileges");
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN tries to toggle user in different organization")
    void orgAdminCannotToggleUserInDifferentOrganization() {
        Organization ownOrg = org(20L);
        Organization otherOrg = org(99L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, ownOrg);
        User otherOrgUser = user(10L, UserRole.ORGANIZER_USER, otherOrg);

        mockCurrentUser(orgAdmin);
        mockTargetUser(otherOrgUser);

        assertThatThrownBy(() -> userService.toggleUserEnabled(10L, orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("ORGANIZER_USER toggles DISTRIBUTOR in same organization")
    void orgUserTogglesDistributorInSameOrganization() {
        Organization organization = org(20L);
        User orgUser = user(4L, UserRole.ORGANIZER_USER, organization);
        User distributor = user(5L, UserRole.DISTRIBUTOR, organization);
        distributor.setEnabled(true);

        mockCurrentUser(orgUser);
        mockTargetUser(distributor);
        mockSave(distributor);

        userService.toggleUserEnabled(5L, orgUser.getUsername());

        assertThat(distributor.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_USER tries to toggle a non-DISTRIBUTOR")
    void orgUserCannotToggleNonDistributor() {
        Organization organization = org(20L);
        User orgUser = user(4L, UserRole.ORGANIZER_USER, organization);
        User anotherOrgUser = user(6L, UserRole.ORGANIZER_USER, organization);

        mockCurrentUser(orgUser);
        mockTargetUser(anotherOrgUser);

        assertThatThrownBy(() -> userService.toggleUserEnabled(6L, orgUser.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("distributor accounts");
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when DISTRIBUTOR tries to toggle any user")
    void distributorCannotToggleAnyUser() {
        Organization organization = org(20L);
        User distributor = user(5L, UserRole.DISTRIBUTOR, organization);
        User target = user(6L, UserRole.DISTRIBUTOR, organization);

        mockCurrentUser(distributor);
        mockTargetUser(target);

        assertThatThrownBy(() -> userService.toggleUserEnabled(6L, distributor.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("throws UserNotFoundException when target user does not exist")
    void throwsWhenTargetUserNotFound() {
        User root = user(1L, UserRole.ROOT, null);

        mockCurrentUser(root);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.toggleUserEnabled(999L, root.getUsername()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
