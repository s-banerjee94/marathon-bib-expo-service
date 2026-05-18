package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.JwtConfig;
import com.timekeeper.bibexpo.exception.AccountDisabledException;
import com.timekeeper.bibexpo.exception.CsrfValidationException;
import com.timekeeper.bibexpo.exception.InvalidCredentialsException;
import com.timekeeper.bibexpo.exception.JwtAuthenticationException;
import com.timekeeper.bibexpo.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.model.dto.response.RefreshResponse;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.CsrfTokenService;
import com.timekeeper.bibexpo.service.JwtService;
import com.timekeeper.bibexpo.service.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private JwtConfig jwtConfig;
    @Mock private SessionService sessionService;
    @Mock private CsrfTokenService csrfTokenService;
    @Mock private HttpServletRequest httpRequest;
    @Mock private HttpServletResponse httpResponse;

    @InjectMocks private AuthServiceImpl authService;

    @BeforeEach
    void setupCookieConfig() {
        when(jwtConfig.getRefreshCookieName()).thenReturn("refreshToken");
        when(jwtConfig.getCsrfCookieName()).thenReturn("csrfToken");
    }

    private User buildUser(Long id, String username, UserRole role, Organization org) {
        return User.builder()
                .id(id)
                .username(username)
                .password("hashed")
                .role(role)
                .organization(org)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .enabled(true)
                .build();
    }

    @Test
    void loginReturnsAccessTokenAndStartsSession() {
        LoginRequest request = new LoginRequest("jane.doe", "password");
        User user = buildUser(42L, "jane.doe", UserRole.ADMIN, null);

        when(userRepository.findByUsername("jane.doe")).thenReturn(Optional.of(user));
        when(sessionService.startSession(eq(user), any())).thenReturn("sid-1");
        when(jwtService.generateAccessToken(user, "sid-1")).thenReturn("access-jwt");
        when(jwtService.generateRefreshToken(user, "sid-1")).thenReturn("refresh-jwt");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900_000L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(csrfTokenService.generate()).thenReturn("csrf-value");

        LoginResponse response = authService.login(request, httpRequest, httpResponse);

        verify(authenticationManager).authenticate(argThat(token ->
                token instanceof UsernamePasswordAuthenticationToken
                        && "jane.doe".equals(token.getName())
                        && "password".equals(token.getCredentials())
        ));
        verify(sessionService).startSession(eq(user), any());

        assertEquals("access-jwt", response.getAccessToken());
        assertEquals(900_000L, response.getExpiresIn());
        assertEquals(42L, response.getUserId());
        assertEquals("jane.doe", response.getUsername());
        assertEquals("ADMIN", response.getRole());
        assertNull(response.getOrganizationId());
    }

    @Test
    void loginThrowsInvalidCredentialsForBadPassword() {
        LoginRequest request = new LoginRequest("john.doe", "bad-password");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad creds"));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request, httpRequest, httpResponse));

        verify(userRepository, never()).findByUsername(any());
        verify(sessionService, never()).startSession(any(), any());
    }

    @Test
    void loginRejectsDisabledAccount() {
        LoginRequest request = new LoginRequest("disabled.user", "password");
        User disabledUser = User.builder()
                .id(7L).username("disabled.user").password("hashed")
                .role(UserRole.ORGANIZER_USER)
                .accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true)
                .enabled(false)
                .build();
        when(userRepository.findByUsername("disabled.user")).thenReturn(Optional.of(disabledUser));

        assertThrows(AccountDisabledException.class,
                () -> authService.login(request, httpRequest, httpResponse));

        verify(sessionService, never()).startSession(any(), any());
    }

    @Test
    void loginIncludesOrganizationIdWhenPresent() {
        LoginRequest request = new LoginRequest("org.user", "password");
        Organization organization = Organization.builder()
                .id(99L).organizerName("Org").email("org@example.com").build();
        User userWithOrg = buildUser(3L, "org.user", UserRole.ORGANIZER_USER, organization);

        when(userRepository.findByUsername("org.user")).thenReturn(Optional.of(userWithOrg));
        when(sessionService.startSession(eq(userWithOrg), any())).thenReturn("sid-2");
        when(jwtService.generateAccessToken(userWithOrg, "sid-2")).thenReturn("jwt-with-org");
        when(jwtService.generateRefreshToken(userWithOrg, "sid-2")).thenReturn("refresh");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(5000L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(csrfTokenService.generate()).thenReturn("csrf");

        LoginResponse response = authService.login(request, httpRequest, httpResponse);

        assertEquals(99L, response.getOrganizationId());
        assertEquals("jwt-with-org", response.getAccessToken());
    }

    @Test
    void refreshRejectsRequestWhenCsrfMismatch() {
        when(httpRequest.getHeader("X-CSRF-Token")).thenReturn("a");
        when(httpRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("csrfToken", "b")});
        when(csrfTokenService.matches("a", "b")).thenReturn(false);

        assertThrows(CsrfValidationException.class,
                () -> authService.refresh(httpRequest, httpResponse));
        verify(sessionService, never()).extendSession(any());
    }

    @Test
    void refreshExtendsSessionAndKeepsSameSid() {
        User user = buildUser(10L, "alice", UserRole.ORGANIZER_ADMIN, null);

        when(httpRequest.getHeader("X-CSRF-Token")).thenReturn("csrf");
        when(httpRequest.getCookies()).thenReturn(new Cookie[]{
                new Cookie("csrfToken", "csrf"),
                new Cookie("refreshToken", "old-refresh")
        });
        when(csrfTokenService.matches("csrf", "csrf")).thenReturn(true);

        when(jwtService.extractUsername("old-refresh")).thenReturn("alice");
        when(jwtService.extractTokenType("old-refresh")).thenReturn(JwtService.TYPE_REFRESH);
        when(jwtService.extractSid("old-refresh")).thenReturn("stable-sid");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(sessionService.getActiveSid("alice")).thenReturn("stable-sid");
        when(jwtService.generateAccessToken(user, "stable-sid")).thenReturn("new-access");
        when(jwtService.generateRefreshToken(user, "stable-sid")).thenReturn("new-refresh");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900_000L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);

        RefreshResponse response = authService.refresh(httpRequest, httpResponse);

        assertEquals("new-access", response.getAccessToken());
        assertEquals(900_000L, response.getExpiresIn());
        verify(sessionService).extendSession(user);
    }

    @Test
    void refreshDetectsReuseAndEndsSession() {
        User user = buildUser(11L, "bob", UserRole.ORGANIZER_ADMIN, null);

        when(httpRequest.getHeader("X-CSRF-Token")).thenReturn("c");
        when(httpRequest.getCookies()).thenReturn(new Cookie[]{
                new Cookie("csrfToken", "c"),
                new Cookie("refreshToken", "stolen-refresh")
        });
        when(csrfTokenService.matches("c", "c")).thenReturn(true);

        when(jwtService.extractUsername("stolen-refresh")).thenReturn("bob");
        when(jwtService.extractTokenType("stolen-refresh")).thenReturn(JwtService.TYPE_REFRESH);
        when(jwtService.extractSid("stolen-refresh")).thenReturn("old-sid");

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(jwtConfig.getCookieSecure()).thenReturn(false);
        // active sid has already been rotated to something else
        when(sessionService.getActiveSid("bob")).thenReturn("current-sid");

        assertThrows(JwtAuthenticationException.class,
                () -> authService.refresh(httpRequest, httpResponse));
        verify(sessionService).endSession(user);
    }

    @Test
    void logoutEndsSessionAndClearsCookies() {
        User user = buildUser(20L, "carol", UserRole.ORGANIZER_USER, null);
        when(jwtConfig.getCookieSecure()).thenReturn(false);

        authService.logout(user, httpResponse);

        verify(sessionService).endSession(user);
        verify(httpResponse, org.mockito.Mockito.atLeastOnce()).addHeader(eq("Set-Cookie"), anyString());
    }
}
