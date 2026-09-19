package com.timekeeper.bibexpo.inventory.model.entity;

import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * One line of the append-only stock ledger. Never updated or deleted — an undo is always a new,
 * opposite line. {@link InventoryStock} is a derived, rebuildable sum of these rows.
 */
@Entity
@Table(name = "inventory_movements",
        // Every index ends on occurred_at, the column the list orders by, so the database walks it
        // backwards and stops at the page size instead of sorting the ledger. Type, reason and who
        // posted the line are checked while walking — six values each, they narrow too little to
        // lead an index of their own.
        indexes = {
                @Index(name = "idx_inventory_movement_org_occurred",
                        columnList = "organization_id, occurred_at"),
                @Index(name = "idx_inventory_movement_org_item_occurred",
                        columnList = "organization_id, item_id, occurred_at"),
                @Index(name = "idx_inventory_movement_variant_occurred",
                        columnList = "variant_id, occurred_at")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Copied from the variant's item at write time, never afterwards: a ledger line is written once
    // and an item never changes organization, so the copy cannot drift. It is what lets the ledger
    // be read from one table instead of hunting through variants and items to find the owner.
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MovementReason reason;

    // null for a RECEIPT — stock is coming from outside the system
    @Column(name = "from_location_id")
    private Long fromLocationId;

    // null for an ISSUE — stock is going out to a person, not another of our locations
    @Column(name = "to_location_id")
    private Long toLocationId;

    // always positive; direction is implied by type and which of the two location columns is set
    @Column(nullable = false)
    private Integer quantity;

    // bib number, issue slip id, PO id — whatever caused this line
    @Column(length = 100)
    private String reference;

    @Column(name = "beyond_entitlement", nullable = false)
    @Builder.Default
    private Boolean beyondEntitlement = false;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @CreatedBy
    private String createdBy;
}
