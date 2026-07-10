package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.InvalidFileException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.UploadCategory;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.security.CurrentActor;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.service.UserProfileMediaService;
import com.timekeeper.bibexpo.service.cache.AuthUserCache;
import com.timekeeper.bibexpo.service.util.UserResponseMapper;
import com.timekeeper.bibexpo.service.validator.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileMediaServiceImpl implements UserProfileMediaService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuthUserCache authUserCache;
    private final UserAccessPolicy accessPolicy;
    private final UserResponseMapper responseMapper;

    @Override
    @Transactional(readOnly = true)
    public PresignUploadResponse createProfilePictureUploadUrl(Long userId, String contentType, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);
        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);
        return storageService.createUploadUrl(UploadCategory.PROFILE_PICTURE, targetUser.getId(), contentType);
    }

    @Override
    @Transactional
    public UserResponse attachProfilePicture(Long userId, String objectKey, CurrentActor actor) {
        log.info("Attaching profile picture for user ID: {} by: {}", userId, actor.username());
        User targetUser = fetchTargetUser(userId);
        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);

        if (UploadCategory.PROFILE_PICTURE.ownsKey(targetUser.getId(), objectKey)) {
            throw new InvalidFileException("This upload does not belong to this profile.");
        }
        if (!storageService.objectExists(objectKey)) {
            throw new InvalidFileException("The uploaded file could not be found.");
        }

        String previousKey = targetUser.getProfilePictureKey();
        targetUser.setProfilePictureKey(objectKey);
        User saved = userRepository.saveAndFlush(targetUser);
        authUserCache.evict(saved.getUsername());
        if (previousKey != null && !previousKey.equals(objectKey)) {
            deletePictureQuietly(previousKey);
        }
        log.info("Successfully attached profile picture for user ID: {}", userId);
        return responseMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse removeProfilePicture(Long userId, CurrentActor actor) {
        log.info("Removing profile picture for user ID: {} by: {}", userId, actor.username());
        User targetUser = fetchTargetUser(userId);
        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);

        String previousKey = targetUser.getProfilePictureKey();
        targetUser.setProfilePictureKey(null);
        User saved = userRepository.saveAndFlush(targetUser);
        authUserCache.evict(saved.getUsername());
        deletePictureQuietly(previousKey);
        log.info("Successfully removed profile picture for user ID: {}", userId);
        return responseMapper.toResponse(saved);
    }

    @Override
    public void deletePictureQuietly(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete object {}: {}", objectKey, e.getMessage());
        }
    }

    private User fetchTargetUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userId);
                    return new UserNotFoundException();
                });
    }
}
