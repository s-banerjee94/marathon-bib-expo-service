package com.timekeeper.bibexpo.bootstrap;

import com.timekeeper.bibexpo.user.api.RootAccountProvisioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

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
        if (!StringUtils.hasText(rootUsername) || !StringUtils.hasText(rootPassword)) {
            log.atWarn().addKeyValue("event", "root.provision.skipped")
                    .log("ROOT_USERNAME or ROOT_PASSWORD is not set, so no root account was provisioned");
            return;
        }

        rootAccountProvisioner.ensureRootAccount(rootUsername, rootPassword);
    }
}
