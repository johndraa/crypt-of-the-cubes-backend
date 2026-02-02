package survivor.match;

import survivor.ws.dto.LobbyPlayer;
import survivor.ws.dto.ParticipantResult;

import java.time.Instant;
import java.util.List;

public interface MatchStore
{
    Match createLobby();

    void markActive(long id, Instant startedAt);
    void markEnded(long id, Integer winnerAccountId, Instant endedAt);

    void join(long matchId, int accountId);
    void leave(long matchId, int accountId);

    void setReady(long matchId, int accountId, boolean ready);

    boolean lockCharacter(long matchId, int accountId, String characterCode);

    // live view for WS
    List<LobbyPlayer> snapshot(long matchId);

    // persist final per-player results
    void writeResults(long matchId, List<ParticipantResult> results);

    // (optional, nice later) boolean isParticipant(long matchId, int accountId);
}