package survivor.runtime;

import survivor.combat.*;
import survivor.model.EnemyState;
import survivor.model.PlayerState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author John Draa
 */

public class MatchRuntime {
    private static final int TILE_PX = 24;
    private static final double DT = 1.0 / 20.0;

    private final long id;
    private final int R_LIGHT, R_WAKE, R_SLEEP;

    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>();
    private final List<EnemyState> enemies = Collections.synchronizedList(new ArrayList<>());

    // Per-match world-level / wave manager (handles spawning and scaling)
    private final WorldLevelManager worldLevelManager = new WorldLevelManager();

    private boolean started = false, ended = false;
    private long startTime;

    private int enemySeq = 1000;                 // simple id generator for enemies
    private int nextEnemyId() { return enemySeq++; }

    public MatchRuntime(long id, int light, int wake, int sleep)
    {
        this.id = id; this.R_LIGHT = light; this.R_WAKE = wake; this.R_SLEEP = sleep;
    }

    public long id() { return id; }
    public Map<Integer, PlayerState> getPlayers() { return players; }
    public long getStartTime() { return startTime; }

    public boolean isStarted() { return started; }
    public boolean isEnded() { return ended; }

    /** Expose current world level for websocket snapshots / clients. */
    public int getCurrentWorldLevel() {
        return worldLevelManager.getCurrentWorldLevel();
    }

    public void start()
    {
        started = true;
        startTime = System.currentTimeMillis();

        // Start world level 1 and spawn the first wave.
        // Enemies spawn inactive; fog-of-war still controls when they wake up.
        worldLevelManager.startFirstWave(enemies, players.values(), this::nextEnemyId);
    }

    public void stop()  { ended = true; }

    public void addPlayer(PlayerState p) { players.put(p.id(), p); }
    public void removePlayer(int accountId) { players.remove(accountId); }
    public void addEnemy(EnemyState e) { enemies.add(e); }

    public java.util.Optional<survivor.model.PlayerState> player(int accountId)
    {
        return java.util.Optional.ofNullable(players.get(accountId));
    }

    public MatchDelta tick()
    {
        if (!started || ended) return MatchDelta.empty();

        Physics.integrate(players.values(), DT);
        Physics.integrateEnemies(enemies, DT);

        EnemyAiSystem.gateActivity(
                enemies, players.values(),
                R_WAKE * (double)TILE_PX, R_SLEEP * (double)TILE_PX
        );

        var eHits = EnemyAiSystem.tick(
                enemies, players.values(),
                R_LIGHT * (double)TILE_PX, TILE_PX
        );

        var pHits = AttackSystem.resolveAuto(players.values(), enemies, TILE_PX);

        var events = CombatResolve.apply(pHits, eHits, players, enemies);

        // Check for match end condition (<1 players alive - all eliminated)
        // TODO change to <=1 players alive for production
        long alivePlayers = players.values().stream().filter(p -> !p.isDead()).count();
        boolean shouldEnd = alivePlayers < 1;

        // If match should end, mark runtime as ended immediately
        // This ensures runtime state is consistent with the delta
        if (shouldEnd) {
            ended = true;
        }

        // Update world-level progression based on enemy deaths, and spawn the
        // next wave if the current one is cleared (and the match is not ending).
        worldLevelManager.handleDeaths(
                events.deaths(),
                enemies,
                players.values(),
                this::nextEnemyId,
                shouldEnd
        );

        var snaps  = SnapshotBuilder.build(players.values(), enemies, R_LIGHT * (double)TILE_PX);

        return new MatchDelta(events, snaps, shouldEnd);
    }
}
