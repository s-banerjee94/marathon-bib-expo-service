package com.timekeeper.bibexpo.aspect;

/**
 * Per-thread overrides a service method can set so {@link AuditAspect} records the right
 * entity name and organization for actions whose return value cannot carry them — chiefly
 * void deletes, where the entity is gone by the time the aspect runs.
 *
 * <p>Set the hint inside the service method (while the entity is still loaded), then the
 * aspect consumes and clears it after the method completes. Same request-thread idiom as
 * {@code SecurityContextHolder}; never read on async worker threads.
 */
public final class AuditContextHolder {

    private static final ThreadLocal<String> ENTITY_LABEL = new ThreadLocal<>();
    private static final ThreadLocal<Long> ORGANIZATION_ID = new ThreadLocal<>();

    private AuditContextHolder() {
    }

    public static void setEntityLabel(String label) {
        ENTITY_LABEL.set(label);
    }

    public static void setOrganizationId(Long organizationId) {
        ORGANIZATION_ID.set(organizationId);
    }

    public static String getEntityLabel() {
        return ENTITY_LABEL.get();
    }

    public static Long getOrganizationId() {
        return ORGANIZATION_ID.get();
    }

    public static void clear() {
        ENTITY_LABEL.remove();
        ORGANIZATION_ID.remove();
    }
}
