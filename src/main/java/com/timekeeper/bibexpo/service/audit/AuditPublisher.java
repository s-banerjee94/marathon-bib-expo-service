package com.timekeeper.bibexpo.service.audit;

import com.timekeeper.bibexpo.model.dto.audit.AuditEvent;

/**
 * Sink for audit events emitted by {@code AuditAspect}.
 *
 * <p>Today's implementation writes directly to DynamoDB. To move to a queue-backed pipeline,
 * supply a second implementation (e.g. {@code SqsAuditPublisher}) and switch the active bean.
 * No aspect or service code needs to change.
 */
public interface AuditPublisher {

    /**
     * Publishes a single audit event. Implementations should never throw — a publish failure
     * must not propagate to the business call that triggered the audit.
     */
    void publish(AuditEvent event);
}
