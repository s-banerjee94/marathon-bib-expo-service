package com.timekeeper.bibexpo.passwordreset.controller;

import com.timekeeper.bibexpo.passwordreset.model.dto.request.IssueResetLinkRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetLinkResponse;
import com.timekeeper.bibexpo.passwordreset.service.PasswordResetService;
import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PasswordResetAdminController implements PasswordResetAdminControllerApi {

    private final PasswordResetService passwordResetService;

    @Override
    public ResponseEntity<PasswordResetLinkResponse> issueResetLink(
            Long userId, IssueResetLinkRequest request, User currentUser) {
        PasswordResetLinkResponse response =
                passwordResetService.issueForUser(userId, request, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }
}
