package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceToggleStatusTest extends OrganizationServiceTestBase {

    @Test
    @DisplayName("enables organization without touching users")
    void toggleOrganizationStatusEnablesOrg() {
        Organization org = org(10L, "org@test.com");
        org.setEnabled(false);

        mockFindOrg(org);
        mockSave(org);

        OrganizationResponse response = organizationService.toggleOrganizationStatus(10L, true);

        assertThat(response.getEnabled()).isTrue();
        verify(userRepository, never()).findByOrganizationIdAndDeletedFalse(any());
    }

    @Test
    @DisplayName("disables organization and disables all its users")
    void toggleOrganizationStatusDisablesOrgAndAllUsers() {
        Organization org = org(10L, "org@test.com");
        User user1 = user(5L, UserRole.ORGANIZER_USER, org);
        User user2 = user(6L, UserRole.ORGANIZER_ADMIN, org);

        mockFindOrg(org);
        mockSave(org);
        mockOrgUsers(10L, List.of(user1, user2));

        organizationService.toggleOrganizationStatus(10L, false);

        assertThat(user1.isEnabled()).isFalse();
        assertThat(user2.isEnabled()).isFalse();
        verify(userRepository).saveAll(List.of(user1, user2));
    }

    @Test
    @DisplayName("disables organization with no users without throwing")
    void toggleOrganizationStatusDisablesOrgWithNoUsersGracefully() {
        Organization org = org(10L, "org@test.com");

        mockFindOrg(org);
        mockSave(org);
        mockOrgUsers(10L, List.of());

        organizationService.toggleOrganizationStatus(10L, false);

        verify(userRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("throws OrganizationNotFoundException when organization does not exist")
    void toggleOrganizationStatusThrowsWhenNotFound() {
        mockFindOrgEmpty(99L);

        assertThatThrownBy(() -> organizationService.toggleOrganizationStatus(99L, true))
                .isInstanceOf(OrganizationNotFoundException.class);
    }
}
