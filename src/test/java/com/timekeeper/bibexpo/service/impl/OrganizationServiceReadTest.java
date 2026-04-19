package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceReadTest extends OrganizationServiceTestBase {

    @Test
    @DisplayName("ROOT user can retrieve any organization by ID")
    void getOrganizationByIdSucceedsForRoot() {
        User root = user(1L, UserRole.ROOT, null);
        Organization org = org(10L, "org@test.com");

        mockFindOrg(org);

        OrganizationResponse response = organizationService.getOrganizationById(10L, root);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ADMIN user can retrieve any organization by ID")
    void getOrganizationByIdSucceedsForAdmin() {
        User admin = user(2L, UserRole.ADMIN, null);
        Organization org = org(10L, "org@test.com");

        mockFindOrg(org);

        OrganizationResponse response = organizationService.getOrganizationById(10L, admin);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN can retrieve their own organization")
    void getOrganizationByIdSucceedsForOrganizerAdminOnOwnOrg() {
        Organization org = org(10L, "org@test.com");
        User orgAdmin = user(2L, UserRole.ORGANIZER_ADMIN, org);

        mockFindOrg(org);

        OrganizationResponse response = organizationService.getOrganizationById(10L, orgAdmin);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when ORGANIZER_USER requests a different org")
    void getOrganizationByIdThrowsForOrganizerUserOnDifferentOrg() {
        Organization ownOrg = org(20L, "own@org.com");
        User orgUser = user(3L, UserRole.ORGANIZER_USER, ownOrg);
        Organization targetOrg = org(10L, "other@org.com");

        mockFindOrg(targetOrg);

        assertThatThrownBy(() -> organizationService.getOrganizationById(10L, orgUser))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("throws OrganizationNotFoundException when organization does not exist")
    void getOrganizationByIdThrowsWhenNotFound() {
        User root = user(1L, UserRole.ROOT, null);
        mockFindOrgEmpty(99L);

        assertThatThrownBy(() -> organizationService.getOrganizationById(99L, root))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    @DisplayName("returns current user's organization")
    void getCurrentUserOrganizationSucceeds() {
        Organization org = org(10L, "org@test.com");
        User orgAdmin = user(2L, UserRole.ORGANIZER_ADMIN, org);

        mockFindOrg(org);

        OrganizationResponse response = organizationService.getCurrentUserOrganization(orgAdmin);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException when user has no organization")
    void getCurrentUserOrganizationThrowsWhenUserHasNoOrganization() {
        User root = user(1L, UserRole.ROOT, null);

        assertThatThrownBy(() -> organizationService.getCurrentUserOrganization(root))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("throws OrganizationNotFoundException when user's organization is deleted")
    void getCurrentUserOrganizationThrowsWhenOrganizationDeleted() {
        Organization org = org(10L, "org@test.com");
        User orgAdmin = user(2L, UserRole.ORGANIZER_ADMIN, org);

        mockFindOrgEmpty(10L);

        assertThatThrownBy(() -> organizationService.getCurrentUserOrganization(orgAdmin))
                .isInstanceOf(OrganizationNotFoundException.class);
    }
}
