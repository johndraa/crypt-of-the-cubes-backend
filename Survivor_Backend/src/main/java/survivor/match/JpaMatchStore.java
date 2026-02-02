package survivor.match;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import survivor.characters.GameCharacterRepository;
import survivor.ws.dto.LobbyPlayer;
import survivor.ws.dto.ParticipantResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class JpaMatchStore implements MatchStore
{

    private final MatchRepository matches;
    private final MatchParticipantRepository parts;
    private final GameCharacterRepository characters;
    private final survivor.progress.UserProgressRepository progressRepo;
    private final survivor.characters.UserCharacterUnlockRepository unlockRepo;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Override
    public Match createLobby()
    {
        String code = genCode();
        var m = Match.builder().status(MatchStatus.LOBBY).joinCode(code).build();
        return matches.save(m);
    }

    private String genCode()
    {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder s = new StringBuilder(6);
        for (int i = 0; i < 6; i++) s.append(CODE_CHARS.charAt(r.nextInt(CODE_CHARS.length())));
        return s.toString();
    }

    @Override
    public void markActive(long id, Instant t)
    {
        var m = matches.findById(id).orElseThrow();
        m.setStatus(MatchStatus.ACTIVE);
        m.setStartedAt(t);
        matches.save(m);
    }

    @Override
    public void markEnded(long id, Integer winner, Instant t)
    {
        var m = matches.findById(id).orElseThrow();
        m.setStatus(MatchStatus.ENDED);
        m.setWinnerAccountId(winner);
        m.setEndedAt(t);
        matches.save(m);
    }

    @Override
    public void join(long mid, int aid)
    {
        if (parts.existsByMatchIdAndAccountId(mid, aid)) return;
        // Enforce max 4 active participants (exclude those who have left)
        long activeCount = parts.findByMatchId(mid).stream()
                .filter(p -> p.getLeftAt() == null)
                .count();
        if (activeCount >= 4) return;
        var m = matches.findById(mid).orElseThrow();
        
        // Default everyone to WANDERER (free character)
        var wanderer = characters.findByCodeIgnoreCase("WANDERER").orElse(null);
        
        parts.save(MatchParticipant.builder()
                .match(m)
                .accountId(aid)
                .joinedAt(Instant.now())
                .ready(false)
                .selectedCharacterCode("WANDERER")
                .characterId(wanderer != null ? wanderer.getId() : null)
                .build());
    }

    @Override
    public void leave(long mid, int aid)
    {
        parts.findByMatchId(mid).stream()
                .filter(p -> p.getAccountId() == aid)
                .forEach(p -> { p.setLeftAt(Instant.now()); parts.save(p); });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void setReady(long mid, int aid, boolean r)
    {
        var participantOpt = parts.findByMatchId(mid).stream()
                .filter(p -> p.getAccountId() == aid)
                .filter(p -> p.getLeftAt() == null)  // Only update active participants
                .findFirst();
        
        if (participantOpt.isPresent()) {
            var participant = parts.findById(participantOpt.get().getId()).orElse(null);
            if (participant != null) {
                participant.setReady(r);
                parts.save(participant);
            }
        }
    }

    /**
     * Select character by code (case-insensitive). If code is null/blank, clears selection for this player.
     * Returns true if selection succeeded/cleared. Returns false if character doesn't exist or player doesn't own it.
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean lockCharacter(long mid, int aid, String code)
    {
        final String norm = (code == null) ? null : code.trim();
        
        // Find the participant entity
        var meOpt = parts.findByMatchId(mid).stream()
                .filter(p -> p.getAccountId() == aid)
                .findFirst();
        if (meOpt.isEmpty()) return false;
        
        var me = parts.findById(meOpt.get().getId()).orElse(null);
        if (me == null) return false;

        if (norm == null || norm.isBlank())
        {
            me.setSelectedCharacterCode(null);
            me.setCharacterId(null);
            me.setReady(false);
            parts.save(me);
            return true;
        }

        // Validate character exists
        var gc = characters.findByCodeIgnoreCase(norm).orElse(null);
        if (gc == null) return false; // Character doesn't exist
        
        // Special handling for WANDERER - always allowed, no ownership check
        if (gc.getCode().equals("WANDERER")) {
            // Check if already selected (early return for efficiency)
            if (me.getSelectedCharacterCode() != null 
                    && me.getSelectedCharacterCode().equalsIgnoreCase("WANDERER")) {
                return true; // Already selected - no change needed
            }
            // Just set it - WANDERER is always available
            me.setSelectedCharacterCode("WANDERER");
            me.setCharacterId(gc.getId());
            me.setReady(false);
            parts.save(me);
            return true;
        }
        
        // For non-WANDERER characters: check ownership only (no exclusivity)
        // Check if player owns this character
        boolean ownsCharacter = unlockRepo.existsByAccount_IdAndCharacter_Id(aid, gc.getId());
        if (!ownsCharacter) return false; // Player doesn't own this character

        // Check if already selected by this player
        boolean alreadySelectedByMe = me.getSelectedCharacterCode() != null 
                && me.getSelectedCharacterCode().equalsIgnoreCase(norm);
        if (alreadySelectedByMe) {
            return true; // Already selected - no change needed
        }

        // Set the selection - multiple players can select the same character
        me.setSelectedCharacterCode(norm);
        me.setCharacterId(gc.getId());
        me.setReady(false);
        parts.save(me);
        return true;
    }

    @Override
    public List<LobbyPlayer> snapshot(long mid)
    {
        return parts.findByMatchId(mid).stream()
                .filter(p -> p.getLeftAt() == null)
                .map(p -> new LobbyPlayer(
                        p.getAccountId(),
                        p.getSelectedCharacterCode(),
                        Boolean.TRUE.equals(p.getReady())))
                .toList();
    }

    @Override
    public void writeResults(long mid, List<ParticipantResult> res)
    {
        var list = parts.findByMatchId(mid);
        for (var r : res)
        {
            list.stream()
                    .filter(p -> p.getAccountId() == r.accountId())
                    .findFirst()
                    .ifPresent(p ->
                    {
                        // Save to MatchParticipant
                        p.setScore(r.score());
                        p.setCoinsEarned(r.coins());
                        p.setTimeAliveMs(r.timeAliveMs());
                        parts.save(p);

                        // Update UserProgress with earned coins and score
                        progressRepo.findByAccount_Id(r.accountId())
                                .ifPresent(progress -> {
                                    progress.setCoins(progress.getCoins() + r.coins());
                                    progress.setTotalScore(progress.getTotalScore() + r.score());
                                    progressRepo.save(progress);
                                });
                    });
        }
    }
}
