package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for user management operations
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController implements UserControllerApi {

    private final UserService userService;

    @Override
    public ResponseEntity<UserResponse> createUser(CreateUserRequest request, User currentUser) {
        log.info("Request to create user: {} by: {}", request.getUsername(), currentUser.getUsername());
        UserResponse response = userService.createUser(request, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<UserResponse> updateUser(Long userId, UpdateUserRequest request, User currentUser) {
        log.info("Request to update user ID: {} by: {}", userId, currentUser.getUsername());
        UserResponse response = userService.updateUser(userId, request, currentUser.getUsername());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> toggleUserEnabled(Long userId, User currentUser) {
        log.info("Request to toggle enabled status for user ID: {} by: {}", userId, currentUser.getUsername());
        UserResponse response = userService.toggleUserEnabled(userId, currentUser.getUsername());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> getUserById(Long userId, User currentUser) {
        log.info("Request to get user ID: {} by: {}", userId, currentUser.getUsername());
        UserResponse response = userService.getUserById(userId, currentUser.getUsername());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PageableResponse<UserResponse>> getUsers(UserRole role, Long organizationId,
                                                                    Boolean enabled, String search,
                                                                    Pageable pageable, User currentUser) {
        log.info("Request to get users by: {}", currentUser.getUsername());
        return ResponseEntity.ok(PageableResponse.of(
                userService.getUsers(role, organizationId, enabled, search, pageable,
                        currentUser.getUsername())));
    }

    @Override
    public ResponseEntity<UserResponse> getCurrentUser(User currentUser) {
        log.info("Request to get current user profile: {}", currentUser.getUsername());
        UserResponse response = userService.getCurrentUser(currentUser.getUsername());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteUser(Long userId, User currentUser) {
        log.info("Request to archive user ID: {} by: {}", userId, currentUser.getUsername());
        userService.deleteUser(userId, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
