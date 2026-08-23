package com.timekeeper.bibexpo.event.api;

import java.time.Instant;
import java.util.Map;

/**
 * The resolved race and category names for one event, as {@link RaceCategoryNameQuery} hands them
 * out.
 *
 * <p>Participants keep only ids, so an id whose row is no longer reachable from this event — the
 * two stores having drifted apart — has no name to return. Two flavours of lookup exist for that
 * case: {@code *Name} returns {@code null} and is what participant-facing surfaces (public
 * self-service, SMS/WhatsApp campaigns) must use, since an internal id must never reach a runner.
 * {@code *Label} substitutes a visible placeholder carrying the id and is what staff-facing
 * surfaces (dashboards, statistics, participant grid, distribution, export) use, so the drift shows
 * up as a labelled bucket instead of silently disappearing from a chart.
 */
public record EventNames(Map<String, String> raceNames, Map<String, String> categoryNames,
                         Map<String, Instant> raceReportingTimes, Map<String, String> categoryRaceIds) {

    private static final String UNKNOWN_RACE = "Unknown race";
    private static final String UNKNOWN_CATEGORY = "Unknown category";

    public String raceName(String raceId) {
        return raceId == null ? null : raceNames.get(raceId);
    }

    public String categoryName(String categoryId) {
        return categoryId == null ? null : categoryNames.get(categoryId);
    }

    /** Display label for a race, falling back to a placeholder when the id no longer resolves. */
    public String raceLabel(String raceId) {
        if (raceId == null) {
            return null;
        }
        String name = raceNames.get(raceId);
        return name != null ? name : placeholder(UNKNOWN_RACE, raceId);
    }

    /** Display label for a category, falling back to a placeholder when the id no longer resolves. */
    public String categoryLabel(String categoryId) {
        if (categoryId == null) {
            return null;
        }
        String name = categoryNames.get(categoryId);
        return name != null ? name : placeholder(UNKNOWN_CATEGORY, categoryId);
    }

    /** Whether the id still maps to a race of this event. */
    public boolean hasRace(String raceId) {
        return raceId != null && raceNames.containsKey(raceId);
    }

    /** Whether the id still maps to a category of this event. */
    public boolean hasCategory(String categoryId) {
        return categoryId != null && categoryNames.containsKey(categoryId);
    }

    private static String placeholder(String prefix, String id) {
        return prefix + " (#" + id + ")";
    }

    public Instant reportingTime(String raceId) {
        return raceId == null ? null : raceReportingTimes.get(raceId);
    }

    public String categoryRaceId(String categoryId) {
        return categoryId == null ? null : categoryRaceIds.get(categoryId);
    }
}
