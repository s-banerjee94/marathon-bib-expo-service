package com.timekeeper.bibexpo.passwordreset.service;

import com.timekeeper.bibexpo.passwordreset.model.dto.request.CompletePasswordResetRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.ForgotPasswordRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.IssueResetLinkRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetLinkResponse;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetTokenStatusResponse;

/**
 * Issues and completes short-lived, single-use password-reset links. A link may originate from an
 * administrator (who receives the link back to share) or from a user's own forgot-password request
 * (where the link is only delivered to the account's registered phone and never returned). The
 * actual password change is audited; where the link came from is recorded on that audit event.
 */
public interface PasswordResetService {

    /**
     * Administrator-initiated: issue a reset link for a user the caller is allowed to manage. The link
     * is returned so the administrator can share it, and is additionally delivered to the user's own
     * registered phone over any requested channels. Records an audit event marking the issuing
     * administrator.
     *
     * <p>A caller cannot issue a link for their own account: self-service must go through
     * change-password (which verifies the current password) or forgot-password (delivered to the
     * account's own phone), so a signed-in session can never reset its own password without a
     * second factor.
     *
     * @param userId          the user the reset link is for
     * @param request         optional channels to deliver the link on
     * @param currentUsername the administrator issuing the link
     * @return the reset link plus any per-channel delivery outcomes
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException if the user does not exist
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller may not manage the user
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if the caller targets their own account
     */
    PasswordResetLinkResponse issueForUser(Long userId, IssueResetLinkRequest request, String currentUsername);

    /**
     * Public forgot-password: if the identifier matches an eligible (enabled, unlocked) account, issue
     * a reset link and deliver it to that account's own registered phone. Runs asynchronously and
     * returns immediately, so the endpoint responds in constant time and never discloses — by outcome
     * or by timing — whether an account exists. Not audited (the requester is unauthenticated); the
     * eventual completion is what gets audited.
     *
     * @param request the account identifier (username, email, or phone)
     */
    void requestReset(ForgotPasswordRequest request);

    /**
     * Public: validate a reset token and return minimal display detail for the set-new-password form.
     *
     * @param token the reset token
     * @return the masked username and expiry of the pending reset
     * @throws com.timekeeper.bibexpo.passwordreset.exception.PasswordResetInvalidException
     *         if the token is missing, expired, or no longer resolves to a user
     */
    PasswordResetTokenStatusResponse validate(String token);

    /**
     * Public: complete a reset by setting the new password, then consume the token so it cannot be
     * reused, and audit the change (marking whether it began as a forgot-password request or an
     * administrator-issued link). No session is ended: the app enforces single-device login, so the
     * next sign-in overwrites any prior session.
     *
     * @param token   the reset token
     * @param request the new password
     * @throws com.timekeeper.bibexpo.passwordreset.exception.PasswordResetInvalidException
     *         if the token is missing, expired, or no longer resolves to a user
     */
    void completeReset(String token, CompletePasswordResetRequest request);
}
