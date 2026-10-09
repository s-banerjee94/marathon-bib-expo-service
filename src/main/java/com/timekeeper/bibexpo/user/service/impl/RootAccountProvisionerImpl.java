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
            log.debug("Root account already exists");
            return;
        }

        User rootUser = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .fullName("System Root Administrator")
                .role(UserRole.ROOT)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        userRepository.save(rootUser);
        log.atInfo().addKeyValue("event", "user.root.created").addKeyValue("targetUsername", username)
                .log("Root account created");
    }
}
