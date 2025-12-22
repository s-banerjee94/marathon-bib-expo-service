package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.InvalidCredentialsException;
import com.timekeeper.bibexpo.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication endpoints for user login")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JwtConfig jwtConfig;

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticate user with username and password, returns JWT token",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Login successful",
                            content = @Content(schema = @Schema(implementation = LoginResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Invalid credentials or account disabled/locked"
                    )
            }
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
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
            if (!user.isEnabled()) {
                log.warn("Login attempt for disabled account: {}", request.getUsername());
                throw new InvalidCredentialsException("Account is disabled");
            }

            if (!user.isAccountNonLocked()) {
                log.warn("Login attempt for locked account: {}", request.getUsername());
                throw new InvalidCredentialsException("Account is locked");
            }

            if (!user.isAccountNonExpired()) {
                log.warn("Login attempt for expired account: {}", request.getUsername());
                throw new InvalidCredentialsException("Account has expired");
            }

            if (!user.isCredentialsNonExpired()) {
                log.warn("Login attempt for account with expired credentials: {}", request.getUsername());
                throw new InvalidCredentialsException("Credentials have expired");
            }

            // Generate JWT token
            String token = jwtService.generateToken(user);

            // Build response
            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(jwtConfig.getExpiration())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .organizationId(user.getOrganization() != null ? user.getOrganization().getId() : null)
                    .build();

            log.info("Login successful for user: {}", request.getUsername());
            return ResponseEntity.ok(response);

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
}
