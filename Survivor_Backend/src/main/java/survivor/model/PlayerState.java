package survivor.model;

import survivor.combat.*;
import survivor.shared.AttackStyle; // or wherever your enum lives
import survivor.runtime.UpgradeGenerator;
import survivor.runtime.UpgradeType;

import java.util.*;

public class PlayerState implements
        AttackSystem.PlayerView,
        EnemyAiSystem.PlayerActor
{

    private final int id;
    private Vec2 pos;
    private Vec2 vel = new Vec2(0,0);
    private Vec2 lastDir = new Vec2(1,0);

    private float moveX, moveY;
    private long lastSeq;

    private final Stats stats;
    private AttackStyle style;
    private long lastAttackAt;
    private int hp;
    private boolean dead;

    // Match progress tracking
    private int score = 0;
    private int coinsEarned = 0;

    // ---- Level / XP / Upgrades ----
    private int xp = 0;
    private int level = 1;
    private int xpToNext = 100 + level * 50;

    private boolean choosingUpgrade = false;   // freeze movement inputs when true
    private boolean invincible = false;        // ignore damage when true

    private float damageMultiplier = 1.0f;
    private float moveSpeedMultiplier = 1.0f;

    private int maxHp;

    // Current 3 upgrade choices when level-up happens
    private List<UpgradeType> currentUpgradeOptions = List.of();

    // ---- Orbit weapon tracking ----
    private double orbitAngle = 0.0;  // Current angle of the orbiting orb (in radians)
    private final Map<Integer, Long> orbitLastHitTime = new HashMap<>();  // Per-enemy cooldown tracking

    //simple circle hitbox for the player
    private final double radiusPx = 12.0;

    public PlayerState(int id, Vec2 spawn, Stats stats, AttackStyle style, int hp)
    {
        this.id = id; this.pos = spawn; this.stats = stats; this.style = style; this.hp = hp;
        this.maxHp = hp;
    }

    public void setMove(float mx, float my, long seq)
    {
        // While choosing an upgrade, ignore movement inputs entirely
        if (choosingUpgrade) {
            // #region agent log
            try (var fw = new java.io.FileWriter("C:\\Users\\jh_dr\\OneDrive\\Desktop\\School\\Git\\3_rasel_6\\Backend\\Survivor_Backend\\.cursor\\debug.log", true);
                 var bw = new java.io.BufferedWriter(fw)) {
                String payload = "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"PlayerState.java:setMove\",\"message\":\"movement_ignored_while_choosing_upgrade\",\"data\":{\"id\":" + id + ",\"seq\":" + seq + "},\"timestamp\":" + System.currentTimeMillis() + "}";
                bw.write(payload);
                bw.newLine();
                System.out.println(payload);
            } catch (Exception ignored) {}
            // #endregion
            return;
        }

        if (seq < lastSeq) return;
        lastSeq = seq; moveX = mx; moveY = my;
        if (Math.abs(mx) > 0.0001 || Math.abs(my) > 0.0001)
        {
            lastDir = new Vec2(mx, my).norm();
        }
    }

    // movement helpers used by Physics
    public float moveX(){ return moveX; }
    public float moveY(){ return moveY; }
    public void setPos(Vec2 p){ this.pos = p; }
    public void setVel(Vec2 v){ this.vel = v; }
    public Vec2 vel(){ return vel; }
    public Vec2 lastDir(){ return lastDir; }

    public void applyDamage(int dmg){ if (dead) return; hp -= dmg; if (hp <= 0){ hp = 0; dead = true; } }
    public int hp(){ return hp; }

    // ---- AttackSystem.PlayerView ----
    @Override public int id(){ return id; }
    @Override public Vec2 pos(){ return pos; }
    @Override public Stats stats(){ return stats; }
    @Override public AttackStyle effectiveStyle(){ return style; }
    @Override public long lastAttackAt(){ return lastAttackAt; }
    @Override public void setLastAttackAt(long t){ lastAttackAt = t; }
    @Override public Vec2 aimDir(){ return (Math.abs(moveX)>0.0001 || Math.abs(moveY)>0.0001) ? new Vec2(moveX,moveY).norm() : lastDir; }
    @Override public boolean isDead(){ return dead; }

    // ---- Orbit weapon methods for AttackSystem.PlayerView ----
    @Override public double orbitAngle(){ return orbitAngle; }
    @Override public void setOrbitAngle(double angle){ this.orbitAngle = angle; }
    @Override public long getOrbitLastHitTime(int enemyId){ return orbitLastHitTime.getOrDefault(enemyId, 0L); }
    @Override public void setOrbitLastHitTime(int enemyId, long time){ orbitLastHitTime.put(enemyId, time); }

    // ---- EnemyAiSystem.PlayerActor ----
    @Override public double radiusPx(){ return radiusPx; }

    // ---- Match Progress ----
    public int getScore() { return score; }
    public int getCoinsEarned() { return coinsEarned; }
    
    public void awardScore(int points) { 
        if (!dead) score += points; 
    }
    
    public void awardCoins(int coins) { 
        if (!dead) coinsEarned += coins; 
    }

    // ---- Upgrade System (for Demo 4) ----
    public void setAttackStyle(AttackStyle newStyle) {
        this.style = newStyle;
    }

    // ---- NEW: XP / Level helpers ----

    public int getXp() { return xp; }
    public int getLevel() { return level; }
    public int getXpToNext() { return xpToNext; }
    public boolean isChoosingUpgrade() { return choosingUpgrade; }
    public boolean isInvincible() { return invincible; }

    public float damageMultiplier() { return damageMultiplier; }
    public float moveSpeedMultiplier() { return moveSpeedMultiplier; }

    public List<UpgradeType> getCurrentUpgradeOptions() { return currentUpgradeOptions; }

    public void addXp(int amount) {
        if (dead || amount <= 0) return;
        xp += amount;
        while (xp >= xpToNext) {
            xp -= xpToNext;       // keep overflow XP
            levelUp();
        }
    }

    public void levelUp() {
        level++;
        xpToNext = 100 + level * 50;

        // #region agent log
        try (var fw = new java.io.FileWriter("C:\\Users\\jh_dr\\OneDrive\\Desktop\\School\\Git\\3_rasel_6\\Backend\\Survivor_Backend\\.cursor\\debug.log", true);
             var bw = new java.io.BufferedWriter(fw)) {
            String payload = "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"PlayerState.java:levelUp\",\"message\":\"level_up\",\"data\":{\"id\":" + id + ",\"level\":" + level + ",\"xp\":" + xp + ",\"xpToNext\":" + xpToNext + "},\"timestamp\":" + System.currentTimeMillis() + "}";
            bw.write(payload);
            bw.newLine();
            System.out.println(payload);
        } catch (Exception ignored) {}
        // #endregion

        // Freeze + invincible during upgrade choice
        choosingUpgrade = true;
        invincible = true;
        moveX = 0f;
        moveY = 0f;

        currentUpgradeOptions = UpgradeGenerator.generate3Random();
    }

    public void applyUpgrade(UpgradeType type) {
        if (type == null) return;

        // #region agent log
        try (var fw = new java.io.FileWriter("C:\\Users\\jh_dr\\OneDrive\\Desktop\\School\\Git\\3_rasel_6\\Backend\\Survivor_Backend\\.cursor\\debug.log", true);
             var bw = new java.io.BufferedWriter(fw)) {
            String payload = "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H1\",\"location\":\"PlayerState.java:applyUpgrade\",\"message\":\"apply_upgrade_called\",\"data\":{\"id\":" + id + ",\"type\":\"" + type + "\",\"choosingUpgrade\":" + choosingUpgrade + ",\"invincible\":" + invincible + "},\"timestamp\":" + System.currentTimeMillis() + "}";
            bw.write(payload);
            bw.newLine();
            System.out.println(payload);
        } catch (Exception ignored) {}
        // #endregion

        switch (type) {
            case DAMAGE_UP -> {
                // +1% damage each time
                damageMultiplier *= 1.01f;
            }
            case ATKSPEED_UP -> {
                // Treat as movement speed upgrade: +0.1 (10%) move speed
                moveSpeedMultiplier += 0.1f;
            }
            case MAX_HP_UP -> {
                int bonus = 10;
                maxHp += bonus;
                hp = Math.min(maxHp, hp + bonus);
            }
            case WEAPON_CONE -> {
                setAttackStyle(AttackStyle.CONE);
            }
            case WEAPON_ORBIT -> {
                setAttackStyle(AttackStyle.ORBIT);
            }
        }

        // Done choosing – unfreeze + remove invincibility
        choosingUpgrade = false;
        invincible = false;

        // #region agent log
        try (var fw = new java.io.FileWriter("C:\\Users\\jh_dr\\OneDrive\\Desktop\\School\\Git\\3_rasel_6\\Backend\\Survivor_Backend\\.cursor\\debug.log", true);
             var bw = new java.io.BufferedWriter(fw)) {
            String payload = "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"PlayerState.java:applyUpgrade\",\"message\":\"apply_upgrade_completed\",\"data\":{\"id\":" + id + ",\"type\":\"" + type + "\",\"choosingUpgrade\":" + choosingUpgrade + ",\"invincible\":" + invincible + "},\"timestamp\":" + System.currentTimeMillis() + "}";
            bw.write(payload);
            bw.newLine();
            System.out.println(payload);
        } catch (Exception ignored) {}
        // #endregion
    }
}
