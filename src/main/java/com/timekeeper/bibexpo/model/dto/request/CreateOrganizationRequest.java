package com.timekeeper.bibexpo.model.dto.request;

import com.timekeeper.bibexpo.model.enums.SubscriptionTier;
import com.timekeeper.bibexpo.validator.ValidEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request payload for creating a new organization")
public class CreateOrganizationRequest {

    @NotBlank(message = "Organizer name is required")
    @Size(min = 2, max = 200, message = "Organizer name must be between 2 and 200 characters")
    @Schema(description = "Organization name", example = "India Book Expo Pvt Ltd")
    private String organizerName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Organization email", example = "contact@indiabookexpo.in")
    private String email;

    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    @Schema(description = "Organization phone number", example = "9876543210", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phoneNumber;

    @Schema(description = "Organization website", example = "https://www.indiabookexpo.in", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String website;

    @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
    @Schema(description = "Address line 1", example = "Plot No 123, Sector 18", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String addressLine1;

    @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
    @Schema(description = "Address line 2", example = "Nehru Place", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String addressLine2;

    @Size(max = 100, message = "City must not exceed 100 characters")
    @Schema(description = "City", example = "New Delhi", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String city;

    @Size(max = 100, message = "State/Province must not exceed 100 characters")
    @Schema(description = "State or Province", example = "Delhi", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String stateProvince;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @Schema(description = "Postal code", example = "110019", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String postalCode;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    @Schema(description = "Country", example = "India", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String country;

    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    @Schema(description = "Tax identification number", example = "29ABCDE1234F1Z5", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String taxId;

    @Size(max = 50, message = "Registration number must not exceed 50 characters")
    @Schema(description = "Business registration number", example = "U74999DL2020PTC123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String registrationNumber;

    @Valid
    @Schema(description = "Per-role user quota caps. Optional; default caps apply when omitted.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private UserQuotaRequest userQuota;

    @ValidEnum(enumClass = SubscriptionTier.class)
    @Schema(description = "Subscription tier; defaults to PAY_AS_YOU_GO (the baseline) when omitted",
            example = "PREMIUM", allowableValues = {"PAY_AS_YOU_GO", "PREMIUM", "PARTNER"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String subscriptionTier;

    @Email(message = "Billing email must be valid")
    @Schema(description = "Billing email address", example = "billing@indiabookexpo.in", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String billingEmail;
}
