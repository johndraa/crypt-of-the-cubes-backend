package survivor.combat;

import java.util.SplittableRandom;

/**
 * @author John Draa
 */

public final class Formulas
{
    private Formulas() {}

    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(v, hi)); }

    public static int intervalMs(Stats s, double baseAPS)
    {
        double aps = baseAPS * (0.5 + 0.015 * s.attackSpeed());
        return clamp((int)Math.round(1000.0 / Math.max(aps, 0.0001)), 80, 400);
    }

    public static double damage(Stats s, double base)
    {
        return base * (0.6 + 0.02 * s.damageMult());
    }

    public static boolean crit(Stats s, SplittableRandom r)
    {
        return r.nextDouble() < (s.critChance() / 100.0);
    }

    public static double rangeFactor(Stats s)
    {
        return 1.0 + 0.03 * s.range();
    }
}
