package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.OrganizationLimit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Organization response payload")
public class OrganizationResponse {

    @Schema(description = "Organization ID", example = "1")
    private Long id;

    @Schema(description = "Organization name", example = "India Book Expo Pvt Ltd")
    private String organizerName;

    @Schema(description = "Organization email", example = "contact@indiabookexpo.in")
    private String email;

    @Schema(description = "Organization phone number", example = "+91-98765-43210")
    private String phoneNumber;

    @Schema(description = "Organization website", example = "https://www.indiabookexpo.in")
    private String website;

    @Schema(description = "Address line 1", example = "Plot No 123, Sector 18")
    private String addressLine1;

    @Schema(description = "Address line 2", example = "Nehru Place")
    private String addressLine2;

    @Schema(description = "City", example = "New Delhi")
    private String city;

    @Schema(description = "State or Province", example = "Delhi")
    private String stateProvince;

    @Schema(description = "Postal code", example = "110019")
    private String postalCode;

    @Schema(description = "Country", example = "India")
    private String country;

    @Schema(description = "Tax identification number", example = "29ABCDE1234F1Z5")
    private String taxId;

    @Schema(description = "Business registration number", example = "U74999DL2020PTC123456")
    private String registrationNumber;

    @Schema(description = "Short-lived presigned URL for the organization logo, null if none set",
            example = "https://bucket.s3.ap-south-1.amazonaws.com/organizations/1/logo/uuid.png?X-Amz-...")
    private String logoUrl;

    @Schema(description = "Per-role user quota (maximum allowed and current usage)")
    private UserQuotaDto userQuota;

    @Schema(description = "Subscription tier; PAY_AS_YOU_GO is the baseline (committed plans: PREMIUM, PARTNER)",
            example = "PREMIUM", allowableValues = {"PAY_AS_YOU_GO", "PREMIUM", "PARTNER"})
    private String subscriptionTier;

    @Schema(description = "Subscription status, derived from the tier (read-only): ACTIVE/EXPIRED for PREMIUM and PARTNER, FREE on the PAY_AS_YOU_GO baseline",
            example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "FREE"})
    private String subscriptionStatus;

    @Schema(description = "Subscription term start; null on the PAY_AS_YOU_GO baseline")
    private LocalDateTime subscriptionStartDate;

    @Schema(description = "Subscription term end (start + 1 year); null on the PAY_AS_YOU_GO baseline")
    private LocalDateTime subscriptionEndDate;

    @Schema(description = "Billing email address", example = "billing@indiabookexpo.in")
    private String billingEmail;

    @Schema(description = "Organization enabled status", example = "true")
    private Boolean enabled;

    @Schema(description = "Organization deleted status", example = "false")
    private Boolean deleted;

    @Schema(description = "Creation timestamp", example = "2026-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Last update timestamp", example = "2026-01-15T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    /**
     * Factory method to create OrganizationResponse from an Organization and its
     * limits. The limit may be null (e.g. before reconciliation), in which case
     * the cap and usage fields are left unset.
     *
     * @param organization the organization entity
     * @param limit the organization's limits/usage, or null if not yet present
     * @return the assembled response
     */
    public static OrganizationResponse fromEntity(Organization organization, OrganizationLimit limit) {
        OrganizationResponseBuilder builder = OrganizationResponse.builder()
                .id(organization.getId())
                .organizerName(organization.getOrganizerName())
                .email(organization.getEmail())
                .phoneNumber(organization.getPhoneNumber())
                .website(organization.getWebsite())
                .addressLine1(organization.getAddressLine1())
                .addressLine2(organization.getAddressLine2())
                .city(organization.getCity())
                .stateProvince(organization.getStateProvince())
                .postalCode(organization.getPostalCode())
                .country(organization.getCountry())
                .taxId(organization.getTaxId())
                .registrationNumber(organization.getRegistrationNumber())
                .subscriptionTier(organization.getSubscriptionTier())
                .subscriptionStatus(organization.getSubscriptionStatus())
                .subscriptionStartDate(organization.getSubscriptionStartDate())
                .subscriptionEndDate(organization.getSubscriptionEndDate())
                .billingEmail(organization.getBillingEmail())
                .enabled(organization.getEnabled())
                .deleted(organization.getDeleted())
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .createdBy(organization.getCreatedBy())
                .lastModifiedBy(organization.getLastModifiedBy());

        if (limit != null) {
            builder.userQuota(UserQuotaDto.fromEntity(limit));
        }

        return builder.build();
    }
}
