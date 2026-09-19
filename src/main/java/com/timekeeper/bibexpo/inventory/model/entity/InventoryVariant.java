package com.timekeeper.bibexpo.inventory.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

/**
 * A size, colour or flavour of an {@link InventoryItem}. Stock is always counted per variant; an
 * item with no real variants still gets exactly one, carrying no attribute values, so every code
 * path has a variant to work with.
 *
 * <p>{@code combinationKey} is the variant's only identity &mdash; its selected attribute/value id
 * pairs, sorted by {@code attributeId} and joined into one string. It is null just for the single
 * attribute-less variant such an item gets, and an item may hold at most one of those.</p>
 */
@Entity
@Table(name = "inventory_variants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_variant_item_combination",
                        columnNames = {"item_id", "combination_key"})
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryVariant implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "combination_key", length = 255)
    private String combinationKey;

    // S3 object key under UploadCategory.INVENTORY_VARIANT_IMAGE, null until an image is attached
    @Column(name = "image_key", length = 512)
    private String imageKey;

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
