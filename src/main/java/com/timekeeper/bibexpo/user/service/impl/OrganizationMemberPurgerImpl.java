package com.timekeeper.bibexpo.user.service.impl;

import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.organization.api.OrganizationMemberPurger;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.model.enums.LockReason;
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
    public void lockMembers(Long organizationId) {
        List<User> toLock = userRepository.findByOrganizationId(organizationId).stream()
                .filter(User::isAccountNonLocked)
                .toList();
        toLock.forEach(user -> {
            user.setAccountNonLocked(false);
            user.setLockReason(LockReason.ORGANIZATION_DISABLED);
        });
        saveAndEvict(toLock);
        log.atInfo()
                .addKeyValue("event", "user.org_members.locked")
                .addKeyValue("targetOrgId", organizationId)
                .addKeyValue("count", toLock.size())
                .log("Organization members locked");
    }

    @Override
    @Transactional
    public int unlockMembersLockedByOrganization(Long organizationId) {
        List<User> toUnlock = userRepository.findByOrganizationId(organizationId).stream()
                .filter(user -> !user.isAccountNonLocked()
                        && user.getLockReason() == LockReason.ORGANIZATION_DISABLED)
                .toList();
        toUnlock.forEach(user -> {
            user.setAccountNonLocked(true);
            user.setLockReason(null);
        });
        saveAndEvict(toUnlock);
        log.atInfo()
                .addKeyValue("event", "user.org_members.unlocked")
                .addKeyValue("targetOrgId", organizationId)
                .addKeyValue("count", toUnlock.size())
                .log("Organization members unlocked");
        return toUnlock.size();
    }

    // Evicting drops the cached principal, so the new status applies from the next request.
    private void saveAndEvict(List<User> users) {
        userRepository.saveAll(users);
        users.forEach(user -> authUserCache.evict(user.getUsername()));
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
        log.atInfo()
                .addKeyValue("event", "user.org_members.purged")
                .addKeyValue("targetOrgId", organizationId)
                .addKeyValue("count", users.size())
                .log("Organization members purged");
        return users.size();
    }
}
