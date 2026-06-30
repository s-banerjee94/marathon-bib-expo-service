package com.timekeeper.bibexpo.ai.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AgentChatRequest {

    @Schema(description = "The user's message to the assistant.",
            example = "How many participants have collected their bib?")
    @NotBlank(message = "Message cannot be empty.")
    private String message;

    /** Required per-request approval mode: auto, agent or ask. */
    @Schema(description = "Approval mode for this message (required): auto (run writes without asking), "
            + "agent (let the agent decide), ask (always pause for approval).",
            example = "ask", allowableValues = {"auto", "agent", "ask"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Mode is required.")
    @Pattern(regexp = "auto|agent|ask", message = "Mode must be auto, agent or ask.")
    private String mode;
}
