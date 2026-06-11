package com.timekeeper.bibexpo.whatsapp.model.dto.response;

import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppConfigResponse {

    private boolean configured;
    private WhatsAppSenderMode mode;
    private String accountSid;
    private String authTokenMasked;
    private String fromNumber;
    private boolean verified;
}
