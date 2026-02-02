package survivor.match;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import survivor.config.FogConfig;
import survivor.runtime.MatchRuntimeRegistry;
import survivor.ws.GameWs;
import survivor.ws.dto.*;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import survivor.characters.GameCharacterRepository;
import survivor.characters.GameCharacter;          // PA entity
import survivor.shared.AttackStyle;                 // shared enum
import survivor.combat.Stats;
import survivor.combat.Vec2;
import survivor.model.PlayerState;

@Component
@RequiredArgsConstructor
public class LobbySocketHandler
{
    private final MatchStore store;
    private final MatchRepository matches;
    private final FogConfig fog;
    private final MatchRuntimeRegistry runtimes;
    private final GameWs ws;
    private final GameCharacterRepository characters;
    private final survivor.characters.UserCharacterUnlockRepository unlockRepo;

    private final Map<Long, Deque<ChatMessage>> chatHistory = new ConcurrentHashMap<>();

    // JOIN / LEAVE / READY / SELECT
    public void onJoin(JoinMsg m)
    {
        store.join(m.matchId(), m.accountId());
        broadcast(m.matchId());
    }

    public void onLeave(survivor.ws.dto.LeaveMsg m)
    {
        store.leave(m.matchId(), m.accountId());
        broadcast(m.matchId());
    }

    public void onReady(ReadyMsg m)
    {
        store.setReady(m.matchId(), m.accountId(), m.ready());
        broadcast(m.matchId());
    }

    public void onSelect(SelectMsg m)
    {
        if (store.lockCharacter(m.matchId(), m.accountId(), m.characterCode()))
        {
            broadcast(m.matchId());
        }
        else
        {
            // Determine specific reason for failure
            String reason = "Unknown error";
            String characterCode = m.characterCode();
            
            if (characterCode == null || characterCode.isBlank()) {
                reason = "No character specified";
            } else {
                var gc = characters.findByCodeIgnoreCase(characterCode).orElse(null);
                if (gc == null) {
                    reason = "Character does not exist";
                } else if (!gc.getCode().equals("WANDERER")) {
                    // Check ownership for non-WANDERER characters
                    boolean ownsCharacter = unlockRepo.existsByAccount_IdAndCharacter_Id(m.accountId(), gc.getId());
                    if (!ownsCharacter) {
                        reason = "Character not owned";
                    } else {
                        // This should not happen - ownership check passed but selection failed
                        reason = "Unexpected error during character selection";
                    }
                } else {
                    // WANDERER should never fail (it's non-exclusive and free)
                    // This case should not happen, but just in case
                    reason = "Unexpected error with WANDERER selection";
                }
            }
            
            sendPrivate(m.matchId(), m.accountId(), Map.of(
                    "event", "SELECT_DENIED",
                    "characterCode", characterCode,
                    "reason", reason
            ));
        }
    }

    // CHAT
    public void onChat(ChatMessage msg)
    {
        if (msg.text() == null || msg.text().isBlank()) return;
        ChatMessage c = new ChatMessage(
                msg.matchId(), msg.accountId(), msg.username(),
                msg.text().trim(), Instant.now()
        );
        chatHistory.computeIfAbsent(msg.matchId(), k -> new ArrayDeque<>()).addLast(c);
        if (chatHistory.get(msg.matchId()).size() > 50) chatHistory.get(msg.matchId()).removeFirst();
        ws.chat(msg.matchId(), c);
    }

    // START
    public void onRequestStart(RequestStartMsg m)
    {
        var snap = store.snapshot(m.matchId());
        
        // Validation checks
        if (snap.isEmpty()) {
            sendToMatch(m.matchId(), Map.of("event", "START_FAILED", "reason", "No players in lobby"));
            return;
        }
        
        // Commented out to allow single player matches for testing
        //TODO switch back to this logic for production
        // if (snap.size() < 2) {
        //     sendToMatch(m.matchId(), Map.of("event", "START_FAILED", "reason", "Need at least 2 players"));
        //     return;
        // }

        if (snap.size() > 4) {
            sendToMatch(m.matchId(), Map.of("event", "START_FAILED", "reason", "Can't have more than 4 players"));
            return;
        }
        
        
        boolean allReady = snap.stream().allMatch(LobbyPlayer::ready);
        boolean allPicked = snap.stream().allMatch(lp -> lp.characterCode() != null);
        
        if (!allReady) {
            var unreadyPlayers = snap.stream()
                    .filter(lp -> !lp.ready())
                    .map(LobbyPlayer::accountId)
                    .toList();
            sendToMatch(m.matchId(), Map.of(
                    "event", "START_FAILED", 
                    "reason", "Not all players ready",
                    "unreadyPlayers", unreadyPlayers
            ));
            return;
        }
        
        if (!allPicked) {
            var unselectedPlayers = snap.stream()
                    .filter(lp -> lp.characterCode() == null)
                    .map(LobbyPlayer::accountId)
                    .toList();
            sendToMatch(m.matchId(), Map.of(
                    "event", "START_FAILED", 
                    "reason", "Not all players have selected characters",
                    "unselectedPlayers", unselectedPlayers
            ));
            return;
        }

        // All conditions met - prepare the match
        store.markActive(m.matchId(), Instant.now());
        
        // Create runtime without starting it yet (will start after countdown)
        runtimes.createRuntime(m.matchId(), fog);

        //add players to runtime using characterCode + findByCodeIgnoreCase
        runtimes.get(m.matchId()).ifPresent(rt ->
        {
            int i = 0;
            for (var lp : snap)
            {
                String code = lp.characterCode();
                if (code == null || code.isBlank()) continue; // defensive

                var gcOpt = characters.findByCodeIgnoreCase(code);
                if (gcOpt.isEmpty()) {
                    System.err.println("Character not found: " + code);
                    continue;
                }

                GameCharacter gc = gcOpt.get();

                // Map JPA entity -> runtime Stats
                Stats stats = new Stats(
                        gc.getHealth(),
                        gc.getMoveSpeed(),
                        gc.getAttackSpeed(),
                        gc.getDamageMult(),
                        gc.getCritChance(),
                        gc.getRangeUnits()
                );

                // Everyone starts with AOE weapon (upgrades via XP system change this later)
                AttackStyle style = AttackStyle.AOE;

                Vec2 spawn = spawnForIndex(i++);
                PlayerState p = new PlayerState(lp.accountId(), spawn, stats, style, stats.health());
                rt.addPlayer(p);
            }
        });

        // Send countdown start message (client-side UI only)
        int countdownSeconds = 3;
        sendToMatch(m.matchId(),
                new StartMsg(m.matchId(), new Fog(fog.getLight(), fog.getWake(), fog.getSleep()), countdownSeconds));

        // Start the runtime immediately (client will handle countdown UI)
        runtimes.startExistingRuntime(m.matchId());
    }

    private Vec2 spawnForIndex(int idx)
    {
        // Keep lobby spawn positions aligned with the 2000x2000 world used by Physics/MatchRuntime
        final int TILE = 24;
        final int MAP_W = 2000;
        final int MAP_H = 2000;
        final int M = 4 * TILE; // margin from each edge (in pixels)

        return switch (idx % 4)
        {
            case 0 -> new Vec2(M, M);
            case 1 -> new Vec2(MAP_W - M, M);
            case 2 -> new Vec2(M, MAP_H - M);
            default -> new Vec2(MAP_W - M, MAP_H - M);
        };
    }

    private void broadcast(long mid) {
        var match = matches.findById(mid).orElseThrow();
        sendToMatch(mid, new LobbySnapshot(mid, match.getStatus(), store.snapshot(mid)));
    }

    private void sendToMatch(long mid, Object payload) {
        ws.lobby(mid, payload);
    }

    private void sendPrivate(long mid, int accountId, Object payload) {
        ws.toPlayer(mid, accountId, payload);
    }
}
