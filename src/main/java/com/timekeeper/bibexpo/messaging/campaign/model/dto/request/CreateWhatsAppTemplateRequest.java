package com.timekeeper.bibexpo.messaging.campaign.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for registering an approved WhatsApp Content Template")
public class CreateWhatsAppTemplateRequest {

    @NotBlank(message = "Template name is required.")
    @Size(max = 100, message = "Template name must not exceed 100 characters.")
    @Schema(description = "Human-readable name for the template", example = "bib collection confirmation")
    private String name;

    @NotBlank(message = "Content SID is required.")
    @Pattern(regexp = "^HX[0-9a-fA-F]{32}$", message = "Content SID must look like HX followed by 32 characters.")
    @Schema(description = "Twilio Content SID of the approved template", example = "HX1234567890abcdef1234567890abcdef")
    private String contentSid;

    @NotBlank(message = "Message body is required.")
    @Size(max = 1024, message = "Message body must not exceed 1024 characters.")
    @Schema(description = "The approved template body with positional {{n}} markers, exactly as it appears on Twilio",
            example = "Hi {{1}}, your bib {{2}} is ready for collection at {{3}}.")
    private String body;

    @Size(max = 20, message = "A template can have a maximum of 20 variables.")
    @Schema(
            description = """
                    Ordered variable expressions; entry n fills the Twilio template variable {{n}}. \
                    Use #{fieldName} placeholders. \
                    Participant: #{fullName}, #{bibNumber}, #{raceName}, #{categoryName}, \
                    #{bibCollectedAt}, #{bibCollectedByName}, #{bibCollectedByPhone}. \
                    Event: #{eventName}, #{venueName}, #{eventStartDate}, #{eventEndDate}, #{eventCity}. \
                    Race: #{reportingTime}. \
                    Any placeholder not in this list will be rejected with a validation error.""",
            example = "[\"#{fullName}\", \"#{bibNumber}\", \"#{eventName}\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<@NotBlank(message = "Template variables must not be blank.")
            @Size(max = 200, message = "A template variable must not exceed 200 characters.") String> bodyVariables;

    @Size(max = 500, message = "Note must not exceed 500 characters.")
    @Schema(description = "Optional note or description", example = "Sent automatically when a bib is collected", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;
}
