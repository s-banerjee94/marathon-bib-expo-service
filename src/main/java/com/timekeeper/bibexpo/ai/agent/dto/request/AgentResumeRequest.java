package com.timekeeper.bibexpo.ai.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class AgentResumeRequest {

    @Schema(description = "One decision per pending action, in the order they were returned by /chat.")
    @NotEmpty(message = "At least one decision is required.")
    @Valid
    private List<AgentDecision> decisions;

    /** Required per-request approval mode: auto, agent or ask (send the same mode the conversation is using). */
    @Schema(description = "Approval mode for this resume (required): auto (run writes without asking), "
            + "agent (let the agent decide), ask (always pause for approval). Send the same mode the "
            + "conversation is currently using.",
            example = "ask", allowableValues = {"auto", "agent", "ask"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Mode is required.")
    @Pattern(regexp = "auto|agent|ask", message = "Mode must be auto, agent or ask.")
    private String mode;
}
