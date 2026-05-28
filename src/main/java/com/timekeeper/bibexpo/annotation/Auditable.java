package com.timekeeper.bibexpo.annotation;

import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as auditable. {@code AuditAspect} captures actor + entity data,
 * builds an {@link com.timekeeper.bibexpo.model.dto.audit.AuditEvent}, and hands it to the
 * active {@link com.timekeeper.bibexpo.service.audit.AuditPublisher}.
 *
 * <p>Audits fire on successful return only — methods that throw are not audited.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    AuditEntityType entityType();
    AuditAction action();
}
