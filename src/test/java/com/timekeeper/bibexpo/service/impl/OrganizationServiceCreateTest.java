package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationAlreadyExistsException;
import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceCreateTest extends OrganizationServiceTestBase {

    @Test
    @DisplayName("creates organization and returns response with correct fields")
    void createOrganizationSucceeds() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("New Org")
                .email("new@org.com")
                .maxOrganizerUsers(5)
                .maxDistributors(30)
                .build();

        Organization saved = org(100L, "new@org.com");
        saved.setOrganizerName("New Org");

        when(organizationRepository.existsByEmailAndDeletedFalse("new@org.com")).thenReturn(false);
        mockSave(saved);

        OrganizationResponse response = organizationService.createOrganization(request);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getEmail()).isEqualTo("new@org.com");
        assertThat(response.getSubscriptionStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("applies default maxOrganizerUsers and maxDistributors when null in request")
    void createOrganizationDefaultsMaxUsersAndDistributors() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("Org")
                .email("org@test.com")
                .maxOrganizerUsers(null)
                .maxDistributors(null)
                .build();

        when(organizationRepository.existsByEmailAndDeletedFalse(any())).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            return Organization.builder()
                    .id(1L).organizerName(o.getOrganizerName()).email(o.getEmail())
                    .maxOrganizerUsers(o.getMaxOrganizerUsers())
                    .maxDistributors(o.getMaxDistributors())
                    .subscriptionStatus("ACTIVE").enabled(true).deleted(false).build();
        });

        OrganizationResponse response = organizationService.createOrganization(request);

        assertThat(response.getMaxOrganizerUsers()).isEqualTo(5);
        assertThat(response.getMaxDistributors()).isEqualTo(30);
    }

    @Test
    @DisplayName("throws OrganizationAlreadyExistsException when email is already taken")
    void createOrganizationThrowsWhenEmailAlreadyExists() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("Dup Org")
                .email("dup@org.com")
                .build();

        when(organizationRepository.existsByEmailAndDeletedFalse("dup@org.com")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(request))
                .isInstanceOf(OrganizationAlreadyExistsException.class);

        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws OrganizationAlreadyExistsException when tax ID is already taken")
    void createOrganizationThrowsWhenTaxIdAlreadyExists() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("Org")
                .email("unique@org.com")
                .taxId("TAX123")
                .build();

        when(organizationRepository.existsByEmailAndDeletedFalse("unique@org.com")).thenReturn(false);
        when(organizationRepository.existsByTaxIdAndDeletedFalse("TAX123")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(request))
                .isInstanceOf(OrganizationAlreadyExistsException.class);

        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("does not check tax ID uniqueness when taxId is blank")
    void createOrganizationIgnoresBlankTaxId() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("Org")
                .email("org@test.com")
                .taxId("   ")
                .build();

        when(organizationRepository.existsByEmailAndDeletedFalse("org@test.com")).thenReturn(false);
        mockSave(org(1L, "org@test.com"));

        assertThat(organizationService.createOrganization(request)).isNotNull();
        verify(organizationRepository, never()).existsByTaxIdAndDeletedFalse(any());
    }

    @Test
    @DisplayName("converts blank subscriptionTier to null before saving")
    void createOrganizationConvertsBlankSubscriptionTierToNull() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("Org")
                .email("org@test.com")
                .subscriptionTier("   ")
                .build();

        when(organizationRepository.existsByEmailAndDeletedFalse(any())).thenReturn(false);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        when(organizationRepository.save(captor.capture())).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            return Organization.builder().id(1L).organizerName(o.getOrganizerName())
                    .email(o.getEmail()).subscriptionTier(o.getSubscriptionTier())
                    .subscriptionStatus("ACTIVE").enabled(true).deleted(false)
                    .maxOrganizerUsers(5).maxDistributors(30).build();
        });

        organizationService.createOrganization(request);

        assertThat(captor.getValue().getSubscriptionTier()).isNull();
    }
}
