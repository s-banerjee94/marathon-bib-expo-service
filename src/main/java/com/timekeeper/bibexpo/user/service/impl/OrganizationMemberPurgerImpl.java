package com.timekeeper.bibexpo.user.service.impl;

import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.organization.api.OrganizationMemberPurger;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.repository.UserArchiveRepository;
import com.timekeeper.bibexpo.user.repository.UserRepository;
import com.timekeeper.bibexpo.user.service.cache.AuthUserCache;
import com.timekeeper.bibexpo.user.service.UserProfileMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationMemberPurgerImpl implements OrganizationMemberPurger {

    private final UserRepository userRepository;
    private final UserArchiveRepository userArchiveRepository;
    private final NotificationService notificationService;
    private final AuthUserCache authUserCache;
    private final UserProfileMediaService profileMediaService;

    @Override
    @Transactional
    public void disableMembers(Long organizationId) {
        List<User> organizationUsers = userRepository.findByOrganizationId(organizationId);

        if (organizationUsers.isEmpty()) {
            log.info("No users found for organization ID: {}", organizationId);
            return;
        }

        log.info("Disabling {} users for organization ID: {}", organizationUsers.size(), organizationId);

        organizationUsers.forEach(user -> {
            user.setEnabled(false);
            log.debug("Disabling user: {} (ID: {})", user.getUsername(), user.getId());
        });

        userRepository.saveAll(organizationUsers);
        // Drop the disabled users from the auth cache so they cannot keep authenticating.
        organizationUsers.forEach(user -> authUserCache.evict(user.getUsername()));
        log.info("Successfully disabled all users for organization ID: {}", organizationId);
    }

    @Override
    @Transactional
    public int purgeMembers(Long organizationId) {
        List<User> users = userRepository.findByOrganizationId(organizationId);
        for (User user : users) {
            notificationService.deleteAllForUser(user.getId());
            authUserCache.evict(user.getUsername());
            profileMediaService.deletePictureQuietly(user.getProfilePictureKey());
        }
        userRepository.deleteAll(users);
        // Archived (former) users still FK the organization, so drop those rows before it is removed.
        userArchiveRepository.deleteByOrganizationId(organizationId);
        userRepository.flush();
        log.info("Purged {} users for organization ID: {}", users.size(), organizationId);
        return users.size();
    }
}
