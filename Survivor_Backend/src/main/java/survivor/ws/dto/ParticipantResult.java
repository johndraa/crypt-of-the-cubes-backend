package survivor.ws.dto;

/**
 * @author John Draa
 */

public record ParticipantResult(int accountId, int score, int coins, int kills, long timeAliveMs) {}
