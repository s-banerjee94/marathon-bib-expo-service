package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.billing.model.dto.response.OrganizationBillingResponse;
import com.timekeeper.bibexpo.billing.service.BillingAdminService;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/billing")
@RequiredArgsConstructor
public class OrganizationBillingController implements OrganizationBillingControllerApi {

    private final BillingAdminService billingAdminService;

    @Override
    public ResponseEntity<OrganizationBillingResponse> listOrganizationBills(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(billingAdminService.listOrganizationBills(organizationId, currentUser));
    }
}
