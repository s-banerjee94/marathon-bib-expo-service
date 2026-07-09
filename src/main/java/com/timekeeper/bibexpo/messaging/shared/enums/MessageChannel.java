package com.timekeeper.bibexpo.messaging.shared.enums;

/**
 * Delivery channels a system message can be sent over. WhatsApp is implemented first;
 * SMS and email are scaffolded for later flows (password reset, OTP, bills).
 */
public enum MessageChannel {
    WHATSAPP,
    SMS,
    EMAIL
}
