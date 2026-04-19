package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserAlreadyExistsException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
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
class UserServiceUpdateUserTest extends UserServiceTestBase {

    @Test
    @DisplayName("ROOT updates any user successfully")
    void rootUpdatesAnyUserSuccessfully() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("New Name").build();

        mockCurrentUser(root);
        mockTargetUser(target);
        mockSave(target);

        UserResponse response = userService.updateUser(10L, request, root.getUsername());

        assertThat(response).isNotNull();
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("ADMIN updates ORGANIZER_USER successfully")
    void adminUpdatesOrganizerUserSuccessfully() {
        User admin = user(2L, UserRole.ADMIN, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Updated").build();

        mockCurrentUser(admin);
        mockTargetUser(target);
        mockSave(target);

        UserResponse response = userService.updateUser(10L, request, admin.getUsername());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("ADMIN updates itself successfully")
    void adminUpdatesItselfSuccessfully() {
        User admin = user(2L, UserRole.ADMIN, null);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Updated Admin").build();

        mockCurrentUser(admin);
        mockTargetUser(admin);
        mockSave(admin);

        UserResponse response = userService.updateUser(2L, request, admin.getUsername());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ADMIN tries to update ROOT user")
    void adminCannotUpdateRootUser() {
        User admin = user(2L, UserRole.ADMIN, null);
        User root = user(1L, UserRole.ROOT, null);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Hacked").build();

        mockCurrentUser(admin);
        mockTargetUser(root);

        assertThatThrownBy(() -> userService.updateUser(1L, request, admin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("equal or higher privileges");
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ADMIN tries to update another ADMIN")
    void adminCannotUpdateAnotherAdmin() {
        User admin = user(2L, UserRole.ADMIN, null);
        User otherAdmin = user(3L, UserRole.ADMIN, null);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Changed").build();

        mockCurrentUser(admin);
        mockTargetUser(otherAdmin);

        assertThatThrownBy(() -> userService.updateUser(3L, request, admin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("equal or higher privileges");
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN updates itself successfully")
    void orgAdminUpdatesItselfSuccessfully() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Self Update").build();

        mockCurrentUser(orgAdmin);
        mockTargetUser(orgAdmin);
        mockSave(orgAdmin);

        UserResponse response = userService.updateUser(3L, request, orgAdmin.getUsername());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN updates ORGANIZER_USER in same organization successfully")
    void orgAdminUpdatesOrgUserInSameOrgSuccessfully() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        User orgUser = user(10L, UserRole.ORGANIZER_USER, organization);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Updated User").build();

        mockCurrentUser(orgAdmin);
        mockTargetUser(orgUser);
        mockSave(orgUser);

        UserResponse response = userService.updateUser(10L, request, orgAdmin.getUsername());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN tries to update user with equal or higher privileges")
    void orgAdminCannotUpdatePrivilegedUser() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        User anotherOrgAdmin = user(4L, UserRole.ORGANIZER_ADMIN, organization);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Nope").build();

        mockCurrentUser(orgAdmin);
        mockTargetUser(anotherOrgAdmin);

        assertThatThrownBy(() -> userService.updateUser(4L, request, orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("equal or higher privileges");
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN tries to update user in different organization")
    void orgAdminCannotUpdateUserInDifferentOrganization() {
        Organization ownOrg = org(20L);
        Organization otherOrg = org(99L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, ownOrg);
        User otherOrgUser = user(10L, UserRole.ORGANIZER_USER, otherOrg);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Cross-org").build();

        mockCurrentUser(orgAdmin);
        mockTargetUser(otherOrgUser);

        assertThatThrownBy(() -> userService.updateUser(10L, request, orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("ORGANIZER_USER can only update itself")
    void orgUserCanOnlyUpdateItself() {
        Organization organization = org(20L);
        User orgUser = user(4L, UserRole.ORGANIZER_USER, organization);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Own Update").build();

        mockCurrentUser(orgUser);
        mockTargetUser(orgUser);
        mockSave(orgUser);

        UserResponse response = userService.updateUser(4L, request, orgUser.getUsername());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_USER tries to update another user")
    void orgUserCannotUpdateAnotherUser() {
        Organization organization = org(20L);
        User orgUser = user(4L, UserRole.ORGANIZER_USER, organization);
        User distributor = user(5L, UserRole.DISTRIBUTOR, organization);
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("Changed").build();

        mockCurrentUser(orgUser);
        mockTargetUser(distributor);

        assertThatThrownBy(() -> userService.updateUser(5L, request, orgUser.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("throws UserAlreadyExistsException when updating with an already taken email")
    void throwsWhenUpdatingWithTakenEmail() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        target.setEmail("current@test.com");
        UpdateUserRequest request = UpdateUserRequest.builder().email("taken@test.com").build();

        User conflictUser = user(99L, UserRole.ORGANIZER_USER, org(20L));
        conflictUser.setEmail("taken@test.com");

        mockCurrentUser(root);
        mockTargetUser(target);
        when(userRepository.findByEmail("taken@test.com")).thenReturn(Optional.of(conflictUser));

        assertThatThrownBy(() -> userService.updateUser(10L, request, root.getUsername()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("email is already registered");
    }

    @Test
    @DisplayName("allows updating with the same email already belonging to the target user")
    void allowsUpdatingWithSameEmailAsCurrentUser() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        target.setEmail("own@test.com");
        UpdateUserRequest request = UpdateUserRequest.builder().email("own@test.com").build();

        mockCurrentUser(root);
        mockTargetUser(target);
        when(userRepository.findByEmail("own@test.com")).thenReturn(Optional.of(target));
        mockSave(target);

        assertThat(userService.updateUser(10L, request, root.getUsername())).isNotNull();
    }

    @Test
    @DisplayName("throws UserNotFoundException when target user does not exist")
    void throwsWhenTargetUserNotFound() {
        User root = user(1L, UserRole.ROOT, null);

        mockCurrentUser(root);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(999L, UpdateUserRequest.builder().build(), root.getUsername()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("encodes password when updating user with a new password")
    void encodesPasswordWhenUpdating() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        UpdateUserRequest request = UpdateUserRequest.builder().password("NewPass123!").build();

        mockCurrentUser(root);
        mockTargetUser(target);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-encoded");
        mockSave(target);

        userService.updateUser(10L, request, root.getUsername());

        verify(passwordEncoder).encode("NewPass123!");
        assertThat(target.getPassword()).isEqualTo("new-encoded");
    }

    @Test
    @DisplayName("throws UserAlreadyExistsException when updating with an already taken phone number")
    void throwsWhenUpdatingWithTakenPhoneNumber() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        UpdateUserRequest request = UpdateUserRequest.builder().phoneNumber("1234567890").build();

        User conflictUser = user(99L, UserRole.ORGANIZER_USER, org(20L));

        mockCurrentUser(root);
        mockTargetUser(target);
        when(userRepository.findByPhoneNumber("1234567890")).thenReturn(Optional.of(conflictUser));

        assertThatThrownBy(() -> userService.updateUser(10L, request, root.getUsername()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("phone number is already registered");
    }

    @Test
    @DisplayName("allows updating with the same phone number already belonging to the target user")
    void allowsUpdatingWithSamePhoneNumberAsCurrentUser() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        UpdateUserRequest request = UpdateUserRequest.builder().phoneNumber("1234567890").build();

        mockCurrentUser(root);
        mockTargetUser(target);
        when(userRepository.findByPhoneNumber("1234567890")).thenReturn(Optional.of(target));
        mockSave(target);

        assertThat(userService.updateUser(10L, request, root.getUsername())).isNotNull();
    }

    @Test
    @DisplayName("updates successfully when phone number is not taken by any other user")
    void updatesSuccessfullyWhenPhoneNumberIsUnique() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        UpdateUserRequest request = UpdateUserRequest.builder().phoneNumber("0987654321").build();

        mockCurrentUser(root);
        mockTargetUser(target);
        when(userRepository.findByPhoneNumber("0987654321")).thenReturn(Optional.empty());
        mockSave(target);

        assertThat(userService.updateUser(10L, request, root.getUsername())).isNotNull();
        assertThat(target.getPhoneNumber()).isEqualTo("0987654321");
    }
}
