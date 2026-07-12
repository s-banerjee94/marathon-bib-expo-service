package com.timekeeper.bibexpo.demo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A live demo session: the code the QR encodes plus the fabricated runner")
public class DemoSessionResponse {

    @Schema(description = "Opaque single-use session code", example = "hV9kQx3mZp1LFa8T2cWn0g")
    private String code;

    @Schema(description = "Fabricated runner details")
    private DemoRunnerResponse runner;

    @Schema(description = "When the session and its QR stop working", example = "2026-07-11T12:34:56Z")
    private Instant expiresAt;
}
