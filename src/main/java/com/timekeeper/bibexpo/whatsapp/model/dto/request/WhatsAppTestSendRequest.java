package com.timekeeper.bibexpo.whatsapp.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppTestSendRequest {

    @NotBlank(message = "Content SID is required.")
    private String contentSid;

    @NotBlank(message = "Recipient number is required.")
    private String toNumber;
}
