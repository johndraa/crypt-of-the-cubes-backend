package survivor.model;

import survivor.combat.*;

/**
 * @author John Draa
 */

public class EnemyState implements
        AttackSystem.EnemyView,
        EnemyAiSystem.EnemyActor {

    private final int id;
    private Vec2 pos;
    private Vec2 vel = new Vec2(0,0);
    private final Stats stats;
    private long lastAttackAt;
    private long lastContactAt;            // <- contact cadence
    private boolean active;
    private int hp;
    private boolean dead;

    private final EnemyType type;
    private final double radiusPx;

    public EnemyState(int id, Vec2 spawn, Stats stats, int hp, EnemyType type, double radiusPx) {
        this.id = id; this.pos = spawn; this.stats = stats; this.hp = hp;
        this.type = type; this.radiusPx = radiusPx;
    }

    // movement/steering
    @Override public void seek(Vec2 targetPos) {
        Vec2 dir = new Vec2(targetPos.x()-pos.x(), targetPos.y()-pos.y()).norm();
        // Slightly higher base factor so enemies can traverse the larger 2000x2000 map,
        // but still slower than players on average.
        double speed = Math.max(20, stats.moveSpeed()) * 0.8; // was 0.6
        this.vel = new Vec2(dir.x()*speed, dir.y()*speed);
    }

    public void applyDamage(int dmg){ if (dead) return; hp -= dmg; if (hp<=0){ hp=0; dead=true; } }
    public int hp(){ return hp; }

    // ----- EnemyView / EnemyActor / EnemyGate implementations -----
    @Override public int id(){ return id; }
    @Override public Vec2 pos(){ return pos; }
    public void setPos(Vec2 p){ this.pos = p; }
    public Vec2 vel(){ return vel; }
    public void setVel(Vec2 v){ this.vel = v; }

    @Override public Stats stats(){ return stats; }
    @Override public boolean isDead(){ return dead; }

    @Override public long lastAttackAt(){ return lastAttackAt; }
    @Override public void setLastAttackAt(long t){ lastAttackAt = t; }

    @Override public long lastContactAt(){ return lastContactAt; }
    @Override public void setLastContactAt(long t){ lastContactAt = t; }

    @Override public boolean active(){ return active; }
    @Override public void setActive(boolean v){ active = v; }

    @Override public EnemyType type(){ return type; }
    @Override public double radiusPx(){ return radiusPx; }
}
