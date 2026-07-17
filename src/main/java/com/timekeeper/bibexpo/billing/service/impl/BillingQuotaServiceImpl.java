package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.model.entity.EventBillingState;
import com.timekeeper.bibexpo.billing.repository.EventBillingStateRepository;
import com.timekeeper.bibexpo.billing.service.BillingQuotaService;
import com.timekeeper.bibexpo.billing.service.QuotaClaimResult;
import com.timekeeper.bibexpo.model.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingQuotaServiceImpl implements BillingQuotaService {

    // Per-event manual-request quota, separate per role group. Hardcoded by decision.
    private static final int MAX_MANUAL_REQUESTS = 5;

    private final EventBillingStateRepository repository;

    @Override
    // Called from an AFTER_COMMIT event listener where no transaction is active, so a fresh one
    // (REQUIRES_NEW) is required — plain REQUIRED would fail the modifying query with
    // TransactionRequiredException and abort the auto-bill scheduling that follows it.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureState(Long eventId) {
        repository.insertIfAbsent(eventId);
    }

    @Override
    @Transactional
    public QuotaClaimResult claim(Long eventId, UserRole role) {
        repository.insertIfAbsent(eventId);
        int claimed = isOrganizer(role)
                ? repository.claimOrgAdminSlot(eventId, MAX_MANUAL_REQUESTS)
                : repository.claimAdminSlot(eventId, MAX_MANUAL_REQUESTS);
        if (claimed == 1) {
            return QuotaClaimResult.CLAIMED;
        }
        boolean finalized = repository.findById(eventId)
                .map(EventBillingState::isFinalLocked)
                .orElse(false);
        return finalized ? QuotaClaimResult.FINALIZED : QuotaClaimResult.QUOTA_EXHAUSTED;
    }

    @Override
    @Transactional
    public void refund(Long eventId, UserRole role) {
        if (isOrganizer(role)) {
            repository.refundOrgAdminSlot(eventId);
        } else {
            repository.refundAdminSlot(eventId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFinalized(Long eventId) {
        return repository.findById(eventId)
                .map(EventBillingState::isFinalLocked)
                .orElse(false);
    }

    private boolean isOrganizer(UserRole role) {
        return role == UserRole.ORGANIZER_ADMIN;
    }
}
