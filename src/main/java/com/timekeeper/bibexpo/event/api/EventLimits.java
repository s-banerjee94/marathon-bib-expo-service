package com.timekeeper.bibexpo.event.api;

/**
 * An event's resource ceilings, as {@link EventQuota} hands them out. Ceilings only — the current
 * usage they are compared against belongs to whichever module owns the resource being counted.
 */
public record EventLimits(
        int maxParticipants,
        int maxRaces,
        int maxCategoriesPerRace,
        int maxGoodies,
        int maxSmsTemplates,
        int maxSmsCampaigns,
        int maxImports,
        int maxAddOns,
        int usedImports,
        int usedAddOns) {
}
