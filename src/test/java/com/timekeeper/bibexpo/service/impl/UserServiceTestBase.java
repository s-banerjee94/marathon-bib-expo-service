package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

abstract class UserServiceTestBase {

    @Mock UserRepository userRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    UserServiceImpl userService;

    // ── Fixtures ──────────────────────────────────────────────────────────

    Organization org(long id) {
        return Organization.builder()
                .id(id).organizerName("Test Org").email("org@test.com")
                .enabled(true).deleted(false)
                .maxOrganizerUsers(5).maxDistributors(30)
                .build();
    }

    User user(long id, UserRole role, Organization organization) {
        return User.builder()
                .id(id).username("user" + id).password("hashed").role(role)
                .organization(organization).enabled(true).deleted(false)
                .accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true)
                .build();
    }

    CreateUserRequest.CreateUserRequestBuilder baseRequest(String username, UserRole role) {
        return CreateUserRequest.builder()
                .username(username).password("Password1!")
                .fullName("Test User").role(role.name())
                .email("test@test.com").phoneNumber("9876543210");
    }

    // ── Mock-setup helpers ────────────────────────────────────────────────

    void mockCurrentUser(User u) {
        when(userRepository.findByUsernameAndDeletedFalse(u.getUsername())).thenReturn(Optional.of(u));
    }

    void mockTargetUser(User u) {
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
    }

    void mockSave(User saved) {
        when(userRepository.save(any())).thenReturn(saved);
    }

    void mockOrganization(Organization o) {
        when(organizationRepository.findById(o.getId())).thenReturn(Optional.of(o));
    }

    void mockOrgUserCount(long orgId, UserRole role, long count) {
        when(userRepository.countByOrganizationIdAndRoleAndDeletedFalse(orgId, role)).thenReturn(count);
    }

    void mockPasswordEncoding() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
    }
}
