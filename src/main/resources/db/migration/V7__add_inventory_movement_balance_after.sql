-- What the shelf held right after each ledger line, so an undo can tell where the shelf stood before it.
-- Lines written earlier keep null.
ALTER TABLE `inventory_movements` ADD COLUMN `balance_after` int DEFAULT NULL;
