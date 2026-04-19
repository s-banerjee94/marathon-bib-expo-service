package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private CustomUserDetailsService userDetailsService;

    @Test
    void loadUserByUsernameReturnsUserWhenFound() {
        User user = User.builder()
                .id(1L).username("alice").password("hashed").role(UserRole.ADMIN)
                .enabled(true).accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).deleted(false)
                .build();
        when(userRepository.findByUsernameAndDeletedFalse("alice")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("alice");

        assertSame(user, result);
        assertEquals("alice", result.getUsername());
    }

    @Test
    void loadUserByUsernameThrowsWhenNotFound() {
        when(userRepository.findByUsernameAndDeletedFalse("ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("ghost")
        );

        assertTrue(ex.getMessage().contains("ghost"));
    }
}
