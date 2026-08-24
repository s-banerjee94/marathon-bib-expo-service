package com.timekeeper.bibexpo.user.service.impl;

import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.api.RootAccountProvisioner;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RootAccountProvisionerImpl implements RootAccountProvisioner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void ensureRootAccount(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            log.info("Root user '{}' already exists. Skipping creation.", username);
            return;
        }

        User rootUser = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .fullName("System Root Administrator")
                .role(UserRole.ROOT)
                .email(null)  // Optional for root user
                .organization(null)  // Root users don't belong to any organization
                .enabled(true)
                .accountNonLocked(true)
                .build();

        userRepository.save(rootUser);
        log.info("Root user '{}' created successfully with role ROOT", username);
    }
}
