package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserAlreadyExistsException;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceCreateUserTest extends UserServiceTestBase {

    @Test
    @DisplayName("ROOT creates ADMIN successfully")
    void rootCreatesAdminSuccessfully() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("new.admin", UserRole.ADMIN).build();
        User saved = user(10L, UserRole.ADMIN, null);

        mockCurrentUser(root);
        mockPasswordEncoding();
        mockSave(saved);

        UserResponse response = userService.createUser(request, root.getUsername());

        assertThat(response.getId()).isEqualTo(10L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("ROOT creates ORGANIZER_ADMIN with organization successfully")
    void rootCreatesOrganizerAdminWithOrgSuccessfully() {
        User root = user(1L, UserRole.ROOT, null);
        Organization organization = org(20L);
        CreateUserRequest request = baseRequest("org.admin", UserRole.ORGANIZER_ADMIN)
                .organizationId(20L).build();
        User saved = user(11L, UserRole.ORGANIZER_ADMIN, organization);

        mockCurrentUser(root);
        mockOrganization(organization);
        mockOrgUserCount(20L, UserRole.ORGANIZER_ADMIN, 0L);
        mockOrgUserCount(20L, UserRole.ORGANIZER_USER, 0L);
        mockPasswordEncoding();
        mockSave(saved);

        UserResponse response = userService.createUser(request, root.getUsername());

        assertThat(response.getId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("ROOT creates DISTRIBUTOR without email and phone successfully")
    void rootCreatesDistributorWithoutEmailAndPhoneSuccessfully() {
        User root = user(1L, UserRole.ROOT, null);
        Organization organization = org(20L);
        CreateUserRequest request = CreateUserRequest.builder()
                .username("dist.user").password("Password1!").fullName("Dist User")
                .role(UserRole.DISTRIBUTOR.name()).organizationId(20L).build();
        User saved = user(12L, UserRole.DISTRIBUTOR, organization);

        mockCurrentUser(root);
        mockOrganization(organization);
        mockOrgUserCount(20L, UserRole.DISTRIBUTOR, 0L);
        mockPasswordEncoding();
        mockSave(saved);

        UserResponse response = userService.createUser(request, root.getUsername());

        assertThat(response.getId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when attempting to create ROOT user")
    void throwsWhenAttemptingToCreateRootUser() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("root2", UserRole.ROOT).build();

        mockCurrentUser(root);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN creates ORGANIZER_ADMIN successfully")
    void adminCreatesOrganizerAdminSuccessfully() {
        User admin = user(2L, UserRole.ADMIN, null);
        Organization organization = org(20L);
        CreateUserRequest request = baseRequest("org.admin", UserRole.ORGANIZER_ADMIN)
                .organizationId(20L).build();
        User saved = user(13L, UserRole.ORGANIZER_ADMIN, organization);

        mockCurrentUser(admin);
        mockOrganization(organization);
        mockOrgUserCount(20L, UserRole.ORGANIZER_ADMIN, 0L);
        mockOrgUserCount(20L, UserRole.ORGANIZER_USER, 0L);
        mockPasswordEncoding();
        mockSave(saved);

        UserResponse response = userService.createUser(request, admin.getUsername());

        assertThat(response.getId()).isEqualTo(13L);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ADMIN tries to create another ADMIN")
    void adminCannotCreateAnotherAdmin() {
        User admin = user(2L, UserRole.ADMIN, null);
        CreateUserRequest request = baseRequest("admin2", UserRole.ADMIN).build();

        mockCurrentUser(admin);

        assertThatThrownBy(() -> userService.createUser(request, admin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN creates ORGANIZER_USER in own organization successfully")
    void orgAdminCreatesOrgUserInOwnOrganizationSuccessfully() {
        Organization organization = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, organization);
        CreateUserRequest request = baseRequest("org.user", UserRole.ORGANIZER_USER)
                .organizationId(20L).build();
        User saved = user(14L, UserRole.ORGANIZER_USER, organization);

        mockCurrentUser(orgAdmin);
        mockOrganization(organization);
        mockOrgUserCount(20L, UserRole.ORGANIZER_ADMIN, 1L);
        mockOrgUserCount(20L, UserRole.ORGANIZER_USER, 0L);
        mockPasswordEncoding();
        mockSave(saved);

        UserResponse response = userService.createUser(request, orgAdmin.getUsername());

        assertThat(response.getId()).isEqualTo(14L);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN creates user in different organization")
    void orgAdminCannotCreateUserInDifferentOrganization() {
        Organization ownOrg = org(20L);
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, ownOrg);
        CreateUserRequest request = baseRequest("other.user", UserRole.ORGANIZER_USER)
                .organizationId(99L).build();

        mockCurrentUser(orgAdmin);

        assertThatThrownBy(() -> userService.createUser(request, orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_ADMIN has no organization assigned")
    void orgAdminWithNoOrganizationThrows() {
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, null);
        CreateUserRequest request = baseRequest("org.user", UserRole.ORGANIZER_USER)
                .organizationId(20L).build();

        mockCurrentUser(orgAdmin);

        assertThatThrownBy(() -> userService.createUser(request, orgAdmin.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("ORGANIZER_USER creates DISTRIBUTOR in own organization successfully")
    void orgUserCreatesDistributorInOwnOrganizationSuccessfully() {
        Organization organization = org(20L);
        User orgUser = user(4L, UserRole.ORGANIZER_USER, organization);
        CreateUserRequest request = CreateUserRequest.builder()
                .username("dist.user").password("Password1!").fullName("Dist")
                .role(UserRole.DISTRIBUTOR.name()).organizationId(20L).build();
        User saved = user(15L, UserRole.DISTRIBUTOR, organization);

        mockCurrentUser(orgUser);
        mockOrganization(organization);
        mockOrgUserCount(20L, UserRole.DISTRIBUTOR, 0L);
        mockPasswordEncoding();
        mockSave(saved);

        UserResponse response = userService.createUser(request, orgUser.getUsername());

        assertThat(response.getId()).isEqualTo(15L);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when DISTRIBUTOR tries to create any user")
    void distributorCannotCreateAnyUser() {
        Organization organization = org(20L);
        User distributor = user(5L, UserRole.DISTRIBUTOR, organization);
        CreateUserRequest request = CreateUserRequest.builder()
                .username("another").password("Password1!").fullName("Another")
                .role(UserRole.DISTRIBUTOR.name()).organizationId(20L).build();

        mockCurrentUser(distributor);

        assertThatThrownBy(() -> userService.createUser(request, distributor.getUsername()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("throws InvalidUserDataException when email is missing for non-DISTRIBUTOR role")
    void throwsWhenEmailMissingForNonDistributorRole() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = CreateUserRequest.builder()
                .username("admin2").password("Password1!").fullName("Admin")
                .role(UserRole.ADMIN.name()).phoneNumber("9876543210").build();

        mockCurrentUser(root);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("Email is required.");
    }

    @Test
    @DisplayName("throws InvalidUserDataException when phone number is missing for non-DISTRIBUTOR role")
    void throwsWhenPhoneMissingForNonDistributorRole() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = CreateUserRequest.builder()
                .username("admin2").password("Password1!").fullName("Admin")
                .role(UserRole.ADMIN.name()).email("admin2@test.com").build();

        mockCurrentUser(root);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("Phone number is required.");
    }

    @Test
    @DisplayName("throws UserAlreadyExistsException when username is already taken")
    void throwsWhenUsernameAlreadyTaken() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("existing.user", UserRole.ADMIN).build();

        mockCurrentUser(root);
        when(userRepository.existsByUsername("existing.user")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("username is already taken");
    }

    @Test
    @DisplayName("throws UserAlreadyExistsException when email is already registered")
    void throwsWhenEmailAlreadyRegistered() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("new.admin", UserRole.ADMIN)
                .email("taken@test.com").build();

        mockCurrentUser(root);
        when(userRepository.existsByUsername("new.admin")).thenReturn(false);
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("email is already registered");
    }

    @Test
    @DisplayName("throws UserAlreadyExistsException when phone number is already registered")
    void throwsWhenPhoneAlreadyRegistered() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("new.admin", UserRole.ADMIN)
                .phoneNumber("9999999999").build();

        mockCurrentUser(root);
        when(userRepository.existsByUsername("new.admin")).thenReturn(false);
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("9999999999")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("phone number is already registered");
    }

    @Test
    @DisplayName("throws InvalidUserDataException when organization ID is missing for org-scoped role")
    void throwsWhenOrgIdMissingForOrgRole() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("org.user", UserRole.ORGANIZER_USER)
                .organizationId(null).build();

        mockCurrentUser(root);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("Organization is required.");
    }

    @Test
    @DisplayName("throws OrganizationNotFoundException when organization ID does not exist")
    void throwsWhenOrganizationNotFound() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("org.user", UserRole.ORGANIZER_USER)
                .organizationId(999L).build();

        mockCurrentUser(root);
        when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    @DisplayName("throws InvalidUserDataException when organization is disabled (enabled=false)")
    void throwsWhenOrganizationIsDisabled() {
        User root = user(1L, UserRole.ROOT, null);
        Organization disabledOrg = Organization.builder()
                .id(20L).organizerName("Disabled Org").email("d@test.com")
                .enabled(false).deleted(false)
                .maxOrganizerUsers(5).maxDistributors(30).build();
        CreateUserRequest request = baseRequest("org.user", UserRole.ORGANIZER_USER)
                .organizationId(20L).build();

        mockCurrentUser(root);
        mockOrganization(disabledOrg);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("organization is currently disabled");
    }

    @Test
    @DisplayName("throws OrganizationNotFoundException when organization exists but is soft-deleted (deleted=true)")
    void throwsWhenOrganizationIsSoftDeleted() {
        User root = user(1L, UserRole.ROOT, null);
        Organization deletedOrg = Organization.builder()
                .id(20L).organizerName("Deleted Org").email("d@test.com")
                .enabled(true).deleted(true)
                .maxOrganizerUsers(5).maxDistributors(30).build();
        CreateUserRequest request = baseRequest("org.user", UserRole.ORGANIZER_USER)
                .organizationId(20L).build();

        mockCurrentUser(root);
        mockOrganization(deletedOrg);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    @DisplayName("throws InvalidUserDataException when distributor limit is exceeded")
    void throwsWhenDistributorLimitExceeded() {
        User root = user(1L, UserRole.ROOT, null);
        Organization organization = Organization.builder()
                .id(20L).organizerName("Org").email("org@test.com")
                .enabled(true).deleted(false)
                .maxOrganizerUsers(5).maxDistributors(2).build();
        CreateUserRequest request = CreateUserRequest.builder()
                .username("dist3").password("Password1!").fullName("Dist")
                .role(UserRole.DISTRIBUTOR.name()).organizationId(20L).build();

        mockCurrentUser(root);
        mockOrganization(organization);
        mockOrgUserCount(20L, UserRole.DISTRIBUTOR, 2L);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("maximum number of distributors");
    }

    @Test
    @DisplayName("maxDistributors=0 means unlimited — creation succeeds and count is not checked")
    void unlimitedDistributorLimitZeroAllowsCreation() {
        User root = user(1L, UserRole.ROOT, null);
        Organization organization = Organization.builder()
                .id(20L).organizerName("Org").email("org@test.com")
                .enabled(true).deleted(false)
                .maxOrganizerUsers(5).maxDistributors(0).build();
        CreateUserRequest request = CreateUserRequest.builder()
                .username("dist.user").password("Password1!").fullName("Dist")
                .role(UserRole.DISTRIBUTOR.name()).organizationId(20L).build();
        User saved = user(16L, UserRole.DISTRIBUTOR, organization);

        mockCurrentUser(root);
        mockOrganization(organization);
        mockPasswordEncoding();
        mockSave(saved);

        UserResponse response = userService.createUser(request, root.getUsername());

        assertThat(response.getId()).isEqualTo(16L);
        verify(userRepository, never()).countByOrganizationIdAndRoleAndDeletedFalse(anyLong(), eq(UserRole.DISTRIBUTOR));
    }

    @Test
    @DisplayName("throws InvalidUserDataException when organizer user limit is exceeded")
    void throwsWhenOrganizerUserLimitExceeded() {
        User root = user(1L, UserRole.ROOT, null);
        Organization organization = Organization.builder()
                .id(20L).organizerName("Org").email("org@test.com")
                .enabled(true).deleted(false)
                .maxOrganizerUsers(2).maxDistributors(30).build();
        CreateUserRequest request = baseRequest("org.user3", UserRole.ORGANIZER_USER)
                .organizationId(20L).build();

        mockCurrentUser(root);
        mockOrganization(organization);
        mockOrgUserCount(20L, UserRole.ORGANIZER_ADMIN, 1L);
        mockOrgUserCount(20L, UserRole.ORGANIZER_USER, 1L);

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("maximum number of organizer users");
    }

    @Test
    @DisplayName("throws InvalidUserDataException when the calling user does not exist")
    void throwsWhenCurrentUserNotFound() {
        CreateUserRequest request = baseRequest("new.admin", UserRole.ADMIN).build();
        when(userRepository.findByUsernameAndDeletedFalse("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser(request, "ghost"))
                .isInstanceOf(InvalidUserDataException.class);
    }
}
