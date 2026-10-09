-- avg_unit_cost and reserved were never filled and required was never checked, so the app no longer maps them.
-- Each is dropped only where it exists, so a database that never had one still migrates.
SET @present = (SELECT COUNT(*) > 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_stock' AND COLUMN_NAME = 'avg_unit_cost');
SET @ddl = IF(@present, 'ALTER TABLE `inventory_stock` DROP COLUMN `avg_unit_cost`', 'DO 0');
PREPARE drop_avg_unit_cost FROM @ddl;
EXECUTE drop_avg_unit_cost;
DEALLOCATE PREPARE drop_avg_unit_cost;

SET @present = (SELECT COUNT(*) > 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_stock' AND COLUMN_NAME = 'reserved');
SET @ddl = IF(@present, 'ALTER TABLE `inventory_stock` DROP COLUMN `reserved`', 'DO 0');
PREPARE drop_reserved FROM @ddl;
EXECUTE drop_reserved;
DEALLOCATE PREPARE drop_reserved;

SET @present = (SELECT COUNT(*) > 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_attributes' AND COLUMN_NAME = 'required');
SET @ddl = IF(@present, 'ALTER TABLE `inventory_attributes` DROP COLUMN `required`', 'DO 0');
PREPARE drop_required FROM @ddl;
EXECUTE drop_required;
DEALLOCATE PREPARE drop_required;
