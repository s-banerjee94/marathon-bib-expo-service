-- A local database running ddl-auto=update may already have the column, so it is added only where missing.
SET @missing = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'lock_reason');
SET @ddl = IF(@missing,
              'ALTER TABLE `users` ADD COLUMN `lock_reason` enum(''MANUAL'',''ORGANIZATION_DISABLED'') DEFAULT NULL',
              'DO 0');
PREPARE add_lock_reason FROM @ddl;
EXECUTE add_lock_reason;
DEALLOCATE PREPARE add_lock_reason;
