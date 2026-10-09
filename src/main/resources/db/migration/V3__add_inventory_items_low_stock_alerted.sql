-- A local database running ddl-auto=update may already have the column, so it is added only where missing.
SET @missing = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_items' AND COLUMN_NAME = 'low_stock_alerted');
SET @ddl = IF(@missing,
              'ALTER TABLE `inventory_items` ADD COLUMN `low_stock_alerted` bit(1) NOT NULL DEFAULT b''0''',
              'DO 0');
PREPARE add_low_stock_alerted FROM @ddl;
EXECUTE add_low_stock_alerted;
DEALLOCATE PREPARE add_low_stock_alerted;
