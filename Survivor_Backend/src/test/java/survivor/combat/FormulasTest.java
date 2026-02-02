package survivor.combat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import survivor.combat.Stats;

import java.util.Arrays;
import java.util.Collection;
import java.util.SplittableRandom;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class FormulasTest {

    private final Stats stats;
    private final double baseAPS;
    private final int expectedMin;
    private final int expectedMax;

    public FormulasTest(Stats stats, double baseAPS, int expectedMin, int expectedMax) {
        this.stats = stats;
        this.baseAPS = baseAPS;
        this.expectedMin = expectedMin;
        this.expectedMax = expectedMax;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { new Stats(100, 50, 0, 0, 0, 0), 1.0, 80, 400 },
            { new Stats(100, 50, 100, 0, 0, 0), 1.0, 80, 400 },
            { new Stats(100, 50, 200, 0, 0, 0), 1.0, 80, 400 }
        });
    }

    /**
     * Test: intervalMs()
     * Coverage: Formulas.intervalMs() - boundary values
     * Strategy: White-box, boundary testing (clamp to 80-400)
     * Equivalence: Various attack speed values
     */
    @Test
    public void testIntervalMs_ClampsToBounds() {
        int result = Formulas.intervalMs(stats, baseAPS);
        assertTrue("Result should be >= 80", result >= 80);
        assertTrue("Result should be <= 400", result <= 400);
    }

    /**
     * Test: damage()
     * Coverage: Formulas.damage()
     * Strategy: White-box, equivalence class
     * Equivalence: Various damage multipliers
     */
    @Test
    public void testDamage_ScalesWithDamageMult() {
        Stats lowDmg = new Stats(100, 50, 30, 0, 0, 0);
        Stats highDmg = new Stats(100, 50, 30, 100, 0, 0);

        double base = 10.0;
        double lowResult = Formulas.damage(lowDmg, base);
        double highResult = Formulas.damage(highDmg, base);

        assertTrue("Higher damage mult should produce more damage", highResult > lowResult);
    }

    /**
     * Test: crit()
     * Coverage: Formulas.crit() - true and false branches
     * Strategy: White-box, branch coverage
     * Equivalence: Various crit chances
     */
    @Test
    public void testCrit_RespectsCritChance() {
        SplittableRandom rng = new SplittableRandom(12345);
        Stats noCrit = new Stats(100, 50, 30, 0, 0, 0);
        Stats highCrit = new Stats(100, 50, 30, 0, 100, 0);

        // With 0% crit chance, should never crit
        boolean resultNoCrit = Formulas.crit(noCrit, rng);
        assertFalse("0% crit chance should not crit", resultNoCrit);

        // With 100% crit chance, should always crit
        boolean resultHighCrit = Formulas.crit(highCrit, rng);
        assertTrue("100% crit chance should crit", resultHighCrit);
    }

    /**
     * Test: rangeFactor()
     * Coverage: Formulas.rangeFactor()
     * Strategy: White-box, equivalence class
     * Equivalence: Various range values
     */
    @Test
    public void testRangeFactor_ScalesWithRange() {
        Stats lowRange = new Stats(100, 50, 30, 0, 0, 0);
        Stats highRange = new Stats(100, 50, 30, 0, 0, 100);

        double lowResult = Formulas.rangeFactor(lowRange);
        double highResult = Formulas.rangeFactor(highRange);

        assertTrue("Higher range should produce larger factor", highResult > lowResult);
        assertEquals(1.0, lowResult, 0.01); // 0 range = 1.0 factor
    }

    /**
     * Test: clamp()
     * Coverage: Formulas.clamp() - all branches
     * Strategy: White-box, boundary value testing
     * Equivalence: Below min, in range, above max
     */
    @Test
    public void testClamp_AllBranches() {
        // Below minimum
        assertEquals(10, Formulas.clamp(5, 10, 20));
        // In range
        assertEquals(15, Formulas.clamp(15, 10, 20));
        // Above maximum
        assertEquals(20, Formulas.clamp(25, 10, 20));
        // At boundaries
        assertEquals(10, Formulas.clamp(10, 10, 20));
        assertEquals(20, Formulas.clamp(20, 10, 20));
    }
}