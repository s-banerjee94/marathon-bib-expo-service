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
 * Ties one goodies column of an imported participant list to the {@link InventoryItem} it is
 * handed out from. {@code goodieName} is the CSV column heading exactly as the import stored it,
 * since that is the key distribution looks the participant's entitlement up by.
 *
 * <p>The mapping deliberately carries no variant of its own. An item either varies by nothing, in
 * which case its single variant is the one handed over, or by exactly one attribute, in which case
 * the participant's own cell value picks it. An item that varies by more than one attribute cannot
 * be mapped, because a single CSV cell cannot say which combination a runner is owed.</p>
 */
@Entity
@Table(name = "inventory_goodie_mappings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_goodie_mapping_event_goodie",
                        columnNames = {"event_id", "goodie_name"})
        },
        // Asked before an item is deleted, and that question spans every event, so it cannot ride
        // on the unique constraint above.
        indexes = {
                @Index(name = "idx_inventory_goodie_mapping_item", columnList = "item_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryGoodieMapping implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "goodie_name", nullable = false, length = 150)
    private String goodieName;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

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
