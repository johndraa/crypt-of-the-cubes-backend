-- Script to safely wipe the dev database (3rasel6_dev)
-- Run this before importing Postman tests to avoid 409 conflicts

USE `3rasel6_dev`;

-- Disable foreign key checks temporarily
SET FOREIGN_KEY_CHECKS = 0;

-- Drop all tables in dependency order to avoid foreign key violations
-- Order matters due to foreign key constraints
DROP TABLE IF EXISTS `match_participants`;
DROP TABLE IF EXISTS `matches`;
DROP TABLE IF EXISTS `messages`;
DROP TABLE IF EXISTS `user_character_unlocks`;
DROP TABLE IF EXISTS `characters`;
DROP TABLE IF EXISTS `user_progress`;
DROP TABLE IF EXISTS `accounts`;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Verify database is empty
SELECT 'Database wiped successfully' AS status;
SELECT COUNT(*) AS remaining_tables FROM information_schema.tables WHERE table_schema = '3rasel6_dev' AND table_type = 'BASE TABLE';

