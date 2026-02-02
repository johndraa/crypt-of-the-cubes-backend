package survivor.combat;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test: Vec2 record methods
 * Coverage: Vec2.len(), Vec2.norm(), Vec2.dot()
 * Strategy: White-box, branch coverage, boundary values
 */
public class Vec2Test {

    /**
     * Test: len()
     * Coverage: Vec2.len()
     * Strategy: Boundary value (zero vector, unit vector, arbitrary)
     * Equivalence: Various vector lengths
     */
    @Test
    public void testLen_CalculatesCorrectLength() {
        Vec2 zero = new Vec2(0, 0);
        assertEquals(0.0, zero.len(), 0.001);

        Vec2 unit = new Vec2(1, 0);
        assertEquals(1.0, unit.len(), 0.001);

        Vec2 vec = new Vec2(3, 4);
        assertEquals(5.0, vec.len(), 0.001); // 3-4-5 triangle
    }

    /**
     * Test: norm()
     * Coverage: Vec2.norm() - zero vector branch
     * Strategy: White-box, branch coverage
     * Equivalence: Zero vector
     */
    @Test
    public void testNorm_ZeroVector_ReturnsZero() {
        Vec2 zero = new Vec2(0, 0);
        Vec2 normalized = zero.norm();
        assertEquals(0.0, normalized.x(), 0.001);
        assertEquals(0.0, normalized.y(), 0.001);
    }

    /**
     * Test: norm()
     * Coverage: Vec2.norm() - non-zero branch
     * Strategy: White-box, branch coverage
     * Equivalence: Non-zero vector
     */
    @Test
    public void testNorm_NonZeroVector_Normalizes() {
        Vec2 vec = new Vec2(3, 4);
        Vec2 normalized = vec.norm();
        assertEquals(1.0, normalized.len(), 0.001);
        assertEquals(0.6, normalized.x(), 0.001);
        assertEquals(0.8, normalized.y(), 0.001);
    }

    /**
     * Test: dot()
     * Coverage: Vec2.dot()
     * Strategy: Equivalence class
     * Equivalence: Various vector pairs
     */
    @Test
    public void testDot_CalculatesDotProduct() {
        Vec2 a = new Vec2(1, 2);
        Vec2 b = new Vec2(3, 4);
        double dot = Vec2.dot(a, b);
        assertEquals(11.0, dot, 0.001); // 1*3 + 2*4 = 11
    }
}