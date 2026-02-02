package survivor.runtime;
import survivor.combat.Vec2;
import survivor.model.PlayerState;
import survivor.model.EnemyState;
import survivor.shared.AttackStyle;

import java.util.*;

/**
 * @author John Draa
 */

public final class SnapshotBuilder
{
    private SnapshotBuilder(){}

    /** Build per-player visibility-filtered snapshots (R_light already in px) */
    public static Map<Integer, PlayerSnapshot> build(Collection<PlayerState> players,
                                                     List<EnemyState> enemies,
                                                     double rLightPx)
    {
        Map<Integer, PlayerSnapshot> out = new HashMap<>();
        for (var me : players){
            List<EntityView> visible = new ArrayList<>();

            // teammates always visible (optional rule)
            for (var p : players)
            {
                visible.add(new EntityView(
                    "player", 
                    p.id(), 
                    p.pos().x(), 
                    p.pos().y(), 
                    p.hp(),
                    null  // No enemy type for players
                    // Commented out attack timing fields for now:
                    // p.lastAttackAt(),
                    // p.stats().attackSpeed(),
                    // p.effectiveStyle()
                ));
            }

            // enemies within R_light
            for (var e : enemies)
            {
                if (!e.isDead() && dist(me.pos(), e.pos()) <= rLightPx)
                    visible.add(new EntityView(
                        "enemy", 
                        e.id(), 
                        e.pos().x(), 
                        e.pos().y(), 
                        e.hp(),
                        e.type().name()  // Include enemy type (BUMPER, SWIPER, etc.)
                    ));
            }

            out.put(me.id(), new PlayerSnapshot(me.id(), visible));
        }
        return out;
    }

    private static double dist(Vec2 a, Vec2 b){ return Math.hypot(a.x()-b.x(), a.y()-b.y()); }

    // DTOs sent to clients (serialize via Jackson/Gson over STOMP)
    public record PlayerSnapshot(int accountId, List<EntityView> entities) {}
    
    /**
     * Entity view for frontend rendering.
     * Attack timing fields commented out for now.
     */
    public record EntityView(
        String type,          // "player" or "enemy"
        int id, 
        double x, 
        double y, 
        int hp,
        String enemyType      // Enemy type (BUMPER, SWIPER, etc.) - null for players
        // Attack timing fields commented out for now:
        // long lastAttackAt,      // timestamp of last attack (milliseconds since epoch)
        // int attackSpeed,         // attack speed stat (10-100 range)
        // AttackStyle attackStyle  // AOE, CONE, or ORBIT (null for enemies)
    ) {}
}

