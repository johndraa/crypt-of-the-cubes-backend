package survivor.runtime;

import org.springframework.stereotype.Component;
import survivor.config.FogConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author John Draa
 */

@Component
public class MatchRuntimeRegistry
{
    private final Map<Long, MatchRuntime> map = new ConcurrentHashMap<>();

    public void startRuntime(long matchId, FogConfig fog)
    {
        map.computeIfAbsent(matchId, id -> new MatchRuntime(id, fog.getLight(), fog.getWake(), fog.getSleep()))
                .start();
    }

    /**
     * Create a runtime without starting it (for countdown delay)
     */
    public void createRuntime(long matchId, FogConfig fog)
    {
        map.computeIfAbsent(matchId, id -> new MatchRuntime(id, fog.getLight(), fog.getWake(), fog.getSleep()));
    }

    /**
     * Start an existing runtime (called after countdown completes)
     */
    public void startExistingRuntime(long matchId)
    {
        var rt = map.get(matchId);
        if (rt != null && !rt.isStarted()) {
            rt.start();
        }
    }

    public Optional<MatchRuntime> get(long id){ return Optional.ofNullable(map.get(id)); }
    public Collection<MatchRuntime> active(){ return map.values(); }
    public void end(long id){ map.remove(id); }
}
