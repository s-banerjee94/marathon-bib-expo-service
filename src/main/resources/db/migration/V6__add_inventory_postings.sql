-- A posting groups the ledger lines one action wrote, so they post all or none and undo together.
CREATE TABLE IF NOT EXISTS `inventory_postings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `line_count` int NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `organization_id` bigint NOT NULL,
  `reference` varchar(100) DEFAULT NULL,
  `request_hash` varchar(64) NOT NULL,
  `reversed_by_posting_id` bigint DEFAULT NULL,
  `reverses_posting_id` bigint DEFAULT NULL,
  `type` enum('ADJUSTMENT','ISSUE','RECEIPT','RETURN','REVERSAL','TRANSFER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_posting_idempotency` (`organization_id`,`created_by`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A local database running ddl-auto=update may already have these columns, so each is added only where missing.
SET @missing = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_movements' AND COLUMN_NAME = 'posting_id');
SET @ddl = IF(@missing,
              'ALTER TABLE `inventory_movements`
                 ADD COLUMN `posting_id` bigint DEFAULT NULL,
                 ADD KEY `idx_inventory_movement_posting` (`posting_id`)',
              'DO 0');
PREPARE add_posting_id FROM @ddl;
EXECUTE add_posting_id;
DEALLOCATE PREPARE add_posting_id;

SET @missing = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_items' AND COLUMN_NAME = 'image_key');
SET @ddl = IF(@missing, 'ALTER TABLE `inventory_items` ADD COLUMN `image_key` varchar(512) DEFAULT NULL', 'DO 0');
PREPARE add_item_image_key FROM @ddl;
EXECUTE add_item_image_key;
DEALLOCATE PREPARE add_item_image_key;
