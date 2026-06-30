package com.timekeeper.bibexpo.ai.agent.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * One human decision on a single pending write, sent back via {@code /resume} in the same order the
 * pending actions were returned.
 *
 * <ul>
 *   <li>{@code approve} — run the action as proposed.</li>
 *   <li>{@code edit} — put the change in plain words in {@code message}; the agent revises the action
 *       and asks for confirmation again (expect another {@code needs_approval}).</li>
 *   <li>{@code reject} — cancel the action; {@code message} is an optional reason.</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A decision on one pending write action.")
public class AgentDecision {

    @Schema(description = "approve = run as proposed; edit = revise using 'message' and confirm again; "
            + "reject = cancel.",
            example = "approve", allowableValues = {"approve", "edit", "reject"})
    @NotBlank(message = "Decision type is required.")
    private String type;

    @Schema(description = "Required for 'edit' — the change in plain words (e.g. \"change the email to "
            + "new@x.com\"). Optional reason for 'reject'. Ignored for 'approve'.",
            example = "change the email to new@x.com")
    private String message;
}
