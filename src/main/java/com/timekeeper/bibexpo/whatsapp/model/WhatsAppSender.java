package com.timekeeper.bibexpo.whatsapp.model;

import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class WhatsAppSender {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final WhatsAppSenderScope scope;
}
