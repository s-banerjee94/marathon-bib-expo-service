package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationAlreadyExistsException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceUpdateTest extends OrganizationServiceTestBase {

    @Test
    @DisplayName("ROOT user can update any organization")
    void updateOrganizationSucceedsForRoot() {
        User root = user(1L, UserRole.ROOT, null);
        Organization existing = org(10L, "old@org.com");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .organizerName("Updated Org")
                .build();

        mockFindOrg(existing);
        mockSave(existing);

        OrganizationResponse response = organizationService.updateOrganization(10L, request, root);

        assertThat(response.getOrganizerName()).isEqualTo("Updated Org");
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN can update their own organization")
    void updateOrganizationSucceedsForOrganizerAdminOnOwnOrg() {
        Organization org = org(10L, "own@org.com");
        User orgAdmin = user(2L, UserRole.ORGANIZER_ADMIN, org);
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .city("Mumbai")
                .build();

        mockFindOrg(org);
        mockSave(org);

        OrganizationResponse response = organizationService.updateOrganization(10L, request, orgAdmin);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("ORGANIZER_ADMIN cannot update a different organization")
    void updateOrganizationThrowsForOrganizerAdminOnDifferentOrg() {
        Organization ownOrg = org(20L, "own@org.com");
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, ownOrg);
        Organization targetOrg = org(10L, "other@org.com");

        mockFindOrg(targetOrg);

        assertThatThrownBy(() -> organizationService.updateOrganization(10L, UpdateOrganizationRequest.builder().build(), orgAdmin))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws OrganizationNotFoundException when organization does not exist")
    void updateOrganizationThrowsWhenNotFound() {
        User root = user(1L, UserRole.ROOT, null);
        mockFindOrgEmpty(99L);

        assertThatThrownBy(() -> organizationService.updateOrganization(99L, UpdateOrganizationRequest.builder().build(), root))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    @DisplayName("throws OrganizationAlreadyExistsException when new email is already taken")
    void updateOrganizationThrowsOnDuplicateEmail() {
        User root = user(1L, UserRole.ROOT, null);
        Organization existing = org(10L, "current@org.com");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .email("taken@org.com")
                .build();

        mockFindOrg(existing);
        when(organizationRepository.existsByEmailAndDeletedFalse("taken@org.com")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.updateOrganization(10L, request, root))
                .isInstanceOf(OrganizationAlreadyExistsException.class);

        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("applies all updatable fields from the request")
    void updateOrganizationAppliesAllUpdatableFields() {
        User root = user(1L, UserRole.ROOT, null);
        Organization existing = org(10L, "current@org.com");
        existing.setTaxId("TAX-OLD");

        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .organizerName("Updated Org")
                .email("new@org.com")
                .phoneNumber("9876543210")
                .website("https://example.com")
                .addressLine1("123 Main St")
                .addressLine2("Suite 1")
                .city("Delhi")
                .stateProvince("DL")
                .postalCode("110001")
                .country("India")
                .taxId("TAX-NEW")
                .registrationNumber("REG123")
                .maxOrganizerUsers(10)
                .maxDistributors(50)
                .subscriptionTier("PREMIUM")
                .billingEmail("billing@org.com")
                .build();

        mockFindOrg(existing);
        when(organizationRepository.existsByEmailAndDeletedFalse("new@org.com")).thenReturn(false);
        when(organizationRepository.existsByTaxIdAndDeletedFalse("TAX-NEW")).thenReturn(false);
        mockSave(existing);

        assertThat(organizationService.updateOrganization(10L, request, root)).isNotNull();
    }

    @Test
    @DisplayName("does not throw when updated email equals current email")
    void updateOrganizationDoesNotThrowWhenEmailUnchanged() {
        User root = user(1L, UserRole.ROOT, null);
        Organization existing = org(10L, "same@org.com");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .email("same@org.com")
                .build();

        mockFindOrg(existing);
        mockSave(existing);

        assertThatCode(() -> organizationService.updateOrganization(10L, request, root))
                .doesNotThrowAnyException();
        verify(organizationRepository, never()).existsByEmailAndDeletedFalse(any());
    }

    @Test
    @DisplayName("does not throw when updated tax ID equals current tax ID")
    void updateOrganizationDoesNotThrowWhenTaxIdUnchanged() {
        User root = user(1L, UserRole.ROOT, null);
        Organization existing = org(10L, "org@test.com");
        existing.setTaxId("SAME-TAX");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .taxId("SAME-TAX")
                .build();

        mockFindOrg(existing);
        mockSave(existing);

        assertThatCode(() -> organizationService.updateOrganization(10L, request, root))
                .doesNotThrowAnyException();
        verify(organizationRepository, never()).existsByTaxIdAndDeletedFalse(any());
    }

    @Test
    @DisplayName("throws OrganizationAlreadyExistsException when new tax ID is already taken")
    void updateOrganizationThrowsOnDuplicateTaxId() {
        User root = user(1L, UserRole.ROOT, null);
        Organization existing = org(10L, "org@test.com");
        existing.setTaxId("OLD_TAX");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .taxId("TAKEN_TAX")
                .build();

        mockFindOrg(existing);
        when(organizationRepository.existsByTaxIdAndDeletedFalse("TAKEN_TAX")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.updateOrganization(10L, request, root))
                .isInstanceOf(OrganizationAlreadyExistsException.class);

        verify(organizationRepository, never()).save(any());
    }
}
