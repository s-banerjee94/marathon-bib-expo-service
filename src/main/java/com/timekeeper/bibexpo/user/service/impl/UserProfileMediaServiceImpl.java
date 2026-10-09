package com.timekeeper.bibexpo.user.service.impl;

import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.storage.exception.InvalidFileException;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.storage.model.enums.UploadCategory;
import com.timekeeper.bibexpo.storage.service.StorageService;
import com.timekeeper.bibexpo.user.exception.UserNotFoundException;
import com.timekeeper.bibexpo.user.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.repository.UserRepository;
import com.timekeeper.bibexpo.user.service.cache.AuthUserCache;
import com.timekeeper.bibexpo.user.service.UserProfileMediaService;
import com.timekeeper.bibexpo.user.service.util.UserResponseMapper;
import com.timekeeper.bibexpo.user.service.validator.UserAccessPolicy;
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
        User targetUser = fetchTargetUser(userId);
        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);

        if (UploadCategory.PROFILE_PICTURE.isForeignKeyFor(targetUser.getId(), objectKey)) {
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
        log.atInfo().addKeyValue("event", "user.picture.attached").addKeyValue("targetUserId", userId)
                .log("Profile picture attached");
        return responseMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse removeProfilePicture(Long userId, CurrentActor actor) {
        User targetUser = fetchTargetUser(userId);
        accessPolicy.validateUpdateUserAuthorization(actor, targetUser);

        String previousKey = targetUser.getProfilePictureKey();
        targetUser.setProfilePictureKey(null);
        User saved = userRepository.saveAndFlush(targetUser);
        authUserCache.evict(saved.getUsername());
        deletePictureQuietly(previousKey);
        log.atInfo().addKeyValue("event", "user.picture.removed").addKeyValue("targetUserId", userId)
                .log("Profile picture removed");
        return responseMapper.toResponse(saved);
    }

    @Override
    public void deletePictureQuietly(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.atWarn()
                    .addKeyValue("event", "storage.delete.failed")
                    .addKeyValue("objectKey", objectKey)
                    .setCause(e)
                    .log("Could not delete an orphaned profile picture");
        }
    }

    private User fetchTargetUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
