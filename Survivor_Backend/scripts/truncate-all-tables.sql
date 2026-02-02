-- Truncate all tables (quick reset without dropping tables)
-- Use this when you want to keep the schema but delete all data
-- Spring Boot will NOT need to recreate tables

USE `3rasel6_dev`;

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `match_participants`;
TRUNCATE TABLE `matches`;
TRUNCATE TABLE `messages`;
TRUNCATE TABLE `user_character_unlocks`;
TRUNCATE TABLE `characters`;
TRUNCATE TABLE `user_progress`;
TRUNCATE TABLE `accounts`;

SET FOREIGN_KEY_CHECKS = 1;

-- Verify all tables are empty
SELECT 'All tables truncated. Data deleted but schema intact.' AS status;
SELECT 
    'accounts' AS table_name, COUNT(*) AS row_count FROM accounts
UNION ALL SELECT 'user_progress', COUNT(*) FROM user_progress
UNION ALL SELECT 'characters', COUNT(*) FROM characters
UNION ALL SELECT 'user_character_unlocks', COUNT(*) FROM user_character_unlocks
UNION ALL SELECT 'matches', COUNT(*) FROM matches
UNION ALL SELECT 'match_participants', COUNT(*) FROM match_participants
UNION ALL SELECT 'messages', COUNT(*) FROM messages;

