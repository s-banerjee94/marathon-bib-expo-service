package com.timekeeper.bibexpo.service.util;

public final class DistributionConstants {

    private DistributionConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String DISTRIBUTOR_SEPARATOR = "__|__";

    public static final String ACTION_BIB_COLLECTED = "BIB_COLLECTED";
    public static final String ACTION_BIB_UNDONE = "BIB_UNDONE";
    public static final String ACTION_GOODIES_DISTRIBUTED = "GOODIES_DISTRIBUTED";

    public static String formatDistributorInfo(Long userId, String username) {
        return userId + DISTRIBUTOR_SEPARATOR + username;
    }
}
