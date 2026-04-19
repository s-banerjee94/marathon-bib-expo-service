package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationAlreadyExistsException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    private Organization buildOrg(long id, String email) {
        return Organization.builder()
                .id(id)
                .organizerName("Test Org")
                .email(email)
                .enabled(true)
                .deleted(false)
                .maxOrganizerUsers(5)
                .maxDistributors(30)
                .subscriptionStatus("ACTIVE")
                .build();
    }

    private User buildUser(long id, UserRole role, Organization organization) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .role(role)
                .organization(organization)
                .enabled(true)
                .deleted(false)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    // ── getAllOrganizations ───────────────────────────────────────────────

    @Test
    void getAllOrganizationsReturnsPageForRootUser() {
        User root = buildUser(1L, UserRole.ROOT, null);
        Organization org = buildOrg(10L, "org@test.com");
        Pageable pageable = PageRequest.of(0, 10);
        Page<Organization> orgPage = new PageImpl<>(List.of(org));

        when(organizationRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orgPage);

        Page<OrganizationResponse> result = organizationService.getAllOrganizations(null, null, null, pageable, root);

        assertEquals(1, result.getTotalElements());
        assertEquals("org@test.com", result.getContent().get(0).getEmail());
    }

    @Test
    void getAllOrganizationsReturnsPageForAdminUser() {
        User admin = buildUser(2L, UserRole.ADMIN, null);
        Pageable pageable = PageRequest.of(0, 10);
        when(organizationRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<OrganizationResponse> result = organizationService.getAllOrganizations(null, null, null, pageable, admin);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getAllOrganizationsThrowsForOrganizerAdmin() {
        User orgAdmin = buildUser(3L, UserRole.ORGANIZER_ADMIN, buildOrg(10L, "org@test.com"));
        Pageable pageable = PageRequest.of(0, 10);

        assertThrows(UnauthorizedAccessException.class,
                () -> organizationService.getAllOrganizations(null, null, null, pageable, orgAdmin));

        verify(organizationRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // ── createOrganization ───────────────────────────────────────────────

    @Test
    void createOrganizationSucceeds() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("New Org")
                .email("new@org.com")
                .maxOrganizerUsers(5)
                .maxDistributors(30)
                .build();

        Organization saved = buildOrg(100L, "new@org.com");
        saved.setOrganizerName("New Org");

        when(organizationRepository.existsByEmailAndDeletedFalse("new@org.com")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(saved);

        OrganizationResponse response = organizationService.createOrganization(request);

        assertEquals(100L, response.getId());
        assertEquals("new@org.com", response.getEmail());
        assertEquals("ACTIVE", response.getSubscriptionStatus());
    }

    @Test
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
            o = Organization.builder()
                    .id(1L).organizerName(o.getOrganizerName()).email(o.getEmail())
                    .maxOrganizerUsers(o.getMaxOrganizerUsers())
                    .maxDistributors(o.getMaxDistributors())
                    .subscriptionStatus("ACTIVE").enabled(true).deleted(false).build();
            return o;
        });

        OrganizationResponse response = organizationService.createOrganization(request);

        assertEquals(5, response.getMaxOrganizerUsers());
        assertEquals(30, response.getMaxDistributors());
    }

    @Test
    void createOrganizationThrowsWhenEmailAlreadyExists() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("Dup Org")
                .email("dup@org.com")
                .build();

        when(organizationRepository.existsByEmailAndDeletedFalse("dup@org.com")).thenReturn(true);

        assertThrows(OrganizationAlreadyExistsException.class,
                () -> organizationService.createOrganization(request));

        verify(organizationRepository, never()).save(any());
    }

    @Test
    void createOrganizationThrowsWhenTaxIdAlreadyExists() {
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .organizerName("Org")
                .email("unique@org.com")
                .taxId("TAX123")
                .build();

        when(organizationRepository.existsByEmailAndDeletedFalse("unique@org.com")).thenReturn(false);
        when(organizationRepository.existsByTaxIdAndDeletedFalse("TAX123")).thenReturn(true);

        assertThrows(OrganizationAlreadyExistsException.class,
                () -> organizationService.createOrganization(request));

        verify(organizationRepository, never()).save(any());
    }

    @Test
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

        assertNull(captor.getValue().getSubscriptionTier());
    }

    // ── updateOrganization ───────────────────────────────────────────────

    @Test
    void updateOrganizationSucceedsForRoot() {
        User root = buildUser(1L, UserRole.ROOT, null);
        Organization existing = buildOrg(10L, "old@org.com");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .organizerName("Updated Org")
                .build();

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
        when(organizationRepository.save(any())).thenReturn(existing);

        OrganizationResponse response = organizationService.updateOrganization(10L, request, root);

        assertEquals("Updated Org", response.getOrganizerName());
    }

    @Test
    void updateOrganizationSucceedsForOrganizerAdminOnOwnOrg() {
        Organization org = buildOrg(10L, "own@org.com");
        User orgAdmin = buildUser(2L, UserRole.ORGANIZER_ADMIN, org);
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .city("Mumbai")
                .build();

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);

        OrganizationResponse response = organizationService.updateOrganization(10L, request, orgAdmin);

        assertNotNull(response);
    }

    @Test
    void updateOrganizationThrowsForOrganizerAdminOnDifferentOrg() {
        Organization ownOrg = buildOrg(20L, "own@org.com");
        User orgAdmin = buildUser(3L, UserRole.ORGANIZER_ADMIN, ownOrg);
        Organization targetOrg = buildOrg(10L, "other@org.com");

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(targetOrg));

        assertThrows(UnauthorizedAccessException.class,
                () -> organizationService.updateOrganization(10L, UpdateOrganizationRequest.builder().build(), orgAdmin));

        verify(organizationRepository, never()).save(any());
    }

    @Test
    void updateOrganizationThrowsWhenNotFound() {
        User root = buildUser(1L, UserRole.ROOT, null);
        when(organizationRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.updateOrganization(99L, UpdateOrganizationRequest.builder().build(), root));
    }

    @Test
    void updateOrganizationThrowsOnDuplicateEmail() {
        User root = buildUser(1L, UserRole.ROOT, null);
        Organization existing = buildOrg(10L, "current@org.com");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .email("taken@org.com")
                .build();

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
        when(organizationRepository.existsByEmailAndDeletedFalse("taken@org.com")).thenReturn(true);

        assertThrows(OrganizationAlreadyExistsException.class,
                () -> organizationService.updateOrganization(10L, request, root));

        verify(organizationRepository, never()).save(any());
    }

    @Test
    void updateOrganizationThrowsOnDuplicateTaxId() {
        User root = buildUser(1L, UserRole.ROOT, null);
        Organization existing = buildOrg(10L, "org@test.com");
        existing.setTaxId("OLD_TAX");
        UpdateOrganizationRequest request = UpdateOrganizationRequest.builder()
                .taxId("TAKEN_TAX")
                .build();

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
        when(organizationRepository.existsByTaxIdAndDeletedFalse("TAKEN_TAX")).thenReturn(true);

        assertThrows(OrganizationAlreadyExistsException.class,
                () -> organizationService.updateOrganization(10L, request, root));

        verify(organizationRepository, never()).save(any());
    }

    // ── toggleOrganizationStatus ─────────────────────────────────────────

    @Test
    void toggleOrganizationStatusEnablesOrg() {
        Organization org = buildOrg(10L, "org@test.com");
        org.setEnabled(false);

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);

        OrganizationResponse response = organizationService.toggleOrganizationStatus(10L, true);

        assertTrue(response.getEnabled());
        verify(userRepository, never()).findByOrganizationIdAndDeletedFalse(any());
    }

    @Test
    void toggleOrganizationStatusDisablesOrgAndAllUsers() {
        Organization org = buildOrg(10L, "org@test.com");
        User user1 = buildUser(5L, UserRole.ORGANIZER_USER, org);
        User user2 = buildUser(6L, UserRole.ORGANIZER_ADMIN, org);

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);
        when(userRepository.findByOrganizationIdAndDeletedFalse(10L)).thenReturn(List.of(user1, user2));

        organizationService.toggleOrganizationStatus(10L, false);

        assertFalse(user1.isEnabled());
        assertFalse(user2.isEnabled());
        verify(userRepository).saveAll(List.of(user1, user2));
    }

    @Test
    void toggleOrganizationStatusDisablesOrgWithNoUsersGracefully() {
        Organization org = buildOrg(10L, "org@test.com");

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);
        when(userRepository.findByOrganizationIdAndDeletedFalse(10L)).thenReturn(List.of());

        assertDoesNotThrow(() -> organizationService.toggleOrganizationStatus(10L, false));

        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void toggleOrganizationStatusThrowsWhenNotFound() {
        when(organizationRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.toggleOrganizationStatus(99L, true));
    }

    // ── getOrganizationById ──────────────────────────────────────────────

    @Test
    void getOrganizationByIdSucceedsForRoot() {
        User root = buildUser(1L, UserRole.ROOT, null);
        Organization org = buildOrg(10L, "org@test.com");

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(org));

        OrganizationResponse response = organizationService.getOrganizationById(10L, root);

        assertEquals(10L, response.getId());
    }

    @Test
    void getOrganizationByIdSucceedsForOrganizerAdminOnOwnOrg() {
        Organization org = buildOrg(10L, "org@test.com");
        User orgAdmin = buildUser(2L, UserRole.ORGANIZER_ADMIN, org);

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(org));

        OrganizationResponse response = organizationService.getOrganizationById(10L, orgAdmin);

        assertEquals(10L, response.getId());
    }

    @Test
    void getOrganizationByIdThrowsForOrganizerUserOnDifferentOrg() {
        Organization ownOrg = buildOrg(20L, "own@org.com");
        User orgUser = buildUser(3L, UserRole.ORGANIZER_USER, ownOrg);
        Organization targetOrg = buildOrg(10L, "other@org.com");

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(targetOrg));

        assertThrows(UnauthorizedAccessException.class,
                () -> organizationService.getOrganizationById(10L, orgUser));
    }

    @Test
    void getOrganizationByIdThrowsWhenNotFound() {
        User root = buildUser(1L, UserRole.ROOT, null);
        when(organizationRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.getOrganizationById(99L, root));
    }

    // ── getCurrentUserOrganization ───────────────────────────────────────

    @Test
    void getCurrentUserOrganizationSucceeds() {
        Organization org = buildOrg(10L, "org@test.com");
        User orgAdmin = buildUser(2L, UserRole.ORGANIZER_ADMIN, org);

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(org));

        OrganizationResponse response = organizationService.getCurrentUserOrganization(orgAdmin);

        assertEquals(10L, response.getId());
    }

    @Test
    void getCurrentUserOrganizationThrowsWhenUserHasNoOrganization() {
        User root = buildUser(1L, UserRole.ROOT, null);

        assertThrows(UnauthorizedAccessException.class,
                () -> organizationService.getCurrentUserOrganization(root));
    }

    @Test
    void getCurrentUserOrganizationThrowsWhenOrganizationDeleted() {
        Organization org = buildOrg(10L, "org@test.com");
        User orgAdmin = buildUser(2L, UserRole.ORGANIZER_ADMIN, org);

        when(organizationRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.getCurrentUserOrganization(orgAdmin));
    }
}
