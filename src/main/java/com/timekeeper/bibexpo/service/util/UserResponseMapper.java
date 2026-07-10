package com.timekeeper.bibexpo.service.util;

import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link User} to its API response, presigning a short-lived URL for the
 * profile picture. All user read paths go through here so the stored object key
 * is never exposed directly.
 */
@Component
@RequiredArgsConstructor
public class UserResponseMapper {

    private final StorageService storageService;

    public UserResponse toResponse(User user) {
        UserResponse response = UserResponse.fromEntity(user);
        response.setProfilePictureUrl(storageService.createDownloadUrl(user.getProfilePictureKey()));
        return response;
    }
}
