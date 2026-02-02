package survivor.ws.dto;

/**
 * @author John Draa
 * @param matchId
 * @param fog
 * @param countdown
 */

public record StartMsg(long matchId, Fog fog, int countdown){}
