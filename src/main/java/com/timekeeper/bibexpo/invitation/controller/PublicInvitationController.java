package com.timekeeper.bibexpo.invitation.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.invitation.exception.InvitationInvalidException;
import com.timekeeper.bibexpo.invitation.model.dto.request.AcceptInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationDetailsResponse;
import com.timekeeper.bibexpo.invitation.service.InvitationService;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PublicInvitationController implements PublicInvitationControllerApi {

    private final InvitationService invitationService;

    @Override
    public ResponseEntity<InvitationDetailsResponse> getInvitation(String token) {
        log.info("Public request to read invite");
        return ResponseEntity.ok(invitationService.getInvitation(token));
    }

    @Override
    public ResponseEntity<UserResponse> acceptInvitation(String token, AcceptInvitationRequest request) {
        log.info("Public request to accept invite for username: {}", request.getUsername());
        UserResponse response = invitationService.acceptInvitation(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(InvitationInvalidException.class)
    public ResponseEntity<ErrorResponse> handleInvitationInvalid(InvitationInvalidException ex, WebRequest request) {
        log.warn("Invite rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }
}
