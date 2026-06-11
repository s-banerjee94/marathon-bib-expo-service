package com.timekeeper.bibexpo.whatsapp.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveWhatsAppConfigRequest {

    @NotBlank(message = "Account SID is required.")
    @Size(max = 64, message = "Account SID must not exceed 64 characters.")
    private String accountSid;

    @NotBlank(message = "Auth token is required.")
    private String authToken;

    @NotBlank(message = "WhatsApp sender number is required.")
    @Size(max = 32, message = "WhatsApp sender number must not exceed 32 characters.")
    private String fromNumber;
}
