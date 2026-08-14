package com.timekeeper.bibexpo.participant.service.util;

/**
 * Encodes and decodes {@code ParticipantDDB.bibDistributedBy}, the {@code <userId>__|__<username>}
 * stamp written when a bib is handed out.
 *
 * <p>It lives with the participant record that carries the field rather than with either of the two
 * modules that touch it: distribution writes the stamp and event stats reads the id back out, so
 * letting distribution own the format would point event at distribution.
 */
public final class DistributorStamp {

    private static final String SEPARATOR = "__|__";

    private DistributorStamp() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String of(Long userId, String username) {
        return userId + SEPARATOR + username;
    }

    /**
     * Reads the user id back out of a stamp. A value stored without the separator is returned
     * whole, which is what the activity counters have always keyed on.
     */
    public static String userIdOf(String stamp) {
        if (stamp == null || stamp.isBlank()) {
            return null;
        }
        int idx = stamp.indexOf(SEPARATOR);
        return idx > 0 ? stamp.substring(0, idx) : stamp;
    }
}
