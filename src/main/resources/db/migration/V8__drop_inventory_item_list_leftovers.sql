-- The item list is now every item as {id, name} in name order, which uk_inventory_item_org_name
-- (organization_id, name) already serves, so the two newest-first indexes go. variant_count was kept
-- only for that list and nothing reads it any more.
ALTER TABLE `inventory_items`
    DROP INDEX `idx_inventory_item_org_created`,
    DROP INDEX `idx_inventory_item_org_category_created`,
    DROP COLUMN `variant_count`;
