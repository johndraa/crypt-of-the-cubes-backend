# Database Wipe Scripts

Scripts to safely wipe your dev database for testing.

## Database Information

- **Production**: `3rasel6` (DO NOT TOUCH)
- **Development**: `3rasel6_dev` (safe to wipe)

## Option 1: Quick Wipe (Recommended)

Simply drop and recreate the database. Spring Boot will auto-create all tables on startup.

```bash
mysql -h coms-3090-051.class.las.iastate.edu -u JDRAA -p 3rasel6 < scripts/wipe-dev-database-quick.sql
```

Or in MySQL Workbench:
1. Open `scripts/wipe-dev-database-quick.sql`
2. Execute the script

**Pros**: Fast, clean slate
**Cons**: Need to restart Spring Boot to recreate tables

## Option 2: Selective Wipe

Drop only tables, keep schema. Tables will be recreated with current schema.

```bash
mysql -h coms-3090-051.class.las.iastate.edu -u JDRAA -p 3rasel6 < scripts/wipe-dev-database.sql
```

**Pros**: No restart needed
**Cons**: Slightly slower

## Option 3: Truncate Only (Fastest)

Delete all data but keep the schema. No need to recreate tables or restart Spring Boot.

```bash
mysql -h coms-3090-051.class.las.iastate.edu -u JDRAA -p 3rasel6 < scripts/truncate-all-tables.sql
```

**Pros**: Fastest, no restart needed
**Cons**: Schema changes won't apply (use Option 1 or 2 if you changed entities)

## Using MySQL Workbench

1. Connect to `coms-3090-051.class.las.iastate.edu` as user `JDRAA`
2. Select schema `3rasel6_dev`
3. File → Open SQL Script → `wipe-dev-database.sql` or `wipe-dev-database-quick.sql`
4. Execute script (⚡ button)

## After Wiping

1. If using Option 1 (drop database), restart Spring Boot application
2. Run Postman setup tests to create fresh test data
3. Verify no 409 conflicts

## What Gets Wiped

All tables:
- `accounts`
- `user_progress`
- `user_character_unlocks`
- `game_characters`
- `matches`
- `match_participants`
- `messages`

Spring Boot will:
- Automatically recreate tables via JPA entities
- Seed characters via `CharacterSeedRunner`
- Start with clean test data

## Verification

After wiping, verify database is clean:

```sql
USE `3rasel6_dev`;
SELECT COUNT(*) FROM accounts;  -- Should return 0
SELECT COUNT(*) FROM game_characters;  -- Will be 0 until Spring Boot starts
```

After starting Spring Boot:

```sql
SELECT * FROM game_characters;  -- Should show seeded characters
```

