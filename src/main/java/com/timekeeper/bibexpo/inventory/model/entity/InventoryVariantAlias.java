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
 * One of an item's spellings: a value that appears in imported rosters, and the
 * {@link InventoryVariant} of this item it means. A roster may say {@code M} where the item's own
 * sizes read {@code 38}, and no amount of matching will bridge that on its own.
 *
 * <p>A spelling hangs off the item rather than off an event's goody because {@code M} means 38 by
 * virtue of which shirt this is, not which race it was handed out at. The same item used by next
 * year's event is understood without anyone teaching them again, and the spellings that do
 * vary between rosters — {@code M} one year, {@code Medium} the next — simply accumulate as
 * separate lines pointing at the same variant.</p>
 *
 * <p>A null {@code variantId} is a deliberate answer rather than a missing one: it records that
 * this spelling means the participant is owed nothing, such as {@code No} in a medal column, so it
 * neither counts as demand nor takes anything off the shelf at the counter.</p>
 */
@Entity
@Table(name = "inventory_variant_aliases",
        // Spellings are matched without regard to case, and the column's collation is what makes
        // that true of the constraint as well: one line covers both M and m.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_variant_alias_item_value",
                        columnNames = {"item_id", "source_value"})
        },
        // Asked before a variant is removed, and that question spans every item's spellings.
        indexes = {
                @Index(name = "idx_inventory_variant_alias_variant", columnList = "variant_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryVariantAlias implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "source_value", nullable = false, length = 150)
    private String sourceValue;

    // null means this spelling is owed nothing at all
    @Column(name = "variant_id")
    private Long variantId;

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
