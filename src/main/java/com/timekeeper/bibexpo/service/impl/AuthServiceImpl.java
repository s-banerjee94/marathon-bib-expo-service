package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.InvalidCredentialsException;
import com.timekeeper.bibexpo.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.AuthService;
import com.timekeeper.bibexpo.service.JwtService;
import com.timekeeper.bibexpo.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JwtConfig jwtConfig;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Load user entity
            User user = userRepository.findByUsernameAndDeletedFalse(request.getUsername())
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

            // Check account status
            validateUserAccount(user, request.getUsername());

            // Generate JWT token
            String token = jwtService.generateToken(user);

            // Build response
            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(jwtConfig.getExpiration())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .organizationId(user.getOrganization() != null ? user.getOrganization().getId() : null)
                    .build();

            log.info("Login successful for user: {}", request.getUsername());
            return response;

        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}", request.getUsername());
            throw new InvalidCredentialsException("Invalid username or password");
        } catch (DisabledException e) {
            log.error("Account disabled: {}", request.getUsername());
            throw new InvalidCredentialsException("Account is disabled");
        } catch (LockedException e) {
            log.error("Account locked: {}", request.getUsername());
            throw new InvalidCredentialsException("Account is locked");
        }
    }

    @Override
    public void logout(User user) {
        sseEmitterRegistry.removeAll(user.getId());
        log.info("User {} logged out, SSE connections closed", user.getUsername());
    }

    private void validateUserAccount(User user, String username) {
        if (!user.isEnabled()) {
            log.warn("Login attempt for disabled account: {}", username);
            throw new InvalidCredentialsException("Account is disabled");
        }

        if (!user.isAccountNonLocked()) {
            log.warn("Login attempt for locked account: {}", username);
            throw new InvalidCredentialsException("Account is locked");
        }

        if (!user.isAccountNonExpired()) {
            log.warn("Login attempt for expired account: {}", username);
            throw new InvalidCredentialsException("Account has expired");
        }

        if (!user.isCredentialsNonExpired()) {
            log.warn("Login attempt for account with expired credentials: {}", username);
            throw new InvalidCredentialsException("Credentials have expired");
        }
    }
}
