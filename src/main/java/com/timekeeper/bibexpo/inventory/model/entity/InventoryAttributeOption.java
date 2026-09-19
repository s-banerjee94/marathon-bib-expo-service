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
 * One allowed choice for a {@code SELECT}-type {@link InventoryAttribute} &mdash; e.g.
 * &ldquo;500ml&rdquo; and &ldquo;1L&rdquo; under &ldquo;Size&rdquo;. Only {@code SELECT} attributes
 * have rows here; {@code TEXT}/{@code NUMBER}/{@code BOOLEAN} values are stored verbatim in the
 * {@code rawValue} column of the item or variant value row.
 */
@Entity
@Table(name = "inventory_attribute_options",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_attribute_option_attribute_value",
                        columnNames = {"attribute_id", "value"})
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAttributeOption implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attribute_id", nullable = false)
    private Long attributeId;

    @Column(nullable = false, length = 100)
    private String value;

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
