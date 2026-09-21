SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `active_sessions` (
  `sid` varchar(36) NOT NULL,
  `browser` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `device_type` enum('DESKTOP','MOBILE','TABLET','UNKNOWN') DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `operating_system` varchar(100) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`sid`),
  KEY `idx_active_sessions_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `billing_stats_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `computed_by` varchar(16) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `refreshed_at` datetime(6) NOT NULL,
  `scope` varchar(20) NOT NULL,
  `scope_key` bigint NOT NULL,
  `snapshot_data` json NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_stats_scope` (`scope`,`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_name` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `race_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name_race` (`category_name`,`race_id`),
  KEY `idx_category_name` (`category_name`),
  KEY `idx_category_race` (`race_id`),
  CONSTRAINT `fk_category_race` FOREIGN KEY (`race_id`) REFERENCES `races` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `event_billing_state` (
  `event_id` bigint NOT NULL,
  `admin_attempts` int NOT NULL,
  `final_locked` bit(1) NOT NULL,
  `org_admin_attempts` int NOT NULL,
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `event_limits` (
  `event_id` bigint NOT NULL,
  `max_add_ons` int NOT NULL,
  `max_categories_per_race` int NOT NULL,
  `max_goodies` int NOT NULL,
  `max_imports` int NOT NULL,
  `max_participants` int NOT NULL,
  `max_races` int NOT NULL,
  `max_sms_campaigns` int NOT NULL,
  `max_sms_templates` int NOT NULL,
  `used_add_ons` int NOT NULL,
  `used_imports` int NOT NULL,
  PRIMARY KEY (`event_id`),
  CONSTRAINT `fk_event_limit_event` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `distribution_started` bit(1) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `event_description` text,
  `event_end_date` datetime(6) NOT NULL,
  `event_goodies` json DEFAULT NULL,
  `event_name` varchar(255) NOT NULL,
  `event_start_date` datetime(6) NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `logo_object_key` varchar(255) DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `postal_code` varchar(255) DEFAULT NULL,
  `state_province` varchar(255) DEFAULT NULL,
  `status` enum('CANCELLED','COMPLETED','DRAFT','PUBLISHED') NOT NULL,
  `timezone` varchar(50) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `venue_name` varchar(255) NOT NULL,
  `organization_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_name_org` (`event_name`,`organization_id`),
  KEY `idx_event_name` (`event_name`),
  KEY `idx_event_status` (`status`),
  KEY `idx_event_enabled` (`enabled`),
  KEY `idx_event_start_date` (`event_start_date`),
  KEY `idx_event_organization` (`organization_id`),
  CONSTRAINT `fk_event_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `import_errors` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `error_type` varchar(32) DEFAULT NULL,
  `field` varchar(64) DEFAULT NULL,
  `message` text,
  `line_number` int DEFAULT NULL,
  `import_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_import_errors_import` (`import_id`),
  CONSTRAINT `fk_import_errors_job` FOREIGN KEY (`import_id`) REFERENCES `import_jobs` (`import_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `import_jobs` (
  `import_id` varchar(36) NOT NULL,
  `error_summary` text,
  `event_id` bigint NOT NULL,
  `event_name` varchar(255) NOT NULL,
  `failure_count` int NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `goodies_detected` text,
  `imported_at` datetime(6) NOT NULL,
  `imported_by` bigint NOT NULL,
  `job_execution_id` bigint DEFAULT NULL,
  `mode` enum('ADD_ON','IMPORT') DEFAULT NULL,
  `status` enum('COMPLETED','FAILED','IN_PROGRESS') NOT NULL,
  `success_count` int NOT NULL,
  `total_rows` int NOT NULL,
  PRIMARY KEY (`import_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_attribute_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attribute_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `value` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_attribute_option_attribute_value` (`attribute_id`,`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_attributes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `option_count` int NOT NULL,
  `organization_id` bigint NOT NULL,
  `required` bit(1) NOT NULL,
  `type` enum('BOOLEAN','NUMBER','SELECT','TEXT') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `variant_attribute` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_attribute_org_name` (`organization_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_goodie_mappings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `goodie_name` varchar(150) NOT NULL,
  `item_id` bigint NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `organization_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_goodie_mapping_event_goodie` (`event_id`,`goodie_name`),
  KEY `idx_inventory_goodie_mapping_item` (`item_id`),
  KEY `idx_inventory_goodie_mapping_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_item_attribute_values` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attribute_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `option_id` bigint DEFAULT NULL,
  `raw_value` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_item_attribute_value_item_attribute` (`item_id`,`attribute_id`),
  KEY `idx_inventory_item_attribute_value_attribute` (`attribute_id`),
  KEY `idx_inventory_item_attribute_value_option` (`option_id`),
  CONSTRAINT `ck_inventory_item_attribute_value_one_of` CHECK (((`option_id` is null) or (`raw_value` is null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `low_stock_threshold` int DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `organization_id` bigint NOT NULL,
  `unit_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `variant_count` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_item_org_name` (`organization_id`,`name`),
  KEY `idx_inventory_item_org_created` (`organization_id`,`created_at`),
  KEY `idx_inventory_item_org_category_created` (`organization_id`,`category_id`,`created_at`),
  KEY `idx_inventory_item_category` (`category_id`),
  KEY `idx_inventory_item_unit` (`unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_locations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `organization_id` bigint NOT NULL,
  `type_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_location_org_name` (`organization_id`,`name`),
  KEY `idx_inventory_location_type` (`type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_movements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `beyond_entitlement` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `from_location_id` bigint DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `organization_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `reason` enum('CORRECTION','DAMAGED','LOST','OPENING_BALANCE','TRANSFER') DEFAULT NULL,
  `reference` varchar(100) DEFAULT NULL,
  `to_location_id` bigint DEFAULT NULL,
  `type` enum('ADJUSTMENT','ISSUE','RECEIPT','RETURN','REVERSAL','TRANSFER') NOT NULL,
  `variant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_inventory_movement_org_occurred` (`organization_id`,`occurred_at`),
  KEY `idx_inventory_movement_org_item_occurred` (`organization_id`,`item_id`,`occurred_at`),
  KEY `idx_inventory_movement_variant_occurred` (`variant_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avg_unit_cost` decimal(14,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `location_id` bigint NOT NULL,
  `on_hand` int NOT NULL,
  `reserved` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `variant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_stock_variant_location` (`variant_id`,`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_terms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `kind` enum('ITEM_CATEGORY','LOCATION_TYPE','UNIT') NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `organization_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_term_kind_org_name` (`kind`,`organization_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_variant_aliases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `organization_id` bigint NOT NULL,
  `source_value` varchar(150) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `variant_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_variant_alias_item_value` (`item_id`,`source_value`),
  KEY `idx_inventory_variant_alias_variant` (`variant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_variant_attribute_values` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attribute_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `option_id` bigint DEFAULT NULL,
  `raw_value` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `variant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_variant_attribute_value_variant_attribute` (`variant_id`,`attribute_id`),
  KEY `idx_inventory_variant_attribute_value_attribute` (`attribute_id`),
  KEY `idx_inventory_variant_attribute_value_option` (`option_id`),
  CONSTRAINT `ck_inventory_variant_attribute_value_one_of` CHECK (((`option_id` is null) or (`raw_value` is null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_variants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `combination_key` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `image_key` varchar(512) DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_variant_item_combination` (`item_id`,`combination_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invoice_line_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(14,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(255) NOT NULL,
  `discount_percent` decimal(5,2) DEFAULT NULL,
  `invoice_id` varchar(36) NOT NULL,
  `kind` enum('CUSTOM','DISCOUNT','EXTRA_USER','PARTICIPANT','SMS_CAMPAIGN','SURCHARGE') NOT NULL,
  `quantity` int DEFAULT NULL,
  `system_generated` bit(1) NOT NULL,
  `unit_price` decimal(12,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_line_item_invoice` (`invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invoice_number_sequence` (
  `financial_year` varchar(7) NOT NULL,
  `last_number` bigint NOT NULL,
  PRIMARY KEY (`financial_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invoices` (
  `bill_id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_date` datetime(6) NOT NULL,
  `event_id` bigint NOT NULL,
  `event_name` varchar(255) NOT NULL,
  `finalized_at` datetime(6) DEFAULT NULL,
  `invoice_number` varchar(32) DEFAULT NULL,
  `organization_id` bigint NOT NULL,
  `organizer_name` varchar(255) NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payment_status` enum('PAID','UNPAID') NOT NULL,
  `pdf_key` varchar(255) DEFAULT NULL,
  `reason` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'FINAL',
  `subtotal` decimal(14,2) NOT NULL,
  `tax_amount` decimal(14,2) NOT NULL,
  `total_amount` decimal(14,2) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`bill_id`),
  UNIQUE KEY `uk_invoice_number` (`invoice_number`),
  KEY `idx_invoice_event` (`event_id`),
  KEY `idx_invoice_organization` (`organization_id`),
  KEY `idx_invoice_created_at` (`created_at`),
  KEY `idx_invoice_reason` (`reason`),
  KEY `idx_invoice_payment_status` (`payment_status`),
  KEY `idx_invoice_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `messaging_providers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth_token` text,
  `auth_type` enum('TOKEN','USERNAME_PASSWORD') NOT NULL,
  `base_url` varchar(512) DEFAULT NULL,
  `body_template` text,
  `channel` enum('EMAIL','SMS','WHATSAPP') NOT NULL,
  `content_type` enum('FORM','JSON','TEXT','XML') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `default_country_code` varchar(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `http_method` enum('GET','PATCH','POST','PUT') NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `organization_id` bigint DEFAULT NULL,
  `password` text,
  `request_params` text,
  `success_contains` varchar(200) DEFAULT NULL,
  `template_mode` enum('CLIENT_RENDERED','PROVIDER_RENDERED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `message_usage` enum('CAMPAIGN','SYSTEM') NOT NULL,
  `username` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_messaging_provider_usage_channel_org` (`message_usage`,`channel`,`organization_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `org_daily_stats` (
  `organization_id` bigint NOT NULL,
  `snapshot_date` date NOT NULL,
  `active_events` int NOT NULL,
  `computed_at` datetime(6) NOT NULL,
  `distinct_cities` int NOT NULL,
  `total_events` int NOT NULL,
  `total_users` int NOT NULL,
  PRIMARY KEY (`organization_id`,`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `organization_limits` (
  `organization_id` bigint NOT NULL,
  `max_admins` int NOT NULL,
  `max_distributors` int NOT NULL,
  `max_inventory_attribute_options` int NOT NULL,
  `max_inventory_locations` int NOT NULL,
  `max_inventory_terms` int NOT NULL,
  `max_item_variants` int NOT NULL,
  `max_organizer_users` int NOT NULL,
  `max_variant_attributes_per_item` int NOT NULL,
  `used_admins` int NOT NULL,
  `used_distributors` int NOT NULL,
  `used_inventory_locations` int NOT NULL,
  `used_inventory_terms` int NOT NULL,
  `used_organizer_users` int NOT NULL,
  PRIMARY KEY (`organization_id`),
  CONSTRAINT `fk_organization_limit_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `organizations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `billing_email` varchar(255) DEFAULT NULL,
  `city` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `logo_key` varchar(255) DEFAULT NULL,
  `organizer_name` varchar(255) NOT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `postal_code` varchar(255) DEFAULT NULL,
  `registration_number` varchar(255) DEFAULT NULL,
  `settings` json DEFAULT NULL,
  `state_province` varchar(255) DEFAULT NULL,
  `subscription_end_date` datetime(6) DEFAULT NULL,
  `subscription_start_date` datetime(6) DEFAULT NULL,
  `subscription_status` varchar(50) DEFAULT NULL,
  `subscription_tier` varchar(50) DEFAULT NULL,
  `tax_id` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_email` (`email`),
  UNIQUE KEY `uk_org_organizer_name` (`organizer_name`),
  UNIQUE KEY `uk_org_tax_id` (`tax_id`),
  UNIQUE KEY `uk_org_phone_number` (`phone_number`),
  KEY `idx_org_email` (`email`),
  KEY `idx_org_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `platform_daily_stats` (
  `snapshot_date` date NOT NULL,
  `active_events` int NOT NULL,
  `computed_at` datetime(6) NOT NULL,
  `distinct_cities` int NOT NULL,
  `organizations` int NOT NULL,
  `total_events` int NOT NULL,
  `total_users` int NOT NULL,
  PRIMARY KEY (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `races` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `race_description` text,
  `race_name` varchar(255) NOT NULL,
  `reporting_time` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_race_name_event` (`race_name`,`event_id`),
  KEY `idx_race_name` (`race_name`),
  KEY `idx_race_deleted` (`deleted`),
  KEY `idx_race_event` (`event_id`),
  CONSTRAINT `fk_race_event` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sms_campaigns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `organization_id` bigint DEFAULT NULL,
  `retry_count` int NOT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `sent_count` int NOT NULL,
  `status` enum('ACTIVE','DRAFT','FAILED','SENDING','SENT') NOT NULL,
  `target_filter` enum('ALL','NOT_COLLECTED') DEFAULT NULL,
  `trigger_type` enum('AUTO_BIB_COLLECTED','SCHEDULED') DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `sms_template_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sms_campaign_event` (`event_id`),
  KEY `idx_sms_campaign_trigger_type` (`trigger_type`),
  KEY `idx_sms_campaign_status` (`status`),
  KEY `idx_sms_campaign_scheduled_at` (`scheduled_at`),
  KEY `fk_sms_campaign_template` (`sms_template_id`),
  CONSTRAINT `fk_sms_campaign_event` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_sms_campaign_template` FOREIGN KEY (`sms_template_id`) REFERENCES `sms_templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sms_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body_variables` text,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `note` text,
  `provider_source` enum('DEFAULT','ORGANIZATION') DEFAULT NULL,
  `render_mode` enum('CLIENT_RENDERED','PROVIDER_RENDERED') DEFAULT NULL,
  `sender_id` varchar(32) DEFAULT NULL,
  `sms_template_id` varchar(100) DEFAULT NULL,
  `template` text,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sms_template_id_event` (`sms_template_id`,`event_id`),
  KEY `idx_sms_template_id` (`sms_template_id`),
  KEY `idx_sms_event` (`event_id`),
  CONSTRAINT `fk_sms_template_event` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `system_message_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body` text,
  `channel` enum('EMAIL','SMS','WHATSAPP') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `dlt_template_id` varchar(64) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `purpose` enum('INVITE','OTP','PASSWORD_RESET') NOT NULL,
  `sender_id` varchar(32) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `variables` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_template_purpose_channel` (`purpose`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_non_locked` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `full_name` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `profile_picture_key` varchar(255) DEFAULT NULL,
  `role` enum('ADMIN','DISTRIBUTOR','ORGANIZER_ADMIN','ORGANIZER_USER','ROOT') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `event_id` bigint DEFAULT NULL,
  `organization_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  UNIQUE KEY `uk_user_phone` (`phone_number`),
  KEY `idx_user_username` (`username`),
  KEY `idx_user_email` (`email`),
  KEY `idx_user_phone` (`phone_number`),
  KEY `fk_user_event` (`event_id`),
  KEY `fk_user_organization` (`organization_id`),
  CONSTRAINT `fk_user_event` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_user_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users_archive` (
  `id` bigint NOT NULL,
  `account_non_locked` bit(1) NOT NULL,
  `archived_at` datetime(6) NOT NULL,
  `archived_by` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `full_name` varchar(255) DEFAULT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `role` enum('ADMIN','DISTRIBUTOR','ORGANIZER_ADMIN','ORGANIZER_USER','ROOT') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(255) NOT NULL,
  `organization_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_archive_username` (`username`),
  KEY `idx_user_archive_email` (`email`),
  KEY `idx_user_archive_phone` (`phone_number`),
  KEY `idx_user_archive_org` (`organization_id`),
  KEY `idx_user_archive_role` (`role`),
  CONSTRAINT `fk_user_archive_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `whatsapp_campaigns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `organization_id` bigint NOT NULL,
  `retry_count` int NOT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `sent_count` int NOT NULL,
  `status` enum('ACTIVE','DRAFT','FAILED','SENDING','SENT') NOT NULL,
  `target_filter` enum('ALL','NOT_COLLECTED') DEFAULT NULL,
  `trigger_type` enum('AUTO_BIB_COLLECTED','SCHEDULED') DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `whatsapp_template_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_whatsapp_campaign_event` (`event_id`),
  KEY `idx_whatsapp_campaign_trigger_type` (`trigger_type`),
  KEY `idx_whatsapp_campaign_status` (`status`),
  KEY `idx_whatsapp_campaign_scheduled_at` (`scheduled_at`),
  KEY `fk_whatsapp_campaign_template` (`whatsapp_template_id`),
  CONSTRAINT `fk_whatsapp_campaign_template` FOREIGN KEY (`whatsapp_template_id`) REFERENCES `whatsapp_templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `whatsapp_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body` text NOT NULL,
  `body_variables` text,
  `content_sid` varchar(64) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `last_modified_by` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `note` text,
  `organization_id` bigint NOT NULL,
  `provider_source` enum('DEFAULT','ORGANIZATION') DEFAULT NULL,
  `render_mode` enum('CLIENT_RENDERED','PROVIDER_RENDERED') DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_whatsapp_template_sid_event` (`content_sid`,`event_id`),
  KEY `idx_whatsapp_template_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
