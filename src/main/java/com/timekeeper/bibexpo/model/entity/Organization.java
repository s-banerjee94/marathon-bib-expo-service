package com.timekeeper.bibexpo.model.entity;

import com.timekeeper.bibexpo.config.EmptyStringToNullConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organizations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_org_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_org_tax_id", columnNames = "taxId"),
                @UniqueConstraint(name = "uk_org_organizer_name", columnNames = "organizerName"),
                @UniqueConstraint(name = "uk_org_phone_number", columnNames = "phoneNumber")
        },
        indexes = {
                @Index(name = "idx_org_email", columnList = "email"),
                @Index(name = "idx_org_deleted", columnList = "deleted"),
                @Index(name = "idx_org_enabled", columnList = "enabled")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Organizer Information
    @Column(nullable = false)
    private String organizerName;

    // Contact Information
    @Column(nullable = false)
    private String email;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String phoneNumber;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String website;

    // Address Information
    private String addressLine1;

    private String addressLine2;

    private String city;

    private String stateProvince;

    private String postalCode;

    private String country;

    // Tax & Legal Information
    @Convert(converter = EmptyStringToNullConverter.class)
    private String taxId;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String registrationNumber;

    // S3 object key of the organization logo; presigned to a URL at read time
    @Convert(converter = EmptyStringToNullConverter.class)
    private String logoKey;

    // Settings & Configuration
    @Column(columnDefinition = "JSON")
    private String settings;  // Store as JSON string

    // Billing & Subscription
    @Column(length = 50)
    private String subscriptionTier;  // PAY_AS_YOU_GO (baseline), PREMIUM, PARTNER; null normalizes to PAY_AS_YOU_GO

    @Column(length = 50)
    private String subscriptionStatus;  // ACTIVE (PREMIUM/PARTNER, in term), EXPIRED (term lapsed), FREE (PAY_AS_YOU_GO baseline)

    private LocalDateTime subscriptionStartDate;

    private LocalDateTime subscriptionEndDate;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String billingEmail;

    // Status Fields
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    // Audit Fields
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    // Bidirectional relationship with Users
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    @Builder.Default
    private List<User> users = new ArrayList<>();

    // Bidirectional relationship with Events
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Event> events = new ArrayList<>();
}
