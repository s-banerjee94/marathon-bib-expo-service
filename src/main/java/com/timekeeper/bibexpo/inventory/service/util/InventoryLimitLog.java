package com.timekeeper.bibexpo.inventory.service.util;

import lombok.extern.slf4j.Slf4j;

/**
 * The one log line for an organization reaching an inventory cap of its plan, so every cap can be
 * counted with a single query on {@code event=inventory.limit_reached}.
 */
@Slf4j
public final class InventoryLimitLog {

    private InventoryLimitLog() {
    }

    /**
     * @param organizationId the organization that hit the cap
     * @param limit          which cap, such as {@code TERMS} or {@code VARIANTS_PER_ITEM}
     */
    public static void limitReached(Long organizationId, String limit) {
        log.atInfo()
                .addKeyValue("event", "inventory.limit_reached")
                .addKeyValue("targetOrgId", organizationId)
                .addKeyValue("limit", limit)
                .log("Inventory plan limit reached");
    }
}
