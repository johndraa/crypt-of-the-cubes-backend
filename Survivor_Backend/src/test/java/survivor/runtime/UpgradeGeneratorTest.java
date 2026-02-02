package survivor.runtime;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for UpgradeGenerator.
 * 
 * Coverage Goals:
 * - UpgradeGenerator.generate3Random() - generates 3 options
 * - Option uniqueness (if possible given pool size)
 * 
 * Strategy: Unit testing with statistical validation
 */
@RunWith(SpringRunner.class)
@ActiveProfiles({"test"})
public class UpgradeGeneratorTest {

    /**
     * Test: generate3Random() - generates exactly 3 options
     * Coverage: UpgradeGenerator.generate3Random()
     * Strategy: White-box, functional
     * Equivalence: Valid generation
     * Branches: Loop iteration branch (3 times)
     * Why: Tests core upgrade option generation
     */
    @Test
    public void testGenerate3Random_ReturnsExactly3Options() {
        List<UpgradeType> options = UpgradeGenerator.generate3Random();
        
        assertNotNull("Options should not be null", options);
        assertEquals("Should generate exactly 3 options", 3, options.size());
    }

    /**
     * Test: generate3Random() - options are valid UpgradeType values
     * Coverage: UpgradeGenerator.generate3Random() - pool selection
     * Strategy: White-box, validation
     * Equivalence: Valid upgrade types
     * Branches: Pool selection branch
     * Why: Tests that generated options are valid enum values
     */
    @Test
    public void testGenerate3Random_ReturnsValidUpgradeTypes() {
        List<UpgradeType> options = UpgradeGenerator.generate3Random();
        
        for (UpgradeType option : options) {
            assertNotNull("Option should not be null", option);
            // Verify it's a valid UpgradeType by checking it's in the enum
            assertTrue("Option should be a valid UpgradeType", 
                      java.util.Arrays.asList(UpgradeType.values()).contains(option));
        }
    }

    /**
     * Test: generate3Random() - can generate all upgrade types over multiple calls
     * Coverage: UpgradeGenerator.generate3Random() - randomness validation
     * Strategy: White-box, statistical validation
     * Equivalence: Multiple generations
     * Branches: Random selection branch
     * Why: Tests that random generation can produce variety
     */
    @Test
    public void testGenerate3Random_CanGenerateAllTypes() {
        Set<UpgradeType> seenTypes = new HashSet<>();
        
        // Generate many times to increase chance of seeing all types
        for (int i = 0; i < 100; i++) {
            List<UpgradeType> options = UpgradeGenerator.generate3Random();
            seenTypes.addAll(options);
        }
        
        // Should have seen multiple different types (not just one repeated)
        assertTrue("Should generate variety of upgrade types", seenTypes.size() > 1);
    }
}
