package survivor.ws.dto;

import survivor.match.MatchStatus;

import java.util.List;

/**
 * @author John Draa
 */

public record LobbySnapshot(long matchId, MatchStatus status, List<LobbyPlayer> players){}
