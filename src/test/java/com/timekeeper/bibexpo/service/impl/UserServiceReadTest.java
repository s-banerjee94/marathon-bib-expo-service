package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceReadTest extends UserServiceTestBase {

    // ── getUserById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("ROOT views any user")
    void rootViewsAnyUser() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));

        mockCurrentUser(root);
        mockTargetUser(target);

        UserResponse response = userService.getUserById(10L, root.getUsername());

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ADMIN views any user")
    void adminViewsAnyUser() {
        User admin = user(2L, UserRole.ADMIN, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));

        mockCurrentUser(admin);
        mockTargetUser(target);

        UserResponse response = userService.getUserById(10L, admin.getUsername());

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN views user in same organization")
    void orgAdminViewsUserInSameOrganization() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        User orgUser = user(10L, UserRole.ORGANIZER_USER, organization);

        mockCurrentUser(orgAdmin);
        mockTargetUser(orgUser);

        UserResponse response = userService.getUserById(10L, orgAdmin.getUsername());

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN views user in different organization")
    void orgAdminCannotViewUserInDifferentOrganization() {
        Organization ownOrg = org(20L);
        Organization otherOrg = org(99L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, ownOrg);
        User otherOrgUser = user(10L, UserRole.ORGANIZER_USER, otherOrg);

        mockCurrentUser(orgAdmin);
        mockTargetUser(otherOrgUser);

        assertThatThrownBy(() -> userService.getUserById(10L, orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("throws UserNotFoundException when user ID does not exist")
    void throwsWhenUserNotFound() {
        User root = user(1L, UserRole.ROOT, null);

        mockCurrentUser(root);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L, root.getUsername()))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── getUsers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ROOT gets all users with filters applied")
    void rootGetsAllUsersWithFilters() {
        User root = user(1L, UserRole.ROOT, null);
        Page<User> page = new PageImpl<>(List.of(root));

        mockCurrentUser(root);
        when(userRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<UserResponse> result = userService.getUsers(null, null, null, null, null,
                PageRequest.of(0, 10), root.getUsername());

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN gets users scoped to own organization only")
    void orgAdminGetsScopedToOwnOrganization() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        Page<User> page = new PageImpl<>(List.of());

        mockCurrentUser(orgAdmin);
        when(userRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<UserResponse> result = userService.getUsers(null, null, null, true, null,
                PageRequest.of(0, 10), orgAdmin.getUsername());

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN has no organization assigned")
    void orgAdminWithNoOrganizationThrows() {
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, null);

        mockCurrentUser(orgAdmin);

        assertThatThrownBy(() -> userService.getUsers(null, null, null, null, null,
                PageRequest.of(0, 10), orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ── getCurrentUser ────────────────────────────────────────────────────

    @Test
    @DisplayName("returns the profile of the currently authenticated user")
    void returnsCurrentUserProfile() {
        User root = user(1L, UserRole.ROOT, null);

        mockCurrentUser(root);

        UserResponse response = userService.getCurrentUser(root.getUsername());

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo(root.getUsername());
    }

    @Test
    @DisplayName("throws InvalidUserDataException when the authenticated username does not exist")
    void throwsWhenCurrentUserNotFound() {
        when(userRepository.findByUsernameAndDeletedFalse("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser("ghost"))
                .isInstanceOf(InvalidUserDataException.class);
    }
}
