package survivor.runtime;

import survivor.combat.AttackSystem;
import survivor.model.PlayerState;
import survivor.model.EnemyState;

import java.util.*;

/**
 * @author John Draa
 */

public final class CombatResolve
{
    private CombatResolve(){}

    public static MatchEvents apply(
            List<AttackSystem.Hit> playerHits,
            List<AttackSystem.Hit> enemyHits,
            Map<Integer, PlayerState> players,
            List<EnemyState> enemies
    )
    {
        List<DamageEvent> damages = new ArrayList<>();
        List<DeathEvent> deaths = new ArrayList<>();

        // player → enemy
        for (var h : playerHits)
        {
            EnemyState e = findEnemy(enemies, h.targetId());
            if (e == null || e.isDead()) continue;

            PlayerState attacker = players.get(h.attackerId());
            int appliedDamage = h.damage();
            if (attacker != null) {
                appliedDamage = (int)Math.round(appliedDamage * attacker.damageMultiplier());
            }

            boolean wasAlive = !e.isDead();
            e.applyDamage(appliedDamage);
            damages.add(new DamageEvent(h.targetId(), "enemy", appliedDamage));
            
            // Award coins, score, and XP for killing enemies
            if (wasAlive && e.isDead()) {
                deaths.add(new DeathEvent(h.targetId(), "enemy"));
                
                PlayerState killer = attacker;
                if (killer != null && !killer.isDead()) {
                    // Score is always awarded
                    int scoreReward = switch (e.type()) {
                        case BUMPER -> 20;  // Basic enemy
                        case SWIPER -> 30;  // Slightly tougher
                        default -> 20;
                    };
                    killer.awardScore(scoreReward);
                    
                    // Coins are chance-based (not guaranteed)
                    double coinDropChance = 0.4; // 40% chance for regular enemies
                    if (Math.random() < coinDropChance) {
                        int coinReward = switch (e.type()) {
                            case BUMPER -> 2;  // Basic enemy
                            case SWIPER -> 5;  // Slightly tougher
                            default -> 2;
                        };
                        killer.awardCoins(coinReward);
                    }

                    // Simple XP reward per kill (tune as needed)
                    int xpReward = 25;
                    killer.addXp(xpReward);
                }
            }
        }

        // enemy → player
        for (var h : enemyHits)
        {
            PlayerState p = players.get(h.targetId());
            if (p == null || p.isDead()) continue;

            // NEW: invincibility during upgrade choice
            if (p.isInvincible()) continue;

            p.applyDamage(h.damage());
            damages.add(new DamageEvent(h.targetId(), "player", h.damage()));
            if (p.isDead()) deaths.add(new DeathEvent(h.targetId(), "player"));
        }

        return new MatchEvents(damages, deaths);
    }

    private static EnemyState findEnemy(List<EnemyState> enemies, int id)
    {
        for (var e : enemies) if (e.id()==id) return e;
        return null;
    }

    // Simple event DTOs (broadcast over /topic/match.{id}.game as needed)
    public record DamageEvent(int targetId, String targetType, int damage) {}
    public record DeathEvent(int id, String type) {}

    public record MatchEvents(List<DamageEvent> damages, List<DeathEvent> deaths) {}
}
