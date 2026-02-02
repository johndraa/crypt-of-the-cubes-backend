package survivor.runtime;

import java.util.Map;
import survivor.runtime.SnapshotBuilder.PlayerSnapshot;

/**
 * @author John Draa
 * @param events
 * @param snapshots
 */

public record MatchDelta(
        CombatResolve.MatchEvents events,
        Map<Integer, PlayerSnapshot> snapshots,
        boolean shouldEnd
)
{
    public static MatchDelta empty(){ return new MatchDelta(new CombatResolve.MatchEvents(java.util.List.of(), java.util.List.of()), java.util.Map.of(), false); }
}
