package com.timekeeper.bibexpo.user.service.impl;

import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.user.api.UserDirectory;
import com.timekeeper.bibexpo.user.exception.UserNotFoundException;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Carries no transaction of its own beyond {@link #findByLoginIdentifier}: every other method is a
 * single repository call, already read-only through Spring Data, and callers are typically inside
 * their own transaction anyway.
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryImpl implements UserDirectory {

    private final UserRepository userRepository;

    @Override
    public Optional<User> findById(Long userId) {
        return userId == null ? Optional.empty() : userRepository.findById(userId);
    }

    @Override
    public User requireById(Long userId) {
        return findById(userId).orElseThrow(UserNotFoundException::new);
    }

    @Override
    public Optional<String> findUsername(Long userId) {
        return findById(userId).map(User::getUsername);
    }

    /** The three lookups run in one transaction so an identifier is resolved against one snapshot. */
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByLoginIdentifier(String identifier) {
        String value = TextUtils.trimToNull(identifier);
        if (value == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(value)
                .or(() -> userRepository.findByEmail(value))
                .or(() -> userRepository.findByPhoneNumber(value));
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    @Override
    public List<User> findByRoleAndOrganizationId(UserRole role, Long organizationId) {
        return userRepository.findByRoleAndOrganizationId(role, organizationId);
    }

    @Override
    public List<User> findByOrganizationId(Long organizationId) {
        return userRepository.findByOrganizationId(organizationId);
    }
}
