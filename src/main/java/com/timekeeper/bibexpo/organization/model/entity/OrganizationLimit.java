package com.timekeeper.bibexpo.organization.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Per-organization limits and live usage counters.
 * Shares its primary key with {@link Organization} (one row per organization).
 * Usage counters are maintained atomically on user create/delete.
 */
@Entity
@Table(name = "organization_limits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationLimit implements Serializable {

    @Id
    private Long organizationId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "max_admins", nullable = false)
    @Builder.Default
    private Integer maxAdmins = 1;

    @Column(name = "max_organizer_users", nullable = false)
    @Builder.Default
    private Integer maxOrganizerUsers = 1;

    @Column(name = "max_distributors", nullable = false)
    @Builder.Default
    private Integer maxDistributors = 3;

    // TODO: the four inventory caps below are read by the inventory module, but no admin
    // endpoint changes them yet.
    @Column(name = "max_inventory_terms", nullable = false)
    @Builder.Default
    private Integer maxInventoryTerms = 30;

    @Column(name = "max_inventory_attribute_options", nullable = false)
    @Builder.Default
    private Integer maxInventoryAttributeOptions = 30;

    @Column(name = "max_variant_attributes_per_item", nullable = false)
    @Builder.Default
    private Integer maxVariantAttributesPerItem = 2;

    @Column(name = "max_item_variants", nullable = false)
    @Builder.Default
    private Integer maxItemVariants = 30;

    @Column(name = "used_admins", nullable = false)
    @Builder.Default
    private Integer usedAdmins = 0;

    @Column(name = "used_organizer_users", nullable = false)
    @Builder.Default
    private Integer usedOrganizerUsers = 0;

    @Column(name = "used_distributors", nullable = false)
    @Builder.Default
    private Integer usedDistributors = 0;
}
