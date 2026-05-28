package com.timekeeper.bibexpo.aspect;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.model.dto.audit.AuditEvent;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.service.audit.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * Captures audit events for {@link Auditable}-annotated service methods.
 *
 * <p>Order is set to the lowest precedence so the aspect runs OUTSIDE any
 * {@code @Transactional} boundary — the business transaction has committed
 * before the audit event is published.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditPublisher publisher;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        // Capture actor on the request thread BEFORE the method runs — the security
        // context is not propagated to async threads downstream.
        User actor = resolveActor(pjp.getArgs());

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            AuditContextHolder.clear();
            throw t;
        }

        try {
            // A service method may set these (chiefly void deletes); they take precedence.
            String hintedLabel = AuditContextHolder.getEntityLabel();
            Long hintedOrgId = AuditContextHolder.getOrganizationId();

            String label = hintedLabel != null ? hintedLabel : extractEntityLabel(result);
            Long orgId = hintedOrgId != null ? hintedOrgId
                    : resolveOrgId(auditable.entityType(), actor, result, pjp.getArgs());

            AuditEvent event = AuditEvent.builder()
                    .organizationId(orgId)
                    .actorUserId(actor != null ? actor.getId() : null)
                    .actorName(username(actor))
                    .action(auditable.action())
                    .entityType(auditable.entityType())
                    .entityId(extractEntityId(result, pjp.getArgs()))
                    .entityLabel(label)
                    .description(buildDescription(auditable.action(), auditable.entityType(), label))
                    .occurredAt(Instant.now())
                    .build();
            publisher.publish(event);
        } catch (Exception e) {
            log.warn("Failed to capture audit event for {}.{}",
                    pjp.getTarget().getClass().getSimpleName(),
                    pjp.getSignature().getName(), e);
        } finally {
            AuditContextHolder.clear();
        }

        return result;
    }

    // ---- actor ----------------------------------------------------------------

    private User resolveActor(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof User user) {
                return user;
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private String username(User actor) {
        return actor != null ? actor.getUsername() : "system";
    }

    // ---- org id ---------------------------------------------------------------

    /**
     * Resolution order: target entity's org → actor's org → 0 (system).
     * For ORGANIZATION audits, the entity itself IS the org so we use its id.
     */
    private Long resolveOrgId(AuditEntityType type, User actor, Object result, Object[] args) {
        if (result != null) {
            Long fromResponse = invokeLong(result, "getOrganizationId");
            if (fromResponse != null) return fromResponse;

            if (type == AuditEntityType.ORGANIZATION) {
                Long ownId = invokeLong(result, "getId");
                if (ownId != null) return ownId;
            }

            Object orgRef = invokeObject(result, "getOrganization");
            if (orgRef != null) {
                Long fromRef = invokeLong(orgRef, "getId");
                if (fromRef != null) return fromRef;
            }
        }
        if (actor != null && actor.getOrganization() != null) {
            return actor.getOrganization().getId();
        }
        return 0L;
    }

    // ---- entity id + label ----------------------------------------------------

    private String extractEntityId(Object result, Object[] args) {
        if (result != null) {
            Object id = invokeObject(result, "getId");
            if (id != null) return id.toString();
        }
        for (Object arg : args) {
            if (arg instanceof Long id) return id.toString();
        }
        return null;
    }

    private String extractEntityLabel(Object result) {
        if (result == null) return null;
        for (String getter : new String[]{"getEventName", "getRaceName", "getCategoryName",
                "getName", "getFullName", "getUsername", "getOrganizerName", "getTitle"}) {
            Object value = invokeObject(result, getter);
            if (value instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    // ---- description ----------------------------------------------------------

    private String buildDescription(AuditAction action, AuditEntityType entityType, String label) {
        String entityName = entityType.name().charAt(0)
                + entityType.name().substring(1).toLowerCase().replace('_', ' ');
        String verb = switch (action) {
            case CREATE        -> "created";
            case UPDATE        -> "updated";
            case DELETE        -> "deleted";
            case STATUS_CHANGE -> "status changed";
            case LOGIN         -> "logged in";
            case GENERATE      -> "generated";
        };
        return (label != null && !label.isBlank())
                ? entityName + " \"" + label + "\" " + verb
                : entityName + " " + verb;
    }

    // ---- reflection helpers ---------------------------------------------------

    private Object invokeObject(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            return m.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Long invokeLong(Object target, String getter) {
        Object value = invokeObject(target, getter);
        return (value instanceof Long l) ? l : null;
    }
}
