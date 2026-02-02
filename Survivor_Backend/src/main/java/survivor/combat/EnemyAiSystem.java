package survivor.combat;

import java.util.*;
import java.util.SplittableRandom;

import survivor.model.EnemyType; // enum: BUMPER, SWIPER

/**
 * @author John Draa
 */
public final class EnemyAiSystem {
    private EnemyAiSystem() {}

    private static final SplittableRandom RNG = new SplittableRandom();

    /** Wake/sleep by distance to the nearest player */
    public static void gateActivity(
            List<? extends EnemyGate> enemies,
            Collection<? extends PlayerPos> players,
            double rWakePx, double rSleepPx
    )
    {
        for (var e : enemies)
        {
            double d = players.stream().mapToDouble(p -> dist(e.pos(), p.pos())).min().orElse(Double.MAX_VALUE);
            if (!e.active() && d <= rWakePx) e.setActive(true);
            if ( e.active() && d >= rSleepPx) e.setActive(false);
        }
    }

    /**
     * Enemy AI step.
     * Returns enemy→player hits using shared cone logic (SWIPER) and contact overlap (BUMPER).
     * Fairness: only attack if target is within player's visibility (≤ fairnessLightPx).
     */
    public static List<AttackSystem.Hit> tick(
            List<? extends EnemyActor> enemies,
            Collection<? extends PlayerActor> players,
            double fairnessLightPx,
            int tilePx
    )
    {
        long now = System.currentTimeMillis();
        List<AttackSystem.Hit> out = new ArrayList<>();

        for (var e : enemies)
        {
            if (!e.active() || e.isDead()) continue;

            var target = nearest(players, e.pos());
            // steer toward target; replace with richer physics if desired
            e.seek(target.pos());

            // fairness guard: don't start/commit attacks from out of sight
            if (dist(e.pos(), target.pos()) > fairnessLightPx + 8) continue;

            switch (e.type())
            {
                case BUMPER ->
                {
                    // simple contact DPS every ~300 ms (scaled by attackSpeed)
                    int intervalMs = Math.max(200, 600 - e.stats().attackSpeed() * 5);
                    if (now - e.lastContactAt() >= intervalMs &&
                            circlesOverlap(e.pos(), e.radiusPx(), target.pos(), target.radiusPx()))
                    {

                        int dmg = (int)Math.round(Formulas.damage(e.stats(), 6.0));
                        if (RNG.nextDouble() < (e.stats().critChance() / 100.0)) dmg = (int)Math.round(dmg * 1.5);

                        out.add(new AttackSystem.Hit(e.id(), target.id(), dmg, false));
                        e.setLastContactAt(now);
                    }
                }
                case SWIPER ->
                {
                    // cone swipe (shared math with players)
                    int iv = Formulas.intervalMs(e.stats(), 1.2);
                    if (now - e.lastAttackAt() < iv) break;
                    e.setLastAttackAt(now);

                    var aim     = dir(e.pos(), target.pos());
                    double len  = 2.0 * tilePx * Formulas.rangeFactor(e.stats());
                    double half = Math.toRadians(60.0) * (0.9 + 0.005 * e.stats().range()) * 0.5;

                    int dmg = (int)Math.round(Formulas.damage(e.stats(), 8.0));
                    if (RNG.nextDouble() < (e.stats().critChance() / 100.0)) dmg = (int)Math.round(dmg * 1.5);

                    if (Cone.contains(e.pos(), aim, target.pos(), len, half))
                    {
                        out.add(new AttackSystem.Hit(e.id(), target.id(), dmg, false));
                    }
                }
            }
        }
        return out;
    }

    // ——— Helper minimal views to decouple from your models ———
    public interface PlayerPos { Vec2 pos(); }

    public interface EnemyGate
    {
        Vec2 pos();
        boolean active();
        void setActive(boolean v);
    }

    public interface PlayerActor extends PlayerPos
    {
        int id();
        double radiusPx();
    }

    public interface EnemyActor extends EnemyGate
    {
        int id();
        Stats stats();
        boolean isDead();

        long lastAttackAt();
        void setLastAttackAt(long t);

        long lastContactAt();
        void setLastContactAt(long t);

        /** Basic chase step; implement with your physics/collision */
        void seek(Vec2 targetPos);

        EnemyType type();
        double radiusPx();
    }

    // ——— internals ———
    private static PlayerActor nearest(Collection<? extends PlayerActor> players, Vec2 pos)
    {
        return players.stream()
                .min(Comparator.comparingDouble(p -> dist(p.pos(), pos)))
                .orElseThrow();
    }

    private static boolean circlesOverlap(Vec2 a, double ra, Vec2 b, double rb)
    {
        double dx = a.x() - b.x(), dy = a.y() - b.y();
        double r  = ra + rb;
        return dx*dx + dy*dy <= r*r;
    }

    private static Vec2 dir(Vec2 a, Vec2 b)
    {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), L = Math.hypot(dx, dy);
        return L == 0.0 ? new Vec2(1, 0) : new Vec2(dx / L, dy / L);
    }

    private static double dist(Vec2 a, Vec2 b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
}
