package survivor.combat;

/**
 * @author John Draa
 * @param x
 * @param y
 */

public record Vec2(double x, double y)
{
    public double len() { return Math.hypot(x, y); }
    public Vec2 norm()
    {
        double L = len();
        return (L == 0.0) ? new Vec2(0, 0) : new Vec2(x / L, y / L);
    }
    public static double dot(Vec2 a, Vec2 b) { return a.x * b.x + a.y * b.y; }
}
