package survivor.combat;

import survivor.shared.AttackStyle;
import java.util.*;
import java.util.SplittableRandom;

/**
 * @author John Draa
 */

public final class AttackSystem {
    private AttackSystem() {}

    // tune these as needed
    private static final double BASE_AOE_APS       = 1.2;
    private static final double BASE_AOE_R_TILES   = 2.0;
    private static final double BASE_AOE_DAMAGE    = 7.0;

    private static final double BASE_CONE_APS      = 1.6;
    private static final double BASE_CONE_LEN_T    = 2.5;
    private static final double BASE_CONE_ARC_DEG  = 70.0;
    private static final double BASE_CONE_DAMAGE   = 12.0;

    // Orbit weapon constants
    private static final double BASE_ORBIT_RADIUS_TILES = 1.5;  // Orbit radius in tiles
    private static final double BASE_ORBIT_DAMAGE = 8.0;
    private static final double BASE_ORBIT_ROTATION_SPEED = 3.0;  // Radians per second
    private static final int ORBIT_HIT_COOLDOWN_MS = 300;  // Cooldown between hits on same enemy

    private static final SplittableRandom RNG = new SplittableRandom();

    public static List<Hit> resolveAuto(
            Collection<? extends PlayerView> players,
            List<? extends EnemyView> enemies,
            int tilePx
    ) {
        long now = System.currentTimeMillis();
        List<Hit> out = new ArrayList<>();

        for (var p : players)
        {
            if (p.isDead()) continue;

            var style = p.effectiveStyle();
            switch (style)
            {
                case AOE ->
                {
                    int iv = Formulas.intervalMs(p.stats(), BASE_AOE_APS);
                    if (now - p.lastAttackAt() < iv) continue;
                    p.setLastAttackAt(now);

                    double rPx = BASE_AOE_R_TILES * tilePx * Formulas.rangeFactor(p.stats());
                    int dmg = (int)Math.round(Formulas.damage(p.stats(), BASE_AOE_DAMAGE));
                    if (Formulas.crit(p.stats(), RNG)) dmg = (int)Math.round(dmg * 1.5);

                    for (var e : enemies)
                    {
                        if (e.isDead()) continue;
                        if (dist(p.pos(), e.pos()) <= rPx)
                        {
                            out.add(new Hit(p.id(), e.id(), dmg, true));
                        }
                    }
                }
                case CONE ->
                {
                    int iv = Formulas.intervalMs(p.stats(), BASE_CONE_APS);
                    if (now - p.lastAttackAt() < iv) continue;
                    p.setLastAttackAt(now);

                    Vec2 aim = p.aimDir(); // movement dir if moving, else last non-zero
                    double lenPx = BASE_CONE_LEN_T * tilePx * Formulas.rangeFactor(p.stats());
                    double halfArc = Math.toRadians(BASE_CONE_ARC_DEG)
                            * (0.9 + 0.005 * p.stats().range()) * 0.5;

                    int dmg = (int)Math.round(Formulas.damage(p.stats(), BASE_CONE_DAMAGE));
                    if (Formulas.crit(p.stats(), RNG)) dmg = (int)Math.round(dmg * 1.5);

                    for (var e : enemies)
                    {
                        if (e.isDead()) continue;
                        if (Cone.contains(p.pos(), aim, e.pos(), lenPx, halfArc))
                        {
                            out.add(new Hit(p.id(), e.id(), dmg, true));
                        }
                    }
                }
                case ORBIT ->
                {
                    // Update orbit angle based on rotation speed
                    // Rotation speed scales with attack speed stat
                    double rotationSpeed = BASE_ORBIT_ROTATION_SPEED * (0.8 + 0.01 * p.stats().attackSpeed());
                    double deltaTime = 1.0 / 20.0;  // Assuming 20 ticks per second
                    double newAngle = p.orbitAngle() + rotationSpeed * deltaTime;
                    // Keep angle in [0, 2π) range
                    if (newAngle >= 2 * Math.PI) newAngle -= 2 * Math.PI;
                    p.setOrbitAngle(newAngle);

                    // Calculate orb position
                    double orbitRadiusPx = BASE_ORBIT_RADIUS_TILES * tilePx * Formulas.rangeFactor(p.stats());
                    Vec2 orbPos = new Vec2(
                            p.pos().x() + Math.cos(newAngle) * orbitRadiusPx,
                            p.pos().y() + Math.sin(newAngle) * orbitRadiusPx
                    );

                    // Orb hitbox radius (small orb)
                    double orbRadiusPx = 8.0;

                    // Check collision with enemies
                    for (var e : enemies)
                    {
                        if (e.isDead()) continue;

                        // Check if enemy is in contact with orb
                        double distToOrb = dist(orbPos, e.pos());
                        double contactDist = orbRadiusPx + e.radiusPx();

                        if (distToOrb <= contactDist)
                        {
                            // Check cooldown for this specific enemy
                            long lastHit = p.getOrbitLastHitTime(e.id());
                            if (now - lastHit >= ORBIT_HIT_COOLDOWN_MS)
                            {
                                int dmg = (int)Math.round(Formulas.damage(p.stats(), BASE_ORBIT_DAMAGE));
                                if (Formulas.crit(p.stats(), RNG)) dmg = (int)Math.round(dmg * 1.5);

                                out.add(new Hit(p.id(), e.id(), dmg, true));
                                p.setOrbitLastHitTime(e.id(), now);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static double dist(Vec2 a, Vec2 b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }

    // Minimal views to decouple AttackSystem from your concrete PlayerState/EnemyState
    public interface PlayerView
    {
        int id();
        Vec2 pos();
        Stats stats();
        AttackStyle effectiveStyle();
        long lastAttackAt();
        void setLastAttackAt(long t);
        Vec2 aimDir();
        boolean isDead();

        // Orbit weapon methods (default implementations for backward compatibility)
        default double orbitAngle() { return 0.0; }
        default void setOrbitAngle(double angle) { /* no-op for non-orbit players */ }
        default long getOrbitLastHitTime(int enemyId) { return 0L; }
        default void setOrbitLastHitTime(int enemyId, long time) { /* no-op for non-orbit players */ }
    }
    public interface EnemyView
    {
        int id();
        Vec2 pos();
        Stats stats();
        boolean isDead();
        double radiusPx();  // Added for orbit collision detection
    }

    public record Hit(int attackerId, int targetId, int damage, boolean playerToEnemy) {}
}

