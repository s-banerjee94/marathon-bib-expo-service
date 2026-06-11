package com.timekeeper.bibexpo.whatsapp.model.dto.request;

import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWhatsAppSenderModeRequest {

    @NotNull(message = "Sender mode is required.")
    private WhatsAppSenderMode mode;
}
