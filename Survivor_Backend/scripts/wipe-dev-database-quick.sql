-- Quick wipe script - just drop the schema and recreate it
-- WARNING: This will delete everything in 3rasel6_dev

DROP DATABASE IF EXISTS `3rasel6_dev`;
CREATE DATABASE `3rasel6_dev` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `3rasel6_dev`;

SELECT 'Database recreated successfully. Spring Boot will auto-create tables on startup.' AS status;

