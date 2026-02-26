package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.model.entity.User;

public interface AuthService {

    /**
     * Authenticate user with username and password
     *
     * @param request login request containing username and password
     * @return login response with JWT token and user details
     */
    LoginResponse login(LoginRequest request);

    /**
     * Cleans up server-side resources for the user on logout.
     * Closes all active SSE connections so no orphaned threads remain.
     *
     * @param user the authenticated user logging out
     */
    void logout(User user);
}
