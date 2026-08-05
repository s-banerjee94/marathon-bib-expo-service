package com.timekeeper.bibexpo.audit.api;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as auditable. {@code AuditAspect} captures actor + entity data,
 * builds an {@link AuditEvent}, and hands it to the active {@link AuditPublisher}.
 *
 * <p>Audits fire on successful return only — methods that throw are not audited.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    AuditEntityType entityType();
    AuditAction action();
}
