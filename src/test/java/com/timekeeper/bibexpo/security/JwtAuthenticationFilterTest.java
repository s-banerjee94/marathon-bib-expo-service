package com.timekeeper.bibexpo.security;

import com.timekeeper.bibexpo.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private UserDetails userDetails;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsProcessingWhenNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(jwtService, userDetailsService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void skipsProcessingWhenAuthorizationHeaderIsNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(jwtService, userDetailsService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void setsAuthenticationInContextForValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractUsername("valid.jwt.token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid.jwt.token", userDetails)).thenReturn(true);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertSame(userDetails, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void doesNotSetAuthenticationWhenTokenValidationFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token");
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractUsername("invalid.token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.isTokenValid("invalid.token", userDetails)).thenReturn(false);

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNotSetAuthenticationWhenUsernameExtractionThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad.token");
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractUsername(anyString())).thenThrow(new RuntimeException("parse error"));

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(userDetailsService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void skipsUserLoadingWhenExtractedUsernameIsNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.token");
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractUsername("some.token")).thenReturn(null);

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(userDetailsService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void alwaysContinuesFilterChainRegardlessOfTokenValidity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer any.token");
        MockFilterChain chain = spy(new MockFilterChain());

        when(jwtService.extractUsername("any.token")).thenThrow(new RuntimeException("bad"));

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        // Filter chain must always proceed — unauthorized response is handled by entry point
        assertNotNull(chain.getRequest());
    }
}
