package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.AccountDisabledException;
import com.timekeeper.bibexpo.exception.InvalidCredentialsException;
import com.timekeeper.bibexpo.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.JwtService;
import com.timekeeper.bibexpo.service.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private JwtConfig jwtConfig;
    @Mock private SseEmitterRegistry sseEmitterRegistry;

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser(long id, String username, UserRole role) {
        return User.builder()
                .id(id).username(username).password("hashed").role(role)
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).enabled(true).deleted(false)
                .build();
    }

    @Test
    void loginReturnsTokenWhenCredentialsValid() {
        LoginRequest request = new LoginRequest("jane.doe", "password");
        User user = activeUser(42L, "jane.doe", UserRole.ADMIN);

        when(userRepository.findByUsernameAndDeletedFalse("jane.doe")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("signed-jwt");
        when(jwtConfig.getExpiration()).thenReturn(604800000L);

        LoginResponse response = authService.login(request);

        verify(authenticationManager).authenticate(argThat(token ->
                token instanceof UsernamePasswordAuthenticationToken
                        && "jane.doe".equals(token.getName())
                        && "password".equals(token.getCredentials())
        ));
        verify(jwtService).generateToken(user);

        assertEquals("signed-jwt", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(604800000L, response.getExpiresIn());
        assertEquals(42L, response.getUserId());
        assertEquals("jane.doe", response.getUsername());
        assertEquals("ADMIN", response.getRole());
        assertNull(response.getOrganizationId());
    }

    @Test
    void loginIncludesOrganizationIdWhenPresent() {
        LoginRequest request = new LoginRequest("org.user", "password");
        Organization organization = Organization.builder()
                .id(99L).organizerName("Org").email("org@example.com").build();
        User user = User.builder()
                .id(3L).username("org.user").password("hashed")
                .role(UserRole.ORGANIZER_USER).organization(organization)
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).enabled(true).deleted(false)
                .build();

        when(userRepository.findByUsernameAndDeletedFalse("org.user")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-with-org");
        when(jwtConfig.getExpiration()).thenReturn(5000L);

        LoginResponse response = authService.login(request);

        assertEquals(99L, response.getOrganizationId());
        assertEquals("jwt-with-org", response.getToken());
    }

    @Test
    void loginThrowsInvalidCredentialsForBadPassword() {
        LoginRequest request = new LoginRequest("john.doe", "bad-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        verify(userRepository, never()).findByUsernameAndDeletedFalse(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginThrowsAccountDisabledWhenAuthManagerThrowsDisabledException() {
        LoginRequest request = new LoginRequest("disabled.user", "password");
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThrows(AccountDisabledException.class, () -> authService.login(request));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginThrowsAccountDisabledWhenAuthManagerThrowsLockedException() {
        LoginRequest request = new LoginRequest("locked.user", "password");
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));

        assertThrows(AccountDisabledException.class, () -> authService.login(request));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginThrowsWhenUserNotFoundAfterAuthentication() {
        LoginRequest request = new LoginRequest("ghost.user", "password");
        when(userRepository.findByUsernameAndDeletedFalse("ghost.user")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        verify(authenticationManager).authenticate(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginThrowsAccountDisabledForDisabledUser() {
        LoginRequest request = new LoginRequest("disabled.user", "password");
        User disabledUser = User.builder()
                .id(7L).username("disabled.user").password("hashed")
                .role(UserRole.ORGANIZER_USER)
                .accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true)
                .enabled(false).deleted(false)
                .build();

        when(userRepository.findByUsernameAndDeletedFalse("disabled.user")).thenReturn(Optional.of(disabledUser));

        assertThrows(AccountDisabledException.class, () -> authService.login(request));

        verify(authenticationManager).authenticate(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginThrowsAccountDisabledForLockedUser() {
        LoginRequest request = new LoginRequest("locked.user", "password");
        User lockedUser = User.builder()
                .id(8L).username("locked.user").password("hashed")
                .role(UserRole.ORGANIZER_ADMIN)
                .accountNonExpired(true).accountNonLocked(false).credentialsNonExpired(true)
                .enabled(true).deleted(false)
                .build();

        when(userRepository.findByUsernameAndDeletedFalse("locked.user")).thenReturn(Optional.of(lockedUser));

        assertThrows(AccountDisabledException.class, () -> authService.login(request));

        verify(authenticationManager).authenticate(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginThrowsAccountDisabledForExpiredAccount() {
        LoginRequest request = new LoginRequest("expired.user", "password");
        User expiredUser = User.builder()
                .id(9L).username("expired.user").password("hashed")
                .role(UserRole.DISTRIBUTOR)
                .accountNonExpired(false).accountNonLocked(true).credentialsNonExpired(true)
                .enabled(true).deleted(false)
                .build();

        when(userRepository.findByUsernameAndDeletedFalse("expired.user")).thenReturn(Optional.of(expiredUser));

        assertThrows(AccountDisabledException.class, () -> authService.login(request));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginThrowsAccountDisabledForExpiredCredentials() {
        LoginRequest request = new LoginRequest("cred.expired", "password");
        User user = User.builder()
                .id(10L).username("cred.expired").password("hashed")
                .role(UserRole.ORGANIZER_USER)
                .accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(false)
                .enabled(true).deleted(false)
                .build();

        when(userRepository.findByUsernameAndDeletedFalse("cred.expired")).thenReturn(Optional.of(user));

        assertThrows(AccountDisabledException.class, () -> authService.login(request));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void logoutRemovesAllSseConnections() {
        User user = activeUser(1L, "jane.doe", UserRole.ADMIN);

        authService.logout(user);

        verify(sseEmitterRegistry).removeAll(1L);
    }
}
