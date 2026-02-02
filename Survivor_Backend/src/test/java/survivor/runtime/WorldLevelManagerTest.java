package survivor.runtime;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;
import survivor.combat.Stats;
import survivor.combat.Vec2;
import survivor.model.EnemyState;
import survivor.model.EnemyType;
import survivor.model.PlayerState;
import survivor.shared.AttackStyle;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for WorldLevelManager.
 * 
 * Coverage Goals:
 * - WorldLevelManager.startFirstWave() - initial wave generation
 * - WorldLevelManager.handleDeaths() - wave advancement, enemy cap, stat scaling
 * - WorldLevelManager.getCurrentWorldLevel() - level tracking
 * - WorldLevelManager.getTotalEnemiesThisWave() - enemy count calculation
 * 
 * Strategy: Unit testing with boundary values (enemy cap at 250), state progression validation
 */
@ActiveProfiles({"test"})
public class WorldLevelManagerTest {

    private WorldLevelManager worldLevelManager;
    private List<EnemyState> enemies;
    private List<PlayerState> players;
    private int enemyIdCounter;

    @Before
    public void setUp() {
        worldLevelManager = new WorldLevelManager();
        enemies = new ArrayList<>();
        players = new ArrayList<>();
        enemyIdCounter = 1000;

        // Create a test player
        Stats baseStats = new Stats(100, 50, 30, 20, 5, 10);
        Vec2 spawn = new Vec2(100.0, 100.0);
        PlayerState player = new PlayerState(1, spawn, baseStats, AttackStyle.AOE, baseStats.health());
        players.add(player);
    }

    /**
     * Test: startFirstWave() - initial state
     * Coverage: WorldLevelManager.startFirstWave() - initial wave branch
     * Strategy: White-box, initial state validation
     * Equivalence: First wave generation
     * Branches: Initial wave generation branch
     * Why: Tests initial world level and first wave generation
     */
    @Test
    public void testWorldLevelStartsAtOne() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);

        assertEquals("World level should start at 1", 1, worldLevelManager.getCurrentWorldLevel());
        assertEquals("First wave should have 20 enemies (20 + (1-1)*10)", 20, worldLevelManager.getTotalEnemiesThisWave());
        assertEquals("All enemies should be remaining initially", 20, worldLevelManager.getRemainingEnemiesInWave());
        assertEquals("Enemies list should have 20 enemies", 20, enemies.size());
    }

    /**
     * Test: handleDeaths() - partial enemy deaths
     * Coverage: WorldLevelManager.handleDeaths() - no advancement branch
     * Strategy: White-box, branch coverage
     * Equivalence: Not all enemies dead
     * Branches: Partial death branch (no advancement)
     * Why: Tests that world level doesn't advance until all enemies are dead
     */
    @Test
    public void testWorldLevelDoesNotAdvanceWhenEnemiesAlive() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);
        int initialLevel = worldLevelManager.getCurrentWorldLevel();
        int initialEnemyCount = enemies.size();

        // Kill only some enemies (not all)
        List<CombatResolve.DeathEvent> partialDeaths = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            enemies.get(i).applyDamage(1000); // Kill enemy
            partialDeaths.add(new CombatResolve.DeathEvent(enemies.get(i).id(), "enemy"));
        }

        // Process deaths
        worldLevelManager.handleDeaths(partialDeaths, enemies, players, this::nextEnemyId, false);

        // World level should NOT advance
        assertEquals("World level should not advance when enemies remain", initialLevel, worldLevelManager.getCurrentWorldLevel());
        assertEquals("Remaining enemies should be reduced", 10, worldLevelManager.getRemainingEnemiesInWave());
        assertEquals("No new wave should spawn", initialEnemyCount, enemies.size());
    }

    /**
     * Test: handleDeaths() - all enemies dead
     * Coverage: WorldLevelManager.handleDeaths() - wave advancement branch
     * Strategy: White-box, branch coverage
     * Equivalence: All enemies dead
     * Branches: Wave advancement branch, new wave generation branch
     * Why: Tests world level advancement when wave is cleared
     */
    @Test
    public void testWorldLevelAdvancesWhenAllEnemiesDie() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);
        int initialLevel = worldLevelManager.getCurrentWorldLevel();
        int initialEnemyCount = enemies.size();

        // Kill ALL enemies
        List<CombatResolve.DeathEvent> allDeaths = new ArrayList<>();
        for (EnemyState enemy : enemies) {
            enemy.applyDamage(1000); // Kill enemy
            allDeaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
        }

        // Process deaths
        worldLevelManager.handleDeaths(allDeaths, enemies, players, this::nextEnemyId, false);

        // World level should advance
        assertEquals("World level should advance to 2", initialLevel + 1, worldLevelManager.getCurrentWorldLevel());
        assertEquals("New wave should spawn with 30 enemies (20 + (2-1)*10)", 30, worldLevelManager.getTotalEnemiesThisWave());
        assertEquals("All new enemies should be remaining", 30, worldLevelManager.getRemainingEnemiesInWave());
        // Note: enemies list still contains dead enemies, but new ones are added
        assertTrue("New enemies should be added", enemies.size() >= initialEnemyCount);
    }

    /**
     * Test: handleDeaths() - enemy count progression
     * Coverage: WorldLevelManager.handleDeaths() - enemy count calculation branch
     * Strategy: White-box, boundary value testing (enemy count formula)
     * Equivalence: Multiple wave progressions
     * Branches: Enemy count calculation branch for each level
     * Why: Tests enemy count formula: 20 + (level - 1) * 10
     */
    @Test
    public void testEnemyCountIncreasesUpToCap() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);

        // Test progression: Level 1 = 20, Level 2 = 30, Level 3 = 40, etc.
        int[] expectedCounts = {20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250};

        for (int i = 0; i < expectedCounts.length; i++) {
            int expectedCount = expectedCounts[i];
            int actualCount = worldLevelManager.getTotalEnemiesThisWave();
            assertEquals("Level " + worldLevelManager.getCurrentWorldLevel() + " should have " + expectedCount + " enemies",
                    expectedCount, actualCount);

            // Clear all enemies to advance to next level
            if (i < expectedCounts.length - 1) {
                List<CombatResolve.DeathEvent> deaths = new ArrayList<>();
                for (EnemyState enemy : enemies) {
                    if (!enemy.isDead()) {
                        enemy.applyDamage(1000);
                        deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
                    }
                }
                worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);
            }
        }
    }

    /**
     * Test: handleDeaths() - enemy count cap at 250
     * Coverage: WorldLevelManager.handleDeaths() - enemy cap branch
     * Strategy: White-box, boundary value testing (250 cap)
     * Equivalence: Levels at and above cap (level 24+)
     * Branches: Enemy cap check branch
     * Why: Tests that enemy count doesn't exceed 250 after level 24
     */
    @Test
    public void testEnemyCountCapsAt250() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);

        // Advance to level 24 (first level that hits 250)
        // Level 1 = 20, so we need to advance 23 times
        for (int level = 1; level < 24; level++) {
            List<CombatResolve.DeathEvent> deaths = new ArrayList<>();
            for (EnemyState enemy : enemies) {
                if (!enemy.isDead()) {
                    enemy.applyDamage(1000);
                    deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
                }
            }
            worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);
        }

        // Level 24 should have exactly 250 enemies
        assertEquals("Level 24 should have 250 enemies", 250, worldLevelManager.getTotalEnemiesThisWave());

        // Advance to level 25
        List<CombatResolve.DeathEvent> deaths = new ArrayList<>();
        for (EnemyState enemy : enemies) {
            if (!enemy.isDead()) {
                enemy.applyDamage(1000);
                deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
            }
        }
        worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);

        // Level 25 should still have 250 enemies (capped)
        assertEquals("Level 25 should still have 250 enemies (capped)", 250, worldLevelManager.getTotalEnemiesThisWave());

        // Advance a few more levels to ensure it stays at 250
        for (int level = 25; level <= 30; level++) {
            deaths = new ArrayList<>();
            for (EnemyState enemy : enemies) {
                if (!enemy.isDead()) {
                    enemy.applyDamage(1000);
                    deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
                }
            }
            worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);
            assertEquals("Level " + worldLevelManager.getCurrentWorldLevel() + " should still have 250 enemies",
                    250, worldLevelManager.getTotalEnemiesThisWave());
        }
    }

    /**
     * Test: handleDeaths() - enemy stat scaling after cap
     * Coverage: WorldLevelManager.handleDeaths() - stat scaling branch
     * Strategy: White-box, boundary value testing (post-cap scaling)
     * Equivalence: Levels above cap (level 25+)
     * Branches: Stat scaling branch (after cap)
     * Why: Tests that enemy stats scale after reaching 250 cap
     */
    @Test
    public void testEnemyStatsScaleAfterCap() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);

        // Get base stats from first wave
        EnemyState firstWaveEnemy = enemies.get(0);
        int baseHealth = firstWaveEnemy.hp();
        int baseMoveSpeed = firstWaveEnemy.stats().moveSpeed();
        int baseDamageMult = firstWaveEnemy.stats().damageMult();

        // Advance to level 24 (hits cap)
        for (int level = 1; level < 24; level++) {
            List<CombatResolve.DeathEvent> deaths = new ArrayList<>();
            for (EnemyState enemy : enemies) {
                if (!enemy.isDead()) {
                    enemy.applyDamage(1000);
                    deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
                }
            }
            worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);
        }

        // Level 24 should be at cap
        assertEquals("Level 24 should be at cap", 250, worldLevelManager.getTotalEnemiesThisWave());

        // Get stats at level 24 (cap level)
        // Clear enemies to get fresh ones at level 24
        List<CombatResolve.DeathEvent> deaths = new ArrayList<>();
        for (EnemyState enemy : enemies) {
            if (!enemy.isDead()) {
                enemy.applyDamage(1000);
                deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
            }
        }
        worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);

        // Find a fresh enemy at level 25 (first level after cap)
        EnemyState level25Enemy = null;
        for (EnemyState enemy : enemies) {
            if (!enemy.isDead()) {
                level25Enemy = enemy;
                break;
            }
        }
        assertNotNull("Should have a fresh enemy at level 25", level25Enemy);

        int level25Health = level25Enemy.hp();
        int level25MoveSpeed = level25Enemy.stats().moveSpeed();
        int level25DamageMult = level25Enemy.stats().damageMult();

        // Advance to level 30 (scaleLevel = 30 - 24 = 6)
        for (int level = 25; level < 30; level++) {
            deaths = new ArrayList<>();
            for (EnemyState enemy : enemies) {
                if (!enemy.isDead()) {
                    enemy.applyDamage(1000);
                    deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
                }
            }
            worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);
        }

        // Find a fresh enemy at level 30
        EnemyState level30Enemy = null;
        for (EnemyState enemy : enemies) {
            if (!enemy.isDead()) {
                level30Enemy = enemy;
                break;
            }
        }
        assertNotNull("Should have a fresh enemy at level 30", level30Enemy);

        int level30Health = level30Enemy.hp();
        int level30MoveSpeed = level30Enemy.stats().moveSpeed();
        int level30DamageMult = level30Enemy.stats().damageMult();

        // Level 30 stats should be higher than level 25 stats
        assertTrue("Level 30 health should be higher than level 25", level30Health > level25Health);
        assertTrue("Level 30 move speed should be higher than level 25", level30MoveSpeed > level25MoveSpeed);
        assertTrue("Level 30 damage mult should be higher than level 25", level30DamageMult > level25DamageMult);

        // Verify enemy count is still 250
        assertEquals("Enemy count should still be 250 at level 30", 250, worldLevelManager.getTotalEnemiesThisWave());
    }

    /**
     * Test: handleDeaths() - match ending prevents advancement
     * Coverage: WorldLevelManager.handleDeaths() - match ending branch
     * Strategy: White-box, branch coverage
     * Equivalence: Match ending flag set
     * Branches: Match ending check branch (no advancement)
     * Why: Tests that world level doesn't advance when match is ending
     */
    @Test
    public void testWorldLevelDoesNotAdvanceWhenMatchEnding() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);
        int initialLevel = worldLevelManager.getCurrentWorldLevel();

        // Kill ALL enemies but mark match as ending
        List<CombatResolve.DeathEvent> allDeaths = new ArrayList<>();
        for (EnemyState enemy : enemies) {
            enemy.applyDamage(1000);
            allDeaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
        }

        // Process deaths with matchEndingSoon = true
        worldLevelManager.handleDeaths(allDeaths, enemies, players, this::nextEnemyId, true);

        // World level should NOT advance
        assertEquals("World level should not advance when match is ending", initialLevel, worldLevelManager.getCurrentWorldLevel());
    }

    /**
     * Test: handleDeaths() - multiple wave progressions
     * Coverage: WorldLevelManager.handleDeaths() - multiple advancement branches
     * Strategy: White-box, sequential state validation
     * Equivalence: Multiple wave completions
     * Branches: Wave advancement branch for each level
     * Why: Tests consistency across multiple wave progressions
     */
    @Test
    public void testMultipleWaveProgressions() {
        worldLevelManager.startFirstWave(enemies, players, this::nextEnemyId);

        // Progress through several waves
        for (int targetLevel = 2; targetLevel <= 5; targetLevel++) {
            // Clear current wave
            List<CombatResolve.DeathEvent> deaths = new ArrayList<>();
            for (EnemyState enemy : enemies) {
                if (!enemy.isDead()) {
                    enemy.applyDamage(1000);
                    deaths.add(new CombatResolve.DeathEvent(enemy.id(), "enemy"));
                }
            }
            worldLevelManager.handleDeaths(deaths, enemies, players, this::nextEnemyId, false);

            // Verify level advanced
            assertEquals("Should be at level " + targetLevel, targetLevel, worldLevelManager.getCurrentWorldLevel());

            // Verify enemy count formula: 20 + (level - 1) * 10
            int expectedCount = 20 + (targetLevel - 1) * 10;
            assertEquals("Level " + targetLevel + " should have " + expectedCount + " enemies",
                    expectedCount, worldLevelManager.getTotalEnemiesThisWave());
        }
    }

    /**
     * Helper method to generate enemy IDs
     */
    private int nextEnemyId() {
        return enemyIdCounter++;
    }
}