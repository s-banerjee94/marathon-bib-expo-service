package com.timekeeper.bibexpo.inventory.model.enums;

/**
 * The kind of value an {@link com.timekeeper.bibexpo.inventory.model.entity.InventoryAttribute}
 * holds. {@code SELECT} takes its value from a pre-defined {@code InventoryAttributeOption} row and
 * stores that option's id; every other type is stored verbatim as text in the {@code rawValue}
 * column of the item or variant value row.
 */
public enum AttributeType {
    TEXT,
    NUMBER,
    BOOLEAN,
    SELECT
}
