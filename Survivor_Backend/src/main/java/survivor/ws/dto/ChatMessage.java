package survivor.ws.dto;

import java.time.Instant;

/**
 * @author John Draa
 * @param matchId
 * @param accountId
 * @param username
 * @param text
 * @param sentAt
 */

public record ChatMessage(long matchId, int accountId, String username, String text, Instant sentAt){}