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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organizations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_org_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_org_tax_id", columnNames = "taxId")
        },
        indexes = {
                @Index(name = "idx_org_email", columnList = "email"),
                @Index(name = "idx_org_deleted", columnList = "deleted"),
                @Index(name = "idx_org_enabled", columnList = "enabled"),
                @Index(name = "idx_org_organizer_name", columnList = "organizerName")
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

    // Limits & Quotas (moved from User entity)
    @Column(name = "max_organizer_users")
    @Builder.Default
    private Integer maxOrganizerUsers = 5;  // Default: 5, set to 0 for unlimited

    @Column(name = "max_distributors")
    @Builder.Default
    private Integer maxDistributors = 30;  // Default: 30, set to 0 for unlimited

    // Settings & Configuration
    @Column(columnDefinition = "JSON")
    private String settings;  // Store as JSON string

    // Billing & Subscription
    @Column(length = 50)
    private String subscriptionTier;  // FREE, BASIC, PREMIUM, ENTERPRISE

    @Column(length = 50)
    private String subscriptionStatus;  // ACTIVE, SUSPENDED, CANCELLED, TRIAL

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
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    // Bidirectional relationship with Users
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    @Builder.Default
    private List<User> users = new ArrayList<>();
}
