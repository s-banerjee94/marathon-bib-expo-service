package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single chat turn sent to the in-app AI assistant")
public class AiChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 4000, message = "Message must be at most 4000 characters")
    @Schema(description = "What you want to ask or do, in plain language (refer to events, races and people by name, not id)",
            example = "Add a Half Marathon race to the Mumbai Marathon")
    private String message;
}
