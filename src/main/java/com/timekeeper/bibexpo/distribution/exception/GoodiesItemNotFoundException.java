package com.timekeeper.bibexpo.distribution.exception;

import com.timekeeper.bibexpo.shared.util.TextUtils;

import java.util.List;

public class GoodiesItemNotFoundException extends RuntimeException {

    public GoodiesItemNotFoundException(List<String> itemNames) {
        super(TextUtils.joinAsSentence(itemNames) + (itemNames.size() == 1 ? " is" : " are")
                + " neither allocated to this participant nor added to the event by hand.");
    }
}
