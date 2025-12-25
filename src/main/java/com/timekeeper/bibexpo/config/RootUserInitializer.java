package com.timekeeper.bibexpo.config;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RootUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${root.username:#{null}}")
    private String rootUsername;

    @Value("${root.password:#{null}}")
    private String rootPassword;

    @Override
    public void run(String... args) {
        // Skip if environment variables are not set
        if (rootUsername == null || rootUsername.isBlank() ||
            rootPassword == null || rootPassword.isBlank()) {
            log.warn("ROOT_USERNAME or ROOT_PASSWORD not set. Skipping root user creation.");
            return;
        }

        // Check if root user already exists
        if (userRepository.existsByUsernameAndDeletedFalse(rootUsername)) {
            log.info("Root user '{}' already exists. Skipping creation.", rootUsername);
            return;
        }

        // Create root user
        User rootUser = User.builder()
                .username(rootUsername)
                .password(passwordEncoder.encode(rootPassword))
                .fullName("System Root Administrator")
                .role(UserRole.ROOT)
                .email(null)  // Optional for root user
                .organization(null)  // Root users don't belong to any organization
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .deleted(false)
                .build();

        userRepository.save(rootUser);
        log.info("Root user '{}' created successfully with role ROOT", rootUsername);
    }
}
