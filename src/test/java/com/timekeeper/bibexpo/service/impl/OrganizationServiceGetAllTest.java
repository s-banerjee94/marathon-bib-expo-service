package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceGetAllTest extends OrganizationServiceTestBase {

    @Test
    @DisplayName("returns page of organizations for ROOT user")
    void getAllOrganizationsReturnsPageForRootUser() {
        User root = user(1L, UserRole.ROOT, null);
        Organization org = org(10L, "org@test.com");
        Pageable pageable = PageRequest.of(0, 10);

        when(organizationRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(org)));

        Page<OrganizationResponse> result = organizationService.getAllOrganizations(null, null, null, pageable, root);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("org@test.com");
    }

    @Test
    @DisplayName("returns empty page for ADMIN user")
    void getAllOrganizationsReturnsPageForAdminUser() {
        User admin = user(2L, UserRole.ADMIN, null);
        Pageable pageable = PageRequest.of(0, 10);

        when(organizationRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<OrganizationResponse> result = organizationService.getAllOrganizations(null, null, null, pageable, admin);

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("throws UnauthorizedAccessException for ORGANIZER_ADMIN")
    void getAllOrganizationsThrowsForOrganizerAdmin() {
        User orgAdmin = user(3L, UserRole.ORGANIZER_ADMIN, org(10L, "org@test.com"));
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> organizationService.getAllOrganizations(null, null, null, pageable, orgAdmin))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(organizationRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
