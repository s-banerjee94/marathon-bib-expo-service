package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.service.EventBillingGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Billing-side implementation of the core {@link EventBillingGuard} port.
 */
@Service
@RequiredArgsConstructor
public class EventBillingGuardImpl implements EventBillingGuard {

    private final InvoiceRepository invoiceRepository;

    @Override
    public boolean hasFinalInvoice(Long eventId) {
        return invoiceRepository.existsByEventIdAndStatus(eventId, InvoiceStatus.FINAL);
    }
}
