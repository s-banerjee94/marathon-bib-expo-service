package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.billing.model.dto.response.OrganizationBillingResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.billing.service.BillingAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/billing")
@RequiredArgsConstructor
@Slf4j
public class OrganizationBillingController implements OrganizationBillingControllerApi {

    private final BillingAdminService billingAdminService;

    @Override
    public ResponseEntity<OrganizationBillingResponse> listOrganizationBills(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to list bills for organization ID: {} by user: {}",
                organizationId, currentUser.getUsername());

        return ResponseEntity.ok(billingAdminService.listOrganizationBills(organizationId, currentUser));
    }
}
