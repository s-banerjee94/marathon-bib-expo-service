package com.timekeeper.bibexpo.invitation.service;

import com.timekeeper.bibexpo.invitation.model.dto.request.AcceptInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.request.CreateInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationDetailsResponse;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationLinkResponse;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;

/**
 * Issues and redeems one-time, short-lived user-invite links. The role and organization are
 * fixed by the inviter and stored server-side, so the invitee can only supply their own
 * details — never change the target role or organization.
 */
public interface InvitationService {

    /**
     * Issue an invite link for a fixed role and organization. The caller's authority to
     * create that role/organization is validated before the link is issued.
     *
     * @param request the role and (where applicable) organization the invite is fixed to
     * @param currentUsername the username of the user issuing the invite
     * @return the link to share with the invitee
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if the caller cannot create that role
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if the organization is required but missing or disabled
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if the organization does not exist
     */
    InvitationLinkResponse createInvitation(CreateInvitationRequest request, String currentUsername);

    /**
     * Return the fixed role and organization of a pending invite so the accept form can render.
     *
     * @param token the invite token
     * @return the invite's fixed details
     * @throws com.timekeeper.bibexpo.invitation.exception.InvitationInvalidException if the token is missing or expired
     */
    InvitationDetailsResponse getInvitation(String token);

    /**
     * Redeem an invite, creating the account with the invitation's fixed role and organization.
     * The token is consumed only on success, so a failed attempt (for example a taken username)
     * can be retried while the link is still live.
     *
     * @param token the invite token
     * @param request the invitee's personal details
     * @return the created user
     * @throws com.timekeeper.bibexpo.invitation.exception.InvitationInvalidException if the token is missing or expired
     * @throws com.timekeeper.bibexpo.exception.UserAlreadyExistsException if username, email, or phone already exists
     * @throws com.timekeeper.bibexpo.exception.InvalidUserDataException if required details are missing or limits exceeded
     */
    UserResponse acceptInvitation(String token, AcceptInvitationRequest request);
}
