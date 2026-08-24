package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.api.BillingRevenueQuery;
import com.timekeeper.bibexpo.billing.config.BillingRates;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BillingRevenueQueryImpl implements BillingRevenueQuery {

    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal collectedBetween(Instant from, Instant to) {
        return invoiceRepository.sumCollectedBetween(from, to);
    }

    @Override
    public String currency() {
        return BillingRates.DEFAULT.currency();
    }
}
