package survivor.progress;

/**
 * @author John Draa
 * @param progressId id to track user's progress
 * @param accountId
 * @param username
 * @param coins
 * @param totalScore
 */

public record ProgressResponseDTO(
        Integer progressId,
        Integer accountId,
        String  username,
        int     coins,
        int     totalScore,
        int     rank

)
{
    public static ProgressResponseDTO from(UserProgress p, int rank)
    {
        return new ProgressResponseDTO(
                p.getId(),
                p.getAccount() != null ? p.getAccount().getId() : null,
                p.getAccount() != null ? p.getAccount().getUsername() : null,
                p.getCoins(),
                p.getTotalScore(),
                rank
        );
    }
}