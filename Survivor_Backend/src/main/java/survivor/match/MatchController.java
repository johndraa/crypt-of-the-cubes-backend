package survivor.match;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import survivor.exceptions.NotFoundException;
import survivor.runtime.MatchRuntimeRegistry;
import survivor.ws.dto.LobbyPlayer;
import survivor.ws.dto.ParticipantResult;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/matches") @RequiredArgsConstructor
public class MatchController
{
    private final MatchRepository matches;
    private final MatchStore store;
    private final MatchRuntimeRegistry registry;

    @PostMapping("/create")
    public Match create() { return store.createLobby(); }

    // --- Join by join code -> resolve matchId then store.join ---
    @PostMapping("/join/{joinCode}/{accountId}")
    public Map<String,Object> join(@PathVariable String joinCode, @PathVariable int accountId)
    {
        var match = matches.findByJoinCode(joinCode)
                .orElseThrow(() -> new NotFoundException("match not found"));
        store.join(match.getId(), accountId);
        return Map.of("matchId", match.getId(), "joinCode", match.getJoinCode());
    }

    // NOTE: Leave, Ready, Character Lock, and Start are now handled via WebSocket
    // See LobbyController.java for real-time lobby operations

    // --- End (sets ended_at; optional winner) ---
    public record EndReq(Integer winnerAccountId) {}
    @PostMapping("/{matchId}/end")
    public Map<String,Object> end(@PathVariable long matchId, @RequestBody(required = false) EndReq req)
    {
        store.markEnded(matchId, req == null ? null : req.winnerAccountId(), java.time.Instant.now());
        return Map.of("ok", true);
    }

    // --- Stop (stops runtime and removes from registry) ---
    @PostMapping("/{matchId}/stop")
    public Map<String,Object> stop(@PathVariable long matchId)
    {
        System.out.println("=== STOP CALLED: matchId=" + matchId + ", timestamp=" + System.currentTimeMillis() + " ===");
        
        var runtimeOpt = registry.get(matchId);
        
        if (runtimeOpt.isEmpty()) {
            System.out.println("  -> Runtime not found in registry");
            // Runtime not found - might already be stopped or never existed
            return Map.of("ok", false, "message", "Match runtime not found in registry (may already be stopped)");
        }
        
        var rt = runtimeOpt.get();
        System.out.println("  -> Found runtime: started=" + rt.isStarted() + ", ended=" + rt.isEnded());
        
        // Stop the runtime and remove from registry immediately
        rt.stop();  // Mark runtime as ended internally
        System.out.println("  -> Called rt.stop(), ended=" + rt.isEnded());
        
        registry.end(matchId);  // Remove from active registry - THIS STOPS THE TICKS!
        
        // Verify removal
        var check = registry.get(matchId);
        System.out.println("  -> After removal, registry.get() returns: " + check.isEmpty());
        System.out.println("  -> Registry size: " + registry.active().size());
        
        // Mark as ended in database
        store.markEnded(matchId, null, java.time.Instant.now());
        
        System.out.println("=== STOP COMPLETE ===");
        
        return Map.of("ok", true, "message", "Match runtime stopped and removed from registry");
    }

    @PostMapping("/{matchId}/results")
    public Map<String,Object> results(@PathVariable long matchId, @RequestBody List<ParticipantResult> results)
    {
        store.writeResults(matchId, results);
        return Map.of("ok", true);
    }

    // --- Lobby snapshot ---
    @GetMapping("/{matchId}/lobby")
    public List<LobbyPlayer> lobby(@PathVariable long matchId)
    {
        return store.snapshot(matchId);
    }
}
