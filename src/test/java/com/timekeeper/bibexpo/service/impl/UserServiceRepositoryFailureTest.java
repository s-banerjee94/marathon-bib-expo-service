package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRepositoryFailureTest extends UserServiceTestBase {

    @Test
    @DisplayName("createUser propagates DataIntegrityViolationException from save (race condition on unique constraint)")
    void createUserPropagatesDataIntegrityViolationOnSave() {
        User root = user(1L, UserRole.ROOT, null);
        CreateUserRequest request = baseRequest("new.admin", UserRole.ADMIN).build();

        mockCurrentUser(root);
        mockPasswordEncoding();
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertThatThrownBy(() -> userService.createUser(request, root.getUsername()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("updateUser propagates DataIntegrityViolationException from save (race condition on unique constraint)")
    void updateUserPropagatesDataIntegrityViolationOnSave() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        UpdateUserRequest request = UpdateUserRequest.builder().fullName("New Name").build();

        mockCurrentUser(root);
        mockTargetUser(target);
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertThatThrownBy(() -> userService.updateUser(10L, request, root.getUsername()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("toggleUserEnabled propagates DataIntegrityViolationException from save")
    void toggleUserEnabledPropagatesDataIntegrityViolationOnSave() {
        User root = user(1L, UserRole.ROOT, null);
        User target = user(10L, UserRole.ORGANIZER_USER, org(20L));
        target.setEnabled(true);

        mockCurrentUser(root);
        mockTargetUser(target);
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("constraint violation"));

        assertThatThrownBy(() -> userService.toggleUserEnabled(10L, root.getUsername()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
