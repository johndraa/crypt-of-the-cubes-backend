package survivor.combat;

/**
 * @author John Draa
 */

public final class Cone
{
    private Cone() {}

    /** aim must be normalized */
    public static boolean contains(Vec2 attacker, Vec2 aim, Vec2 target,
                                   double lengthPx, double halfAngleRad)
    {
        Vec2 d = new Vec2(target.x() - attacker.x(), target.y() - attacker.y());
        double L = d.len();
        if (L <= 0.0001 || L > lengthPx) return false;
        Vec2 dir = new Vec2(d.x() / L, d.y() / L);
        return Vec2.dot(aim, dir) >= Math.cos(halfAngleRad);
    }
}
