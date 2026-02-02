package survivor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import survivor.combat.Stats;
import survivor.combat.Vec2;
import survivor.model.PlayerState;
import survivor.model.EnemyState;
import survivor.model.EnemyType;
import survivor.runtime.CombatResolve;
import survivor.runtime.UpgradeType;
import survivor.shared.AttackStyle;
import survivor.combat.AttackSystem;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for upgrade event system.
 * 
 * Coverage Goals:
 * - PlayerState.addXp() - XP accumulation, level up, overflow handling
 * - PlayerState.applyUpgrade() - All upgrade types (DAMAGE_UP, ATKSPEED_UP, MAX_HP_UP, WEAPON_CONE, WEAPON_ORBIT)
 * - PlayerState.setMove() - Movement freezing during upgrade
 * - CombatResolve.apply() - Invincibility handling
 * 
 * Strategy: Unit testing with isolated PlayerState, boundary value testing for XP thresholds
 */
@RunWith(SpringRunner.class)
@ActiveProfiles({"test"})
public class UpgradeEventTest {

    private PlayerState player;
    private Stats baseStats;

    @Before
    public void setUp() {
        // Create base stats for a test player
        baseStats = new Stats(100, 50, 30, 20, 5, 10);
        Vec2 spawn = new Vec2(100.0, 100.0);
        player = new PlayerState(1, spawn, baseStats, AttackStyle.AOE, baseStats.health());
    }

    /**
     * Test: PlayerState.addXp() - level up threshold
     * Coverage: PlayerState.addXp() - level up branch
     * Strategy: White-box, boundary value (exact threshold)
     * Equivalence: XP exactly at threshold (150)
     * Branches: Level up threshold check branch, upgrade options generation branch
     * Why: Tests core level up mechanism and upgrade event triggering
     */
    @Test
    public void testUpgradeTriggeredOnXpThreshold() {
        // Initial state
        assertEquals("Player should start at level 1", 1, player.getLevel());
        assertEquals("Player should start with 0 XP", 0, player.getXp());
        assertEquals("Initial XP to next should be 150", 150, player.getXpToNext());
        assertFalse("Player should not be choosing upgrade initially", player.isChoosingUpgrade());
        assertFalse("Player should not be invincible initially", player.isInvincible());

        // Add XP that reaches threshold (150 XP needed for level 2)
        player.addXp(150);

        // Verify level up occurred
        assertEquals("Player should be at level 2", 2, player.getLevel());
        assertEquals("XP should be reset after level up", 0, player.getXp());
        assertEquals("XP to next should be updated for level 2", 200, player.getXpToNext());
        assertTrue("Player should be choosing upgrade", player.isChoosingUpgrade());
        assertTrue("Player should be invincible during upgrade", player.isInvincible());
        assertNotNull("Upgrade options should be generated", player.getCurrentUpgradeOptions());
        assertEquals("Should have 3 upgrade options", 3, player.getCurrentUpgradeOptions().size());
    }

    /**
     * Test: PlayerState.addXp() - overflow XP handling
     * Coverage: PlayerState.addXp() - overflow branch
     * Strategy: White-box, boundary value (exceeds threshold)
     * Equivalence: XP exceeds threshold (200 when 150 needed)
     * Branches: Overflow XP preservation branch
     * Why: Tests XP overflow handling when multiple kills happen simultaneously
     */
    @Test
    public void testUpgradeHandlesOverflowXp() {
        // Add XP that exceeds threshold
        player.addXp(200); // 150 needed, 50 overflow

        // Verify level up and overflow XP is preserved
        assertEquals("Player should be at level 2", 2, player.getLevel());
        assertEquals("Overflow XP should be preserved", 50, player.getXp());
        assertTrue("Player should be choosing upgrade", player.isChoosingUpgrade());
    }

    /**
     * Test: PlayerState.setMove() - movement freezing
     * Coverage: PlayerState.setMove() - frozen branch
     * Strategy: White-box, branch coverage
     * Equivalence: Player in upgrade choice state
     * Branches: Movement frozen check branch
     * Why: Tests movement freezing during upgrade selection
     */
    @Test
    public void testPlayerFrozenDuringUpgrade() {
        // Set initial movement
        player.setMove(1.0f, 0.0f, 1);
        assertEquals("Player should have movement input", 1.0f, player.moveX(), 0.001f);
        assertEquals("Player should have movement input", 0.0f, player.moveY(), 0.001f);

        // Trigger upgrade event
        player.addXp(150);

        // Verify movement is frozen
        assertTrue("Player should be choosing upgrade", player.isChoosingUpgrade());
        assertEquals("Movement X should be reset to 0", 0.0f, player.moveX(), 0.001f);
        assertEquals("Movement Y should be reset to 0", 0.0f, player.moveY(), 0.001f);

        // Try to set movement while choosing upgrade - should be ignored
        player.setMove(1.0f, 1.0f, 2);
        assertEquals("Movement should remain frozen", 0.0f, player.moveX(), 0.001f);
        assertEquals("Movement should remain frozen", 0.0f, player.moveY(), 0.001f);
    }

    /**
     * Test: CombatResolve.apply() - invincibility handling
     * Coverage: CombatResolve.apply() - invincibility check branch
     * Strategy: White-box, branch coverage
     * Equivalence: Player in invincible state
     * Branches: Invincibility check branch (skip damage)
     * Why: Tests invincibility protection during upgrade selection
     */
    @Test
    public void testPlayerInvincibleDuringUpgrade() {
        int initialHp = player.hp();
        assertEquals("Initial HP should be 100", 100, initialHp);

        // Trigger upgrade event
        player.addXp(150);
        assertTrue("Player should be invincible", player.isInvincible());

        // Test invincibility through CombatResolve
        Map<Integer, PlayerState> players = new HashMap<>();
        players.put(player.id(), player);
        List<EnemyState> enemies = new ArrayList<>();

        // Create a mock enemy hit targeting the player
        List<AttackSystem.Hit> enemyHits = new ArrayList<>();
        enemyHits.add(new AttackSystem.Hit(100, player.id(), 50, false));

        // Apply combat - player should not take damage due to invincibility
        CombatResolve.MatchEvents events = CombatResolve.apply(
                new ArrayList<>(), // no player hits
                enemyHits,
                players,
                enemies
        );

        // Verify no damage was applied
        assertEquals("Player HP should remain unchanged due to invincibility",
                initialHp, player.hp());
        assertTrue("No damage events should be created for invincible player",
                events.damages().isEmpty());
    }

    /**
     * Test: PlayerState.applyUpgrade() - DAMAGE_UP
     * Coverage: PlayerState.applyUpgrade() - damage upgrade branch
     * Strategy: White-box, branch coverage
     * Equivalence: DAMAGE_UP upgrade type
     * Branches: Damage multiplier update branch
     * Why: Tests damage upgrade application and compounding
     */
    @Test
    public void testDamageUpgradeApplied() {
        float initialMultiplier = player.damageMultiplier();
        assertEquals("Initial damage multiplier should be 1.0", 1.0f, initialMultiplier, 0.001f);

        // Trigger upgrade and apply damage upgrade
        player.addXp(150);
        player.applyUpgrade(UpgradeType.DAMAGE_UP);

        // Verify upgrade applied
        assertEquals("Damage multiplier should increase by 1%", 
                     1.01f, player.damageMultiplier(), 0.001f);
        assertFalse("Player should no longer be choosing upgrade", player.isChoosingUpgrade());
        assertFalse("Player should no longer be invincible", player.isInvincible());

        // Apply multiple damage upgrades
        player.addXp(200);
        player.applyUpgrade(UpgradeType.DAMAGE_UP);
        assertEquals("Damage multiplier should compound", 
                     1.01f * 1.01f, player.damageMultiplier(), 0.001f);
    }

    /**
     * Test: PlayerState.applyUpgrade() - ATKSPEED_UP
     * Coverage: PlayerState.applyUpgrade() - attack speed upgrade branch
     * Strategy: White-box, branch coverage
     * Equivalence: ATKSPEED_UP upgrade type
     * Branches: Move speed multiplier update branch
     * Why: Tests attack speed (movement speed) upgrade application
     */
    @Test
    public void testAttackSpeedUpgradeApplied() {
        float initialMultiplier = player.moveSpeedMultiplier();
        assertEquals("Initial move speed multiplier should be 1.0", 1.0f, initialMultiplier, 0.001f);

        // Trigger upgrade and apply attack speed upgrade
        player.addXp(150);
        player.applyUpgrade(UpgradeType.ATKSPEED_UP);

        // Verify upgrade applied
        assertEquals("Move speed multiplier should increase by 0.1", 
                     1.1f, player.moveSpeedMultiplier(), 0.001f);
        assertFalse("Player should no longer be choosing upgrade", player.isChoosingUpgrade());
        assertFalse("Player should no longer be invincible", player.isInvincible());

        // Apply multiple upgrades
        player.addXp(200);
        player.applyUpgrade(UpgradeType.ATKSPEED_UP);
        assertEquals("Move speed multiplier should stack", 
                     1.2f, player.moveSpeedMultiplier(), 0.001f);
    }

    /**
     * Test: PlayerState.applyUpgrade() - MAX_HP_UP
     * Coverage: PlayerState.applyUpgrade() - max HP upgrade branch
     * Strategy: White-box, branch coverage, boundary (damaged player)
     * Equivalence: MAX_HP_UP upgrade type, both full HP and damaged HP cases
     * Branches: HP increase branch, HP cap check branch
     * Why: Tests max HP upgrade with HP cap logic
     */
    @Test
    public void testMaxHpUpgradeApplied() {
        int initialHp = player.hp();
        int initialMaxHp = 100; // from base stats

        // Trigger upgrade and apply max HP upgrade
        player.addXp(150);
        player.applyUpgrade(UpgradeType.MAX_HP_UP);

        // Verify upgrade applied - max HP increased by 10, current HP increased by 10
        assertEquals("Max HP should increase by 10", 
                     initialMaxHp + 10, player.hp());
        assertFalse("Player should no longer be choosing upgrade", player.isChoosingUpgrade());
        assertFalse("Player should no longer be invincible", player.isInvincible());

        // Test with damaged player
        player.applyDamage(20); // HP now at 90
        player.addXp(200);
        player.applyUpgrade(UpgradeType.MAX_HP_UP);
        // HP should be min(maxHp, hp + 10) = min(120, 90 + 10) = 100
        assertEquals("HP should increase but not exceed new max", 
                     100, player.hp());
    }

    /**
     * Test: PlayerState.applyUpgrade() - WEAPON_CONE
     * Coverage: PlayerState.applyUpgrade() - weapon upgrade branch (CONE)
     * Strategy: White-box, branch coverage
     * Equivalence: WEAPON_CONE upgrade type
     * Branches: Weapon style change branch
     * Why: Tests weapon upgrade application
     */
    @Test
    public void testWeaponConeUpgradeApplied() {
        assertEquals("Player should start with AOE weapon", 
                     AttackStyle.AOE, player.effectiveStyle());

        // Trigger upgrade and apply CONE weapon upgrade
        player.addXp(150);
        player.applyUpgrade(UpgradeType.WEAPON_CONE);

        // Verify upgrade applied
        assertEquals("Player should have CONE weapon", 
                     AttackStyle.CONE, player.effectiveStyle());
        assertFalse("Player should no longer be choosing upgrade", player.isChoosingUpgrade());
        assertFalse("Player should no longer be invincible", player.isInvincible());
    }

    /**
     * Test: PlayerState.applyUpgrade() - WEAPON_ORBIT
     * Coverage: PlayerState.applyUpgrade() - weapon upgrade branch (ORBIT)
     * Strategy: White-box, branch coverage
     * Equivalence: WEAPON_ORBIT upgrade type
     * Branches: Weapon style change branch
     * Why: Tests weapon upgrade application
     */
    @Test
    public void testWeaponOrbitUpgradeApplied() {
        assertEquals("Player should start with AOE weapon", 
                     AttackStyle.AOE, player.effectiveStyle());

        // Trigger upgrade and apply ORBIT weapon upgrade
        player.addXp(150);
        player.applyUpgrade(UpgradeType.WEAPON_ORBIT);

        // Verify upgrade applied
        assertEquals("Player should have ORBIT weapon", 
                     AttackStyle.ORBIT, player.effectiveStyle());
        assertFalse("Player should no longer be choosing upgrade", player.isChoosingUpgrade());
        assertFalse("Player should no longer be invincible", player.isInvincible());
    }

    /**
     * Test: PlayerState.setMove() - movement unfreezing after upgrade
     * Coverage: PlayerState.setMove() - normal movement branch (after upgrade)
     * Strategy: White-box, branch coverage
     * Equivalence: Player after upgrade applied
     * Branches: Movement unfrozen branch
     * Why: Tests movement restoration after upgrade selection
     */
    @Test
    public void testPlayerCanMoveAfterUpgrade() {
        // Trigger upgrade event
        player.addXp(150);
        assertTrue("Player should be choosing upgrade", player.isChoosingUpgrade());
        assertEquals("Movement should be frozen", 0.0f, player.moveX(), 0.001f);

        // Apply upgrade
        player.applyUpgrade(UpgradeType.DAMAGE_UP);
        assertFalse("Player should no longer be choosing upgrade", player.isChoosingUpgrade());

        // Verify movement can be set again
        player.setMove(1.0f, 0.5f, 1);
        assertEquals("Player should be able to move", 1.0f, player.moveX(), 0.001f);
        assertEquals("Player should be able to move", 0.5f, player.moveY(), 0.001f);
    }

    /**
     * Test: CombatResolve.apply() - damage after upgrade
     * Coverage: CombatResolve.apply() - normal damage branch (after invincibility)
     * Strategy: White-box, branch coverage
     * Equivalence: Player after upgrade applied
     * Branches: Normal damage application branch
     * Why: Tests damage restoration after upgrade selection
     */
    @Test
    public void testPlayerCanTakeDamageAfterUpgrade() {
        int initialHp = player.hp();

        // Trigger upgrade event
        player.addXp(150);
        assertTrue("Player should be invincible", player.isInvincible());

        // Apply upgrade
        player.applyUpgrade(UpgradeType.DAMAGE_UP);
        assertFalse("Player should no longer be invincible", player.isInvincible());

        // Test damage through CombatResolve
        Map<Integer, PlayerState> players = new HashMap<>();
        players.put(player.id(), player);
        List<EnemyState> enemies = new ArrayList<>();
        List<AttackSystem.Hit> enemyHits = new ArrayList<>();
        enemyHits.add(new AttackSystem.Hit(100, player.id(), 30, false));

        CombatResolve.MatchEvents events = CombatResolve.apply(
                new ArrayList<>(),
                enemyHits,
                players,
                enemies
        );

        // Verify damage was applied
        assertEquals("Player HP should decrease", 
                     initialHp - 30, player.hp());
        assertEquals("Should have one damage event", 1, events.damages().size());
    }

    /**
     * Test: Multiple level ups in sequence
     * Coverage: PlayerState.addXp() - multiple level up branches
     * Strategy: White-box, sequential state changes
     * Equivalence: Multiple XP thresholds
     * Branches: Level 2 threshold, level 3 threshold
     * Why: Tests upgrade system handles sequential level ups correctly
     */
    @Test
    public void testMultipleLevelUps() {
        // Level up to 2
        player.addXp(150);
        assertEquals("Player should be at level 2", 2, player.getLevel());
        player.applyUpgrade(UpgradeType.DAMAGE_UP);

        // Level up to 3
        player.addXp(200);
        assertEquals("Player should be at level 3", 3, player.getLevel());
        assertEquals("XP to next should be 250 for level 3", 250, player.getXpToNext());
        player.applyUpgrade(UpgradeType.ATKSPEED_UP);

        // Verify both upgrades applied
        assertEquals("Damage multiplier should be 1.01", 1.01f, player.damageMultiplier(), 0.001f);
        assertEquals("Move speed multiplier should be 1.1", 1.1f, player.moveSpeedMultiplier(), 0.001f);
    }
}
