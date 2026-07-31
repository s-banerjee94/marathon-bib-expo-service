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
@Schema(description = "Request payload for updating an SMS template")
public class UpdateSmsTemplateRequest {

    @Size(max = 100, message = "Template name must not exceed 100 characters")
    @Schema(description = "Human-readable name for the template", example = "bib collection reminder", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;

    @Size(min = 19, max = 100, message = "SMS Template ID must be between 19 and 100 characters")
        @Schema(description = "DLT Template ID from telecom provider", example = "1107161234567890123", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String smsTemplateId;

    @Size(max = 32, message = "Sender ID must not exceed 32 characters")
    @Schema(description = "Registered DLT header / sender id", example = "BIBEXP", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String senderId;

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

    @Size(max = 20, message = "A template can have a maximum of 20 variables.")
    @Schema(
            description = """
                    Ordered #{fieldName} variable expressions for a provider-rendered SMS provider; entry n fills {{VAR:n}}. \
                    Provide this OR the message text, depending on the provider's rendering mode.""",
            example = "[\"#{fullName}\", \"#{bibNumber}\"]",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<@NotBlank(message = "Template variables must not be blank.")
            @Size(max = 200, message = "A template variable must not exceed 200 characters.") String> bodyVariables;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    @Schema(description = "Optional note or description", example = "Updated reminder for bib collection", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;

}
