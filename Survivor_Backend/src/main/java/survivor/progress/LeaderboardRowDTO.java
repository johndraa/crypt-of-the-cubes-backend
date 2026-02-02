package survivor.progress;


/**
 * @author John Draa
 */

public record LeaderboardRowDTO(
        Integer accountId,
        String username,
        int totalScore,
        int rank
) {}