package survivor.runtime;

import survivor.combat.Vec2;
import survivor.model.PlayerState;
import survivor.model.EnemyState;
import java.util.Collection;

/**
 * @author John Draa
 */

public final class Physics
{
    private Physics(){}

    // Player speed cap in px/s (tuned for 2000x2000 world)
    private static final double MAX_SPEED = 190.0; // was 160.0
    private static final int TILE_PX = 24;
    private static final int MAP_W = 2000; // static map bounds (tune)
    private static final int MAP_H = 2000;

    /** Integrate players: simple Euler, clamp speed, keep inside bounds */
    public static void integrate(Collection<PlayerState> players, double dtSeconds)
    {
        for (var p : players)
        {
            Vec2 inp = new Vec2(p.moveX(), p.moveY());
            Vec2 dir = inp.len() > 0 ? inp.norm() : new Vec2(0,0);

            // Apply per-player moveSpeedMultiplier
            double baseSpeed = 60 + p.stats().moveSpeed() * 3.0;
            double speed = Math.min(MAX_SPEED, baseSpeed * p.moveSpeedMultiplier());

            Vec2 vel = new Vec2(dir.x()*speed, dir.y()*speed);
            p.setVel(vel);
            Vec2 next = new Vec2(p.pos().x() + vel.x()*dtSeconds, p.pos().y() + vel.y()*dtSeconds);
            p.setPos(clampToBounds(next));
        }
    }

    /** Integrate enemies: use existing vel set by AI seek(), clamp, bounds */
    public static void integrateEnemies(Collection<EnemyState> enemies, double dtSeconds)
    {
        for (var e : enemies)
        {
            Vec2 v = e.vel();
            double sp = Math.hypot(v.x(), v.y());
            if (sp > MAX_SPEED) v = new Vec2(v.x()*MAX_SPEED/sp, v.y()*MAX_SPEED/sp);
            Vec2 next = new Vec2(e.pos().x()+v.x()*dtSeconds, e.pos().y()+v.y()*dtSeconds);
            e.setPos(clampToBounds(next));
        }
    }

    private static Vec2 clampToBounds(Vec2 p)
    {
        double x = Math.max(0, Math.min(MAP_W-1, p.x()));
        double y = Math.max(0, Math.min(MAP_H-1, p.y()));
        return new Vec2(x,y);
    }
}
