package com.timekeeper.bibexpo.model.dto.response;

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
@Schema(description = "The AI assistant's reply to a chat turn")
public class AiChatResponse {

    @Schema(description = "Id of the stored assistant message; use it as the message key on the client",
            example = "9f1c2e7a-2b6d-4f0a-9c3e-1a2b3c4d5e6f")
    private String id;

    @Schema(description = "Who sent this message; always ASSISTANT for a reply", example = "ASSISTANT")
    private String role;

    @Schema(description = "The assistant's natural-language reply",
            example = "I found 1 participant: Umesh K Soni, bib 21001.")
    private String reply;

    @Schema(description = "When the reply was stored", example = "2026-06-20T08:30:00Z")
    private Instant createdAt;
}
