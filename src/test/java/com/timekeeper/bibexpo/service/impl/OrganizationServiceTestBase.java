package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

abstract class OrganizationServiceTestBase {

    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;

    @InjectMocks OrganizationServiceImpl organizationService;

    Organization org(long id, String email) {
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

    User user(long id, UserRole role, Organization organization) {
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

    void mockFindOrg(Organization o) {
        when(organizationRepository.findByIdAndDeletedFalse(o.getId())).thenReturn(Optional.of(o));
    }

    void mockFindOrgEmpty(long id) {
        when(organizationRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());
    }

    void mockSave(Organization saved) {
        when(organizationRepository.save(any())).thenReturn(saved);
    }

    void mockOrgUsers(long orgId, List<User> users) {
        when(userRepository.findByOrganizationIdAndDeletedFalse(orgId)).thenReturn(users);
    }
}
