package survivor.combat;

/**
 * @author John Draa
 * @param health
 * @param moveSpeed
 * @param attackSpeed
 * @param damageMult
 * @param critChance
 * @param range
 */

public record Stats(
        int health,
        int moveSpeed,
        int attackSpeed,
        int damageMult,
        int critChance,
        int range
) {}