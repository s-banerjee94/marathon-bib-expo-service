package com.timekeeper.bibexpo.passwordreset.controller;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.IssueResetLinkRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetLinkResponse;
import com.timekeeper.bibexpo.passwordreset.service.PasswordResetService;
import com.timekeeper.bibexpo.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PasswordResetAdminController implements PasswordResetAdminControllerApi {

    private final PasswordResetService passwordResetService;

    @Override
    public ResponseEntity<PasswordResetLinkResponse> issueResetLink(
            Long userId, IssueResetLinkRequest request, User currentUser) {
        log.info("Request to issue password reset link for user ID: {} by: {}", userId, currentUser.getUsername());
        PasswordResetLinkResponse response =
                passwordResetService.issueForUser(userId, request, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }
}
