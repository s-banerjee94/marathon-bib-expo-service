-- A local database running ddl-auto=update may already have the columns, so they are added only where missing.
SET @missing = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organization_limits' AND COLUMN_NAME = 'max_inventory_items');
SET @ddl = IF(@missing,
              'ALTER TABLE `organization_limits`
                 ADD COLUMN `max_inventory_items` int NOT NULL DEFAULT 1000,
                 ADD COLUMN `max_inventory_attributes` int NOT NULL DEFAULT 30,
                 ADD COLUMN `used_inventory_items` int NOT NULL DEFAULT 0,
                 ADD COLUMN `used_inventory_attributes` int NOT NULL DEFAULT 0',
              'DO 0');
PREPARE add_inventory_limits FROM @ddl;
EXECUTE add_inventory_limits;
DEALLOCATE PREPARE add_inventory_limits;

-- Every organization starts counted with what it already holds.
UPDATE `organization_limits` l
SET l.`used_inventory_items` = (SELECT COUNT(*) FROM `inventory_items` i WHERE i.`organization_id` = l.`organization_id`),
    l.`used_inventory_attributes` = (SELECT COUNT(*) FROM `inventory_attributes` a WHERE a.`organization_id` = l.`organization_id`);
