package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.organization.model.event.OrganizationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives every new organization its starter categories, location types and units. Runs after the
 * organization commits and only logs a failure, so inventory can never stop an organization from being
 * created; one that misses its starter set simply begins empty and adds its own.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StarterTermsListener {

    private final InventoryTermService termService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationCreated(OrganizationCreatedEvent event) {
        try {
            termService.seedStarterTerms(event.organizationId());
        } catch (RuntimeException e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("event", "inventory.starter_terms_failed")
                    .addKeyValue("targetOrgId", event.organizationId())
                    .log("Starter inventory terms not created; the organization starts with none");
        }
    }
}
