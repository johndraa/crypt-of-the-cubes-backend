package survivor.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class UpgradeGenerator {

    private static final UpgradeType[] POOL = UpgradeType.values();
    private static final Random RNG = new Random();

    private UpgradeGenerator() {}

    public static List<UpgradeType> generate3Random() {
        List<UpgradeType> out = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            out.add(POOL[RNG.nextInt(POOL.length)]);
        }
        return out;
    }
}
