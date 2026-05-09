package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.exception.SmsSendException;

public interface SmsGatewayService {

    /**
     * Send a single SMS via the DLT gateway.
     *
     * @param phoneNumber  recipient mobile number (with country code)
     * @param message      rendered SMS text after placeholder substitution
     * @param dltTemplateId DLT registered template entity ID
     * @throws SmsSendException if the gateway call fails
     */
    void send(String phoneNumber, String message, String dltTemplateId);
}
