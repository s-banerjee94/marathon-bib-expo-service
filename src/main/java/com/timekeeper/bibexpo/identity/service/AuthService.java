package com.timekeeper.bibexpo.identity.service;

import com.timekeeper.bibexpo.identity.model.dto.request.GoogleLoginRequest;
import com.timekeeper.bibexpo.identity.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.identity.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.identity.model.dto.response.RefreshResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    /**
     * Authenticate the user, start a new session (overwriting any prior one),
     * and write the refresh + CSRF cookies on {@code httpResponse}.
     *
     * @return body containing the short-lived access token + user info
     */
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Signs in with a Google ID token. The token must be valid and issued for this app, and its verified
     * email must belong to an existing, enabled account; no account is ever created. On success it starts
     * a session exactly like {@link #login}.
     *
     * @return body containing the short-lived access token + user info
     */
    LoginResponse loginWithGoogle(GoogleLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Validate the refresh cookie + CSRF double-submit, then rotate the session:
     * issue a new sid, new access token, and new refresh-cookie. Reuse of an
     * already-rotated refresh token triggers a forced logout of the whole chain.
     */
    RefreshResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Ends the session identified by the refresh cookie and clears the auth
     * cookies. Requires the CSRF double-submit check to pass, exactly like
     * {@link #refresh}. Does not rely on an authenticated principal, so logout
     * still succeeds after the access token has expired.
     */
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse);
}
