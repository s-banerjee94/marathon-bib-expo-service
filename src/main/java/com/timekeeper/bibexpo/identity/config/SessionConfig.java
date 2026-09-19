package com.timekeeper.bibexpo.identity.config;

import com.timekeeper.bibexpo.shared.security.UserRole;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "session")
@Data
public class SessionConfig {

    /** Devices allowed to any role the map below does not name. */
    private Integer defaultMaxDevices = 1;

    private Map<UserRole, Integer> maxDevices = new EnumMap<>(UserRole.class);

    /**
     * How many devices this role may stay signed in on at once. Raising a role is a config
     * change, not a code change, so the limit can be tuned per environment.
     *
     * @param role the role of the user logging in, may be null
     * @return the device limit, always at least 1
     */
    public int maxDevicesFor(UserRole role) {
        Integer configured = role == null ? null : maxDevices.get(role);
        int limit = configured != null ? configured : defaultMaxDevices;
        return Math.max(limit, 1);
    }
}
