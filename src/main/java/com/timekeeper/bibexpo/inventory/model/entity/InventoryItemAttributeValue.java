package com.timekeeper.bibexpo.inventory.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * Which {@link InventoryAttribute} one {@link InventoryItem} uses. For a
 * <b>non</b>-variant-defining attribute ({@code variantAttribute = false}), this row also carries the
 * single value that applies to every variant of the item &mdash; either {@code optionId} (a
 * {@code SELECT} choice) or {@code rawValue} (any other type). Variant-defining attributes are opted
 * into here with both left null; their actual values live per-variant in
 * {@link InventoryVariantAttributeValue} instead.
 */
@Entity
@Table(name = "inventory_item_attribute_values",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_item_attribute_value_item_attribute",
                        columnNames = {"item_id", "attribute_id"})
        },
        indexes = {
                @Index(name = "idx_inventory_item_attribute_value_attribute", columnList = "attribute_id"),
                @Index(name = "idx_inventory_item_attribute_value_option", columnList = "option_id")
        })
@Check(name = "ck_inventory_item_attribute_value_one_of", constraints = "option_id is null or raw_value is null")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemAttributeValue implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "attribute_id", nullable = false)
    private Long attributeId;

    // set only for a non-variant SELECT attribute
    @Column(name = "option_id")
    private Long optionId;

    // set only for a non-variant TEXT/NUMBER/BOOLEAN attribute
    @Column(name = "raw_value", length = 255)
    private String rawValue;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;
}
