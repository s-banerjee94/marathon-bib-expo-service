package com.timekeeper.bibexpo.inventory.model.entity;

import com.timekeeper.bibexpo.inventory.model.enums.AttributeType;
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
 * A reusable, named dimension an item can vary by, or simply carry &mdash; &ldquo;Size&rdquo;,
 * &ldquo;Colour&rdquo;, &ldquo;Recyclable&rdquo;. Defined once and reused across every item that needs
 * it, the same way {@link InventoryTerm} works: every attribute belongs to one organization,
 * which owns its choices outright.
 *
 * <p>{@code variantAttribute} decides where a value ends up: {@code true} means choosing a value splits
 * stock into a separate {@link InventoryVariant}, so its values live in
 * {@link InventoryVariantAttributeValue}; {@code false} means the value is the same for the whole item,
 * so it lives once in {@link InventoryItemAttributeValue}.
 */
@Entity
@Table(name = "inventory_attributes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_attribute_org_name",
                        columnNames = {"organization_id", "name"})
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAttribute implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeType type;

    @Column(name = "variant_attribute", nullable = false)
    private boolean variantAttribute;

    @Column(nullable = false)
    private boolean required;

    // Kept in step with the option rows so the per-attribute cap costs no count query, and the
    // list can show "12 of 30" without one either. Always 0 for a type that has no option list.
    @Column(name = "option_count", nullable = false)
    @Builder.Default
    private Integer optionCount = 0;

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
