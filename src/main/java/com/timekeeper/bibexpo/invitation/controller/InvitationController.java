package com.timekeeper.bibexpo.invitation.controller;

import com.timekeeper.bibexpo.invitation.model.dto.request.CreateInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationLinkResponse;
import com.timekeeper.bibexpo.invitation.service.InvitationService;
import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InvitationController implements InvitationControllerApi {

    private final InvitationService invitationService;

    @Override
    public ResponseEntity<InvitationLinkResponse> createInvitation(CreateInvitationRequest request, User currentUser) {
        InvitationLinkResponse response = invitationService.createInvitation(request, CurrentActor.from(currentUser));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
