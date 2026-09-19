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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * A thing an organization stocks — a t-shirt, a medal, a roll of barricade tape. Always
 * organization-scoped.
 */
@Entity
@Table(name = "inventory_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_item_org_name",
                        columnNames = {"organization_id", "name"})
        },
        // The list is always one organization's, newest first, so both indexes end on created_at:
        // the database walks them backwards and stops at the page size instead of sorting the
        // whole catalogue. The second one carries the category filter into the same walk.
        // The last two answer "is this term still in use?" before a term is deleted. That count is
        // keyed by the term alone, so neither can ride on an index that leads with organization_id.
        indexes = {
                @Index(name = "idx_inventory_item_org_created",
                        columnList = "organization_id, created_at"),
                @Index(name = "idx_inventory_item_org_category_created",
                        columnList = "organization_id, category_id, created_at"),
                @Index(name = "idx_inventory_item_category", columnList = "category_id"),
                @Index(name = "idx_inventory_item_unit", columnList = "unit_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    // A free-text warning for whoever opens this item next — not a property of the product itself,
    // which belongs in an attribute. Blank is stored as null, so a stale note can be removed.
    @Column(length = 500)
    private String note;

    // Kept in step by InventoryItemService, the only writer of variants, so the item list can show
    // the count without querying the variants of every row.
    @Column(name = "variant_count", nullable = false)
    @Builder.Default
    private Integer variantCount = 0;

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
