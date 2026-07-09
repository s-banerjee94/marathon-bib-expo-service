package com.timekeeper.bibexpo.service.util;

import com.timekeeper.bibexpo.model.dto.notification.NotifyRequest;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NotifyRequest}'s audience into the concrete list of recipient users, reusing the
 * existing role/organization lookups on {@link UserRepository}. Results are de-duplicated by user id.
 */
@Component
@RequiredArgsConstructor
public class NotificationRecipientResolver {

    private final UserRepository userRepository;

    public List<User> resolve(NotifyRequest req) {
        return switch (req.getAudience()) {
            case PLATFORM_ADMINS -> dedup(userRepository.findByRole(UserRole.ROOT),
                                          userRepository.findByRole(UserRole.ADMIN));
            case ROOT  -> userRepository.findByRole(UserRole.ROOT);
            case ADMIN -> userRepository.findByRole(UserRole.ADMIN);
            case USER  -> resolveSingleUser(req.getTargetUserId());
            case ORGANIZATION_ALL -> userRepository.findByOrganizationId(requireOrg(req));
            case ORGANIZATION_ADMINS ->
                    userRepository.findByRoleAndOrganizationId(UserRole.ORGANIZER_ADMIN, requireOrg(req));
            case ORGANIZATION_STAFF -> {
                Long orgId = requireOrg(req);
                yield dedup(userRepository.findByRoleAndOrganizationId(UserRole.ORGANIZER_ADMIN, orgId),
                            userRepository.findByRoleAndOrganizationId(UserRole.ORGANIZER_USER, orgId));
            }
            case ORGANIZATION_DISTRIBUTORS ->
                    userRepository.findByRoleAndOrganizationId(UserRole.DISTRIBUTOR, requireOrg(req));
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
        return userRepository.findById(userId).map(List::of).orElseGet(List::of);
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
