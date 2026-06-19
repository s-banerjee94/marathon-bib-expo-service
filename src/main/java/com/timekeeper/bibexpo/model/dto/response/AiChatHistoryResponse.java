package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The signed-in user's stored assistant conversation, oldest turn first")
public class AiChatHistoryResponse {

    @Schema(description = "The conversation turns in chronological order; empty when there is no history")
    private List<Turn> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "One stored conversation turn")
    public static class Turn {

        @Schema(description = "Id of the stored message; use it as the message key on the client",
                example = "9f1c2e7a-2b6d-4f0a-9c3e-1a2b3c4d5e6f")
        private String id;

        @Schema(description = "Who sent the turn: USER or ASSISTANT", example = "ASSISTANT")
        private String role;

        @Schema(description = "The message text", example = "I found 1 participant: Umesh K Soni, bib 21001.")
        private String content;

        @Schema(description = "When the turn was stored", example = "2026-06-20T08:30:00Z")
        private Instant createdAt;
    }
}
