package com.timekeeper.bibexpo.shared.cache;

/**
 * The names of the application's caches, and what each one holds.
 *
 * <p>Every cache is declared here rather than beside the bean that reads it, because the two
 * sides live apart on purpose: the manager that sizes and expires a cache is a composition-root
 * concern, while the bean that fills and evicts it belongs to its own module. A shared constant
 * is what keeps those two from drifting onto different spellings of the same string, without
 * making a module depend on the composition root to learn its own cache's name.
 */
public final class CacheNames {

    private CacheNames() {
    }

    public static final String ACTIVE_SESSIONS_CACHE = "activeSessions";

    /**
     * Unread-notification badge counts, keyed by user id. Kept correct by explicit eviction on every
     * write path (new notification, mark-read, delete); the write-expiry is only a backstop for a
     * missed eviction. Lets the frequent badge poll skip DynamoDB between changes.
     */
    public static final String UNREAD_COUNTS_CACHE = "unreadNotificationCounts";

    /**
     * Authenticated user entities keyed by username, loaded on every request by the JWT filter.
     * Each entry carries its organization and event eagerly so the cached (detached) user stays
     * usable outside a session. Evicted on any user mutation and on organization disable; the
     * one-hour write-expiry is only a backstop.
     */
    public static final String USER_DETAILS_CACHE = "userDetails";

    /**
     * Active organizations keyed by id, read repeatedly by organization-scoped users. Evicted on
     * every organization write (update, status toggle, logo change); the one-hour write-expiry is
     * only a backstop.
     */
    public static final String ORGANIZATIONS_CACHE = "organizations";

    /**
     * Provider configuration rows resolved on every outbound send: the SYSTEM transactional row and
     * the CAMPAIGN default/override rows, keyed by channel (and organization). Root-managed and
     * rarely changed, so entries are evicted on the admin save/delete paths; the one-hour
     * write-expiry is only a backstop. Empty lookups (an organization with no override) are cached
     * too, so the common fall-through to the default costs no query.
     */
    public static final String MESSAGING_PROVIDERS_CACHE = "messagingProviders";

    /**
     * System message templates keyed by purpose and channel, resolved on every transactional send
     * (notification, invitation, participant-event SMS/WhatsApp). Root-managed and rarely changed, so
     * entries are evicted on the admin save path; the one-hour write-expiry is only a backstop.
     */
    public static final String SYSTEM_TEMPLATES_CACHE = "systemMessageTemplates";

    /**
     * Per-event race and category name lookup maps, keyed by event id, used to enrich reads that only
     * store race/category ids. Evicted whenever the event's races or categories change
     * (create/update/delete and CSV import); the one-hour write-expiry is only a backstop.
     */
    public static final String EVENT_NAMES_CACHE = "eventRaceCategoryNames";
}
