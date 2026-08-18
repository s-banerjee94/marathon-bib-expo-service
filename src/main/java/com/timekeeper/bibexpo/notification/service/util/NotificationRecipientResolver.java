package com.timekeeper.bibexpo.notification.service.util;

import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.api.UserDirectory;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NotifyRequest}'s audience into the concrete list of recipient users, reusing the
 * role and organization lookups published by {@link UserDirectory}. Results are de-duplicated by user id.
 */
@Component
@RequiredArgsConstructor
public class NotificationRecipientResolver {

    private final UserDirectory userDirectory;

    public List<User> resolve(NotifyRequest req) {
        return switch (req.getAudience()) {
            case PLATFORM_ADMINS -> dedup(userDirectory.findByRole(UserRole.ROOT),
                                          userDirectory.findByRole(UserRole.ADMIN));
            case ROOT  -> userDirectory.findByRole(UserRole.ROOT);
            case ADMIN -> userDirectory.findByRole(UserRole.ADMIN);
            case USER  -> resolveSingleUser(req.getTargetUserId());
            case ORGANIZATION_ALL -> userDirectory.findByOrganizationId(requireOrg(req));
            case ORGANIZATION_ADMINS ->
                    userDirectory.findByRoleAndOrganizationId(UserRole.ORGANIZER_ADMIN, requireOrg(req));
            case ORGANIZATION_STAFF -> {
                Long orgId = requireOrg(req);
                yield dedup(userDirectory.findByRoleAndOrganizationId(UserRole.ORGANIZER_ADMIN, orgId),
                            userDirectory.findByRoleAndOrganizationId(UserRole.ORGANIZER_USER, orgId));
            }
            case ORGANIZATION_DISTRIBUTORS ->
                    userDirectory.findByRoleAndOrganizationId(UserRole.DISTRIBUTOR, requireOrg(req));
        };
    }

    private Long requireOrg(NotifyRequest req) {
        if (req.getOrganizationId() == null) {
            throw new IllegalArgumentException("organizationId is required for audience " + req.getAudience());
        }
        return req.getOrganizationId();
    }

    private List<User> resolveSingleUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("targetUserId is required for the USER audience");
        }
        return userDirectory.findById(userId).map(List::of).orElseGet(List::of);
    }

    @SafeVarargs
    private static List<User> dedup(List<User>... lists) {
        Map<Long, User> byId = new LinkedHashMap<>();
        for (List<User> list : lists) {
            for (User user : list) {
                byId.putIfAbsent(user.getId(), user);
            }
        }
        return new ArrayList<>(byId.values());
    }
}
