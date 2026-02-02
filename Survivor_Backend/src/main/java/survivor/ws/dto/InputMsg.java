package survivor.ws.dto;

/**
 * @author John Draa
 * @param matchId
 * @param accountId
 * @param moveX
 * @param moveY
 * @param seq
 */

public record InputMsg(long matchId, int accountId, float moveX, float moveY, long seq) {}
