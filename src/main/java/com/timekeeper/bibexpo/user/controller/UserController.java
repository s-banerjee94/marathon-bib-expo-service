package com.timekeeper.bibexpo.user.controller;

import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.shared.web.PageableResponse;
import com.timekeeper.bibexpo.storage.model.dto.request.AttachUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.request.PresignUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.user.model.dto.request.ChangePasswordRequest;
import com.timekeeper.bibexpo.user.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.user.model.dto.request.ReassignDistributorEventRequest;
import com.timekeeper.bibexpo.user.model.dto.request.UpdateUserRequest;
import com.timekeeper.bibexpo.user.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.service.UserProfileMediaService;
import com.timekeeper.bibexpo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for user management operations
 */
@RestController
@RequiredArgsConstructor
public class UserController implements UserControllerApi {

    private final UserService userService;
    private final UserProfileMediaService profileMediaService;

    @Override
    public ResponseEntity<UserResponse> createUser(CreateUserRequest request, User currentUser) {
        UserResponse response = userService.createUser(request, CurrentActor.from(currentUser));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<UserResponse> updateUser(Long userId, UpdateUserRequest request, User currentUser) {
        UserResponse response = userService.updateUser(userId, request, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> changeOwnPassword(ChangePasswordRequest request, User currentUser) {
        userService.changeOwnPassword(CurrentActor.from(currentUser), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserResponse> reassignDistributorEvent(
            Long userId, ReassignDistributorEventRequest request, User currentUser) {
        UserResponse response = userService.reassignDistributorEvent(
                userId, request.getEventId(), CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> toggleUserEnabled(Long userId, User currentUser) {
        UserResponse response = userService.toggleUserEnabled(userId, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> toggleUserLocked(Long userId, User currentUser) {
        UserResponse response = userService.toggleUserLocked(userId, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> getUserById(Long userId, User currentUser) {
        UserResponse response = userService.getUserById(userId, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> getUserByUsername(String username, User currentUser) {
        UserResponse response = userService.getUserByUsername(username, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PageableResponse<UserResponse>> getUsers(List<UserRole> roles, Long organizationId,
                                                                    Long eventId, Boolean enabled, String search,
                                                                    Pageable pageable, User currentUser) {
        return ResponseEntity.ok(PageableResponse.of(
                userService.getUsers(roles, organizationId, eventId, enabled, search, pageable,
                        CurrentActor.from(currentUser))));
    }

    @Override
    public ResponseEntity<UserResponse> getCurrentUser(User currentUser) {
        UserResponse response = userService.getCurrentUser(CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteUser(Long userId, User currentUser) {
        userService.deleteUser(userId, CurrentActor.from(currentUser));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PresignUploadResponse> createProfilePictureUploadUrl(
            Long userId, PresignUploadRequest request, User currentUser) {
        PresignUploadResponse response = profileMediaService.createProfilePictureUploadUrl(
                userId, request.getContentType(), CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> attachProfilePicture(
            Long userId, AttachUploadRequest request, User currentUser) {
        UserResponse response = profileMediaService.attachProfilePicture(
                userId, request.getObjectKey(), CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> removeProfilePicture(Long userId, User currentUser) {
        UserResponse response = profileMediaService.removeProfilePicture(userId, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }
}
