package survivor.runtime;

import survivor.combat.Stats;
import survivor.combat.Vec2;
import survivor.model.EnemyState;
import survivor.model.EnemyType;
import survivor.model.PlayerState;

import java.util.Collection;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.IntSupplier;

/**
 * World-level / wave manager (per MatchRuntime).
 *
 * - Tracks currentWorldLevel, remainingEnemiesInWave, totalEnemiesThisWave.
 * - Spawns full waves at the start of each world level.
 * - Increases enemy count per wave up to a hard cap (250).
 * - After cap, keeps count fixed and applies stat scaling instead.
 *
 * Integrates with:
 * - MatchRuntime.start()      -> startFirstWave(...)
 * - MatchRuntime.tick()       -> handleDeaths(...), may advance to next wave
 *
 * Does NOT touch:
 * - match end / DB persistence / XP pipelines.
 */
public class WorldLevelManager {

    private static final int MAP_W = 2000;
    private static final int MAP_H = 2000;

    /** Max enemies spawned per wave. */
    private static final int WAVE_SPAWN_CAP = 250;

    /** Optional safety buffer around players (px). */
    private static final double SAFE_PLAYER_RADIUS = 100.0;

    private final SplittableRandom rng = new SplittableRandom();

    private int currentWorldLevel = 1;
    private int remainingEnemiesInWave = 0;
    private int totalEnemiesThisWave = 0;

    /** First world level at which spawn count hit the cap; null until then. */
    private Integer levelWhereCapReached = null;

    private boolean active = false;

    // Base stats copied from old MatchRuntime.spawnInitial()
    private static final Stats BUMPER_BASE = new Stats(
            /*health*/1, /*moveSpeed*/30, /*attackSpeed*/10,
            /*damageMult*/3, /*critChance*/2, /*range*/5
    );
    private static final Stats SWIPER_BASE = new Stats(
            /*health*/1, /*moveSpeed*/28, /*attackSpeed*/15,
            /*damageMult*/5, /*critChance*/3, /*range*/7
    );
    private static final double ENEMY_RADIUS = 12.0;

    /**
     * Initialize wave progression at world level 1 and spawn the first wave.
     * Called once from MatchRuntime.start().
     */
    public void startFirstWave(List<EnemyState> enemies,
                               Collection<PlayerState> players,
                               IntSupplier idGenerator) {
        currentWorldLevel = 1;
        active = true;
        spawnWave(enemies, players, idGenerator);
    }

    /**
     * Called once per tick from MatchRuntime.tick(), after CombatResolve.apply().
     *
     * @param deaths           combat death events for this tick
     * @param enemies          shared enemy list for the match
     * @param players          current players in the match
     * @param idGenerator      generator for unique enemy ids
     * @param matchEndingSoon  true if this tick has determined the match should end
     *                         (in which case we do NOT spawn a new wave)
     */
    public void handleDeaths(List<CombatResolve.DeathEvent> deaths,
                             List<EnemyState> enemies,
                             Collection<PlayerState> players,
                             IntSupplier idGenerator,
                             boolean matchEndingSoon) {
        if (!active) return;

        long enemyDeaths = deaths.stream()
                .filter(d -> "enemy".equals(d.type()))
                .count();
        if (enemyDeaths == 0) return;

        if (remainingEnemiesInWave > 0) {
            remainingEnemiesInWave -= enemyDeaths;
            if (remainingEnemiesInWave < 0) remainingEnemiesInWave = 0;
        }

        // When current wave is cleared, immediately start the next world level,
        // unless the match is about to end.
        if (remainingEnemiesInWave == 0 && !matchEndingSoon) {
            currentWorldLevel++;
            spawnWave(enemies, players, idGenerator);
        }
    }

    // -------------------- Internals --------------------

    /**
     * Compute and spawn the full wave for currentWorldLevel.
     */
    private void spawnWave(List<EnemyState> enemies,
                           Collection<PlayerState> players,
                           IntSupplier idGenerator) {

        int spawnCount = computeSpawnCount(currentWorldLevel);
        totalEnemiesThisWave = spawnCount;
        remainingEnemiesInWave = spawnCount;

        // Remember the first level at which we hit the cap,
        // for use in the scaling functions.
        if (spawnCount >= WAVE_SPAWN_CAP && levelWhereCapReached == null) {
            levelWhereCapReached = currentWorldLevel;
        }

        // Simple composition: 75% bumpers, 25% swipers (at least 1 swiper if possible)
        int numSwipers = Math.max(1, spawnCount / 4);
        int numBumpers = spawnCount - numSwipers;

        // Spawn bumpers
        for (int i = 0; i < numBumpers; i++) {
            Vec2 pos = randomSpawnPosition(players);
            Stats stats = scaledStats(BUMPER_BASE);
            int hp = scaledHealth(BUMPER_BASE);

            enemies.add(new EnemyState(
                    idGenerator.getAsInt(),
                    pos,
                    stats,
                    hp,
                    EnemyType.BUMPER,
                    ENEMY_RADIUS
            ));
        }

        // Spawn swipers
        for (int i = 0; i < numSwipers; i++) {
            Vec2 pos = randomSpawnPosition(players);
            Stats stats = scaledStats(SWIPER_BASE);
            int hp = scaledHealth(SWIPER_BASE);

            enemies.add(new EnemyState(
                    idGenerator.getAsInt(),
                    pos,
                    stats,
                    hp,
                    EnemyType.SWIPER,
                    ENEMY_RADIUS
            ));
        }
    }

    /**
     * Spawn count formula:
     *   wave N spawn = min(20 + (N - 1) * 10, WAVE_SPAWN_CAP)
     */
    private int computeSpawnCount(int wave) {
        int base = 20 + (wave - 1) * 10;
        return Math.min(base, WAVE_SPAWN_CAP);
    }

    private int scaledHealth(Stats base) {
        // Keep base health at its original value (e.g. 1) before the spawn cap.
        // After the cap is reached, apply the global HP multiplier so that
        // higher world levels (e.g. 30) have strictly higher HP than earlier
        // post-cap levels (e.g. 25).
        double hpMult = computeHpMultiplier();
        return (int) Math.max(1, Math.round(base.health() * hpMult));
    }

    /**
     * Build a scaled Stats record applying HP, damage, and move speed scaling.
     * Other fields (attackSpeed, critChance, range) are left as-is for now.
     */
    private Stats scaledStats(Stats base) {
        double hpMult = computeHpMultiplier();
        double dmgMult = computeDamageMultiplier();
        double speedMult = computeSpeedMultiplier();

        int health      = (int) Math.round(base.health()      * hpMult);
        int moveSpeed   = (int) Math.round(base.moveSpeed()   * speedMult);
        int attackSpeed = base.attackSpeed();
        int damageMult  = (int) Math.round(base.damageMult()  * dmgMult);
        int critChance  = base.critChance();
        int range       = base.range();

        return new Stats(health, moveSpeed, attackSpeed, damageMult, critChance, range);
    }

    // -------- global scaling curve (after spawn cap is reached) --------

    private int computeScaleLevel() {
        if (levelWhereCapReached == null) return 0;
        return Math.max(0, currentWorldLevel - levelWhereCapReached);
    }

    private double computeHpMultiplier() {
        int scaleLevel = computeScaleLevel();
        return 1.0 + 0.10 * scaleLevel;
    }

    private double computeDamageMultiplier() {
        int scaleLevel = computeScaleLevel();
        return 1.0 + 0.08 * scaleLevel;
    }

    private double computeSpeedMultiplier() {
        int scaleLevel = computeScaleLevel();
        return 1.0 + 0.05 * scaleLevel;
    }

    // -------- spawn positioning (respects 2000x2000 map + optional safe radius) --------

    private Vec2 randomSpawnPosition(Collection<PlayerState> players) {
        final int maxAttempts = 10;

        for (int i = 0; i < maxAttempts; i++) {
            double x = rng.nextDouble(0, MAP_W);
            double y = rng.nextDouble(0, MAP_H);
            Vec2 candidate = new Vec2(x, y);

            if (isSafeFromPlayers(candidate, players)) {
                return candidate;
            }
        }

        // Fallback: just return a random point if we couldn't find a safe one.
        double x = rng.nextDouble(0, MAP_W);
        double y = rng.nextDouble(0, MAP_H);
        return new Vec2(x, y);
    }

    private boolean isSafeFromPlayers(Vec2 pos, Collection<PlayerState> players) {
        double r2 = SAFE_PLAYER_RADIUS * SAFE_PLAYER_RADIUS;

        for (PlayerState p : players) {
            if (p == null) continue;
            double dx = pos.x() - p.pos().x();
            double dy = pos.y() - p.pos().y();
            if (dx * dx + dy * dy < r2) {
                return false;
            }
        }
        return true;
    }

    // -------- read-only diagnostics --------

    public int getCurrentWorldLevel() {
        return currentWorldLevel;
    }

    public int getRemainingEnemiesInWave() {
        return remainingEnemiesInWave;
    }

    public int getTotalEnemiesThisWave() {
        return totalEnemiesThisWave;
    }
}


