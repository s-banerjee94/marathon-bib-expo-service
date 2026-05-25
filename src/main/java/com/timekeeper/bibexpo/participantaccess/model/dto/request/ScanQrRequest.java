package com.timekeeper.bibexpo.participantaccess.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "QR scan payload from the distribution counter")
public class ScanQrRequest {

    @NotBlank
    @Schema(description = "Encrypted QR token from the participant's QR code", example = "aGVsbG8...")
    private String code;
}
