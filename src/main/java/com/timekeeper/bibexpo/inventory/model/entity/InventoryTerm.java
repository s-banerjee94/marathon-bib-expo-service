package com.timekeeper.bibexpo.inventory.model.entity;

import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
 * One organisation's (or the platform's) word for a category, location type or unit. Rows are
 * discriminated by {@code kind}; {@code organizationId} null is a platform default seeded at
 * startup and editable only by ROOT; a set value is one organization's own addition.
 */
@Entity
@Table(name = "inventory_terms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_term_kind_org_name",
                        columnNames = {"kind", "organization_id", "name"})
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTerm implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TermKind kind;

    // null = platform default (ROOT-managed); set = this organization's own term
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false, length = 100)
    private String name;

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
