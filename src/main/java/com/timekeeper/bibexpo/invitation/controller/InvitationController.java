package com.timekeeper.bibexpo.invitation.controller;

import com.timekeeper.bibexpo.invitation.model.dto.request.CreateInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationLinkResponse;
import com.timekeeper.bibexpo.invitation.service.InvitationService;
import com.timekeeper.bibexpo.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class InvitationController implements InvitationControllerApi {

    private final InvitationService invitationService;

    @Override
    public ResponseEntity<InvitationLinkResponse> createInvitation(CreateInvitationRequest request, User currentUser) {
        log.info("Request to issue invite for role: {} by: {}", request.getRole(), currentUser.getUsername());
        InvitationLinkResponse response = invitationService.createInvitation(request, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
