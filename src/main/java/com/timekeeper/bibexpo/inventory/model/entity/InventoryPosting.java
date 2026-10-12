package com.timekeeper.bibexpo.inventory.model.entity;

import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * One stock action as the person took it: the ledger lines it wrote carry its id, so they post all or
 * none and are undone together by a single opposite posting.
 */
@Entity
@Table(name = "inventory_postings",
        // The key a client sends with each click: a repeat of it finds this row instead of posting again,
        // and the unique key makes a twin request sent at the same instant wait for this one and fail.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_posting_idempotency",
                        columnNames = {"organization_id", "created_by", "idempotency_key"})
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryPosting implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(length = 100)
    private String reference;

    @Column(length = 500)
    private String note;

    @Column(name = "line_count", nullable = false)
    private Integer lineCount;

    @Column(name = "reverses_posting_id")
    private Long reversesPostingId;

    // Set once, by the conditional update that claims the undo, so two undos at once cannot both win.
    @Column(name = "reversed_by_posting_id", updatable = false)
    private Long reversedByPostingId;

    // ponytail: keys are kept for good rather than for a day; one short string per posting costs nothing.
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    // A repeated key must carry the same request, or it is a different action under a reused key.
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @CreatedBy
    private String createdBy;
}
