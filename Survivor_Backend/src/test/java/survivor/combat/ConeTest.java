package survivor.combat;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test: Cone.contains()
 * Coverage: Cone.contains() - all branches
 * Strategy: White-box, branch coverage, boundary values
 */
public class ConeTest {

    /**
     * Test: contains() - target too close (L <= 0.0001)
     * Coverage: Cone.contains() - early return branch
     * Strategy: Boundary value (very close target)
     * Equivalence: Target at origin
     */
    @Test
    public void testContains_TargetTooClose_ReturnsFalse() {
        Vec2 attacker = new Vec2(0, 0);
        Vec2 aim = new Vec2(1, 0);
        Vec2 target = new Vec2(0.00005, 0);
        double lengthPx = 100.0;
        double halfAngle = Math.PI / 4;

        assertFalse(Cone.contains(attacker, aim, target, lengthPx, halfAngle));
    }

    /**
     * Test: contains() - target too far (L > lengthPx)
     * Coverage: Cone.contains() - distance check branch
     * Strategy: Boundary value (beyond range)
     * Equivalence: Target beyond cone length
     */
    @Test
    public void testContains_TargetTooFar_ReturnsFalse() {
        Vec2 attacker = new Vec2(0, 0);
        Vec2 aim = new Vec2(1, 0);
        Vec2 target = new Vec2(200, 0);
        double lengthPx = 100.0;
        double halfAngle = Math.PI / 4;

        assertFalse(Cone.contains(attacker, aim, target, lengthPx, halfAngle));
    }

    /**
     * Test: contains() - target in cone
     * Coverage: Cone.contains() - angle check branch (true)
     * Strategy: Equivalence class (valid target)
     * Equivalence: Target within angle and distance
     */
    @Test
    public void testContains_TargetInCone_ReturnsTrue() {
        Vec2 attacker = new Vec2(0, 0);
        Vec2 aim = new Vec2(1, 0);
        Vec2 target = new Vec2(50, 10);
        double lengthPx = 100.0;
        double halfAngle = Math.PI / 4; // 45 degrees

        assertTrue(Cone.contains(attacker, aim, target, lengthPx, halfAngle));
    }

    /**
     * Test: contains() - target outside angle
     * Coverage: Cone.contains() - angle check branch (false)
     * Strategy: Equivalence class (invalid angle)
     * Equivalence: Target outside cone angle
     */
    @Test
    public void testContains_TargetOutsideAngle_ReturnsFalse() {
        Vec2 attacker = new Vec2(0, 0);
        Vec2 aim = new Vec2(1, 0);
        Vec2 target = new Vec2(50, 60); // Too far to the side
        double lengthPx = 100.0;
        double halfAngle = Math.PI / 6; // 30 degrees

        assertFalse(Cone.contains(attacker, aim, target, lengthPx, halfAngle));
    }
}