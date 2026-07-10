package com.timekeeper.bibexpo.messaging.campaign.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating an SMS template")
public class UpdateSmsTemplateRequest {

    @Size(max = 100, message = "Template name must not exceed 100 characters")
    @Schema(description = "Human-readable name for the template", example = "bib collection reminder", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;

    @Size(min = 19, max = 200, message = "SMS Template ID must be between 19 and 200 characters")
    @Pattern(regexp = "^[0-9]+$", message = "SMS Template ID must contain only digits")
    @Schema(description = "DLT Template ID from telecom provider", example = "1107161234567890123", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String smsTemplateId;

    @Size(min = 2, max = 1000, message = "Template text must be at least 2 characters")
    @Schema(
            description = """
                    SMS message text. Use #{fieldName} placeholders to personalise the message. \
                    Participant: #{fullName}, #{bibNumber}, #{raceName}, #{categoryName}, \
                    #{bibCollectedAt}, #{bibCollectedByName}, #{bibCollectedByPhone}. \
                    Event: #{eventName}, #{venueName}, #{eventStartDate}, #{eventEndDate}, #{eventCity}. \
                    Race: #{reportingTime}. \
                    Any placeholder not in this list will be rejected with a validation error.""",
            example = "Hi #{fullName}, your bib #{bibNumber} for #{eventName} is ready at #{venueName}, #{eventCity} on #{eventStartDate}!", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String template;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    @Schema(description = "Optional note or description", example = "Updated reminder for bib collection", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;

}
