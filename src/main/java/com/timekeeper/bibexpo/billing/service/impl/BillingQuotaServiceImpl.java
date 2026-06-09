package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.model.entity.EventBillingState;
import com.timekeeper.bibexpo.billing.repository.EventBillingStateRepository;
import com.timekeeper.bibexpo.billing.service.BillingQuotaService;
import com.timekeeper.bibexpo.billing.service.QuotaClaimResult;
import com.timekeeper.bibexpo.model.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingQuotaServiceImpl implements BillingQuotaService {

    // Per-event manual-request quota, separate per role group. Hardcoded by decision.
    private static final int MAX_MANUAL_REQUESTS = 5;

    private final EventBillingStateRepository repository;

    @Override
    @Transactional
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

    private boolean isOrganizer(UserRole role) {
        return role == UserRole.ORGANIZER_ADMIN;
    }
}
