package survivor.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import survivor.ws.GameWs;
import lombok.Synchronized;

@Component
@RequiredArgsConstructor
public class TickService
{
    private final MatchRuntimeRegistry registry;
    private final GameWs ws;
    private final survivor.match.MatchStore matchStore;

    @Value("${tick.rate.hz:20}") private int hz;
    
    // Track which players have already received upgrade events for their current level
    // Key: "matchId:playerId", Value: level for which upgrade event was sent
    private final java.util.Map<String, Integer> upgradeEventSent = new java.util.concurrent.ConcurrentHashMap<>();

    @Scheduled(fixedRateString = "#{1000/${tick.rate.hz:20}}")
    public void tickAll()
    {
        var activeRuntimes = new java.util.ArrayList<>(registry.active()); // Create snapshot copy
        
        if (activeRuntimes.size() > 0) {
            System.out.println("TICK #" + System.currentTimeMillis() + " - Processing " + activeRuntimes.size() + " runtime(s)");
        }
        
        for (var rt : activeRuntimes)
        {
            // Double-check runtime still exists in registry (defensive)
            if (registry.get(rt.id()).isEmpty()) {
                System.out.println("  -> Skipping match " + rt.id() + " - removed from registry");
                continue;
            }
            
            // Skip runtimes that have been manually stopped (ended flag set)
            // Automatic match end removes runtime from registry, so this mainly handles manual stops
            if (rt.isEnded()) {
                System.out.println("  -> Skipping match " + rt.id() + " - marked as ended");
                continue;
            }
            
            var delta = rt.tick();
            dispatch(rt, delta);
        }
    }

    private void dispatch(MatchRuntime rt, MatchDelta delta) {
        // Don't dispatch anything if runtime has been stopped
        if (rt.isEnded()) {
            System.out.println("  -> dispatch() skipping match " + rt.id() + " - ended=true");
            return;
        }
        
        // Double-check runtime is still in registry (defensive check)
        if (registry.get(rt.id()).isEmpty()) {
            System.out.println("  -> dispatch() skipping match " + rt.id() + " - not in registry");
            return;
        }
        
        // events (damage / deaths) - wrap in proper format for frontend
        // Broadcast these BEFORE handling match end so clients receive death notifications
        if (!delta.events().damages().isEmpty()) {
            java.util.Map<String, Object> damageEvent = new java.util.HashMap<>();
            damageEvent.put("event", "DAMAGE");
            damageEvent.put("damages", delta.events().damages());
            ws.game(rt.id(), damageEvent);
        }
        if (!delta.events().deaths().isEmpty()) {
            java.util.Map<String, Object> deathEvent = new java.util.HashMap<>();
            deathEvent.put("event", "DEATH");
            deathEvent.put("deaths", delta.events().deaths());
            ws.game(rt.id(), deathEvent);
        }

        // Always send snapshots during gameplay (even if empty) so frontend receives state updates
        // Aggregate per-player snapshots into unified format for broadcast
        if (!delta.snapshots().isEmpty()) {
            java.util.Map<String, Object> baseSnapshot = aggregateSnapshots(delta.snapshots());
            java.util.Map<String, Object> unifiedSnapshot = new java.util.HashMap<>(baseSnapshot);
            // Attach current world level so frontend can display it in the HUD.
            unifiedSnapshot.put("worldLevel", rt.getCurrentWorldLevel());
            ws.game(rt.id(), unifiedSnapshot);
        } else {
            // Send empty snapshot if match is started but no snapshots generated yet
            // This ensures frontend receives periodic updates even during initialization
            // Don't send snapshots if runtime has been manually stopped
            if (rt.isStarted() && !rt.isEnded()) {
                java.util.Map<String, Object> emptySnapshot = new java.util.HashMap<>();
                emptySnapshot.put("players", java.util.List.of());
                emptySnapshot.put("enemies", java.util.List.of());
                emptySnapshot.put("worldLevel", rt.getCurrentWorldLevel());
                ws.game(rt.id(), emptySnapshot);
            }
        }

        // ---- NEW: XP + Upgrade events ----
        var players = rt.getPlayers().values();
        for (survivor.model.PlayerState p : players) {
            if (p == null || p.isDead()) continue;

            // A. XP update
            var xpEvent = new java.util.HashMap<String, Object>();
            xpEvent.put("event", "xpUpdate");
            xpEvent.put("playerId", p.id());
            xpEvent.put("xp", p.getXp());
            xpEvent.put("level", p.getLevel());
            xpEvent.put("xpToNext", p.getXpToNext());
            ws.game(rt.id(), xpEvent);

            // B. Upgrade choices (send only once per level-up)
            if (p.isChoosingUpgrade()
                    && p.getCurrentUpgradeOptions() != null
                    && !p.getCurrentUpgradeOptions().isEmpty()) {
                
                // Create a key for tracking: "matchId:playerId"
                String key = rt.id() + ":" + p.id();
                int currentLevel = p.getLevel();
                
                // Only send upgrade event if we haven't sent it for this level yet
                if (!upgradeEventSent.containsKey(key) || upgradeEventSent.get(key) != currentLevel) {
                    var upgEvent = new java.util.HashMap<String, Object>();
                    upgEvent.put("event", "upgradeOptions");
                    upgEvent.put("playerId", p.id());
                    upgEvent.put("level", currentLevel);

                    var options = p.getCurrentUpgradeOptions()
                            .stream()
                            .map(Enum::name) // "DAMAGE_UP", "ATKSPEED_UP", "MAX_HP_UP"
                            .toList();

                    upgEvent.put("options", options);
                    ws.game(rt.id(), upgEvent);
                    
                    // Mark that we've sent the upgrade event for this level
                    upgradeEventSent.put(key, currentLevel);
                }
            } else {
                // Player is no longer choosing upgrade - clear tracking for next level-up
                String key = rt.id() + ":" + p.id();
                upgradeEventSent.remove(key);
            }
        }

        // Handle match end AFTER broadcasting events
        // This ensures clients receive death notifications before match end
        if (delta.shouldEnd()) {
            handleMatchEnd(rt);
            return; // Exit immediately after handling match end
        }

        // later (with auth), switch to per-user queues:
        // delta.snapshots().forEach((accountId, snap) -> ws.toPlayer(rt.id(), accountId, snap));
    }

    /**
     * Aggregate per-player snapshots into unified format for broadcast.
     * Collects all unique players and enemies from all per-player snapshots.
     * This provides a single unified snapshot that all players receive via broadcast.
     * 
     * When switching to per-user queues, this aggregation can be removed and
     * individual per-player snapshots can be sent directly to each player.
     */
    private java.util.Map<String, Object> aggregateSnapshots(java.util.Map<Integer, SnapshotBuilder.PlayerSnapshot> snapshots) {
        java.util.Map<Integer, SnapshotBuilder.EntityView> playersMap = new java.util.HashMap<>();
        java.util.Map<Integer, SnapshotBuilder.EntityView> enemiesMap = new java.util.HashMap<>();

        // Aggregate entities from all player snapshots
        for (var playerSnapshot : snapshots.values()) {
            for (var entity : playerSnapshot.entities()) {
                if ("player".equals(entity.type())) {
                    // Players are always included (use putIfAbsent to avoid duplicates)
                    playersMap.putIfAbsent(entity.id(), entity);
                } else if ("enemy".equals(entity.type())) {
                    // For enemies, replace "enemy" type with actual enemy type (BUMPER, SWIPER)
                    // Frontend expects enemy type in the "type" field
                    SnapshotBuilder.EntityView enemyWithType = new SnapshotBuilder.EntityView(
                        entity.enemyType() != null ? entity.enemyType() : "ENEMY",  // Use enemyType as type
                        entity.id(),
                        entity.x(),
                        entity.y(),
                        entity.hp(),
                        entity.enemyType()  // Keep enemyType field as well
                    );
                    enemiesMap.putIfAbsent(entity.id(), enemyWithType);
                }
            }
        }

        // Convert maps to lists for JSON serialization
        java.util.List<SnapshotBuilder.EntityView> playersList = new java.util.ArrayList<>(playersMap.values());
        java.util.List<SnapshotBuilder.EntityView> enemiesList = new java.util.ArrayList<>(enemiesMap.values());

        return java.util.Map.of(
                "players", playersList,
                "enemies", enemiesList
        );
    }

    @Synchronized
    private void handleMatchEnd(MatchRuntime rt) {
        // Prevent multiple calls - mark as ended immediately
        if (rt.isEnded()) {
            System.out.println("  -> handleMatchEnd() skipping match " + rt.id() + " - already ended");
            return;
        }
        
        // Remove from registry FIRST to prevent any further ticks from processing this runtime
        // This is critical to prevent race conditions and repeated calls
        registry.end(rt.id());
        
        // Mark runtime as ended to prevent any remaining operations
        rt.stop();
        
        // Clean up upgrade event tracking for this match to prevent memory leaks
        long matchId = rt.id();
        upgradeEventSent.entrySet().removeIf(entry -> entry.getKey().startsWith(matchId + ":"));
        
        try {
            // Safely get players - check for null
            var players = rt.getPlayers();
            if (players == null) {
                System.err.println("  -> Warning: players map is null for match " + rt.id());
                return;
            }
            
            // Find the winner (last player alive)
            Integer winnerId = players.values().stream()
                    .filter(p -> p != null && !p.isDead())
                    .map(survivor.model.PlayerState::id)
                    .findFirst()
                    .orElse(null);

            // Mark match as ended in database
            matchStore.markEnded(rt.id(), winnerId, java.time.Instant.now());

            // Collect results from gameplay (coins earned from enemy drops, score from kills/time)
            var results = players.values().stream()
                    .filter(p -> p != null)
                    .map(p -> {
                        long timeAlive = rt.getStartTime() > 0 
                            ? System.currentTimeMillis() - rt.getStartTime() 
                            : 0;
                        return new survivor.ws.dto.ParticipantResult(
                                p.id(), 
                                p.getScore(), // score earned during gameplay
                                p.getCoinsEarned(), // coins from enemy drops during gameplay
                                0, // kills (not tracked yet)
                                timeAlive // time alive
                        );
                    })
                    .toList();

            // Persist results to UserProgress (only if we have results)
            if (!results.isEmpty()) {
                matchStore.writeResults(rt.id(), results);

                // Broadcast match end to all players
                ws.game(rt.id(), java.util.Map.of(
                        "event", "MATCH_ENDED",
                        "winnerId", winnerId != null ? winnerId : -1,
                        "results", results
                ));
            } else {
                // Still broadcast match end even if no results
                ws.game(rt.id(), java.util.Map.of(
                        "event", "MATCH_ENDED",
                        "winnerId", winnerId != null ? winnerId : -1,
                        "results", java.util.List.of()
                ));
            }
            
            System.out.println("  -> Match " + rt.id() + " ended successfully. Winner: " + winnerId);

        } catch (Exception e) {
            // Better error logging to identify the actual issue
            System.err.println("Error handling match end for match " + rt.id() + ":");
            System.err.println("  Exception type: " + e.getClass().getName());
            System.err.println("  Message: " + (e.getMessage() != null ? e.getMessage() : "(null)"));
            System.err.println("  Stack trace:");
            e.printStackTrace();
            
            // Runtime is already removed from registry, so we don't need to remove it again
            // Just log the error - the match end cleanup has already happened
        }
    }
}
