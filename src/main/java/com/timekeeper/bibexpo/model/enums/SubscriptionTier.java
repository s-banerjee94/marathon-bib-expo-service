package com.timekeeper.bibexpo.model.enums;

/**
 * The subscription plans an organization can be on. An organization with no tier assigned
 * (null) has no active subscription and resolves to the FREE subscription status.
 */
public enum SubscriptionTier {
    PAY_AS_YOU_GO,
    PREMIUM,
    PARTNER
}
