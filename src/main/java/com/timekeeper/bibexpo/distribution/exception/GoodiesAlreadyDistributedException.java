package com.timekeeper.bibexpo.distribution.exception;

import com.timekeeper.bibexpo.shared.util.TextUtils;

import java.util.List;

public class GoodiesAlreadyDistributedException extends RuntimeException {

    public GoodiesAlreadyDistributedException(List<String> itemNames) {
        super(TextUtils.joinAsSentence(itemNames) + (itemNames.size() == 1 ? " has" : " have")
                + " already been handed to this participant.");
    }
}
