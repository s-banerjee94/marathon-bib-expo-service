package com.timekeeper.bibexpo.bootstrap;

import com.timekeeper.bibexpo.user.api.RootAccountProvisioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RootUserInitializer implements CommandLineRunner {

    private final RootAccountProvisioner rootAccountProvisioner;

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

        rootAccountProvisioner.ensureRootAccount(rootUsername, rootPassword);
    }
}
